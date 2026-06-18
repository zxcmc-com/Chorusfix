# Chorusfix

Chorusfix restores near-vanilla chorus plant behavior on Paper/Purpur servers where `block-updates.disable-chorus-plant-updates` is enabled for custom-block plugins.

It lets naturally generated chorus plants collapse again when their support is broken, while skipping custom chorus states used by providers such as Nexo, ItemsAdder, Oraxen, and Exort.

## Features

- Restores chain collapse for vanilla-eligible `CHORUS_PLANT` and `CHORUS_FLOWER`.
- Recomputes chorus plant faces after nearby block changes.
- Plays normal break sound and particles for unsupported chorus collapse.
- Skips impossible custom masks, configured ignored masks, and provider-claimed custom blocks.
- Warns administrators about unsafe Nexo/Oraxen `custom_variation` values and ItemsAdder runtime chorus states that can overlap vanilla chorus.

## Compatibility

- **Server software**: Paper / Purpur
- **Supported Minecraft**: 1.21.7-26.1.2
- **Java**: Java 25 toolchain for builds; generated plugin bytecode targets Java 21.

## Installation

1. Build with `./gradlew build`.
2. Put `build/libs/Chorusfix-<version>.jar` into `plugins/`.
3. Use `/chorusfix status` to confirm whether the Paper chorus-update flag is active.

## Commands

- `/chorusfix status`
- `/chorusfix reload`

Permission: `chorusfix.admin`.
