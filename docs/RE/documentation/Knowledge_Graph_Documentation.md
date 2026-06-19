# Art of War 2 Online - Knowledge Graph Documentation

## Overview

This document describes the structure, semantics, and usage of the knowledge graph for the Art of War 2 Online reverse-engineering project. The knowledge graph is stored in `knowledge_graph.json` and encodes all discovered entities, their properties, and their relationships.

## Graph Structure

### Node Types

The knowledge graph uses 13 distinct node types to categorize all discovered entities:

| Node Type | Description | Source |
|-----------|-------------|--------|
| `Class` | Decompiled Java class from the APK | `class_mapping.json` |
| `System` | Functional subsystem of the game engine | Derived from class analysis |
| `Faction` | Playable faction (Confederation / Rebels) | `complete_unit_stats.json`, `game_data.json` |
| `Unit` | Trainable military unit | `complete_unit_stats.json`, `game_data.json` |
| `Building` | Constructible building | `complete_building_stats.json`, `game_data.json` |
| `Technology` | Researchable upgrade | `complete_unit_stats.json`, `game_data.json` |
| `NetworkMessage` | Protocol message type | `packet_formats.json` |
| `ScreenState` | UI screen state identifier | `packet_formats.json` |
| `Asset` | Game resource file | `class_mapping.json` |
| `Map` | Map configuration and data | `game_data.json`, `packet_formats.json` |
| `Campaign` | Game mode / progression | `packet_formats.json`, derived |
| `Encryption` | Encryption/obfuscation mechanism | `class_mapping.json`, `packet_formats.json` |
| `GameConstant` | Key game configuration constant | `game_data.json`, `combat_formulas.md` |

### Edge Types

The graph uses 12 edge types to represent relationships:

| Edge Type | Semantics | Example |
|-----------|-----------|---------|
| `Uses` | A depends on B as a tool/dependency | Main Game Class Uses Game Logic Handler |
| `Loads` | A loads B from storage or network | Config Manager Loads s0/ Asset Directory |
| `Creates` | A instantiates B | AppCtrl Creates Sender Thread |
| `References` | A points to or accesses B | Game State Manager References Battle state |
| `Updates` | A modifies the state of B | Combat System Updates unit HP |
| `Sends` | A transmits B over the network | Sender Thread Sends game state updates |
| `Receives` | A receives B from the network | Network Manager Receives SESSION_INIT |
| `Produces` | A manufactures/unlocks B | Infantry Centre Produces Infantry |
| `Upgrades` | A enhances B's stats or unlocks B | Energy suit Upgrades Infantry armour |
| `Attacks` | A can attack B in combat | Sniper Attacks Infantry |
| `Depends_On` | A requires B to function or unlock | Bio suit Depends_On Energy suit |
| `Owns` | A owns/contains B (faction membership) | Confederation Owns AV-40 Fortress |

### Node ID Convention

All node IDs follow a systematic naming convention:

| Type | Prefix | Example |
|------|--------|---------|
| Class (main package) | `cls_` | `cls_appctrl` |
| Class (s0 package) | `s0_` | `s0_k`, `s0_w`, `s0_e` |
| System | `sys_` | `sys_combat`, `sys_network` |
| Faction | `fac_` | `fac_confederation` |
| Unit | `unit_` | `unit_conf_infantry`, `unit_reb_sniper` |
| Building | `bld_` | `bld_conf_command_centre` |
| Technology | `tech_` | `tech_conf_energy_suit` |
| Network Message | `net_` | `net_session_init`, `net_game_state` |
| Screen State | `ss_` | `ss_19`, `ss_44` |
| Asset | `asset_` | `asset_s0`, `asset_font0` |
| Map | `map_` | `map_config` |
| Campaign | `campaign_` | `campaign_online` |
| Encryption | `enc_` | `enc_xor_asset` |
| Game Constant | `gc_` | `gc_max_credits` |

## Major Subgraphs

### 1. Application Bootstrap Subgraph

```
Application → Creates → AppCtrl → Creates → bh (MIDlet)
                             → Creates → an (SurfaceView)
                             → Loads → s0.k (Main Game Class via Class.forName)
```

The application starts through the Android Activity, which delegates to AppCtrl. AppCtrl dynamically loads the screen-appropriate game class from s0, s1, or s2 packages.

### 2. Game Loop Subgraph

