# Light Engine (Fabric 26.3)

Fabric port of Light Engine `1.0.0-alpha3` for Minecraft 26.3.
Source base: `[NeoForge 1.21.1] Light Engine` (stable alpha3) with the
26.3 adaptations re-derived from `[NeoForge 26.3] Light Engine`, re-wired
to the Fabric loader following the `[Fabric 1.21.1]` project layout
(`src/main` + `src/client` split sourcesets,
`ClientPlayNetworking`/`ServerPlayNetworking` transport,
`LightEngineConfig` NightConfig owner, ModMenu entrypoint).

## Requirements

- Java 25 (MC 26.3 ships Java 25; `build.gradle` targets release 25)
- Gradle 9.7.0+ (see `gradle/wrapper/gradle-wrapper.properties`;
  required by Loom 1.18.2)
- Loom 1.18.2, Fabric Loader 0.19.5, Fabric API `0.161.0+26.3`,
  ModMenu `22.0.0-alpha.1` (compile-only, optional at runtime)

## Setup

For setup instructions, please see the [Fabric Documentation page](https://docs.fabricmc.net/develop/getting-started/creating-a-project#setting-up) related to the IDE that you are using.

## Notes for 26.3

- Mojang ships 26.x jars unobfuscated (official names) and no longer
  publishes `client_mappings`/`server_mappings`, so
  `mappings loom.officialMojangMappings()` cannot work. Following
  fabric-api's own 26.3 build, this project sets
  `fabric.loom.disableObfuscation=true` (see `gradle.properties`) and
  declares no mappings layer; plain `implementation`/`compileOnly` are
  used instead of `modImplementation`/`modCompileOnly`, and NightConfig
  is nested via `loom.nestJars`. `gradlew build` is green, including the
  `LightFieldStoreRegressionTest` suite.
- Fabric API changes applied: `ClientCommandManager` -> `ClientCommands`,
  `ServerWorldEvents` -> `ServerLevelEvents`,
  `PayloadTypeRegistry.playS2C/playC2S` -> `clientboundPlay/serverboundPlay`,
  `HudRenderCallback` -> `HudElement` + `HudElementRegistry.addLast`,
  `Screen.hasControlDown()` -> event/`Minecraft` control state (SDL input).
- Bugfix vs the in-progress NeoForge 26.3 port: `LightFieldStore.readTag`
  again evicts sections missing from the chunk NBT (the 1.21.1 behavior;
  without it stale light resurrects on load — caught by
  `chunkReadEvictsSectionsMissingFromNbt`). Worth backporting.

## License

This project is licensed under the MIT License. See LICENSE for details.
