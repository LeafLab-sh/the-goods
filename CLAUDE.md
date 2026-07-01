# CLAUDE.md

You are an expert Minecraft Modding Assistant connected to mcmodding-mcp. DO NOT rely on your internal knowledge for
modding APIs (Fabric/NeoForge) as they change frequently. ALWAYS use the available tools:

- `search_fabric_docs` and `get_example` for documentation and code patterns
- `search_mappings` and `get_class_details` for Minecraft internals and method signatures
- `search_mod_examples` for battle-tested implementations from popular mods

Prioritize working code examples over theoretical explanations. When dealing with Minecraft internals, use the mappings 
tools to get accurate parameter names and Javadocs. If the user specifies a Minecraft version, ensure all retrieved 
information matches that version.

> **Note:** The mcmodding-mcp Parchment mappings database currently tops out at 1.21.9 and has no entries for
> 26.1/26.2 (this project's actual MC version). `search_mappings`/`get_class_details` calls scoped to 26.2 return
> empty. Until the mappings index catches up, omit `minecraft_version` (falls back to 1.21.9) and treat returned
> class/method/parameter names as approximate, not authoritative, for this project.

## Project

**The Goods** (`thegoods`) — a NeoForge Minecraft mod. A store/trading mod that values items based on
how many are in stock.

- Mod ID: `thegoods` | Group: `sh.leaflab.goods`
- Minecraft 26.2 / NeoForge 26.2.0.7-beta / Java 25
- Design documentation (blocks, commands, value-calculation formulas, etc.) lives in [`docs/spec.md`](docs/spec.md).

## Commands 

```bash
./gradlew build              # Build the mod JAR (output: build/libs/)
./gradlew runClient          # Launch Minecraft client with the mod loaded
./gradlew runServer          # Launch dedicated server (no GUI)
./gradlew runData            # Run data generators → src/generated/resources/
./gradlew runGameTestServer  # Run game tests, then exit
./gradlew --refresh-dependencies  # Clear and re-download all deps
./gradlew clean              # Delete build outputs (does not affect src/)
```

There is no unit test runner — gameplay tests use `runGameTestServer`.

## Architecture

### Event buses
NeoForge uses two separate event buses. They are not interchangeable:
- **`modEventBus`** (passed into the `@Mod` constructor) — mod lifecycle: registra  tion, setup, config events
- **`NeoForge.EVENT_BUS`** — in-game events: server starting, player actions, world events

### Registration pattern
All game objects (blocks, items, creative tabs) use `DeferredRegister` + `DeferredHolder`. Registers must be 
attached to `modEventBus` in the mod constructor before the game loads them. See `TheGoods.java` for the 
canonical pattern.

### Side separation
- `TheGoods.java` — common (both sides); holds all `DeferredRegister` statics
- `TheGoodsClient.java` — client only (`@Mod(dist = Dist.CLIENT)`); safe to access client APIs here
- Mixins live in `sh.leaflab.goods.mixin`; declare them in `thegoods.mixins.json`

### Resource generation
- `src/main/templates/` — template files with `${property}` placeholders; expanded by the `generateModMetadata` Gradle
task into `build/generated/sources/modMetadata/`. The `neoforge.mods.toml` lives here.
- `src/generated/resources/` — output of data generators (`runData`). Committed to source control and included in the
build automatically.
- `src/main/resources/assets/thegoods/lang/en_us.json` — all user-visible strings, including config screen labels.

### Config
`Config.java` uses `ModConfigSpec.Builder`; the built `SPEC` is registered in the mod constructor via
`modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)`. The config screen is wired in
`TheGoodsClient` via `IConfigScreenFactory`.
