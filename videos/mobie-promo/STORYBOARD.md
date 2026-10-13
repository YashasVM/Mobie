---
format: 1920x1080
duration: 39s
message: "Real AI models, running on your phone. No cloud. No account. Nothing leaves your device."
arc: Hook → Turn → Product_Intro → Feature ×4 → Benefits → CTA
audience: Android users and AI-curious devs on YouTube and X who are tired of cloud chatbots
mode: autonomous
music: upbeat driving electronic cinematic hype, modern bass pulses
captions: skipped (the terminal on-screen text already carries every line for muted autoplay)
---

## Video direction

- **Look:** a single continuous terminal session that copies Mobie's website (mobie.yash0.in) — see frame.md
  "Mobie terminal override". Flat `#0C0C0B`, IBM Plex Mono only, amber tmux status bar on top of every frame
  (same geometry everywhere; only the active tab changes), man-page section headers with dashed rules, `$`
  prompts in amber, `█░` progress bars, `[✓]` checklists, amber selection highlights. No gradients, glows,
  rounded corners, phones, or non-mono fonts.
- **Motion grammar:** typing, line-print reveals, hard cuts, row-selection jumps, character-swap progress bars,
  amber selection-highlight snaps. Everything cued to the voice word timings. Holds are still except the blinking
  block cursor.
- **Rhythm:** quick hook (1–2), boot/logo (3), the four "man page sections" (4–7) each with a big readable
  payoff line, rapid checklist (8), held end card (9).
- **Scale:** this is a 1080p video watched on phones — terminal text never smaller than 30px; each frame has
  ONE big line (88–150px) that carries the beat for muted autoplay.