```
s0.k (Main Game Class)
  ├── Uses → s0.w (Game Logic/Map Handler)
  │     ├── Implements → Combat System
  │     ├── Implements → Pathfinding System
  │     ├── Implements → Unit AI System
  │     ├── Implements → Fog of War System
  │     ├── Implements → Resource System
  │     ├── Implements → Production System
  │     └── Implements → Research System
  ├── Uses → s0.a (Game State Manager) → References → [Screen States]
  ├── Uses → s0.c (Config Manager) → Loads → [Assets]
  ├── Uses → s0.e (Network Manager) → Uses → [Network Messages]
  ├── Uses → s0.f (Font Manager) → Loads → [Font Assets]
  └── Uses → s0.i (Audio Manager)
```

### 3. Faction Hierarchy Subgraph

```
Confederation
  ├── Owns → [7 Units + 3 Mines]
  ├── Owns → [8 Buildings]
  └── Owns → [8 Technologies]
      └── Technology Dependencies (Depends_On edges)

Rebels
  ├── Owns → [7 Units]
  ├── Owns → [8 Buildings]
  └── Owns → [8 Technologies]
      └── Technology Dependencies (Depends_On edges)
```

### 4. Production Chain Subgraph

```
Infantry Centre ──Produces──→ Infantry
               ──Produces──→ Grenadier
               ──Produces──→ Flame Assault (requires Forced light missiles tech)

Machine Factory ──Produces──→ AV-40 Fortress
               ──Produces──→ T-21 Hammer
               ──Produces──→ T-22 Zeus
               ──Produces──→ MLRS Torrent
               ──Depends_On──→ Technology Centre
```

### 5. Network Protocol Subgraph

```
s0.e (Network Manager)
  ├── Creates → s0.m (Sender Thread) ──Sends──→ [Game State Updates]
  ├── Creates → s0.o (Receiver Thread) ──Receives──→ [Server Messages]
  └── Uses → s0.z (Request Queue, triple-buffered)
       └── Uses → XOR Stream Cipher

Key Message Flow:
  SESSION_INIT (4) → Updates Game State Manager
  MATCH_START (12) → Updates Game Logic Handler, transitions to state 31
  GAME_STATE (30) → Syncs tile + unit data (most frequent)
  UPGRADE_DATA (42) → Syncs research state
  GAME_RESULT (33) → Transitions to state 41
```

### 6. Encryption Subgraph

```
Encryption System
  ├── XOR Asset Cipher (bu.java, rotating 8-byte key)
  ├── Custom Base64 (non-standard alphabet)
  ├── XOR Stream Cipher (15-byte cycle, session keys)
  ├── Data-At-Rest XOR (appended seed method)
  ├── HTTP XOR (15-byte key for fallback)
  ├── String Obfuscation (by.a UTF-8 byte array decoding)
  ├── APK Verification (CRC32 with polynomial 0xEDB88320)
  └── Billing Signature (RSA SHA1withRSA)
```

## Key Data Structures

### Unit Data Array (ca[])

The game stores all unit and building data in a single `ca[7272]` byte array with offset-based access. Each player has 50 unit slots (player 0: indices 1-50, player 1: indices 51-100). Key offsets:

| Offset | Field | Description |
|--------|-------|-------------|
| +0 | posX | Grid X position |
| +101 | posY | Grid Y position |
| +202 | offX | Pixel offset X |
| +303 | offY | Pixel offset Y |
| +404 | facing | Unit facing direction |
| +1212 | animFrame | Current animation frame |
| +1414 | attackState | Attack animation state / death frame |
| +1616 | hp | Current hit points |
| +2323 | unitType | Unit type identifier |
| +2424 | attackCycle | Attack cycle counter |
| +2828 | flags | Unit status flags |
| +3030 | owner | Owning player |
| +5252 | buildingGridX | Building grid position X |
| +5454 | constructionHP | Construction progress HP |
| +5656 | buildingType | Building type identifier |
| +6565 | productionProgress | Production completion |
| +6868 | currentProduction | Currently produced unit type |
| +7171 | poweredFlag | Whether building is powered |

### Unit Type Masks

| Mask | Value | Bits | Category |
|------|-------|------|----------|
| Infantry | 16447 | 0,1,2,3,5,14 | Infantry units |
| Machinery | 16256 | 7-13 | Vehicle/machinery units |
| Producer | 114688 | 14,15,16 | Production buildings |
| Large Unit | 65536 | 16 | Large units (2x2 grid) |

### Combat Formulas

