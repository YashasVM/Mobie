---
format: 1920x1080
duration: 36s
message: "Real AI models, running on your phone. No cloud. No account. Nothing leaves your device."
arc: Hook → Turn → Product_Intro → Feature ×4 → Benefits → CTA
audience: Android users and AI-curious devs on YouTube and X who are tired of cloud chatbots
mode: autonomous
music: upbeat energetic electronic synthwave pop, driving beat, bright and modern, tech promo
captions: skipped (kinetic on-screen type already carries every line for muted autoplay)
---

## Video direction

- **Palette system** (frame.md "Mobie neon override"): ink-black `#0C0C0B` ground on every frame with a slow
  neon radial bloom (1–2 blurred blobs, 25–40% opacity) + faint 1px scanline texture. Amber `#FFB547` = brand / hero /
  CTA. Magenta `#FF3DCB` = energy / slam. Cyan `#2EF2FF` = streaming / thinking / data. Lime `#9BFF5A` = fits / ready / ok.
  Coral `#FF6B57` = blocked / error. ONE hero accent + at most one secondary per frame (named per frame below).
  Full-bleed colour takeover grounds only on frames 2 and 8 (slam beats). Cream `#D6D3C8` for secondary text.
- **Type:** Unbounded 800 lowercase for hero words (display); IBM Plex Mono for terminal / UI / kickers / chrome.
- **Phone mock-up (recurring prop, frames 4–7):** a modern Android phone, near-black body, thin bezel, ~6cqw corner
  radius, 9:20 screen, height ~78% of frame, soft amber/cyan rim glow + long soft shadow. Its screen is a
  reconstruction of Mobie's real dark UI (from `assets/2026-10-11-text-only-ui.png`): near-black `#0B0C0E` screen,
  white sans/Plex text, lime "● Ready · on-device · LiteRT-LM" status, pill cards with ↗ arrows, an "Ask anything"
  pill composer + round send button, footer "🔒 Stays on this phone · LiteRT-LM". Keep the same phone geometry across
  frames 4–7 so it reads as one device.
- **Motion grammar:** upbeat hype promo — snappy `power3.out`/`expo.out` entrances, `back.out(1.6)` spring pops on
  slams, smooth long-tail settles. Reveals paced to the VO (nothing before its spoken cue). Holds: still, at most the
  background bloom drifting slowly. Subtle continuous camera push (≤4% scale) on the content wrapper is OK.
- **Rhythm:** fast cuts frames 1–3 (energy), steady device demo 4–7, slam relay 8, held lockup 9 (the breather is
  the final 1.5s hold of frame 9).
- **Negative list:** no stock AI-brain imagery, no purple-blue generic "AI" gradient wash as a whole ground, no
  cursor arrows, no fake partner logos, no invented performance numbers (never show a specific tok/s figure as a
  claim — use `— tok/s` style or animate tokens only), no iOS. No slideshow (front-load then freeze) and no
  screensaver (everything floating independently).

## Frame 1 — Hook: your words take a trip

- scene: A chat bubble "what's this rash?" flies off a phone up into a giant glowing cloud while servers light up
- voiceover: "Every time you ask an AI something, your words take a trip."
- duration: 3.84s
- transition_in: cut
- status: animated
- src: compositions/frames/01-hook.html
- type: hook
- persuasion: Pain agitation
- beat: unease + curiosity
- blueprint: compose
- focal: none (typography + CSS/SVG illustration)
- asset_candidates:
- sfx: whoosh

narrativeRole: open on a relatable, slightly uncomfortable truth — your prompts leave your phone.
keyMessage: cloud AI = your words go somewhere else.

Hero accent: cyan, secondary magenta.
Scene 1 (0.0–1.2s): centered on dark ground, a single chat bubble (Plex Mono, cream text "is this mole normal?" in a cyan-outlined pill) springs up from the lower third; a tiny phone outline sits beneath it. Small kicker top-left "> sending prompt…". → spring-pop-entrance
Scene 2 (1.2–2.6s): on "your words take a trip", the bubble launches up-right along a dotted arc trail toward a big glowing cloud shape (magenta outline, server-rack stripes inside) at upper-right; a motion-blur streak on the bubble; small data packets (tiny rects) stream along the arc. → motion-blur-streak, svg-path-draw (arc trail)
Scene 3 (2.6–3.8s): bubble lands in the cloud; cloud pulses; a stack of copies of the bubble fan out inside it ("logged", "stored", "trained on?" tiny Plex labels pop in one by one, magenta). Hold. → waterfall-entry

