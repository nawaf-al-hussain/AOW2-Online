# AGENT.md — AOW2-Online Project Guide

> This file provides everything an AI agent needs to quickly understand the project, its architecture, conventions, and how to work on it effectively. Read this FIRST before touching any code.

---

## Project Overview

**AOW2-Online** is a modern recreation of the classic mobile RTS *Art of War 2: Online*, built with Java 21, FXGL (JavaFX), Spring Boot, and Next.js. It's a lockstep-networked real-time strategy game with two factions (Confederation and Resistance), a full tech tree (48 research nodes), AI opponents, campaign missions (29 Lua scripts), modding support, and a web companion dashboard.

| Aspect | Detail |
|--------|--------|
| **Language** | Java 21 (common, core, client, server, modding) + TypeScript (web) |
| **Build** | Gradle (Kotlin DSL) for Java modules; Bun/npm for web |
| **Java Version** | 21 (preview features enabled: sealed interfaces, pattern matching, switch expressions, records) |
| **Group/Version** | `com.aow2` / `0.1.0-SNAPSHOT` |
| **License** | Project is a recreation for educational/learning purposes |

---

## Module Architecture

```
aow2-common   ← Shared data models (no project deps)
    ↑
aow2-core     ← Game engine (depends on common only; modding loaded via reflection)
    ↑
aow2-client   ← JavaFX game client (depends on common, core, modding)
    
aow2-server   ← Spring Boot backend (depends on common only)
    
aow2-web      ← Next.js companion web dashboard (independent; NOT a Gradle module)
    
aow2-modding  ← Lua mod system (depends on common, core)
```

### Module Dependency Rules
- **aow2-core does NOT compile-depend on aow2-modding** — it uses reflection to load `MissionScriptEngine` to avoid circular builds.
- **aow2-server depends on common only** — the server is a relay/lobby service, it does NOT run the game simulation.
- **aow2-web is completely independent** — it's a separate Next.js project in the repo root.

---

## Build & Test Commands

```bash
# Build all Java modules
./gradlew build

# Build without tests
./gradlew build -x test

# Run all tests
./gradlew test

# Run tests for a specific module
./gradlew :aow2-core:test
./gradlew :aow2-server:test

# Run a single test class
./gradlew :aow2-core:test --tests "com.aow2.core.combat.CombatSystemTest"

# Code coverage report
./gradlew jacocoTestReport

# Web module (from aow2-web/ directory)
cd aow2-web
bun install
bun run dev        # Dev server on port 3000
bun run build      # Production build
bun run test       # Run Vitest tests
bun run lint       # ESLint
```

---

## Entry Points

| Module | Entry Point | Type |
|--------|------------|------|
| aow2-client | `com.aow2.client.AOW2App` | JavaFX/FXGL `main()` |
| aow2-server | `com.aow2.server.AOW2ServerApp` | Spring Boot (port 8080) |
| aow2-web | Next.js App Router | Web dashboard (port 3000) |

---

## Game Architecture — How It Works

### Game Loop (10 TPS Fixed Timestep)

The engine runs at **10 ticks per second** (100ms per tick). Each tick, `TickManager.processTick()` executes 10 phases in order:

1. **Commands** — Drain incoming commands from the priority queue
2. **Movement** — `MovementSystem` advances units along paths
3. **Combat** — `CombatSystem` resolves attacks, damage, projectiles
4. **Mine Detonation** — `MineDetonationSystem` processes mine triggers
5. **HP Regeneration** — Every 127 ticks
6. **Production** — `ProductionSystem` advances build queues
7. **Research** — `ResearchSystem` advances active research
8. **Economy** — Credit generation (128-tick cycle)
9. **AI** — Decision cycles vary by difficulty (Easy: 60 ticks, Normal: 30, Hard: 15)
10. **Fog of War** — Updated every 4 ticks
11. **Cleanup** — Remove dead entities
12. **Tick advance** — Increment global tick counter

### Command Flow (Lockstep Networking)

