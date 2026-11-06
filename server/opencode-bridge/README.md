# Mobie OpenCode bridge

Mobie's optional cloud mode connects to the user's Linux box. It runs genuine OpenCode in a fresh container and forwards replies to the phone. Downloaded models continue to use the local Android runtime. Direct anonymous requests to OpenCode's free API are not used.

The deployed instance is `yvm@192.168.1.8`, with public HTTPS at `https://mobie.yvmx.dpdns.org`. The authenticated catalog now discovers every `opencode/` model advertised by the isolated genuine CLI and caches the list for five minutes. Both the app and gateway accept new model IDs without a fixed model allowlist. Model availability does not guarantee a successful reply; OpenCode access restrictions, usage limits and account requirements still apply.

## Pair a phone

Install the APK, open Settings, paste the pairing key under Cloud connection and choose Save connection. The deployed server address is prefilled. The app stores the key separately from Hugging Face credentials using Android Keystore-backed encrypted preferences. The key is never bundled in the APK, put in a URL, or committed.

The private pairing key is `~/mobie-cloud/pairing.key` on the Linux box. Deliver it to the owner using an encrypted file handoff. To rotate it, replace that file with a newly generated random key of at least 32 characters, restart `mobie-cloud.service`, and pair the phone again.

## Operation

Persistent files and commands live under `~/mobie-cloud` on the Linux box. User services are enabled and lingering is enabled for `yvm`:

```sh
systemctl --user status mobie-cloud.service mobie-cloud-egress.service mobie-cloud-tunnel.service
systemctl --user restart mobie-cloud.service
```

- The chat bridge binds only `127.0.0.1:18787`.
- A separate named Cloudflare tunnel serves HTTPS. Its configuration is `~/mobie-cloud/cloudflared.yml`, with a catch-all 404 for other hosts. Existing sites, tunnels and Tailscale Funnel were preserved.
- The egress proxy binds the Docker bridge address `172.17.0.1:18788`.
- Requests require the bearer pairing key. Only health is public. The exposed API has catalog and chat routes, with no OpenCode administrative, file, shell or session-management routes.
- One inference runs at a time. Busy requests return 429 with Retry-After. Requests have a 180-second deadline, bounded input/output, and no bridge retries.
- Phone disconnects terminate the process and container. Each request's photos are removed afterward, and the container's temporary home/session data disappears with it. Content is not logged, including Docker's log driver.

Cloudflare terminates public HTTPS. Selected cloud conversations and photos pass through Cloudflare, the Linux box, OpenCode and its model providers. Provider retention/training policies still apply. This is cloud inference, not on-device inference.

## Isolation

The container exposes genuine built-in OpenCode tools, but the phone cannot administer them. Default-agent OpenCode is required because the tested tool-denied configuration itself received a free-tier denial. Tools operate inside an empty disposable container, with no host home, credentials, source repositories or Docker socket mounted.

The filesystem is read-only except bounded temporary directories. Inference runs as UID 1000 with no capabilities and no-new-privileges. Its network namespace permits only loopback and the egress proxy. The proxy permits CONNECT to `opencode.ai:443` and `models.dev:443`, checks public destination addresses, and requires matching TLS SNI before relaying encrypted bytes. Direct internet, LAN, metadata, IPv6 and unrelated public destinations are blocked.

## Pinned artifacts

- Genuine OpenCode `v2.0.32`, stored at `~/mobie-cloud/bin/opencode`.
- CLI SHA-256: `489b51816a77809e841310e80c88e834c28c19ad1639e12ba8a19f08b47ab607`.
- Ubuntu base digest is pinned in `container/Dockerfile`.
- The reviewed built image ID is pinned in `opencode-container`; its CLI binary and nonsecret configuration are mounted read-only.
- Repository files contain source/configuration templates only. Binary releases, pairing keys, tunnel credentials, model outputs and uploaded photos stay out of Git.

To rebuild the container on the Linux box, copy `container/Dockerfile` and `container/entrypoint` into `~/mobie-cloud/container`, build it with Docker, verify the image, and update the runner to the new immutable image ID. Deploy `sandbox-opencode.json` to `~/mobie-cloud/config/`. Review changes before replacing the pinned binary or image.

## Verification

Run app checks without an emulator:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
python3 -W error::ResourceWarning -m unittest discover -s server/opencode-bridge -p 'test_*.py'
```

Transport tests cover dynamic catalog metadata and new model IDs, pairing, payloads, credential redaction, cancellation, redirects, error handling and completion. Bridge tests use a subprocess fixture to verify catalog discovery and caching, new model selection, catalog failures, the CLI boundary, stdin prompts, inline photos, authentication, denied administrative routes and temporary-file cleanup.

Real Linux-box probes verified DeepSeek text through the genuine CLI and photo input through the hardened container. A larger image containing an unpredictable four-digit number on a blue background was read correctly. Tiny solid-color images gave inconsistent color answers, so model output accuracy is not guaranteed. CLI JSON output currently provides completed text blocks rather than individual token deltas; the app displays those as they arrive.

Android UI verification for the catalog and settings update uses an Android 15 emulator. Native model execution still requires an exact-artifact test on a physical ARM64 device. See [UI verification notes](../../docs/ui/navigation-settings/README.md).

## Catalog capabilities

`GET /v1/models` returns model IDs, readable titles, image support and a privacy notice. The CLI is the availability authority. Unknown model capabilities default to text only until reviewed, so a newly listed model never receives photos based on a guess. Only safe `opencode/` IDs are accepted; arbitrary providers, paths and shell fragments are rejected. NVIDIA trial notices explicitly prohibit personal or confidential inputs. No provider authentication is bundled with the app or introduced by discovery.

The catalog command uses the same disposable container and restricted network as inference, with a 30-second deadline. A failed refresh returns 503 instead of pretending the old DeepSeek-only list is current. Paid models appear only if the genuine CLI advertises them for its configured account; discovery does not create access or add credentials.

Deployment check on 2026-10-18: authenticated localhost and public HTTPS catalog calls returned 12 current OpenCode models. A synthetic Big Pickle text request completed through the genuine container in 11.7 seconds, including content and the completion marker. This proves gateway discovery and one additional model reply, not Android validation of every offering. Public HTTPS probes must send Mobie's normal user agent; the default Python user agent received a Cloudflare 403.
