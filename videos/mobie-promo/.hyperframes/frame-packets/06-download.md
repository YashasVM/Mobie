# Frame packet: 06-download

## Project inputs

- Project: /home/yvm/code/Mobie/videos/mobie-promo
- Design tokens: /home/yvm/code/Mobie/videos/mobie-promo/frame.md
- RULES_DIR: /home/yvm/.claude/skills/hyperframes-animation/rules

## Assigned storyboard block

## Frame 6 — Downloads that survive

- scene: The site's `mobie pull` transcript: bar to 46%, connection lost — paused, resume, 100%, sha256 matches, ready
- voiceover: "Downloads resume if you drop signal, and get verified before they run."
- duration: 3.448s
- transition_in: cut
- status: animated
- src: compositions/frames/06-download.html
- type: feature_showcase
- persuasion: Friction reduction
- beat: ease + trust
- blueprint: compose
- asset_candidates:

narrativeRole: reliability proof.
keyMessage: resumable, verified downloads.

Tmux bar: active tab `3:pull`.
Layout: header `DOWNLOAD` + dashed rule y≈130; big line at y≈190 (96px, 600): `never starts over.` — `over.` in amber,
types in on "resume" (0.72–1.1s). Below at y≈420, the transcript block with a 2px `--faint` left border, padding-left
28px, 40px text, line-height 1.6:
Scene 1 (0.0–1.3s): `$ mobie pull gemma-3n-E2B-it` (amber `$`, typed fast 0.0–0.4s), then the progress line
`| ████████████░░░░░░░░░░░░░░░░  0% downloading` — spinner cycles | / - \, 28-char bar fills by char swap to 46%.
Scene 2 (1.3–1.95s): on "drop signal" the line freezes as `|| █████████████░░░░░░░░░░░░░░░  46% connection lost — paused`
(`||` and the message in `--yel`).
Scene 3 (1.95–2.3s): `  wifi back · resuming from 46% (range request)` prints (`--dim`), new bar line runs 46→100% fast.
Scene 4 (2.3–3.45s): on "verified" the bar line becomes `✓ ████████████████████████████ 100%`, then `✓ sha256 matches`
and `✓ saved to app storage · ready` print (✓ green) on "before" / "run". Hold with blinking cursor on a new `$ ` line.