```
Player Input → InputHandler → CommandType (sealed record)
  → LockstepEngine.submitCommand()
    → CommandBuffer (ring buffer, 2-frame input delay, size 16)
      → CommandSerializer (binary: [typeId:1][tick:8][playerId:4][payload:variable])
        → Network (WebSocket relay via server)
          → Opponent's CommandBuffer.drainFrame()
            → CommandProcessor.process() → specific handler
              → Game system (Movement, Combat, Economy, etc.)
```

Both clients run identical simulations. The server only **relays** commands — it is NOT authoritative. This means determinism is critical: any difference between clients causes a desync.

### CommandType (Sealed Interface — 12 variants)

```java
public sealed interface CommandType {
    record Move(int playerId, int unitId, int targetX, int targetY) implements CommandType {}
    record Attack(int playerId, int unitId, int targetUnitId) implements CommandType {}
    record AttackMove(int playerId, int unitId, int targetX, int targetY) implements CommandType {}
    record Build(int playerId, int buildingType, int gridX, int gridY) implements CommandType {}
    record Produce(int playerId, int buildingId, int unitType) implements CommandType {}
    record Research(int playerId, int buildingId, int researchId) implements CommandType {}
    record Garrison(int playerId, int unitId, int buildingId) implements CommandType {}
    record Ungarrison(int playerId, int unitId) implements CommandType {}
    record Cancel(int playerId, int targetId) implements CommandType {}
    record SiegeMode(int playerId, int unitId, boolean enable) implements CommandType {}
    record Stop(int playerId, int unitId) implements CommandType {}
    record Patrol(int playerId, int unitId, int targetX, int targetY) implements CommandType {}
}
```

### Server WebSocket Endpoints

| Endpoint | Purpose | Key Operations |
|----------|---------|---------------|
| `/ws/lobby` | Matchmaking | `join_queue`, `leave_queue`, `ready` → match_found → game_start |
| `/ws/game` | Game relay | `command` (relay), `sync_hash` (desync check), `game_over` (two-phase ELO commit) |
| `/ws/chat` | In-game chat | Real-time text messages |

### Research / Tech Tree

- **48 research nodes** (IDs 0-47): Confederation (0-23, 43) + Resistance (24-47, excl 43)
- `TechTree.java` — Static structure defining prerequisites and unlocks
- `ResearchRegistry` — Loads from `tech_tree.json` (per-research effects)
- Effects applied lazily: systems call `researchSystem.hasResearch(playerId, researchId)` when needed
- Research requires a powered Technology Centre building

### AI System

- `AISystem` coordinates `EconomyAI`, `MilitaryAI`, `ResearchAI`
- Uses **DeterministicLCG** (Park-Miller) — NOT `java.util.Random` — for lockstep determinism
- Three difficulties affect decision frequency, strategy quality, and concurrent task limits

---

## Key Packages Per Module

### aow2-common (`com.aow2.common.*`)
| Package | Contents |
|---------|----------|
| `model` | CommandType, UnitType, BuildingType, TerrainType, GridPosition, Faction, Direction, etc. |
| `config` | GameConstants, GameConfig, StatsRegistry |
| `event` | GameEvent (sealed), DamageAppliedEvent, UnitKilledEvent, etc. |
| `util` | MathUtils |

### aow2-core (`com.aow2.core.*`)
| Package | Contents |
|---------|----------|
| `engine` | GameLoop, GameState, TickManager |
| `network` | LockstepEngine, CommandBuffer, CommandSerializer, SyncChecker |
| `command` | CommandProcessor, MoveCommandHandler, AttackCommandHandler, etc. |
| `entity` | Entity, Unit, Building, Projectile |
| `world` | GameMap, Tile, EntityManager, MapLoader, FogOfWarSystem |
| `combat` | CombatSystem, DamageCalculator, ArmorCalculator, ProjectileSystem |
| `movement` | MovementSystem, PathfindingSystem (A*), CollisionSystem |
| `economy` | EconomySystem, ProductionSystem, PowerSystem, BuildingPlacementSystem |
| `research` | TechTree, ResearchSystem, ResearchRegistry |
| `ai` | AISystem, EconomyAI, MilitaryAI, ResearchAI, DeterministicLCG |
| `campaign` | CampaignManager, Mission, ScriptEngine |
| `replay` | ReplayRecorder, ReplayPlayer |