**Damage Calculation:**
```
damage = max(min(baseDamage * (10 - armour) / 10, baseDamage - armour), 1)
```

**Splash Damage (Artillery type 10):**
```
distanceFactor = bS[bT[79] + attackType] * bS[distanceTable[dy][dx]] / 12
damage = max(min(((10 - armour) * distanceFactor) / 10, distanceFactor - armour), 1)
```

**Kill Reward:**
```
killReward = (unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2)
```

**Production Time:**
```
effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20)
```

**Credit Income (per 127 ticks when building active):**
```
income = (baseIncome * 7) / 10
```

## Screen Resolution Variants

The game ships three resolution variants, each in its own package. The `s0`/`s1`/`s2` naming does NOT indicate factions — it indicates screen size:

| Variant | Screen Width | Orientation | Package | Main Class |
|---------|-------------|-------------|---------|------------|
| s0 | ≤320px | Portrait | `s0` | `s0.k` |
| s1 | ~320px | Landscape | `s1` | `s1.c` |
| s2 | >320px | Portrait | `s2` | `s2.q` |

All three variants contain functionally identical game logic classes with different obfuscated names. The `s0_to_s1_to_s2_mapping` section in `class_mapping.json` provides the cross-reference.

## Network Protocol

### Wire Format

**Outbound Packet:**
```
[0]    byte    message_type
[1-2]  short   total_payload_length (BE)
[3-4]  short   string_data_length (BE)
[5-N]  byte[]  string_data (chars cast to bytes)
```

**Inbound Packet:**
```
[0]    byte    message_type
[1-2]  short   remaining_payload_length (BE)
[3-4]  short   data_length (BE)
[5-N]  byte[]  message_data
```

### Critical Messages

| ID | Name | Purpose |
|----|------|---------|
| 4 | SESSION_INIT | Full session initialization with game config, alliances, features |
| 12 | MATCH_START | Match start with map data and game mode |
| 30 | GAME_STATE | Most frequent: turn sync with tile and unit data |
| 33 | GAME_RESULT | End-of-game scores, transitions to result screen |
| 42 | UPGRADE_DATA | Technology/upgrade tree sync |

### Screen States

The game has 40+ screen states managed by the Game State Manager (`s0.a`). Key states:

- **8**: Login
- **14**: Online Game Lobby
- **17**: Game Screen
- **19**: Battle
- **30**: Game Session
- **31**: Matchmaking Wait
- **41**: Game Result
- **43**: Building
- **44**: Town View
- **45**: World Map
- **47**: Shop
- **54**: Rankings
- **63**: Error/Connection Lost
- **78**: Tutorial
- **88**: Alliance

## Querying the Graph

### Finding All Dependencies of a System

To find everything the Combat System touches:
1. Find node `sys_combat`
2. Follow all edges where source = `sys_combat` (what it updates/uses)
3. Follow all edges where target = `sys_combat` (what triggers it)

### Tracing a Unit's Full Production Chain

Example: Flame Assault
1. `fac_confederation` → Owns → `unit_conf_flame_assault`
2. `bld_conf_infantry_centre` → Produces → `unit_conf_flame_assault`
3. `tech_conf_forced_missiles` → Upgrades → `unit_conf_flame_assault`
4. `bld_conf_tech_centre` → Produces → `tech_conf_forced_missiles`
5. `bld_conf_generator` → Powers → `bld_conf_infantry_centre`

### Tracing Network Message Flow

1. Server sends `SESSION_INIT` (message 4)
2. `s0.e` (Network Manager) → Receives → `net_session_init`
3. `net_session_init` → Updates → `s0_a` (Game State Manager)
4. Game State Manager transitions to appropriate screen state

## Known Gaps and Future Work

1. **Outbound message types**: Currently only server-to-client messages are documented. Client-to-server command format needs reverse engineering.
2. **Map data format**: The binary map data format within MAP_DATA messages needs full decoding.
3. **Game record sub-structures**: Many message payloads contain nested structures that need further analysis.
4. **Screen state transitions**: The full state machine diagram with all valid transitions is incomplete.
5. **Rebels unit full stats**: Rebels units have partial stat data compared to Confederation units.
6. **Research tree graph**: The full dependency chain for all 48 research IDs needs mapping.
7. **Building production queues**: The exact queue mechanics and slot system needs deeper analysis.
8. **AI behavior parameters**: The specific AI decision-making parameters are not yet extracted.
