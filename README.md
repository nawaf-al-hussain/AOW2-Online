# Art of War 2: Online

A modern recreation of the classic mobile RTS game "Art of War 2: Online" using Java 21, JavaFX/FXGL, and Spring Boot.

## Features
- Faithful recreation of all original game mechanics
- Two factions: Global Confederation and Resistance
- 14 unit types, 16 building types, 48 research effects
- Online multiplayer with matchmaking
- Integrated map builder
- Data-driven modding support (JSON/YAML + Lua)
- Full replay system

## Tech Stack
| Component | Technology |
|-----------|-----------|
| Language | Java 21 (LTS) |
| Game Engine | FXGL 21 |
| UI | JavaFX 21 |
| Backend | Spring Boot 3.3 |
| Database | PostgreSQL 16 |
| Build | Gradle 8.7 (Kotlin DSL) |

## Building

```bash
./gradlew build
```

## Running the Client

```bash
./gradlew :aow2-client:run
```

## Running the Server

```bash
./gradlew :aow2-server:bootRun
```

## Testing

```bash
./gradlew test
```

## Project Structure

- `aow2-common` - Shared data models, events, utilities
- `aow2-core` - Game engine, combat, economy, AI, pathfinding
- `aow2-client` - FXGL game client with JavaFX UI
- `aow2-server` - Spring Boot multiplayer backend
- `aow2-modding` - Mod loader and Lua scripting engine

## Asset Development

Original game assets (sprites, audio, maps, mission briefings) have been
extracted from 4 source distributions and decoded for use in this recreation.
The full reference is at **[`docs/RE/ASSET_DEVELOPMENT_GUIDE.md`](docs/RE/ASSET_DEVELOPMENT_GUIDE.md)**.

Quick status:
- ✅ 286 files extracted from J2ME Global, J2ME Peru, and iOS v2.2 builds
- ✅ 90 iOS sprites decoded from packed i0 containers (45 EN + 45 RU)
- ✅ 72 SFX + 1 music track converted from WAV/MP3 to OGG/Vorbis
- ✅ 38 Peru campaign maps ported from binary to JSON format
- ✅ Both campaign JSONs enriched with original Gear Games briefing text
- ✅ `AssetTestScene` validates the asset pipeline end-to-end
- ⏳ AudioManager bridging, terrain lookup decoding, and CampaignScene wiring pending

To test the asset pipeline: run the client, click **"Asset Test"** on the
main menu to view decoded sprites and play converted OGG SFX.