### aow2-server (`com.aow2.server.*`)
| Package | Contents |
|---------|----------|
| `websocket` | GameWebSocketHandler, LobbyWebSocketHandler, ChatWebSocketHandler |
| `controller` | AuthController, MatchmakingController, ReplayController, etc. |
| `service` | SessionService, AuthService, MatchmakingService, RankingService |
| `security` | JwtUtil, JwtAuthenticationFilter, RateLimitFilter |
| `config` | WebSocketConfig, SecurityConfig, RateLimitConfig |

### aow2-client (`com.aow2.client.*`)
| Package | Contents |
|---------|----------|
| `scene` | GameScene, MainMenuScene, CampaignScene, MapEditorScene, etc. |
| `render` | IsometricRenderer, EntityRenderer, CameraController, SpriteManager |
| `input` | InputHandler, SelectionManager |
| `ui` | HUD, AccessibilitySettings |
| `editor` | MapEditor, EntityPlacer, TilePainter |

### aow2-modding (`com.aow2.mod.*`)
| Package | Contents |
|---------|----------|
| (root) | ModManager, ModLoader, ModManifest, GameDataRegistry |
| `script` | LuaEngine, ScriptBindings, GameAPI |
| `campaign` | MissionScriptEngine |

---

## Code Conventions

### Java 21 Features Used
- **Sealed interfaces** — `CommandType`, `GameEvent`, `MilitaryAction`
- **Records** — All command types, data carriers (GridPosition, UnitStats, etc.)
- **Pattern matching** — `switch` expressions with type patterns everywhere
- **Text blocks** — Used sparingly

### Naming & Style
- **Package names**: lowercase, no abbreviations (`com.aow2.core.combat`, not `com.aow2.core.cmbt`)
- **Class names**: PascalCase, descriptive (`CombatSystem`, `BuildingPlacementSystem`)
- **Constants**: `UPPER_SNAKE_CASE` in `GameConstants` and `StatsRegistry`
- **REF comments**: Many files have `REF: <filename>` comments referencing reverse engineering analysis documents
- **FIX comments**: Bug fixes annotated with `FIX (P0-C3):` or `FIX (P1-H5):` referencing the issue ID

### Testing
- **Framework**: JUnit 5 + Mockito
- **Test location**: `src/test/java/com/aow2/<module>/` mirrors main package structure
- **Naming**: `*Test.java` for unit tests, `*IntegrationTest.java` for integration tests
- **Coverage**: ~77 test files, ~620+ test methods across all modules

### Data Files
| File | Format | Purpose |
|------|--------|---------|
| `units.json` | JSON | Unit type definitions (stats: hp, damage, attackSpeed, etc.) |
| `buildings.json` | JSON | Building type definitions |
| `tech_tree.json` | JSON | Research tree effects |
| `game_config.json` | JSON | Turn times, footprints, power radii, rank thresholds |
| Campaign JSONs | JSON | Mission definitions per episode |
| Map JSONs | JSON | Grid-based map data (terrain tiles) |
| Lua scripts | Lua 5.2 | Campaign mission logic (triggers, objectives, cutscenes) |

### Critical Invariants
1. **Lockstep determinism** — ALL game logic must be deterministic. No `java.util.Random`, no `HashMap` iteration order, no `System.currentTimeMillis()` in game logic. Use `DeterministicLCG` for randomness.
2. **CommandBuffer synchronization** — `submitCommand()` must remain `synchronized` (thread-safe with network thread).
3. **SyncChecker** — Entity iteration must be sorted by ID before hashing (non-deterministic iteration causes false desyncs).
4. **StatsRegistry is the single source of truth** — units.json and buildings.json should match StatsRegistry values. If they diverge, the JSON files need reconciliation.

---

## Configuration

