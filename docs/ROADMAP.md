# Roadmap

## Gate 0 — Observer MVP
Status: implemented in repository.

- screen capture
- frame normalization
- delta gating
- secure server boundary
- multimodal interpretation
- state timeline
- uncertainty and sensitive-content flags

## Gate 1 — Device validation

- compile Android project
- install on target Vivo / Android version
- validate orientation changes
- validate lock-screen / secure-window behavior
- measure battery, thermals, bandwidth and frame latency
- test 30+ common UI transitions

## Gate 2 — Better understanding

- fuse screenshot with foreground-package metadata where safely available
- OCR/semantic text indexing
- element-level visual grounding
- temporal action inference
- explicit task graph
- confidence calibration
- "what changed?" model path separate from "what does it mean?"

## Gate 3 — Realtime

- move from request-per-frame HTTP to realtime multimodal transport
- adaptive cadence based on screen motion
- latency budget and backpressure
- reconnect / offline behavior
- frame/keyframe policy

## Gate 4 — Privacy layer

- on-device sensitive-region detection
- password/OTP/payment masking
- app allowlist and denylist
- pause-on-sensitive-app
- local audit log
- remote retention disabled by default

## Gate 5 — Permission-gated computer use

Only after observer accuracy is measured.

- action schema: tap, swipe, back, text, open-app
- action proposer separate from executor
- local policy engine
- per-action risk score
- confirmation for consequential actions
- hard local stop control
- post-action verification
- no hidden background control

## Gate 6 — "Feels alive"

- low-latency running narration
- current-goal model
- ambiguity detection
- ask-before-guess behavior
- cross-screen task continuity
- user-correctable state
- compact live HUD / companion UI
