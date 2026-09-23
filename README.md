<p align="center">
  <img src="https://raw.githubusercontent.com/tewek444/Light-Engine/fabric/1.21.1/src/main/resources/assets/lightengine/icon.png" width="128" alt="Light Engine icon">
</p>

<h1 align="center">Light Engine</h1>

<p align="center">
  <b>English</b> | <a href="README_ru.md">Русский</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-44bd32" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/Loaders-Fabric_%7C_NeoForge-e1b12c" alt="Fabric and NeoForge">
  <img src="https://img.shields.io/badge/Java-21-f39c12" alt="Java 21">
  <img src="https://img.shields.io/badge/Status-Alpha2-e74c3c" alt="Alpha2">
  <img src="https://img.shields.io/badge/License-MIT-3498db" alt="MIT license">
</p>

<p align="center"><i>Extended block light for Minecraft — push any block beyond the vanilla light level 15.</i></p>

---

## Branches

One branch per loader — the code lives there, not here:

| Branch | Loader | Minecraft | Mod |
| ------ | ------ | --------- | --- |
| [`fabric/1.21.1`](https://github.com/tewek444/Light-Engine/tree/fabric/1.21.1) | Fabric | 1.21.1 | 1.0.0-alpha2 |
| [`neoforge/1.21.1`](https://github.com/tewek444/Light-Engine/tree/neoforge/1.21.1) | NeoForge | 1.21.1 | 1.0.0-alpha2 |
| [`neoforge/26.3`](https://github.com/tewek444/Light-Engine/tree/neoforge/26.3) | NeoForge | 26.3 | 1.0.0-alpha2 |

## What it does

- Gives **block light stronger than 15** — the radius comes from the config, per block.
- Vanilla lighting keeps working untouched in the 0–15 range.
- Extra light data is stored separately, saved with the world and synced to clients with chunks.
- No overexposure in rendering, sky light is never touched.
- One `COMMON` config file + in-game settings screen on the client.

## Support matrix

| Minecraft | Loader   | Mod version  | Status |
| --------- | -------- | ------------ | ------ |
| 26.3      | NeoForge | 1.0.0-alpha2 | 🚧     |
| 26.3      | Fabric   | —            | 📋     |
| 1.21.1    | Fabric   | 1.0.0-alpha2 | ✅     |
| 1.21.1    | NeoForge | 1.0.0-alpha2 | ✅     |
| 1.20.1    | Fabric   | —            | 📋     |
| any       | Forge    | —            | ❌     |
| any       | Quilt    | —            | ❌     |

✅ — supported, 🚧 — in development, 📋 — planned, ❌ — not supported.

## License

MIT — forks allowed as long as the copyright notice (`Copyright (c) 2026 Tewek`) and this permission notice stay included.
