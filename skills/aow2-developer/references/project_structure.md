# Project Structure

## Module Layout

```
AOW2-Online/
├── build.gradle.kts                 # Root build file
├── settings.gradle.kts              # Module includes
├── gradle.properties                # Version props
├── gradlew / gradlew.bat            # Gradle wrapper
├── .github/
│   └── workflows/
│       └── ci.yml                   # GitHub Actions CI
├── docker/
│   ├── docker-compose.yml           # Server + DB + Redis
│   ├── server.Dockerfile            # Spring Boot server
│   └── db/
│       └── init.sql                 # Database initialization
├── aow2-common/                     # Shared module
│   └── src/
│       ├── main/java/com/aow2/
│       │   ├── model/               # Data models (records)
│       │   │   ├── Faction.java
│       │   │   ├── UnitType.java
│       │   │   ├── BuildingType.java
│       │   │   ├── TerrainType.java
│       │   │   ├── UnitStats.java
│       │   │   ├── BuildingStats.java
│       │   │   ├── WeaponType.java
│       │   │   ├── ResearchNode.java
│       │   │   └── CommandType.java
│       │   ├── event/               # Event bus events
│       │   │   ├── GameEvent.java
│       │   │   ├── UnitKilledEvent.java
│       │   │   ├── BuildingDestroyedEvent.java
│       │   │   ├── ResearchCompleteEvent.java
│       │   │   └── ResourceChangedEvent.java
│       │   ├── config/              # Game configuration
│       │   │   ├── GameConfig.java
│       │   │   └── GameConstants.java
│       │   └── util/                # Utilities
│       │       ├── GridPosition.java
│       │       ├── Direction.java
│       │       └── MathUtils.java
│       └── test/java/com/aow2/
│           └── model/
├── aow2-core/                       # Core game logic
│   └── src/
│       ├── main/java/com/aow2/core/
│       │   ├── engine/              # Game loop & state
│       │   │   ├── GameLoop.java
│       │   │   ├── GameState.java
│       │   │   ├── TickManager.java
│       │   │   └── EntityManager.java
│       │   ├── entity/              # Game entities
│       │   │   ├── Entity.java
│       │   │   ├── Unit.java
│       │   │   ├── Building.java
│       │   │   ├── Projectile.java
│       │   │   └── Mine.java
│       │   ├── combat/              # Combat system
│       │   │   ├── CombatSystem.java
│       │   │   ├── DamageCalculator.java
│       │   │   ├── ArmorCalculator.java
│       │   │   └── ProjectileSystem.java
│       │   ├── economy/             # Economy system
│       │   │   ├── EconomySystem.java
│       │   │   ├── ResourceGenerator.java
│       │   │   └── PowerSystem.java
│       │   ├── movement/            # Movement & pathfinding
│       │   │   ├── MovementSystem.java
│       │   │   ├── PathfindingSystem.java
│       │   │   └── CollisionSystem.java
│       │   ├── ai/                  # AI system
│       │   │   ├── AISystem.java
│       │   │   ├── AIController.java
│       │   │   ├── EconomyAI.java
│       │   │   └── MilitaryAI.java
│       │   ├── research/            # Tech tree system
│       │   │   ├── ResearchSystem.java
│       │   │   └── TechTree.java
│       │   ├── world/               # Map & terrain
│       │   │   ├── GameMap.java
│       │   │   ├── Tile.java
│       │   │   ├── FogOfWarSystem.java
│       │   │   └── MapLoader.java
│       │   ├── command/             # Command pattern
│       │   │   ├── Command.java
│       │   │   ├── MoveCommand.java
│       │   │   ├── AttackCommand.java
│       │   │   ├── BuildCommand.java
│       │   │   ├── ProduceCommand.java
│       │   │   └── ResearchCommand.java
│       │   ├── network/             # Lockstep networking
│       │   │   ├── NetworkManager.java
│       │   │   ├── LockstepEngine.java
│       │   │   ├── CommandBuffer.java
│       │   │   ├── SyncChecker.java
│       │   │   └── CommandSerializer.java
│       │   ├── replay/              # Replay system
│       │   │   ├── ReplayRecorder.java
│       │   │   ├── ReplayPlayer.java
│       │   │   └── ReplayFile.java
│       │   └── campaign/            # Campaign system
│       │       ├── CampaignManager.java
│       │       ├── Mission.java
│       │       ├── Objective.java
│       │       └── Trigger.java
│       └── test/java/com/aow2/core/
├── aow2-client/                     # FXGL game client
│   └── src/
│       ├── main/java/com/aow2/client/
│       │   ├── AOW2App.java         # FXGL application entry
│       │   ├── scene/               # Game scenes
│       │   │   ├── MainMenuScene.java
│       │   │   ├── GameScene.java
│       │   │   ├── CampaignScene.java
│       │   │   ├── MultiplayerLobbyScene.java
│       │   │   └── MapEditorScene.java
│       │   ├── render/              # Custom rendering
│       │   │   ├── IsometricRenderer.java
│       │   │   ├── EntityRenderer.java
│       │   │   ├── TerrainRenderer.java
│       │   │   ├── UIRenderer.java
│       │   │   ├── FogRenderer.java
│       │   │   └── MinimapRenderer.java
│       │   ├── input/               # Input handling
│       │   │   ├── InputHandler.java
│       │   │   ├── SelectionManager.java
│       │   │   └── HotkeyConfig.java
│       │   ├── ui/                  # UI components
│       │   │   ├── HUD.java
│       │   │   ├── ProductionPanel.java
│       │   │   ├── ResearchPanel.java
│       │   │   ├── BuildMenu.java
│       │   │   └── ChatPanel.java
│       │   ├── editor/              # Map editor
│       │   │   ├── MapEditor.java
│       │   │   ├── TilePainter.java
│       │   │   └── EntityPlacer.java
│       │   └── audio/               # Audio system
│       │       ├── AudioManager.java
│       │       └── MusicPlayer.java
│       └── main/resources/
│           ├── assets/              # Game assets
│           │   ├── sprites/
│           │   ├── maps/
│           │   ├── music/
│           │   └── sfx/
│           └── data/                # Game data files
│               ├── units.json
│               ├── buildings.json
│               ├── tech_tree.json
│               └── campaigns/
├── aow2-server/                     # Spring Boot backend
│   └── src/
│       ├── main/java/com/aow2/server/
│       │   ├── AOW2ServerApp.java   # Spring Boot entry
│       │   ├── config/              # Spring configuration
│       │   │   ├── SecurityConfig.java
│       │   │   ├── WebSocketConfig.java
│       │   │   └── DatabaseConfig.java
│       │   ├── controller/          # REST controllers
│       │   │   ├── AuthController.java
│       │   │   ├── MatchmakingController.java
│       │   │   ├── MapController.java
│       │   │   ├── ReplayController.java
│       │   │   └── LeaderboardController.java
│       │   ├── service/             # Business logic
│       │   │   ├── AuthService.java
│       │   │   ├── MatchmakingService.java
│       │   │   ├── SessionService.java
│       │   │   └── RankingService.java
│       │   ├── model/               # JPA entities
│       │   │   ├── Player.java
│       │   │   ├── GameSession.java
│       │   │   ├── MatchResult.java
│       │   │   └── UploadedMap.java
│       │   ├── repository/          # Spring Data repos
│       │   └── websocket/           # WebSocket handlers
│       │       ├── GameWebSocketHandler.java
│       │       └── LobbyWebSocketHandler.java
│       └── main/resources/
│           ├── application.yml
│           └── db/migration/        # Flyway migrations
├── aow2-modding/                    # Mod system
│   └── src/
│       ├── main/java/com/aow2/mod/
│       │   ├── ModLoader.java
│       │   ├── ModManager.java
│       │   ├── ModManifest.java
│       │   ├── DataOverride.java
│       │   └── script/
│       │       ├── LuaEngine.java
│       │       ├── ScriptBindings.java
│       │       └── GameAPI.java    # Lua-accessible API
│       └── main/resources/
│           └── example_mod/
│               ├── mod.json
│               ├── data/
│               └── scripts/
└── docs/                            # Documentation
    ├── architecture.md
    ├── combat_formulas.md
    ├── modding_guide.md
    └── development_report.md
```

## Package Naming Convention

- All code under `com.aow2` base package
- Sub-packages by module: `com.aow2.common`, `com.aow2.core`, `com.aow2.client`, `com.aow2.server`, `com.aow2.mod`
- Inner packages by domain: `.combat`, `.economy`, `.ai`, `.movement`, etc.

## File Naming Convention

- PascalCase for classes: `CombatSystem.java`, `UnitStats.java`
- camelCase for methods and fields: `calculateDamage()`, `maxHp`
- UPPER_SNAKE_CASE for constants: `MAX_UNITS_PER_PLAYER`, `TICK_RATE`
- kebab-case for resource files: `unit-stats.json`, `tech-tree.json`
