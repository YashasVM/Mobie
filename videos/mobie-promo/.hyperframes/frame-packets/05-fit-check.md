# Frame packet: 05-fit-check

## Project inputs

- Project: /home/yvm/code/Mobie/videos/mobie-promo
- Design tokens: /home/yvm/code/Mobie/videos/mobie-promo/frame.md
- RULES_DIR: /home/yvm/.claude/skills/hyperframes-animation/rules

## Assigned storyboard block

## Frame 5 — It checks first

- scene: Same TUI, selection jumps to "tight" then "too big"; the verdict turns yellow then red; big line "mobie checks first."
- voiceover: "Mobie checks your RAM and storage first, so you never download something that won't fit."
- duration: 4.728s
- transition_in: cut
- status: animated
- src: compositions/frames/05-fit-check.html
- type: feature_showcase
- persuasion: Risk reversal
- beat: confidence + control
- blueprint: compose
- asset_candidates:

narrativeRole: the differentiator — honest compatibility before a 3 GB mistake.
keyMessage: it won't let you download something your phone can't run.

Tmux bar: active tab `2:browse`.
Layout: the TUI from frame 4 is now SHORTER and lower (box y 330→900) so a big line fits above it. Same columns,
same rows, same styling.
Scene 1 (0.0–1.9s): header `DESCRIPTION` + dashed rule at y≈130; at y≈190 the big line types word-cued:
`mobie checks` (on "Mobie checks") ` first.` in amber (on "first,") — 96px weight 600. Meanwhile in the TUI the
selection sits on row 3 (gemma-3n-E2B-it, fits); on "RAM" the detail pane's `memory` meter row flashes (amber
selection snap on the label for 0.25s) and on "storage" the `storage` row does the same.
Scene 2 (1.9–3.3s): on "so you never" the selection jumps to row 4 `gemma-3n-E4B-it  tight`: memory meter re-fills
by character swap to 13/16 blocks in `--yel`; verdict box becomes `! close some apps first` (yellow border/text).
Scene 3 (3.3–4.73s): on "won't fit" selection jumps to row 5 `large-model-example  too big`: memory meter fills all
16 blocks `--red` + ` over` in red; verdict box `x won't fit on this phone` (red), and the whole verdict box gets
the amber→red selection snap (red background, `--bg` text) for emphasis. Hold.
