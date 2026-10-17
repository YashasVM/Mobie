# Portfolio integration candidate

This branch combines chat-first home, cross-model history, local text-document attachments and the GPU compute branch. It is blocked for physical-device qualification. Do not treat the historical phone benchmarks as verification of this branch.

The integration preserves current main's home-screen work. The terminal-theme commit is patch-equivalent to main and the inference-efficiency branch adds only that same commit and a merge. The older chat-first branch contributes bounded local document attachments. Its older navigation/theme is superseded by the newer chat-home branch. Cleanup workers from that branch run on IO; atomic startup is limited to cancellation/cleanup, so expired loads remain cancellable.

Further fixes keep an owned engine through refused reload admission, distinguish confirmed ownership from conservative deletion ownership, latch reload-required native error events, avoid Default-dispatcher starvation in JNI operations, require a process restart after uncertain JNI cleanup, and load saved compute settings before restoring the launch chat. Tests include the independent runtime review's unchanged reproduction source and a ViewModel orchestration test.

Run the repository checks on this exact candidate. Unit/emulator results and logs are recorded in the external portfolio worker report. Inference stays local. The only runtime dependency change is the reviewed LiteRT-LM pin from 0.16.1 to 0.18.0 with bundled license notices. No permissions or inference network endpoint are added.

Before native qualification, run the final reset/rebuild, cancel/recover, unload/reload, backend-switch and sustained workloads on a physical ARM64 device using the exact artifact, including release APK execution. Record device/Android/artifact SHA, peak RAM, first-token latency, throughput and thermal behavior. The API 35 x86_64 emulator cannot establish GPU driver behavior or physical memory release.
