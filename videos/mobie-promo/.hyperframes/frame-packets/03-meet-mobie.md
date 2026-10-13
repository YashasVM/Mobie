# Frame packet: 03-meet-mobie

## Project inputs

- Project: /home/yvm/code/Mobie/videos/mobie-promo
- Design tokens: /home/yvm/code/Mobie/videos/mobie-promo/frame.md
- RULES_DIR: /home/yvm/.claude/skills/hyperframes-animation/rules

## Assigned storyboard block

## Frame 3 — Meet Mobie

- scene: The site's boot sequence: `$ mobie --version`, ✓ lines, the ASCII MOBIE logo wiping in line by line, then the lead line
- voiceover: "Meet Mobie. Real AI models, running right on your phone."
- duration: 4.232s
- transition_in: cut
- status: animated
- src: compositions/frames/03-meet-mobie.html
- type: product_intro
- persuasion: Authority by clarity
- beat: intrigue → excitement
- blueprint: compose
- asset_candidates:

narrativeRole: name the product and land the value claim.
keyMessage: Mobie = real AI models on your own phone.

Tmux bar: active tab `0:mobie`.
Scene 1 (0.0–0.9s): boot lines print at the content column top (34px, `--dim`): `$ mobie --version` typed fast
(amber `$`), then instantly `mobie 0.2.0 · android · litert-lm`, `✓ runtime ready` (✓ green),
`✓ network not required for chat` — one line per ~0.12s.
Scene 2 (0.5–1.6s): on "Mobie." the site's 6-line ASCII logo (amber, Plex Mono 500, font-size ~44px,
line-height 1, white-space pre) reveals line by line: each line wipes left→right with `clip-path: inset(0 100% 0 0)
→ inset(0 0 0 0)` in `steps(20)` over 0.26s, lines staggered 0.07s (the site's exact animation). Logo text:
```
███╗   ███╗ ██████╗ ██████╗ ██╗███████╗
████╗ ████║██╔═══██╗██╔══██╗██║██╔════╝
██╔████╔██║██║   ██║██████╔╝██║█████╗
██║╚██╔╝██║██║   ██║██╔══██╗██║██╔══╝
██║ ╚═╝ ██║╚██████╔╝██████╔╝██║███████╗
╚═╝     ╚═╝ ╚═════╝ ╚═════╝ ╚═╝╚══════╝
```
Scene 3 (1.4–4.23s): under the logo the lead line types word-cued: `Run AI models` (on "Real AI models") then
` on your phone,` in amber 600 (on "on your phone") then ` offline.` — 64px weight 500, `--fg`, blinking amber block
cursor at the end. Hold.
