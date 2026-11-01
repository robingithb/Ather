# Midnight black theme

Midnight now uses true-black and charcoal surfaces with high-contrast silver text and selection accents. Decorative UI glows are reduced to 30% of their previous strength. Arctic, Sport and Minimal retain their existing palettes; semantic safety and temperature colors and explicit custom accent/ambient selections remain supported.

The theme card, Settings banner and Home hero use the generated `theme_midnight_black.png` artwork. Home navigation scenery, the generic media placeholder and the Climate cabin image use a monochrome treatment for Midnight. Actual music artwork is preserved. Vehicle scenery uses the neutral dark source and a restrained silver ring; the SUV model, materials, lighting and GLB file are unchanged.

## Asset

- Generator: built-in image_gen tool, new image generation.
- Workspace asset: `app/src/main/res/drawable-nodpi/theme_midnight_black.png`.
- Size: 1536 × 1024 RGB PNG.
- The previous blue artwork is preserved outside Android resources at `docs/assets/midnight_before_black.png`.
- Original generated output: `/home/robin/.codex/generated_images/01a18dd2-5527-7492-8ec0-759c17f92bfb/exec-5f854343-6ee0-4c36-9528-9fb7948c9650.png`.

## Final generation prompt

```text
undefined
```

## Verification

Verified on 31 October 2026: source/resource compilation passed, all 143 JVM tests passed
with zero failures/errors/skips, and Android lint passed with zero errors and the same
eight existing warnings. `git diff --check` passed. No APK was generated or installed and
no emulator was used. The existing APK timestamp is unchanged.

The SUV GLB SHA-256 remains `88e4c72f9ff1ef8a2d2a760b73e1f5cca063bb1b1fba441bd25edfd875d79883`.
