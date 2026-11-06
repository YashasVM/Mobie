"""Run with python3 -m unittest discover -s server/opencode-bridge -v."""
import base64
import http.client
import importlib.util
import json
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("mobie_bridge", Path(__file__).with_name("bridge.py"))
bridge = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(bridge)
PNG = b"\x89PNG\r\n\x1a\n"


def payload(content="private test prompt"):
    return {"model": "deepseek-v4.1-flash-free", "stream": True,
            "messages": [{"role": "user", "content": content}]}


def image_content(data=PNG, mime="png"):
    return [{"type": "text", "text": "describe"},
            {"type": "image_url", "image_url": {
                "url": f"data:image/{mime};base64,{base64.b64encode(data).decode()}"}}]


class CatalogTest(unittest.TestCase):
    def test_discovers_all_cli_models_without_a_fixed_allowlist(self):
        catalog = bridge.catalog_models("opencode/deepseek-v4.1-flash-free\nopencode/new-model-free\nopencode/new-paid-model\nopencode/new-model-free\n")
        self.assertEqual(["deepseek-v4.1-flash-free", "new-model-free", "new-paid-model"], list(catalog))
        self.assertTrue(catalog["deepseek-v4.1-flash-free"]["images"])
        self.assertFalse(catalog["new-model-free"]["images"])

    def test_rejects_other_providers_and_unsafe_ids(self):
        self.assertEqual({}, bridge.catalog_models("anthropic/claude\nopencode/../../etc\nopencode/--admin\nopencode/a;b\n"))

    def test_new_cli_model_can_generate_but_unknown_model_cannot(self):
        with tempfile.TemporaryDirectory() as temporary:
            models = bridge.catalog_models("opencode/new-model-free")
            self.assertEqual("new-model-free", bridge.conversation(dict(payload(), model="new-model-free"), Path(temporary), models)[0])
            with self.assertRaises(ValueError):
                bridge.conversation(payload(), Path(temporary), models)