### Server (`application.yml`)
- Port: 8080
- Database: PostgreSQL (localhost:5432/aow2), with Flyway migrations
- JWT: Secret from `AOW2_JWT_SECRET` env var (24h expiration)
- Matchmaking: ELO range 100→500 expanding search
- Docker validates JWT secret at startup (rejects default dev key)

### Docker
- `docker-compose.yml`: `server` (Spring Boot) + `db` (PostgreSQL 16)
- `server.Dockerfile`: Multi-stage (Gradle build → JRE runtime), non-root user, health checks
- Volumes: `pgdata`, `replay-data`, `map-uploads`

---

## Reverse Engineering Reference Files

The original reverse engineering produced a comprehensive archive of analysis documents, game data, diagrams, and processed assets. These files are located in the `docs/RE/` directory and are the **ground truth** for game balance and behavior. When in doubt, consult these files.

### Analysis Documents (`docs/RE/`)

| File | Content |
|------|---------|
| `gameplay_analysis/ai_analysis.md` | Original AI decision patterns and timing |
| `gameplay_analysis/combat_formulas.md` | Damage calculation, attack cycles, research effects |
| `gameplay_analysis/pathfinding.md` | A* implementation details, path storage format |
| `gameplay_analysis/unit_stats.md` | Complete unit stat definitions from RE |
| `gameplay_analysis/building_stats.md` | Complete building stat definitions from RE |
| `gameplay_analysis/complete_unit_stats.json` | Full unit data from RE (authoritative JSON) |
| `gameplay_analysis/complete_building_stats.json` | Full building data from RE (authoritative JSON) |
| `gameplay_analysis/decrypted_data.json` | 3.76 MB of extracted game data |
| `gameplay_analysis/decryption_algorithm.md` | How game data was decrypted |
| `gameplay_analysis/text_strings.json` | 567 decoded in-game strings |
| `gameplay_analysis/campaign_guide.md` | Campaign mission specifications |
| `gameplay_analysis/map_system.md` | Map format and terrain system |
| `network_analysis/protocol_specification.md` | Network protocol, command format, session flow |
| `network_analysis/multiplayer_architecture.md` | ELO system, matchmaking, WebSocket architecture |
| `network_analysis/session_lifecycle.md` | Session start/end/reconnect flow |
| `network_analysis/packet_formats.json` | All 34+ network packet formats |
| `documentation/MASTER_DOCUMENTATION.md` | 3,330-line master reference (THE source of truth) |
| `documentation/ArtOfWar3_Recreation_Blueprint.md` | 2,283-line recreation architecture guide |
| `documentation/class_mapping.json` | Decompiled class → project class mapping |
| `documentation/diagrams/` | Architecture diagrams, combat flow, state machines |
| `documentation/diagrams/tech_tree_confederation.md` | Confederation tech tree structure |
| `documentation/diagrams/tech_tree_rebels.md` | Resistance tech tree structure |
| `database_analysis/save_system.md` | Original save system analysis |

### Extracted Assets (`docs/RE/assets_processed/`)

Original game sprites, fonts, and UI assets extracted from both factions:
- `faction_s0/` — Confederation faction assets
- `faction_s1/` — Resistance faction assets
- Each faction contains: `sprites/`, `fonts/`, `ui/`, `maps/`

### Source Code (`docs/RE/source_readable/`)

Decompiled Java source from the original game (if present in the archive).

### Backup Location

The original zip archive is stored at: `/home/z/my-project/upload/art-of-war-2-online-re-full.zip`

---

## Skills (in `skills/` directory)

Two specialized skills are included in this repo for AI agents. Read their `SKILL.md` files first.

### `skills/aow2-developer/` — Development Skill
**Use when**: implementing game features, fixing bugs, adding new systems, refactoring code.

**How to use**:
1. Read `skills/aow2-developer/SKILL.md` — this is the entry point with full instructions
2. Read `skills/aow2-developer/references/Goal.md` — understand what the final game should be
3. Read `skills/aow2-developer/references/ProjectProgress.md` — check what's already implemented
4. Read the relevant RE doc from `docs/RE/` (see table below)
5. Plan your implementation, quoting the RE spec
6. Implement, test (`./gradlew test`), cross-reference against RE data
7. Update `ProjectProgress.md` with what you did

