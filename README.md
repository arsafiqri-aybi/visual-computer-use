# Visual Computer Use

A real implementation of an AI screen observer for Android.

The first working slice is intentionally **read-only**:

Android screen (with explicit MediaProjection consent)
→ frame-change detection
→ private observer server
→ multimodal AI
→ persistent session timeline
→ structured understanding of what is on screen and what the user appears to be doing.

## Why this is not "just a skill"

A prompt/skill cannot see an Android screen by itself. Literal screen awareness needs an OS capture surface, transport, a vision-capable model, state tracking, and permission boundaries. This repository implements those layers.

## Current capability

- Explicit Android screen-capture consent.
- Foreground capture service.
- Real screen frames via MediaProjection.
- 720px-width adaptive downscale.
- Change detection so static screens are not repeatedly sent.
- JPEG compression before transport.
- Per-session state timeline on the server.
- Multimodal reasoning through the OpenAI Responses API.
- JSON observation containing app/page, likely user action, task context, visible UI, uncertainty and sensitive-content flags.
- Read-only by default. No tap/swipe/type execution is included in v0.

## Repository layout

- `android/` — Android observer app.
- `server/` — private AI observation service.
- `docs/ARCHITECTURE.md` — runtime architecture and data flow.
- `docs/SECURITY.md` — consent, privacy, and control boundaries.
- `docs/ROADMAP.md` — path from observer to permission-gated computer-use agent.

## Run the observer server

Requires Node.js 20+.

```bash
cd server
cp .env.example .env
# set OPENAI_API_KEY in your shell or environment
OPENAI_API_KEY=... node server.mjs
```

Default port: `8787`.

Health check:

```bash
curl http://localhost:8787/health
```

## Run the Android app

Open the `android/` directory in Android Studio and run it on the phone.

1. Put the observer-server URL in the app. For same-Wi-Fi development, use the computer's LAN address, for example `http://192.168.1.20:8787`.
2. Choose a session ID.
3. Tap **Start observing**.
4. Android shows its own screen-sharing consent dialog.
5. Approve it.
6. Use the phone normally.
7. Read current AI state from:

```
GET /state/<sessionId>
```

Example:

```bash
curl http://192.168.1.20:8787/state/my-phone
```

## Important

Do not put the OpenAI API key in the Android app. The key stays on the server.

For internet deployment, use HTTPS and authentication before exposing the server. The Android manifest currently allows cleartext traffic so LAN development works; remove that allowance for production.

## Status

**v0 / Observer MVP:** implementation committed.

This is a real executable architecture, but it has not yet been compiled on the user's Vivo device from inside this chat. Device QA, low-latency realtime transport, semantic UI grounding, and action execution are subsequent gates.
