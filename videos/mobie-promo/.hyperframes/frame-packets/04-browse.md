# Frame packet: 04-browse

## Project inputs

- Project: /home/yvm/code/Mobie/videos/mobie-promo
- Design tokens: /home/yvm/code/Mobie/videos/mobie-promo/frame.md
- RULES_DIR: /home/yvm/.claude/skills/hyperframes-animation/rules

## Assigned storyboard block

## Frame 4 — Browse Hugging Face

- scene: The site's BROWSE TUI box — model list with fits/tight/too big, selection bar jumping, detail pane
- voiceover: "Browse mobile-ready models straight from Hugging Face."
- duration: 3.579s
- transition_in: cut
- status: animated
- src: compositions/frames/04-browse.html
- type: feature_showcase
- persuasion: Show-don't-tell proof
- beat: curiosity → ease
- blueprint: compose
- asset_candidates:

narrativeRole: breadth of choice, right from Hugging Face.
keyMessage: pick real open models from litert-community.

Tmux bar: active tab `2:browse`.
Scene 1 (0.0–0.7s): section header `BROWSE` + dashed rule at y≈130. Below it the TUI box frame snaps in (x 200→1720,
y 200→860, 1px `--dim` border, `--bg2` fill): title row `mobie › browse` (left, `--fg` 500) / `litert-community`
(right, `--dim`); body split 1.25fr list | 1fr detail with a 1px `--dim` divider; footer row with key hints
`[↑↓] move  [enter] details  [/] search  [q] quit` (keys as small `--dim`-filled chips with `--bg` text).
Scene 2 (0.7–2.6s): on "mobile-ready models" the 5 list rows print one per ~0.1s (34px): `Gemma3-1B-IT  fits`,
`Qwen2.5-1.5B-Instruct  fits`, `gemma-3n-E2B-it  fits`, `gemma-3n-E4B-it  tight`, `large-model-example  too big`
(status column right-aligned: fits `--grn`, tight `--yel`, too big `--red`). The amber selection bar (full row
`--amb` background, text `--bg`, `›` marker) lands on row 1, then JUMPS row 2 → row 3 on "straight" / "Hugging"
(instant snaps). Detail pane updates instantly with each selection: model name (600), then a key/value grid
`format .litertlm` · `quant int4/int8` · `type text / text + vision` · `memory █████░░░░░░░░░░░` · `storage ██░░░░…`
· `license shown before download`, and a verdict box `✓ ready to download` (1px green border, green text).
Scene 3 (2.6–3.58s): on "Face." hold on row 3 selected; the `litert-community` label in the title row gets the amber
selection snap. Under the box, `--dim` 24px: `demo data on an imaginary 8 GB phone — not measurements.`
Hold.
