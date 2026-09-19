# Light Engine

Extended block light for Minecraft. Configure per-block light radius beyond the vanilla maximum of 15 via `lightengine-common.toml`.

One branch per loader — code lives there, not here:

- [`fabric/1.21.1`](https://github.com/tewek444/Light-Engine/tree/fabric/1.21.1) — Fabric 1.21.1, mod 1.0.0 (Alpha1)
- [`neoforge/1.21.1`](https://github.com/tewek444/Light-Engine/tree/neoforge/1.21.1) — NeoForge 1.21.1, mod 1.0.0 (Alpha1)

## Support matrix

| Minecraft | Loader   | Mod version    | Status           |
| --------- | -------- | -------------- | ---------------- |
| 1.21.1    | Fabric   | 1.0.0 (Alpha1) | ✅ Supported     |
| 1.21.1    | NeoForge | 1.0.0 (Alpha1) | ✅ Supported     |
| 1.20.1    | Fabric   | —              | 🔜 Planned       |
| 26.3      | Fabric   | —              | 🔜 Planned       |
| 26.3      | NeoForge | —              | 🔜 Planned       |
| any       | Forge    | —              | ❌ Not supported |
| any       | Quilt    | —              | ❌ Not supported |

## Update check

Each build fetches `update.json` from its own branch once per game start and prints one chat message with a link to Releases when a newer version is out. Nothing is downloaded automatically. NeoForge also shows updates in the mod list.

## License

MIT — forks allowed as long as the copyright notice (`Copyright (c) 2026 Tewek`) and this permission notice stay included.
