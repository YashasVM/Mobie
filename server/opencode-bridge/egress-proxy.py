#!/usr/bin/python3
"""CONNECT-only egress for isolated Mobie inference containers; never records requests."""
import ipaddress
import select
import socket
import socketserver
import threading
import time

ALLOWED = frozenset({"opencode.ai", "models.dev"})
SLOTS = threading.BoundedSemaphore(16)

def tls_client_hello(client, initial, expected_host):
    wire = bytearray(initial)
    handshake = bytearray()
    offset = 0
    def ensure(count):
        while len(wire) < count:
            block = client.recv(min(16384, count - len(wire)))
            if not block:
                raise ValueError("incomplete TLS handshake")
            wire.extend(block)
    while True:
        ensure(offset + 5)
        if wire[offset] != 22 or wire[offset + 1] != 3:
            raise ValueError("TLS ClientHello required")
        length = int.from_bytes(wire[offset + 3:offset + 5], "big")
        if length > 18432 or offset + 5 + length > 65536:
            raise ValueError("TLS handshake too large")
        ensure(offset + 5 + length)
        handshake.extend(wire[offset + 5:offset + 5 + length])
        offset += 5 + length
        if len(handshake) >= 4:
            if handshake[0] != 1:
                raise ValueError("ClientHello required")
            needed = 4 + int.from_bytes(handshake[1:4], "big")
            if needed > 65536:
                raise ValueError("TLS handshake too large")
            if len(handshake) >= needed:
                break
    body = memoryview(handshake)[4:needed]
    pos = 34
    def take(count):
        nonlocal pos
        if count < 0 or pos + count > len(body):
            raise ValueError("invalid TLS ClientHello")
        value = bytes(body[pos:pos + count]); pos += count
        return value
    take(int.from_bytes(take(1), "big"))
    take(int.from_bytes(take(2), "big"))
    take(int.from_bytes(take(1), "big"))
    extensions = take(int.from_bytes(take(2), "big"))
    pos = 0
    while pos + 4 <= len(extensions):
        kind = int.from_bytes(extensions[pos:pos + 2], "big")
        length = int.from_bytes(extensions[pos + 2:pos + 4], "big")
        value = extensions[pos + 4:pos + 4 + length]
        if len(value) != length:
            raise ValueError("invalid TLS extension")
        pos += 4 + length
        if kind == 0:
            if len(value) < 5 or value[2] != 0 or int.from_bytes(value[:2], "big") != len(value) - 2:
                raise ValueError("invalid SNI")
            name_length = int.from_bytes(value[3:5], "big")
            if name_length != len(value) - 5 or value[5:].decode("ascii") != expected_host:
                raise ValueError("SNI does not match allowlist")
            return bytes(wire)
    raise ValueError("SNI required")

class Handler(socketserver.BaseRequestHandler):
    def reject(self, status=403):
        self.request.sendall(f"HTTP/1.1 {status} Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encode())

    def handle(self):
        if not SLOTS.acquire(blocking=False):
            self.reject(503)
            return
        try:
            self.tunnel()
        except (OSError, ValueError, UnicodeError):
            pass
        finally:
            SLOTS.release()

    def tunnel(self):
        client = self.request
        if not ipaddress.ip_address(self.client_address[0]) in ipaddress.ip_network("172.17.0.0/16"):
            self.reject()
            return
        client.settimeout(10)
        data = b""
        while b"\r\n\r\n" not in data:
            block = client.recv(2048)
            if not block:
                return
            data += block
            if len(data) > 8192:
                self.reject(431)
                return
        header, remainder = data.split(b"\r\n\r\n", 1)
        parts = header.split(b"\r\n", 1)[0].decode("ascii").split()
        if len(parts) != 3 or parts[0] != "CONNECT" or parts[2] not in {"HTTP/1.0", "HTTP/1.1"}:
            self.reject()
            return
        authority = parts[1]
        if ":" not in authority:
            self.reject()
            return
        host, port = authority.rsplit(":", 1)
        if host not in ALLOWED or port != "443":
            self.reject()
            return
        client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
        hello = tls_client_hello(client, remainder, host)
        addresses = socket.getaddrinfo(host, 443, type=socket.SOCK_STREAM)
        if not addresses or any(not ipaddress.ip_address(a[4][0]).is_global for a in addresses):
            self.reject()
            return
        upstream = None
        for family, socktype, proto, _, address in sorted(addresses, key=lambda a: a[0] != socket.AF_INET):
            candidate = socket.socket(family, socktype, proto)
            candidate.settimeout(10)
            try:
                candidate.connect(address)
            except OSError:
                candidate.close()
                continue
            upstream = candidate
            break
        if upstream is None:
            self.reject(502)
            return
        with upstream:
            upstream.sendall(hello)
            deadline = time.monotonic() + 210
            client.settimeout(20)
            upstream.settimeout(20)
            while time.monotonic() < deadline:
                readable, _, _ = select.select([client, upstream], [], [], min(30, deadline - time.monotonic()))
                if not readable:
                    continue
                for source in readable:
                    chunk = source.recv(65536)
                    if not chunk:
                        return
                    (upstream if source is client else client).sendall(chunk)

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True
    request_queue_size = 16

if __name__ == "__main__":
    with Server(("172.17.0.1", 18788), Handler) as server:
        server.serve_forever()
