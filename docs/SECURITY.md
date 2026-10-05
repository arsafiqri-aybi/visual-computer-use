# Security and Privacy Contract

Screen observation is high-trust functionality. The system is designed around explicit user control.

## Hard rules

1. Capture starts only after Android's MediaProjection consent flow.
2. Capture runs as a foreground service with a visible notification.
3. The user can stop capture from the app at any time.
4. API credentials live on the server, never in the APK.
5. v0 is read-only.
6. Model inference must expose uncertainty rather than invent hidden state.
7. Sensitive-content detection is part of every observation.
8. Internet deployment requires HTTPS and authentication.

## Data minimization

v0 already applies:
- frame-change gating;
- downscale before upload;
- JPEG compression;
- short in-memory state history.

Recommended next:
- on-device redaction for passwords, OTPs, payment screens and private notifications;
- allow/deny app lists;
- retention controls;
- no-frame-storage mode;
- session-scoped encryption keys.

## Android notes

MediaProjection requires user-granted screen-capture permission and a mediaProjection foreground service on modern Android versions.

Generic device automation through Accessibility APIs has additional platform and distribution-policy implications. It is deliberately not enabled in this observer MVP. If a controller is added, it should be an explicit separate mode with clear disclosure and a local kill switch.