## Frame 2 — Not anymore

- scene: Full-bleed amber takeover — "not anymore." slams in, the cloud from frame 1 shatters
- voiceover: "Not anymore."
- duration: 1.123s
- transition_in: zoom-through
- status: animated
- src: compositions/frames/02-not-anymore.html
- type: hook
- persuasion: Negative contrast
- beat: surprise → relief
- blueprint: kinetic-type-beats (Adapt)
- focal: none
- asset_candidates:
- sfx: impact

narrativeRole: the turn — reject the status quo in two words.
keyMessage: there is another way.

Adapt: keep the spring-pop payoff slam; a single two-word statement instead of a relay.
Hero: amber ground, ink type. Scene 1 (0.0–0.5s): amber full-bleed ground flashes in with a white flash frame; "not" slams in left-center (Unbounded 800, ink, huge ~14cqw) with scale 1.4→1 spring + small camera shake. Scene 2 (0.5–1.6s): "anymore." slams beside/below it on the next beat; a big strike-through line of a tiny cloud icon in the corner (ink). Hold. → kinetic-beat-slam, spring-pop-entrance

## Frame 3 — Meet Mobie

- scene: The ASCII block "MOBIE" logo decodes in amber, then resolves into the app icon + "mobie" wordmark with tagline
- voiceover: "Meet Mobie. Real AI models, running right on your phone."
- duration: 4.232s
- transition_in: blur-crossfade
- status: animated
- src: compositions/frames/03-meet-mobie.html
- type: product_intro
- persuasion: Authority by clarity
- beat: intrigue → excitement
- blueprint: logo-assemble-lockup (Adapt)
- focal: none (rebuilt brand mark)
- asset_candidates:
- sfx: riser, glitch

narrativeRole: name the product and land the value claim in beat 3.
keyMessage: Mobie = real AI models on your own phone.

Adapt: keep the "mark comes to exist on screen" signature via a scramble-decode instead of parts assembling.
Hero accent amber, secondary magenta. Background bloom: amber + magenta blobs.
Scene 1 (0.0–1.4s): on "Meet Mobie", a 6-line ASCII box-drawing "MOBIE" logo (█╗ style, amber, Plex Mono, ~60cqw wide, centered upper-middle) decodes left-to-right from scrambled glyphs, with a brief RGB-split flicker (magenta/cyan offset copies) on resolve. → hacker-flip-3d
Scene 2 (1.4–2.6s): the ASCII logo scale-swaps into the clean lockup: app icon (rounded dark square, bold white "M", amber glow ring) left of the wordmark "mobie" (Unbounded 800 lowercase, cream, ~11cqw). → scale-swap-transition
Scene 3 (2.6–4.2s): on "real AI models, running right on your phone", the tagline types in under the wordmark in Plex Mono: "real ai models. on your phone." with "on your phone" in amber + blinking amber block caret. Hold. → discrete-text-sequence

## Frame 4 — Browse Hugging Face

- scene: Phone mock-up slides in; a list of litert-community models cascades in with sizes and a lime "fits" chip
- voiceover: "Browse mobile-ready models straight from Hugging Face."
- duration: 3.579s
- transition_in: push-slide LEFT
- status: animated
- src: compositions/frames/04-browse.html
- type: feature_showcase
- persuasion: Show-don't-tell proof
- beat: curiosity → ease
- blueprint: device-surface-showcase (Adapt)
- focal: assets/2026-10-11-text-only-ui.png
- roles: 2026-10-11-text-only-ui.png = reference only for the reconstructed phone UI (do NOT place the bitmap; rebuild in HTML)
- asset_candidates: assets/2026-10-11-text-only-ui.png — real Mobie chat screen screenshot (dark UI reference)
- sfx: whoosh, pop

narrativeRole: first look at the product surface — breadth of choice.
keyMessage: pick from real open models on Hugging Face.

