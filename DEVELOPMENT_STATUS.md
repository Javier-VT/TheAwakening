# THE AWAKENING — Development Status

Target: Minecraft Java 1.20.1 / Forge 47.4.10 / Java 17

## Stage status

- Stage 0 — Forge architecture: **COMPLETE**
- Stage 1 — Registries, Creative Tabs, config, networking: **COMPLETE**
- Stage 2 — Player data + progression: **COMPLETE**
- Stage 3 — Permanent HP + difficulty: **COMPLETE**
- Stage 4 — Revive + death: **COMPLETE**
- Stage 5 — Food + potions: **COMPLETE — automated build/server acceptance passed**
- Stage 6 — Armor: **NOT STARTED**

## Stage 5 acceptance

Version: `0.5.0-stage5`

Validated CI run: `#48` (`34632083137`)

Validated commit: `b049d4e03e4ae1d22f9b71a47710bec628fd41a7`

Production JAR SHA-256: `00ed80bfe92505f6e0af0f36b3540ae5716dd3718ce5402d8cb30a0288239d80`

Passed gates:

- Exact Stage 5 source overlay SHA-256 reconstruction.
- Stage 2, Stage 3, Stage 4 and Stage 5 static validators.
- ForgeGradle production build.
- `verifyProductionJar`.
- Production JAR ZIP integrity and required-content checks.
- Reobfuscation guard: no raw `CREATIVE_MODE_TAB` development mapping marker.
- Dedicated Forge server smoke test reached the ready state without fatal mod-loading errors.

## Stage 5 implementation

- 23 food items across the planned progression.
- 9 currently playable food recipes for F1–F3.
- Custom `Well Fed` effect with light regeneration and movement bonus, with hooks reserved for future mana/stamina systems.
- 11 potion/elixir families registered.
- 7 currently playable brewing paths.
- Late-game consumables remain gated behind their future real dimensions/materials rather than placeholder recipes.
- Consumable assets, models, translations and Creative Tab integration included.

## Runtime note

Automated dedicated-server runtime acceptance is complete. A manual graphical-client gameplay smoke test is still recommended before beginning Stage 6, particularly for inventory rendering, food consumption visuals, potion tooltips/effects and brewing-stand interaction.
