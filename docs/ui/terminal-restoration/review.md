# Independent review response

Claude Opus 5.5 compared main (`382b61c`), published PR #18 (`5a765cb`), and PR #35 (`50bda2d`). The review was read-only and did not claim device validation.

- **Wrong conversation after refused load:** accepted. The restoration ports PR #35's explicit engine-preservation rule. Only a settings reload can retain the current engine. History reset/replacement failures clear runtime presentation and block prompts. Exceptions become load failures rather than escaping the ViewModel coroutine.
- **JNI names removed or renamed by R8:** accepted. Added a keep rule for the LiteRT-LM SDK, plus a release-mapping check in Android CI. The check reproduced the problem against the original release mapping. Physical release inference remains pending.
- **Persistence and Keystore work on main:** accepted. Off-main persistence changes and regression tests are being integrated independently of the rejected UI.
- **UI differences introduced by main's #34:** accepted. Restored the terminal catalog directly from published PR #18 and restored its scanline backdrop. Runtime settings retain the newer terminal sheet.
- **GPU crash retry marker:** deferred. The review described a possible native driver crash loop, not a reproduced failure. No speculative persistent recovery mechanism is included in this restoration.
- **RAM and speed estimates:** retain estimate labels. Earlier single-model measurements do not certify other artifacts or devices.
- **Physical runtime qualification:** unresolved. No physical ARM64 device is connected. The PR must remain draft until final-build evidence covers the exact artifact, lifecycle/reset loop, CPU control, and release APK.
