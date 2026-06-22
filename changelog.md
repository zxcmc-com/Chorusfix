# Changelog

## 0.1.1 — 2026-06-22
- Cancelled ordinary chorus plant and flower placement when it would rely on custom or ignored chorus carrier states.
- Fixed vanilla-valid horizontal chorus flowers so manual side placements are accepted and survive later rechecks.
- Cleaned up temporary impossible chorus plant states created by ordinary chorus growth, collapse, or side-flower placement, while keeping ignored states and provider-claimed blocks protected.
- Added experimental Paper/Purpur 26.2 compatibility to the supported server range.

## 0.1.0 — 2026-06-18
- Added the Chorusfix Paper plugin for restoring vanilla-style chorus plant and flower updates when Paper disables global chorus plant updates.
- Added safe cascade breaking for unsupported chorus blocks with normal block-break sounds, particles, and drops.
- Added vanilla-style chorus plant face recomputation so surviving plants keep the correct visual connections after nearby block changes.
- Added ignored-state and impossible-mask filtering to avoid treating known custom-block carrier states as vanilla chorus plants.
- Added optional Nexo, ItemsAdder, Oraxen, and Exort provider hooks so registered custom chorus blocks are skipped before any collapse logic runs.
- Added warning-only diagnostics for unsafe Nexo/Oraxen chorus `custom_variation` values and ItemsAdder `REAL_TRANSPARENT` chorus states that overlap vanilla-looking chorus plants.
- Added configurable queue limits for bounded main-thread cascade processing.
- Added `/chorusfix status`, `/chorusfix reload`, and the `/cf` alias for administrator control and diagnostics.
