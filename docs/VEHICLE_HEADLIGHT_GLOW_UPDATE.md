# Visible headlight halo — 30 October 2026

The small headlight projectors now have a clearly visible white halo. Their emission gain increases from 128 to 4,096 (32 times the previous value). The wide DRL strip stays at 2, so the stronger glow surrounds the lamp apertures without becoming a broad white band across the grille.

This changes only headlight projector calibration and its existing regression check. Reverse glow, reverted rear-light appearance, accepted upper brake glow, tyre materials, charging transparency, anti-aliasing and model geometry remain unchanged. It uses the existing bloom pass with no new textures, lights, geometry or render passes.

Verification:

- **BUILD SUCCESSFUL**, 1m 43s, using offline Gradle assembleDebug, testDebugUnitTest and lintDebug.
- **86 tests passed**, zero failures, errors or skips.
- Lint: **zero errors**, 9 existing warnings.
- Installed and checked on the physical Samsung SM_G990E at two camera angles. Off/On comparison confirms visible glow, while the grille remains defined.
- Installed APK hash matches the verified build. The bundled model and original source GLBs are unchanged.
- No fatal or renderer errors in the checked process log. Current demo preferences match the saved pre-test state; app data was preserved. No emulator was used.

Detailed results: `docs/vehicle-headlight-glow-verification.json`. Previous reports describe historical revisions.