- **Negative list:** neon, blooms, blur, Unbounded, phone mock-ups, emoji, invented numbers presented as
  measurements (keep the site's "demo data" honesty note where the TUI shows bars).

## Frame 1 — Hook: your words take a trip

- scene: A grey "cloud chat" terminal sends a private question to someone else's server
- voiceover: "Every time you ask an AI something, your words take a trip."
- duration: 3.84s
- transition_in: cut
- status: animated
- src: compositions/frames/01-hook.html
- type: hook
- persuasion: Pain agitation
- beat: unease + curiosity
- blueprint: compose
- asset_candidates:

narrativeRole: the relatable uncomfortable truth — cloud AI ships your words elsewhere.
keyMessage: your prompts leave your phone.

Tmux bar exception: in THIS frame the status bar is GREY (`#2A2925` background, `#6F6C63` text) with a single
active tab `0:cloud-chat` (inverted: bg `#6F6C63`, text `#0C0C0B`) and right side `connected · us-east-1`.
Scene 1 (0.0–1.9s): content column, y≈220: `$ ` (amber) then `ask "is this mole normal?"` types in (40px) starting
0.3s, finishing by ~1.5s; Enter. → discrete-text-sequence
Scene 2 (1.9–2.9s): dim output lines print one per beat (34px, `--dim`): `| uploading prompt…` (spinner char
cycles | / - \) → `→ POST api.someone-elses-server.com/v1/chat` → `→ 200 OK`. 
Scene 3 (2.9–3.84s): on "take a trip", three yellow lines snap in fast: `logged.` `stored.` `used for training?`
then the big line `your words just left your phone.` (88px, `--fg`, weight 600) prints at y≈640 with the words
"left your phone" in `--yel`. Hold.

## Frame 2 — Not anymore

- scene: ^C — the cloud session dies, the bar flips amber, "not anymore." with an amber selection snap
- voiceover: "Not anymore."
- duration: 1.123s
- transition_in: cut
- status: animated
- src: compositions/frames/02-not-anymore.html
- type: hook
- persuasion: Negative contrast
- beat: surprise → relief
- blueprint: compose
- asset_candidates:

narrativeRole: the turn.
keyMessage: there is another way.

Tmux bar: the normal amber bar, active tab `0:mobie`.
Scene 1 (0.0–0.3s): at t=0 an amber `^C` (40px) at the content column top-left, and the line `$ ` with blinking
cursor beneath it.
Scene 2 (0.3–1.12s): on "Not" (0.30) the giant line `not` appears (150px, 700, `--fg`) at x=200, y≈380; on
"anymore." (0.56) `anymore.` appears beside/under it and an amber selection highlight SNAPS over `anymore.`
(background `--amb`, text `--bg`) — the selection is the slam. Block cursor after it. Hold.

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

## Frame 7 — Just chat

- scene: The site's chat transcript in airplane mode: you › question, ai › streams the answer, "generated on-device · 0 bytes sent"
- voiceover: "Then just chat. Replies stream in, generated by your phone's own chip."
- duration: 4.989s
- transition_in: cut
- status: animated
- src: compositions/frames/07-chat.html
- type: feature_showcase
- persuasion: Show-don't-tell proof
- beat: delight + power
- blueprint: compose
- asset_candidates:

narrativeRole: the payoff demo — it really talks back, locally.
keyMessage: real streaming chat, on-device.

Tmux bar: active tab `4:chat`.
Layout: header `CHAT` + dashed rule y≈130; big line y≈190 (96px 600): `just chat.` (`chat.` amber) appears on
"just chat" (0.47s). Transcript block below (left border like frame 6, 40px):
Scene 1 (0.0–1.4s): `[airplane mode: on]` (`--dim`) at 0.1s; `you › ` (amber) + `three names for a cozy coffee shop?`
types 0.3–1.3s.
Scene 2 (1.4–3.6s): on "Replies stream in" `ai  › ` (`--blu`) and the reply streams word by word with the amber block
cursor riding the end: `Sure. Ember & Oak, The Slow Pour, and Little Lantern.` (word every ~0.13s, wraps to 2 lines
max).
Scene 3 (3.6–4.99s): on "your phone's own chip" the line `  generated on-device · 0 bytes sent` prints in `--dim`,
then `0 bytes sent` gets the amber selection snap. Hold.

## Frame 8 — Private by default

- scene: The site's PRIVACY checklist, big: [✓] no account. [✓] no cloud. [✓] works in airplane mode. then "your prompts never leave your phone."
- voiceover: "No account. No cloud. Works in airplane mode. Your prompts never leave your phone."
- duration: 5.747s
- transition_in: cut
- status: animated
- src: compositions/frames/08-private.html
- type: benefit_highlight
- persuasion: Rule of three
- beat: power + peace of mind
- blueprint: compose
- asset_candidates:

narrativeRole: stack the privacy benefits in rhythm — the shareable moment.
keyMessage: private by default.

Tmux bar: active tab `5:privacy`.
Layout: header `PRIVACY` + dashed rule y≈130; checklist at x=200, y from 220, 72px weight 600, line gap ~110px.
Scene 1 (0.0–1.2s): on "No account." line 1 prints: `[✓]` (green, the ✓ stamps in 0.1s after the brackets) +
` no account.` (`--fg`).
Scene 2 (1.2–2.2s): on "No cloud." line 2: `[✓] no cloud.`
Scene 3 (2.2–3.8s): on "Works in airplane mode." line 3: `[✓] works in airplane mode.`
Scene 4 (3.8–5.75s): on "Your prompts never leave your phone." the final line prints larger below the list
(96px, 700): `your prompts never leave your phone.` with an amber selection highlight snapping over `never leave`
on "never" (bg `--amb`, text `--bg`). Hold.

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

## Final polish · 2026-10-12

User requested frames 6, 7, the final title card, frame 2 animation, and all audio cues be reviewed and finalized for upload.

- Rebuilt download, chat, and end card in the website's terminal palette, IBM Plex Mono, flat surfaces, and amber tab bar. Used the website's own ASCII wordmark and transcript text. Download and chat are explicitly labeled illustrative.
- Removed full-screen zooms, blurs, slides, and crossfades. Exact clip windows now cut between scenes without moving the terminal chrome. Frame 2's words and selection follow the narration at 0.299 and 0.555 seconds.
- Fixed the frame 2 bar covering the canvas, intro whitespace pushing boot lines over the logo, logo line spacing, duplicate root IDs, and the browse right-nav/status-color class collision.
- Replaced broad whooshes, risers, impacts, and the 44-second typing loop with short keypresses cued to text entry. Model reply output is silent. Timing evidence is in audio-cues.json. Existing music is quieter and fades out at the end; narration is unchanged.
- Added a fit-check demo-data note and preserved the final address hold. Duration is 38.986 seconds, rounded to 39 seconds in a 30 fps encode.
- Validation: npm run check passes with zero lint, runtime, layout, motion, or contrast findings. Checked transition snapshots and each scene visually. Delivery export: renders/mobie-promo-final.mp4.


## Restored version · 2026-10-12

At the user's request, restored the original polished terminal version, removing all later cloud graphics, console framing, zoom changes, extra holds, and website end-card addition. Kept only the opening wording "Every time you ask a cloud AI something, your words take a trip to the cloud." The on-screen ending of the opening reads "your words take a trip / to the cloud." Later scenes move 0.889 seconds to fit the revised narration. Total 39.875 seconds. Earlier fixes to frames 2, 6, 7, 9 and audio synchronization remain as in the original polished version.


## Website and soft transitions · 2026-10-12

User requested mobie.yash0.in on the end card and smoother scene handoffs. Added the website above the GitHub address and synchronized both typing cues. Applied one restrained 0.28-second blur crossfade at all eight boundaries with no camera movement. Outgoing scenes remain visible until incoming scenes fully cover them, avoiding a black dip. Local scene windows extend only for visual overlap; narration, keyboard timing, scene start times, and the 39.875-second running time remain unchanged. The previous cloud graphics remain removed.

Validation: npm run check passes with no lint or runtime findings and 163/163 contrast checks passing. Its overlap notices are limited to the intentionally overlapping 0.28-second transition windows; snapshots confirm the text settles cleanly after each handoff. Stable scene classes are used for the animation targets because Studio rewrites authored ID selectors. Delivery export: renders/mobie-promo-smooth.mp4.


## Beta closing note · 2026-10-12

User requested a closing beta notice, upcoming-feature message, and a direction to watch GitHub for releases and updates. Added a final terminal-style RELEASE NOTES card, voiced by the same Rory narrator. It keeps mobie.yash0.in and points directly to github.com/YashasVM/Mobie/releases. The new card uses the existing 0.28-second blur crossfade, word-aligned copy, synchronized typing sounds, and a one-second final reading hold. Music extends from its matching original 63-second source and fades at the new ending. Earlier scenes are unchanged; total duration is 47.302 seconds.


## Narration level correction · 2026-10-12

User reported intermittent voice spikes and requested a final MP4 via CDX. Measured all ten source clips; narration ranged from -19.1 to -15.2 LUFS with several source true peaks close to 0 dBFS. Matched clip gains to the measured -17.15 LUFS median, then applied one shared gentle compressor and voice true-peak limiter. Added a -3 dBTP master ceiling over the complete mix. The picture, text, timing, beta ending, website, and transitions are unchanged. Settings and source measurements are in audio-levels.json.
