# Architecture

## North star

The system should not merely receive screenshots. It should maintain a grounded, evolving representation of what the user is doing on the device.

## Runtime loop

```text
Android display
  ↓
MediaProjection (explicit OS consent)
  ↓
ImageReader
  ↓
Frame normalization + downscale
  ↓
Visual change detector
  ↓ changed frames only
Observer transport
  ↓
Session state store
  ↓
Multimodal perception/reasoning
  ↓
Structured observation
  ↓
Timeline update
  ↺ next changed frame
```

## Observation contract

Every accepted frame is interpreted against prior session observations. The model is asked to return:

- `app_or_surface`
- `page_or_context`
- `likely_user_action`
- `likely_task`
- `visible_ui`
- `important_change`
- `sensitive_content_visible`
- `uncertainty`
- `confidence`

The agent must distinguish what is directly visible from what is inferred.

## Why state tracking matters

A single screenshot can show a page. A timeline can show activity.

For example:

```text
Instagram feed
→ profile opened
→ edit profile opened
→ text field changed
→ save confirmation
```

The useful representation is not four unrelated screenshots; it is one evolving task state.

## Latency strategy

v0 uses changed-frame sampling over HTTP because it is simple, debuggable, and cheap.

Later stages:

1. adaptive frame cadence;
2. server-side frame deduplication;
3. realtime multimodal transport;
4. event + pixels fusion;
5. semantic UI grounding;
6. action proposals;
7. locally approved actions.

## Action boundary

Observation and action are separate subsystems.

No model output should directly become a device gesture. A future controller must pass through:

```text
model proposal
→ policy check
→ risk classification
→ user-control mode
→ optional confirmation
→ bounded action executor
→ result observation
```

This separation prevents a perception error from immediately becoming a device action.