**Key reference files inside the skill**:
| File | What it covers |
|------|---------------|
| `SKILL.md` | Master instructions — read this first |
| `references/Goal.md` | End goal and must-have features |
| `references/phases.md` | 15-phase development plan (Phase 0–14) |
| `references/ProjectProgress.md` | What's been done vs. what's left |
| `references/coding_standards.md` | Java 21 style, testing, git conventions |
| `references/project_structure.md` | Module layout and package naming |
| `references/tech_stack.md` | Detailed tech decisions and versions |
| `references/anti_hallucination.md` | Rules to prevent fabricating game data |

### `skills/aow2-analyzer/` — Analysis/QA Skill
**Use when**: auditing code quality, verifying spec compliance, hunting bugs, generating analysis reports.

**How to use**:
1. Read `skills/aow2-analyzer/SKILL.md` — master instructions
2. Read the developer skill's `Goal.md` and `ProjectProgress.md` first
3. For each system you analyze:
   - Read the RE documentation for that system (e.g., `docs/RE/gameplay_analysis/combat_formulas.md`)
   - Read the implementation code (e.g., `aow2-core/src/main/java/com/aow2/core/combat/`)
   - Compare every constant, formula, and behavior
   - Re-read both docs and code to double-check before reporting
4. Rate confidence: HIGH / MEDIUM / LOW (mark LOW as UNVERIFIED)
5. Generate report with: evidence, file paths, line numbers, fix plan

**Key reference files inside the skill**:
| File | What it covers |
|------|---------------|
| `SKILL.md` | Master instructions — read this first |
| `references/analysis_checklist.md` | Per-system checklists (combat, economy, AI, network, etc.) |
| `references/validation_methods.md` | How to validate specific types of claims |

### Before Starting Any Work:
1. Read `skills/aow2-developer/references/Goal.md` to understand the end state
2. Read `skills/aow2-developer/references/ProjectProgress.md` to see what's been done
3. Choose the right skill: **developer** for implementing, **analyzer** for auditing
4. Consult the appropriate RE documentation in `docs/RE/` before touching any code

---

## Anti-Hallucination Rules (CRITICAL)

When working on this project, you MUST follow these rules:

1. **Never invent game data** — All stats, formulas, and mechanics come from `docs/RE/` files
2. **Cross-reference before implementing** — Read at least 2 RE sources before writing game logic
3. **Mark assumptions** — Use `// ASSUMPTION:` comments when spec is unclear
4. **Validate against spec** — After implementing, compare output against documented formulas
5. **Read before writing** — Always read the relevant RE file, never work from memory
6. **Primary sources** (most authoritative): `MASTER_DOCUMENTATION.md` > `complete_unit_stats.json` > wiki research

---

## Known Assumptions (Unverified from RE)

| Constant | Value | Notes |
|----------|-------|-------|
| `SIEGE_RANGE_BONUS` | 3 | May affect siege balance |
| `ARTILLERY_FIXED_FLIGHT_TIME` | 15 | May affect artillery timing |
| `CC_PLACEMENT_RADIUS` | 20 | May affect build placement |
| `ARM_DELAY_TICKS` | 10 | May affect mine timing |
| `CANCEL_REFUND_PERCENT` | 0.50 | May affect economy balance |
| `BUILDING_ATTACK_COOLDOWN` | 5 | May affect defensive buildings |
| `INFANTRY_BASE_RECOVERY` | 1 | May affect infantry sustainability |

---

## Bug Fix History

All tracked issues from the original full-project analysis have been resolved:

| Priority | Count | Status |
|----------|-------|--------|
| P0 (Showstoppers) | 4 | ✅ All Fixed |
| P1 (Critical) | 22 | ✅ All Fixed |
| P2 (Important) | 10 | ✅ All Fixed |
| P3 (Polish) | 9 | ✅ All Fixed |

