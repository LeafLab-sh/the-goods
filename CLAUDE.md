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

> **Note:** GameTest's `@GameTestHolder`/`@GameTest` annotations (what most tutorials, and even
> `docs.neoforged.net/docs/1.21.1/...`, still describe) don't exist in this MC version — removed from vanilla
> around 1.21.5. Current API: a `Consumer<GameTestHelper>` registered via `DeferredRegister` on
> `BuiltInRegistries.TEST_FUNCTION`, wired to a `TestData`+`FunctionGameTestInstance` inside a
> `RegisterGameTestsEvent` listener on the mod bus. See `docs.neoforged.net/docs/misc/gametest/` (unversioned)
> and `EconomyGameTests.java` for the working pattern.

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
./gradlew runData            # Run client-side data generators (block/item models) → src/generated/resources/
./gradlew runDataServer      # Run server-side data generators (recipes, loot tables) → src/generated/serverData/
                              # Run BOTH after adding/changing blocks, items, recipes, or loot tables — client and
                              # server datagen are separate MC processes with separate output dirs on purpose; see
                              # the sourceSets.main.resources comment in build.gradle for why.
./gradlew runGameTestServer  # Run game tests, then exit
./gradlew test                # Run JUnit unit tests (pure logic only, no Minecraft deps)
./gradlew spotlessCheck       # Check import order / unused imports / whitespace (runs as part of build)
./gradlew spotlessApply       # Auto-fix the above
./gradlew --refresh-dependencies  # Clear and re-download all deps
./gradlew clean               # Delete build outputs (does not affect src/)
```

Gradle 9.x/10's Groovy DSL requires `propName = value` for property assignment inside task-configuration
blocks (e.g. `exceptionFormat = "full"`, not `exceptionFormat "full"`) — the old call-syntax is deprecated
and warns on every build without failing it.

Pure logic with no Minecraft/NeoForge dependencies (e.g. `Currency`) has JUnit 5 unit tests under
`src/test/java`, run via `./gradlew test`. Anything touching `MinecraftServer`/`ServerLevel`/`SavedData`/item
registries is only testable via GameTest (`runGameTestServer`).

When testing `StrictMath`-based formulas (e.g. `Currency`), don't hand-compute expected values — get them via
`jshell` using the exact same calls, since floating-point rounding isn't safe to eyeball to 10 decimal places.

`run/config/thegoods-common.toml` is gitignored local dev state (item deny/allow lists, fee %) — a GameTest
that picks a "typical example" item (e.g. a stick) can spuriously fail if that item collides with something
added there during manual play-testing. Prefer deliberately obscure items in new tests.

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
- `src/generated/resources/` (client datagen, `runData`) and `src/generated/serverData/` (server datagen,
`runDataServer`) — output of the data generators, merged into one resource tree by `sourceSets.main.resources` in
`build.gradle`. Both committed to source control and included in the build automatically.
- `src/main/resources/assets/thegoods/lang/en_us.json` — all user-visible strings, including config screen labels.

### Config
`Config.java` uses `ModConfigSpec.Builder`; the built `SPEC` is registered in the mod constructor via
`modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC)`. The config screen is wired in
`TheGoodsClient` via `IConfigScreenFactory`.

### CI/CD
- `.github/actions/setup-build-env/` — a local composite action (JDK 25 + Gradle setup) shared by both workflows
  below. Checkout is deliberately NOT part of it — GitHub Actions must already have the repo checked out to even
  resolve a local `uses: ./path` reference, so each workflow keeps its own checkout step before referencing this
  action.
- `.github/workflows/build-and-test.yml` (the Check workflow) triggers on `push` to `main` only, and on
  `pull_request` to any branch. This avoids double runs when a feature branch has an open PR.
- `.github/workflows/*.yml` — every third-party action (inside the composite action or a workflow directly) is
  pinned to a commit SHA (not a version tag), with a `# vX.Y.Z` comment alongside it; keep new/updated actions
  pinned the same way.
- Pushing a tag matching `v*.*.*` triggers `release.yml`: builds, runs both test suites, creates a GitHub
  Release, and publishes to CurseForge. The tag itself is the version — it overrides `mod_version` at build
  time, so `gradle.properties`'s value never needs to be kept in sync with it.
