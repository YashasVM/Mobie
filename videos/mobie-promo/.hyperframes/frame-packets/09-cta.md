# Frame packet: 09-cta

## Project inputs

- Project: /home/yvm/code/Mobie/videos/mobie-promo
- Design tokens: /home/yvm/code/Mobie/videos/mobie-promo/frame.md
- RULES_DIR: /home/yvm/.claude/skills/hyperframes-animation/rules

## Assigned storyboard block

## Frame 9 — Get Mobie

- scene: The site's footer/CTA: "$ that's it. go try it." + [ download apk ] [ star on github ] + requirements + "mobie(1) · made by yashas … EOF"
- voiceover: "Mobie. Your AI, offline. Free and open source. Grab it on GitHub."
- duration: 7.3s
- transition_in: cut
- status: animated
- src: compositions/frames/09-cta.html
- type: cta
- persuasion: Friction reduction
- beat: motivation + urgency-to-act
- blueprint: compose
- asset_candidates:

narrativeRole: brand + action.
keyMessage: download Mobie from GitHub.

Tmux bar: active tab `0:mobie`.
Scene 1 (0.0–1.0s): on "Mobie." the ASCII logo (same as frame 3, amber, ~40px) prints instantly at top of the
content column (y≈150) — a hard-cut callback.
Scene 2 (1.0–2.9s): under it, `$ your ai. offline.` types word-cued (104px, 600; `$` amber; `offline.` amber).
Scene 3 (2.9–4.6s): on "Free and open source" a requirements mini-table prints (34px; keys amber, values `--fg`):
`android  8.0 or newer` / `cpu      64-bit ARM (arm64-v8a)` / `license  free & open source`.
Scene 4 (4.6–5.6s): on "Grab it on GitHub" two buttons snap in side by side (site style, 40px, 1px border, 0 radius,
padding 18px 28px): `[ download apk ]  d` (amber border + amber text) and `[ star on github ]  g` (`--faint` border,
`--fg` text); at "Grab" the download button gets pressed: fills amber with `--bg` text (instant, like :hover). Then
`github.com/YashasVM/Mobie` types under them (40px `--fg`, `›` amber prefix) by ~5.6s.
Scene 5 (5.6–7.3s): footer hairline at y≈1000 with `mobie(1) · made by yashas` (`yashas` amber) left and `EOF` right,
both `--dim` 24px. Everything HOLDS still to the end — only the block cursor blinks.