Adapt: static tour variant — phone held right-of-center, labels on the left.
Hero accent lime, secondary amber. Layout asymmetric 60/40: phone at right ~58% x, headline left.
Scene 1 (0.0–1.0s): phone rises in from below with a slight 3D tilt settling flat; on screen header "mobie › browse  litert-community" (Plex Mono, amber "›"). Left side: kicker "01 / BROWSE" + headline "pick a model." (Unbounded, cream, ~6cqw). → spring-pop-entrance
Scene 2 (1.0–3.0s): on "straight from Hugging Face", 4 model rows waterfall into the phone list (names from Mobie's own demo data): "Gemma3-1B-IT · int4 · text", "Qwen2.5-1.5B-Instruct · int8 · text", "gemma-3n-E2B-it · int4 · text + vision", "gemma-3n-E4B-it · int4 · text + vision" — each `.litertlm`, with a lime "fits" pill, except the last with an amber "tight" pill. Left side a small line "from huggingface.co/litert-community" in Plex Mono, cyan. → waterfall-entry
Scene 3 (3.0–3.8s): a highlight bar slides onto row 1 (amber outline glow), as if selected. Hold. → dynamic-content-sequencing

## Frame 5 — It checks first

- scene: Close on the phone's compatibility card — RAM and storage bars fill, then a big lime FITS stamp; one model flips to a coral BLOCKED
- voiceover: "Mobie checks your RAM and storage first, so you never download something that won't fit."
- duration: 4.728s
- transition_in: crossfade
- status: animated
- src: compositions/frames/05-fit-check.html
- type: feature_showcase
- persuasion: Risk reversal
- beat: confidence + control
- blueprint: dataviz-countup (Adapt)
- focal: none (reconstructed UI card)
- asset_candidates:
- sfx: tick, success-chime

narrativeRole: the differentiator — honest compatibility before you waste a 3 GB download.
keyMessage: it won't let you download something your phone can't run.

Adapt: keep "numbers are the hero + bars fill"; two progress bars instead of a ring; camera pushes into the card.
Hero accent lime, secondary coral. Same phone geometry, camera pushed in ~1.25× toward the screen card (phone at center-right).
Scene 1 (0.0–1.6s): on "checks your RAM and storage", on-screen card "Gemma3-1B-IT · 555 MB" with two labelled bars: "RAM  est. 1.2 GB / 8 GB" and "STORAGE  555 MB / 41 GB free"; bars fill left→right in sequence with numbers counting up (Plex Mono, tabular). Left headline "checks first." (Unbounded) with kicker "02 / FIT CHECK". → stat-bars-and-fills, counting-dynamic-scale
Scene 2 (1.6–3.0s): a big lime "✓ FITS THIS PHONE" stamp spring-pops onto the card with glow; lime ring pulse. → spring-pop-entrance, ambient-glow-bloom
Scene 3 (3.0–5.0s): on "something that won't fit", a second card slides up over it: "an 8B model · ~9 GB" with RAM bar overflowing past 100% in coral, then a coral "✕ BLOCKED · needs more RAM" stamp slams with a small shake. Hold. → spring-pop-entrance

## Frame 6 — Downloads that survive

- scene: Download progress bar races, wifi drops (bar pauses, red "connection lost"), resumes, then "SHA-256 ✓ verified"
- voiceover: "Downloads resume if you drop signal, and get verified before they run."
- duration: 3.448s
- transition_in: push-slide LEFT
- status: animated
- src: compositions/frames/06-download.html
- type: feature_showcase
- persuasion: Friction reduction
- beat: ease + trust
- blueprint: agent-progress-theater (Adapt)
- focal: none
- asset_candidates:
- sfx: glitch, success-chime

narrativeRole: reliability proof — big files don't break.
keyMessage: resumable, verified downloads.

Adapt: keep status-theater swaps resolving into a receipt that checks off; no agent, the download is the worker.
Hero accent cyan, secondary lime (+ coral blip). Phone center-right, headline left "never starts over." kicker "03 / DOWNLOAD".
Scene 1 (0.0–1.4s): on screen: "Downloading Gemma3-1B-IT" + thick cyan progress bar racing 0→62% with percentage counter and "312 / 555 MB". → stat-bars-and-fills
Scene 2 (1.4–2.4s): on "drop signal": wifi glyph flickers off, bar freezes, coral chip "connection lost · paused at 62%", a quick chromatic glitch on the phone. → chromatic-glitch
Scene 3 (2.4–3.4s): wifi returns, chip swaps to cyan "resuming from 62%" and the bar continues smoothly to 100%. → discrete-text-sequence
Scene 4 (3.4–4.4s): on "verified", a receipt list checks off: "✓ resumed  ✓ SHA-256 verified  ✓ saved privately" (lime ticks staggering). Hold. → waterfall-entry

## Frame 7 — Just chat

- scene: The chat screen: a prompt types into "Ask anything", sends, and a reply streams in token by token; status reads "on-device"
- voiceover: "Then just chat. Replies stream in, generated by your phone's own chip."
- duration: 4.989s
- transition_in: crossfade
- status: animated
- src: compositions/frames/07-chat.html
- type: feature_showcase
- persuasion: Show-don't-tell proof
- beat: delight + power
- blueprint: prompt-type-submit-generate (Reproduce)
- focal: assets/2026-10-11-text-only-ui.png
- roles: 2026-10-11-text-only-ui.png = reference only for the reconstructed chat UI (do NOT place the bitmap; rebuild in HTML)
- asset_candidates: assets/2026-10-11-text-only-ui.png — real Mobie chat screen (header "Ready · on-device · LiteRT-LM", composer, footer "Stays on this phone")
- sfx: typing, pop

narrativeRole: the payoff demo — it really talks back, locally.
keyMessage: real streaming chat, on-device.

Hero accent cyan (streaming), secondary amber. Phone centered-right, slight continuous push-in; left headline "just chat." kicker "04 / CHAT"; under it a small Plex line "generated on-device · no network" in lime that appears on "your phone's own chip".
Scene 1 (0.0–1.6s): chat screen with header "Gemma3-1B-IT  ● Ready · on-device · LiteRT-LM" (lime dot). In the composer, "write a haiku about my commute" types in with a caret; send button presses (spring). → discrete-text-sequence, press-release-spring
Scene 2 (1.6–4.2s): user bubble pops up right; a cyan "thinking" shimmer for ~0.3s, then the reply streams in word by word in the assistant area: "Train doors sigh open — / headphones hum a quiet song, / the city wakes slow." A small stats strip at top shows "tok/s ▮▮▮" bar pulsing (no number). → dynamic-content-sequencing
Scene 3 (4.2–5.2s): footer "🔒 Stays on this phone" lights up amber with glow; lime "generated on-device · no network" line left side. Hold.

## Frame 8 — Private by default

- scene: Rapid slam relay over magenta takeover: "no account." "no cloud." "airplane mode? ✓" then "your prompts never leave your phone."
- voiceover: "No account. No cloud. Works in airplane mode. Your prompts never leave your phone."
- duration: 5.747s
- transition_in: zoom-through
- status: animated
- src: compositions/frames/08-private.html
- type: benefit_highlight
- persuasion: Rule of three
- beat: power + peace of mind
- blueprint: kinetic-type-beats (Reproduce)
- focal: none
- asset_candidates:
- sfx: impact, impact, impact, whoosh

narrativeRole: stack the privacy benefits in rapid-fire rhythm — the shareable moment.
keyMessage: private by default.

Hero accent magenta ground, ink type; final line on ink ground with amber.
Scene 1 (0.0–0.9s): magenta full-bleed; "no account." slams center (Unbounded 800 ink ~12cqw) — hard-cut swap. → kinetic-beat-slam
Scene 2 (0.9–1.8s): hard cut to "no cloud." with a cloud icon crossed out.
Scene 3 (1.8–3.0s): hard cut to cyan ground: airplane ✈ glyph flies across left→right with a speed trail, "airplane mode ✓" slams. → kinetic-beat-slam
Scene 4 (3.0–5.0s): cut to ink ground with amber bloom: "your prompts never leave your phone." builds word-by-word (Unbounded, cream; "never leave" in amber with glow); a small lock icon springs in. Hold.

## Frame 9 — Get Mobie

- scene: Logo lockup + "your ai. offline." + terminal-style "[ download apk ]" button and github.com/YashasVM/Mobie
- voiceover: "Mobie. Your AI, offline. Free and open source. Grab it on GitHub."
- duration: 7.3s
- transition_in: blur-crossfade
- status: animated
- src: compositions/frames/09-cta.html
- type: cta
- persuasion: Friction reduction
- beat: motivation + urgency-to-act
- blueprint: logo-assemble-lockup (Adapt)
- focal: none
- asset_candidates:
- sfx: riser, impact

narrativeRole: brand + action.
keyMessage: download Mobie from GitHub.

Adapt: lockup builds then extends into the CTA button + URL (end card).
Hero accent amber, secondary magenta+cyan bloom.
Scene 1 (0.0–1.4s): app icon spring-pops center with amber ring burst + a radial spray of small neon particles (deterministic, seeded); wordmark "mobie" slides out from behind it to the right. → spring-pop-entrance
Scene 2 (1.4–2.8s): tagline "your ai. offline." (Unbounded, cream, "offline." in an amber→magenta gradient sweep) reveals beneath.
Scene 3 (2.8–5.0s): on "free and open source / grab it on GitHub", an amber-outlined terminal button "[ download apk ]" springs in, then fills amber with a press; under it Plex Mono "github.com/YashasVM/Mobie" types in, plus tiny chips "android 8+ · arm64 · open source". Hold to end. → press-release-spring