Fixes are annotated in source code with `FIX (P<X>-<ID>):` comments.

---

## How to Add New Features

### Adding a new command type:
1. Add a new record to `CommandType` sealed interface in `aow2-common`
2. Add serialization/deserialization in `CommandSerializer` (`aow2-core/network`)
3. Add a handler method in `CommandProcessor` (`aow2-core/command`)
4. Add handler implementation (new `XxxCommandHandler` class)
5. Wire input in `InputHandler` (`aow2-client/input`)
6. Update `SyncChecker` if the command affects entity state

### Adding a new unit type:
1. Add enum value in `UnitType` (`aow2-common/model`)
2. Add stats in `StatsRegistry` (`aow2-common/config`)
3. Update `units.json` (client and common resources)
4. Add faction/category checks in `UnitType`
5. Update AI decision logic if needed (`EconomyAI`, `MilitaryAI`)

### Adding a new research node:
1. Add to `TechTree.java` (`aow2-core/research`)
2. Add effects in `ResearchRegistry` and `tech_tree.json`
3. Update affected systems (combat, economy, movement) to check the new research

### Adding a server endpoint:
1. Add controller method in `aow2-server/controller`
2. Add service logic in `aow2-server/service`
3. Add repository method if database access needed
4. Update `SecurityConfig` if endpoint needs auth
5. Add Flyway migration if schema changes

---

## Git Workflow

- **Branch**: `main`
- **Commit style**: `fix(scope): description` or `feat(scope): description`
- **Recent history**: Series of bug-fix commits driven by analysis reports
- **Remote**: `https://github.com/nawaf-al-hussain/AOW2-Online.git`

---

## Important File Locations

```
AOW2-Online/
├── build.gradle.kts              # Root build config
├── settings.gradle.kts           # Module list
├── gradle.properties             # Version catalog
├── docker/                       # Docker deployment
│   ├── docker-compose.yml
│   └── server.Dockerfile
├── docs/                         # Documentation
│   └── RE/                       # Reverse Engineering archive (ground truth)
│       ├── gameplay_analysis/    # Combat, units, buildings, AI, pathfinding, campaigns
│       ├── network_analysis/    # Protocol, multiplayer, packets, sessions
│       ├── documentation/       # Master docs, blueprints, diagrams, class mappings
│       ├── database_analysis/   # Save system analysis
│       ├── assets_processed/    # Original sprites, fonts, UI assets per faction
│       └── wiki_research/       # Wiki research results and game data
├── agent-ctx/                    # Agent context from previous sessions
├── aow2-common/                  # Shared models
│   └── src/main/resources/data/   # Game data JSONs
├── aow2-core/                    # Game engine
│   └── src/main/resources/data/  # Campaigns, maps, Lua scripts
├── aow2-client/                  # JavaFX game client
│   └── src/main/resources/data/  # Client-side data copies
├── aow2-server/                  # Spring Boot backend
│   └── src/main/resources/       # application.yml, Flyway migrations
├── aow2-modding/                 # Lua mod system
│   └── src/main/resources/       # Example mod
└── aow2-web/                     # Next.js web dashboard
    ├── src/app/                  # Pages
    ├── src/components/ui/        # shadcn/ui components
    ├── src/lib/                  # Stores, API, DB
    └── src/__tests__/            # Vitest tests
```

---

## Quick Diagnostic Commands

```bash
# Does the project compile?
./gradlew build -x test

# Do all tests pass?
./gradlew test

# What modules exist?
ls -d aow2-*/

# How many source files per module?
find aow2-common/src/main -name "*.java" | wc -l
find aow2-core/src/main -name "*.java" | wc -l
find aow2-server/src/main -name "*.java" | wc -l
find aow2-client/src/main -name "*.java" | wc -l
find aow2-modding/src/main -name "*.java" | wc -l

# Check for TODO/FIX comments
rg "TODO|FIXME" --type java

# Check for hardcoded constants that should be configurable
rg "new HashMap|java\.util\.Random" --type java

# Git status summary
git diff --stat
```

---

*Last updated: 2026-06-21*