class CrashRecoveryTest(unittest.TestCase):
    def test_removes_only_stale_request_directories(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stale = root / "chat-crashed"
            stale.mkdir()
            (stale / "photo.png").write_bytes(PNG)
            unrelated = root / "other-data"
            unrelated.mkdir()
            (unrelated / "keep").write_text("keep")
            (root / "chat-link").symlink_to(unrelated, target_is_directory=True)
            bridge.cleanup_stale_requests(root)
            self.assertFalse(stale.exists())
            self.assertEqual("keep", (unrelated / "keep").read_text())
            self.assertTrue((root / "chat-link").is_symlink())


class ConversationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def test_accepts_png_and_writes_only_expected_attachment(self):
        model, prompt, attachments = bridge.conversation(payload(image_content()), self.directory)
        self.assertEqual("deepseek-v4.1-flash-free", model)
        self.assertEqual(["photo-1.png"], attachments)
        self.assertEqual(PNG, (self.directory / attachments[0]).read_bytes())
        self.assertIn("describe", prompt)
        self.assertEqual(["photo-1.png"], [entry.name for entry in self.directory.iterdir()])

    def test_rejects_remote_image_urls(self):
        for url in ["https://attacker.invalid/photo", "file:///etc/passwd", "http://127.0.0.1/admin"]:
            with self.subTest(url=url), self.assertRaises(ValueError):
                bridge.conversation(payload([{"type": "image_url", "image_url": {"url": url}}]), self.directory)

    def test_rejects_invalid_models_roles_stream_and_final_assistant(self):
        invalid = [dict(payload(), model="paid-model"), dict(payload(), stream=False),
                   dict(payload(), messages=[]),
                   dict(payload(), messages=[{"role": "system", "content": "rules"}]),
                   dict(payload(), messages=[{"role": "assistant", "content": "answer"}])]
        for item in invalid:
            with self.subTest(item=item), self.assertRaises(ValueError):
                bridge.conversation(item, self.directory)

    def test_rejects_bad_base64_and_mismatched_magic(self):
        content = [{"type": "image_url", "image_url": {"url": "data:image/png;base64,A"}}]
        for item in [content, image_content(b"not a PNG"), image_content(PNG, "jpeg")]:
            with self.subTest(item=item), self.assertRaises(ValueError):
                bridge.conversation(payload(item), self.directory)

    def test_enforces_cumulative_photo_limit(self):
        with patch.object(bridge, "MAX_PHOTOS", len(PNG)):
            with self.assertRaises(ValueError):
                bridge.conversation(payload(image_content() + image_content()), self.directory)


class HttpBridgeTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.runner = self.root / "fake-opencode"
        self.capture = self.root / "invocation.json"
        self.requests = self.root / "requests"
        self.configure_runner([{"type": "text", "part": {"text": "Hello from OpenCode"}}])
        self.server = bridge.Bridge(("127.0.0.1", 0), "test-pairing-key", self.runner, self.requests, models=bridge.MODELS)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)
        self.temporary.cleanup()

    def configure_runner(self, events, exit_code=0):
        source = ("#!/usr/bin/python3\nimport json, os, pathlib, sys\n"
                  "prompt = sys.stdin.read()\n"
                  f"pathlib.Path({str(self.capture)!r}).write_text(json.dumps({{'args': sys.argv[1:], "
                  "'prompt': prompt, 'cwd': os.getcwd(), 'photos': sorted(p.name for p in pathlib.Path('.').glob('photo-*'))}))\n"
                  f"events = {events!r}\n"
                  "for event in events:\n print(json.dumps(event), flush=True)\n"
                  f"sys.exit({exit_code})\n")
        self.runner.write_text(source)
        self.runner.chmod(0o700)

    def request(self, method, path, body=None, token="test-pairing-key", content_type="application/json"):
        connection = http.client.HTTPConnection(*self.server.server_address, timeout=5)
        headers = {"Content-Type": content_type}
        if token is not None:
            headers["Authorization"] = "Bearer " + token
        connection.request(method, path, json.dumps(body).encode() if body is not None else None, headers)
        response = connection.getresponse()
        result = response.status, dict(response.getheaders()), response.read().decode()
        connection.close()
        return result

    def assert_clean(self):
        deadline = time.monotonic() + 2
        while list(self.requests.iterdir()) and time.monotonic() < deadline:
            time.sleep(0.01)
        self.assertEqual([], list(self.requests.iterdir()))

    def test_missing_and_wrong_credentials_are_denied(self):
        for token in [None, "wrong-key"]:
            for method, path in [("GET", "/v1/models"), ("POST", "/v1/chat/completions")]:
                with self.subTest(token=token, method=method):
                    self.assertEqual(401, self.request(method, path, payload(), token)[0])
        self.assertFalse(self.capture.exists())

    def test_catalog_and_health_without_exposing_admin_routes(self):
        self.assertEqual(200, self.request("GET", "/health", token=None)[0])
        status, _, body = self.request("GET", "/v1/models")
        self.assertEqual(200, status)
        self.assertEqual(set(bridge.MODELS), {row["id"] for row in json.loads(body)["data"]})
        for path in ["/session", "/config", "/global/event", "/v1/models?admin=1", "/"]:
            for method in ["GET", "POST"]:
                with self.subTest(path=path, method=method):
                    self.assertEqual(404, self.request(method, path, payload())[0])
        self.assertFalse(self.capture.exists())

    def test_catalog_discovers_cli_models_and_new_model_can_be_selected(self):
        self.server._models = None
        self.server._models_until = 0
        self.runner.write_text("#!/bin/sh\nprintf 'opencode/new-free\\nopencode/big-pickle\\n'\n")
        self.runner.chmod(0o700)
        status, _, body = self.request("GET", "/v1/models")
        self.assertEqual(200, status)
        rows = json.loads(body)["data"]
        self.assertEqual(["new-free", "big-pickle"], [row["id"] for row in rows])
        self.assertEqual("New Free", rows[0]["title"])
        self.assertFalse(rows[0]["images"])
        self.configure_runner([{"type": "text", "part": {"text": "New model answer"}}])
        status, _, body = self.request("POST", "/v1/chat/completions", dict(payload(), model="new-free"))
        self.assertEqual(200, status)
        self.assertIn("New model answer", body)
        self.assertIn("opencode/new-free", json.loads(self.capture.read_text())["args"])
        self.assert_clean()

    def test_catalog_failure_is_explicit_and_never_advertises_stale_defaults(self):
        self.server._models = None
        self.server._models_until = 0
        self.configure_runner([], 1)
        status, _, body = self.request("GET", "/v1/models")
        self.assertEqual(503, status)
        self.assertEqual("catalog_unavailable", json.loads(body)["error"]["code"])
        self.assert_clean()

    def test_success_streams_real_cli_event_and_completion_with_stdin_prompt(self):
        status, headers, body = self.request("POST", "/v1/chat/completions", payload(image_content()),
                                             content_type="application/json; charset=utf-8")
        self.assertEqual(200, status)
        self.assertEqual("text/event-stream", headers["Content-Type"])
        self.assertIn('"content": "Hello from OpenCode"', body)
        self.assertIn('"finish_reason": "stop"', body)
        self.assertIn("data: [DONE]", body)
        invocation = json.loads(self.capture.read_text())
        self.assertEqual(["run", "--standalone", "--format", "json", "--model", "opencode/deepseek-v4.1-flash-free",
                          "--file", "/request/photo-1.png"], invocation["args"])
        self.assertIn("describe", invocation["prompt"])
        self.assertEqual(["photo-1.png"], invocation["photos"])
        self.assert_clean()
        self.assertFalse(Path(invocation["cwd"]).exists())

    def test_process_failure_never_claims_completion(self):
        cases = [([], 0), ([{"type": "error", "error": "private details"}], 0),
                 ([{"type": "text", "part": {"text": "Partial"}}], 1),
                 ([{"type": "reasoning", "part": {"text": "Thinking"}}], 0)]
        for events, status in cases:
            with self.subTest(events=events, status=status):
                self.configure_runner(events, status)
                code, _, body = self.request("POST", "/v1/chat/completions", payload())
                self.assertEqual(200, code)
                self.assertIn('"error"', body)
                self.assertNotIn("[DONE]", body)
                self.assertNotIn("finish_reason", body)
                self.assertNotIn("private details", body)
                self.assert_clean()

    def test_invalid_contract_or_content_type_never_starts_cli(self):
        self.assertEqual(400, self.request("POST", "/v1/chat/completions", payload(),
                                          content_type="text/plain")[0])
        self.assertEqual(400, self.request("POST", "/v1/chat/completions", dict(payload(), model="invalid"))[0])
        self.assertFalse(self.capture.exists())
        self.assert_clean()


if __name__ == "__main__":
    unittest.main()
