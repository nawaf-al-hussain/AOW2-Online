# Art of War 2 Online - Complete Asset Catalog

**Game**: Art of War 2: Online  
**Developer**: Gear Games (2010)  
**Publisher/Port**: HeroCraft (2011)  
**Platform**: Android (ported from J2ME)  
**Package**: com.herocraft.game.artofwar2ol  
**Total Assets**: 256 files, 1,948.3 KB (Online APK) + 286 files, 16.5 MB (External versions — see §11)

---

> **NEW (2026-06-28):** Three external (non-APK) distributions of Art of War 2 have been extracted and catalogued. They are the source for asset classes the Online APK does **not** ship (pre-recorded SFX, full-quality music, Episode 2 maps, plain-text strings, master sprite atlas, Russian localization). See [§11. External Versions](#11-external-versions-non-apk-distributions) below for the full inventory, or read the dedicated `docs/RE/external_versions/EXTERNAL_VERSIONS.md` for per-file documentation.

---

## Table of Contents

1. [Asset Overview](#1-asset-overview)
2. [Directory Structure](#2-directory-structure)
3. [Icon Sprites (b/)](#3-icon-sprites-b)
4. [Faction Assets (s0/s1/s2)](#4-faction-assets-s0s1s2)
5. [Music (s0m, s1m)](#5-music-s0m-s1m)
6. [Configuration & Text Data (sn8p)](#6-configuration--text-data-sn8p)
7. [Binary Data Files](#7-binary-data-files)
8. [Loading Screens (l2)](#8-loading-screens-l2)
9. [Faction Comparison](#9-faction-comparison)
10. [Asset-to-Game-System Mapping](#10-asset-to-game-system-mapping)
11. [External Versions (Non-APK Distributions)](#11-external-versions-non-apk-distributions)

---

## 1. Asset Overview

| Category | Files | Total Size | Description |
|----------|-------|------------|-------------|
| Icon Sprites (b/) | 200 | 116.4 KB | Unit/building/terrain mini-icons |
| Faction s0 (low-res) | 15 | 411.7 KB | 240x320 screen assets |
| Faction s1 (med-res) | 15 | 562.6 KB | 480x320 screen assets |
| Faction s2 (high-res) | 15 | 755.7 KB | 480x854 screen assets |
| Music (MIDI) | 2 | 18.3 KB | Background music tracks |
| Config/Text (sn8p) | 1 | 21.5 KB | Game strings & configuration |
| Binary Data | 6 | 56.1 KB | Map data, game state, etc. |
| Loading Screen | 1 | 44.5 KB | Default loading screen |
| **TOTAL** | **256** | **1,948.3 KB** | |

---

## 2. Directory Structure

```
assets/
├── b/                    # 200 icon sprite PNGs (32x27, 8-bit colormap)
│   ├── 001 .. 200       # Numbered unit/building/terrain icons
├── s0/                   # Faction 0 assets (240x320 low-res)
│   ├── f0_0             # Font definition: A-Z, a-z (52 chars)
│   ├── f0_0p            # Font image strip: A-Z, a-z (373x12 PNG)
│   ├── f0_1             # Font definition: digits, punctuation, UTF-8 (51 chars)
│   ├── f0_1p            # Font image strip: digits, punctuation (332x12 PNG)
│   ├── f1_0             # Font definition: secondary subset (35 chars)
│   ├── f1_0p            # Font image strip: secondary (203x12 PNG)
│   ├── f2_0             # Font definition: digits only (10 chars)
│   ├── f2_0p            # Font image strip: digits (39x5 PNG)
│   ├── f3_0             # Font definition: digits alt (10 chars)
│   ├── f3_0p            # Font image strip: digits alt (39x5 PNG)
│   ├── d0               # Game data tables (26 byte arrays + 18 short arrays)
│   ├── i0               # Sprite pack (57 PNG images, 3-byte-length prefixed)
│   ├── hc               # Headquarters image (170x142 PNG)
│   ├── gg               # Globe/minimap image (105x70 PNG)
│   └── l2               # Loading screen (240x320 PNG)
├── s1/                   # Faction 1 assets (480x320 med-res) - same structure
├── s2/                   # Faction 2 assets (480x854 high-res) - same structure
├── s0m                   # MIDI music track 1 (10,550 bytes)
├── s1m                   # MIDI music track 2 (7,710 bytes)
├── sn8p                  # Game text strings & config (21,540 bytes)
├── a                     # Encoded data table (7,001 bytes)
├── d                     # Display flags (8 bytes)
├── f                     # Feature flags (9 bytes, ASCII "000000000")
├── ml                    # Map layout data (1,489 bytes)
├── n                     # Name/data table (2,066 bytes)
├── p                     # Platform version (3 bytes: 0x00 0x00 0x01)
└── l2                    # Default loading screen (240x320 PNG)
```

---

## 3. Icon Sprites (b/)

**Format**: PNG, 32x27 pixels, 8-bit colormap (32 or 16 colors)  
**Total**: 200 icons, numbered 001-200 (contiguous, no gaps)

### Color Faction System

Icons use a 5-color faction coding system where the same unit/building appears in 5 color variants:

| Color Code | Dominant Color | Faction/Category |
|------------|---------------|------------------|
| Brown | `#341100` / `#ab6c42` | Confederation |
| Blue | `#002334` / `#4281ab` | Rebels |
| Green | `#112b08` / `#5f9a53` | Terrain/Nature |
| Gold | `#372c00` / `#b9a736` | Resources/Special |
| Gray/Dark | `#181818` / `#958a6c` | Neutral/Structure |

### Icon Categories by Number Range

| Range | Count | Category | Description |
|-------|-------|----------|-------------|
| 001-015 | 15 | Base Units | 3 groups x 5 faction colors (basic infantry/vehicles) |
| 016-030 | 15 | Upgraded Units | 3 groups x 5 faction colors (advanced units) |
| 031-045 | 15 | Veteran Units | 3 groups x 5 faction colors (elite units) |
| 046-060 | 15 | Heavy Units | 3 groups x 5 faction colors (heavy armor) |
| 061-075 | 15 | Structures | 3 groups x 5 faction colors (buildings) |
| 076-095 | 20 | Terrain (Dark) | Dark/night terrain tiles (very low brightness) |
| 096-115 | 20 | Terrain (Mixed) | Terrain with faction highlights |
| 116-135 | 20 | Simple Icons | 16-color simplified versions of units |
| 136-155 | 20 | Advanced Units | Additional faction unit variants |
| 156-175 | 20 | Special Units | Special/unique units per faction |
| 176-195 | 20 | Shadow/FOW | Black/dark versions (fog of war, destroyed, shadows) |
| 196-200 | 5 | Mini Base | Compact base unit icons (smallest file sizes) |

### Detailed Icon Mapping (based on game text strings)

From the `sn8p` text data, the game defines these units and buildings:

**Units (Confederation)**:
- Infantry, Grenadier, Sniper, Coyote, Armadillo, Rhino, Porcupine

**Units (Rebels)**:
- Assault, Heavy Assault, Flame Assault, AV-40 Fortress, T-21 Hammer, T-22 Zeus, MLRS Torrent

**Buildings (Both)**:
- Command Centre, Generator, Infantry Centre (Barracks), Machine Factory, Technology Centre (Laboratory), Bunker, Locator (Tower), Rocket Launcher, Wall

**Mines**:
- Mine Scorpio, Mine Frog, Mine Lizard

**Ranks**:
- Major-General, Lieutenant-General, General of Army, Marshal

---

## 4. Faction Assets (s0/s1/s2)

### 4.1 Screen Resolution Variants

| Faction | Resolution | Screen Type | i0 Sprites | Total Size |
|---------|-----------|-------------|-----------|------------|
| s0 | 240x320 | Small/QVGA | 57 PNGs | 260 KB |
| s1 | 480x320 | Medium/HVGA | 46 PNGs | 362 KB |
| s2 | 480x854 | Large/WVGA | 47 PNGs | 526 KB |

### 4.2 Font System

Each faction contains 5 font definition files (f0_0, f0_1, f1_0, f2_0, f3_0) paired with 5 font image strips (f*_p).

#### Font Format

Binary format for font definition files:
```
Byte 0:     Character count
Bytes 1-2:  Header (0x01 0x00)
Then per character entry:
  - 1 byte: Character byte count (1 for ASCII, 2 for UTF-8)
  - N bytes: Character code(s)
  - 1 byte: Width in pixels
  - 1 byte: Separator (0x00)
```

#### Font Specifications

| Font | Characters | s0 Width | s1 Width | s2 Width | Purpose |
|------|-----------|----------|----------|----------|---------|
| f0_0 | A-Z, a-z (52) | 2-10px | 3-13px | 4-18px | Main game text |
| f0_1 | Digits, punct, UTF-8 (51-52) | 2-12px | 2-20px | 3-23px | Numbers & symbols |
| f1_0 | Subset (35) | 2-11px | 2-15px | 3-17px | Secondary text |
| f2_0 | 0-9 (10) | 3-4px | 6-7px | 6-8px | Small counters |
| f3_0 | 0-9 (10) | 3-4px | 6-7px | 6-8px | Small counters (alt) |

**UTF-8 Characters in f0_1**: `}` `\` `{` `|` `!` `"` `#` `$` `%` `&` `'` `(` `)` `*` `+` `,` `-` `.` `/` `0-9` `:` `;` `=` `>` `?` `@` `©` `<` `À` `Á` `Â` `Ã` `Ä` `Å` `Æ` `Ç` `È` `Ð` `Ñ` `Ò` `[` `]` `_`

Font image strips are single-row PNG images containing all characters side by side, with widths matching the font definition data.

### 4.3 Sprite Pack (i0)

**Format**: Custom packed container with 3-byte big-endian size prefix per image, terminated by 0xFF.

```
[3-byte size][PNG data][3-byte size][PNG data]...[0xFF terminator]
```

Images are loaded in pairs (even/odd indices): even = base image, odd = alpha mask that gets composited onto the base for transparency effects.

#### s0 Sprite Inventory (57 images)

| Index | Dimensions | Mode | Size | Likely Purpose |
|-------|-----------|------|------|---------------|
| 0 | 6x320 | P | 1.4KB | Terrain tile strip (vertical) |
| 1 | 179x125 | P | 14.9KB | Map terrain - grass/plains |
| 2 | 206x131 | P | 13.7KB | Map terrain - desert |
| 3 | 229x114 | P | 13.7KB | Map terrain - snow/water |
| 4 | 200x63 | P | 7.3KB | Map terrain - road/bridge |
| 5 | 274x147 | P | 32.1KB | Large unit sprite sheet (Confederation) |
| 6 | 268x140 | P | 24.2KB | Large unit sprite sheet (Rebels) |
| 7-9 | 77-107x152 | P | 5-9KB | Building sprites (3 sizes) |
| 10 | 153x49 | P | 4KB | UI bar/panel |
| 11-12 | 91-112x109-118 | P | 6KB | Small unit sprites |
| 13-14 | 118-123x137 | P | 10-11KB | Medium unit sprites |
| 15-16 | 154-223x71-78 | P | 8-12KB | Wide unit sprites (vehicles) |
| 17-19 | 158-193x73-74 | P | 8-11KB | Vehicle sprite sheets |
| 20-21 | 135x129 | P/RGBA | 1-6KB | Command centre sprite + mask |
| 22-23 | 73x119 | P/RGBA | 1-3KB | Generator sprite + mask |
| 24-25 | 65x75 | P/RGBA | 1-2KB | Barracks sprite + mask |
| 26-27 | 121x67 | P/RGBA | 0.1-0.8KB | Factory sprite + mask |
| 28 | 91x82 | P | 3.3KB | Laboratory sprite |
| 29 | 94x63 | P | 2.2KB | Bunker sprite |
| 30 | 106x56 | P | 2.6KB | Tower sprite |
| 31-32 | 169x30 | P | 1.7KB | Wall/barrier sprite |
| 33-34 | 68x81 | P/RGBA | 1-2KB | Rocket launcher sprite |
| 35-36 | 78-126x30-38 | P | 0.4-1.6KB | Mine sprites |
| 37-38 | 231x42 | P | 0.7-1.2KB | Explosion/effect strip |
| 39-40 | 72-80x16-50 | RGBA | 0.9-1.4KB | Projectile/tracer sprites |
| 41-42 | 272x31 | P | 2-3KB | Health bar / status strip |
| 43 | 45x94 | P | 3.7KB | Vertical structure sprite |
| 44 | 162x42 | P | 5.2KB | Selection indicator |
| 45-46 | 122-123x29-36 | P | 0.5-0.8KB | Small UI elements |
| 47 | 99x8 | P | 0.6KB | Thin bar/line |
| 48 | 61x43 | P | 0.9KB | Mini-map icon |
| 49-50 | 88x53 | P | 0.1-0.7KB | Minimap overlay + mask |
| 51 | 50x35 | P | 1KB | Small icon |
| 52 | 63x16 | P | 0.6KB | Button/label |
| 53 | 146x15 | P | 1.1KB | Progress bar |
| 54 | 120x2 | RGBA | 0.2KB | Horizontal line (1px) |
| 55 | 2x160 | RGBA | 0.2KB | Vertical line (1px) |
| 56 | 33x7 | P | 0.3KB | Small indicator |

### 4.4 Game Data (d0)

**Format**: Binary data containing 26 byte arrays + 18 short (16-bit) arrays, each preceded by a 2-byte big-endian length.

#### Byte Arrays (26 total)

| Index | Size (s0) | Unique Values | Purpose |
|-------|-----------|---------------|---------|
| 0 | 2357 | 48 | Unit type classifications |
| 1 | 2357 | 226 | Unit properties (HP, cost, etc.) |
| 2 | 2357 | 145 | Unit combat stats |
| 3 | 2357 | 94 | Unit armor values |
| 4 | 2357 | 62 | Unit speed values |
| 5 | 2357 | 7 | Unit faction assignment (0-6) |
| 6 | 7450 | 2 | Map terrain passability (0/1) |
| 7 | 1556 | 88 | Building properties |
| 8 | 1556 | 67 | Building costs |
| 9 | 1556 | 1 | Building availability (all 1) |
| 10 | 992 | 83 | Terrain graphics mapping |
| 11 | 992 | 62 | Terrain overlay mapping |
| 12 | 992 | 3 | Terrain type flags (1,2) |
| 13 | 992 | 4 | Terrain movement cost (0,1,4) |
| 14 | 263 | 1 | Technology level (all 4) |
| 15 | 797 | 60 | Technology prerequisites |
| 16 | 797 | 56 | Technology costs |
| 17 | 797 | 1 | Category marker (all 3) |
| 18 | 153 | 6 | Upgrade effects |
| 19 | 153 | 5 | Upgrade targets |
| 20 | 1084 | 136 | Sprite frame indices |
| 21 | 1084 | 1 | Padding (all 0) |
| 22 | 1084 | 1 | Padding (all 0) |
| 23 | 1084 | 3 | Animation flags (0,1,2) |
| 24 | 1084 | 3 | Direction flags (0,1,2) |
| 25 | 1084 | 14 | Sound/effect triggers |

#### Short Arrays (18 total)

| Index | Size (s0) | Unique Values | Purpose |
|-------|-----------|---------------|---------|
| 0 | 2342 | 2342 | Sequential index map |
| 1 | 2342 | 2342 | Reverse index map |
| 2 | 7450 | 2021 | Map tile sprite offsets |
| 3 | 7450 | 184 | Building placement data |
| 4 | 7450 | 182 | Unit placement data |
| 5 | 290 | 290 | Unit sprite X offsets |
| 6 | 290 | 290 | Unit sprite Y offsets |
| 7 | 1556 | 799 | Building sprite indices |
| 8 | 405 | 405 | Animation frame X offsets |
| 9 | 405 | 405 | Animation frame Y offsets |
| 10 | 992 | 495 | Terrain rendering data |
| 11 | 263 | 263 | Technology tree indices |
| 12 | 263 | 263 | Technology tree links |
| 13 | 797 | 403 | Technology upgrade map |
| 14 | 153 | 153 | Upgrade sprite X coords |
| 15 | 153 | 153 | Upgrade sprite Y coords |
| 16 | 1084 | 397 | Sprite sheet coordinate map |
| 17 | 1084 | 177 | Movement/attack range data |

### 4.5 UI Images

#### Headquarters Image (hc)

| Faction | Dimensions | Description |
|---------|-----------|-------------|
| s0 | 170x142 | Small HQ command view |
| s1 | 216x181 | Medium HQ command view |
| s2 | 216x181 | Large HQ command view |

Color palette: Black background, red accents (#FF0000), gray structures (#4E5050), white highlights. Appears to be a command center/base overview with a red crosshair or targeting reticle overlay.

#### Globe/Minimap (gg)

| Faction | Dimensions | Description |
|---------|-----------|-------------|
| s0 | 105x70 | Small world map |
| s1 | 148x98 | Medium world map |
| s2 | 148x98 | Large world map |

Color palette: White background, dark blue continents (#17274F/#0E1E48), blue-gray water (#94A8BC). Shows a simplified world map used as a minimap or server selection globe.

#### Loading Screen (l2)

| Faction | Dimensions | Description |
|---------|-----------|-------------|
| s0 | 240x320 | Portrait low-res |
| s1 | 480x320 | Landscape medium |
| s2 | 480x854 | Portrait high-res |

Color palette: Dominated by orange/red/fire colors (#F5D900 yellow, #F36706 orange, #D62800 red, #4A0A00 dark red). Features a fiery war-themed design with the game logo area.

---

## 5. Music (s0m, s1m)

### s0m - Track 1
- **Format**: Standard MIDI (format 1)
- **Size**: 10,550 bytes
- **Tracks**: 9
- **Division**: 96 ticks per quarter note
- **Tempo**: 120.0 BPM
- **Track Distribution**:
  - Track 0: Tempo/conductor (11 bytes)
  - Track 1: Main melody (5,142 bytes) - largest, primary musical content
  - Tracks 2-3: Harmony/rhythm (355-585 bytes)
  - Tracks 4-5: Bass/accompaniment (2,209-2,114 bytes)
  - Tracks 6-8: Percussion/effects (16 bytes each - minimal)

### s1m - Track 2
- **Format**: Standard MIDI (format 1)
- **Size**: 7,710 bytes
- **Tracks**: 9
- **Division**: 96 ticks per quarter note
- **Tempo**: 180.0 BPM (faster, more intense)
- **Track Distribution**:
  - Track 0: Tempo/conductor (11 bytes)
  - Track 1: Main melody (2,093 bytes)
  - Tracks 2-3: Harmony/rhythm (395-665 bytes)
  - Track 4: Accompaniment (2,220 bytes)
  - Track 5: Bass (296 bytes)
  - Track 6: Additional melody (1,722 bytes)
  - Track 7: Effects (201 bytes)
  - Track 8: Minimal (21 bytes)

**Music Credit**: Bogatenko A.N. (per game credits in sn8p)

---

## 6. Configuration & Text Data (sn8p)

**Size**: 21,540 bytes  
**Format**: Length-prefixed string table (1-byte length + ASCII string)  

### Device Configuration

| Key | Value | Purpose |
|-----|-------|---------|
| LSK1 | -6 | Left soft key code |
| LSK2 | 0 | Left soft key code 2 |
| RSK1 | -7 | Right soft key code |
| RSK2 | 0 | Right soft key code 2 |
| SMTYPE | -1 | SMS type |
| VP | TRUE | Vibration support |
| CMDLR | FALSE | Commander mode |
| WAP_JAVA | 1 | WAP Java support |
| SMS_JAVA | 4 | SMS Java mode |
| GAME_ID | 219 | HeroCraft game ID |
| PROV_ID | 629 | Provider ID |
| LANG_ID | en | Language (English) |
| PROD_ID | 82335 | Product ID |
| PORT_ID | 33116 | Server port |
| DCNTYPE | 1 | Connection type |
| INS | 320 | Installation size |

### URLs

| Key | URL |
|-----|-----|
| UMG | market://search?q=herocraft |
| UMGD | market://search?q=pub:"HeroCraft Ltd" |
| HC-prf | http://wap.herocraft.com/heroes/[L]/?app=1&password=[PP]&login=[L] |
| HC-d | http://m.herocraft.com/heroforum/games/219/?app=1&session=[S] |

### Game Text Strings (T0_*) - 310 UI Strings

Key categories:
- **T0_0 - T0_5**: Main menu (Game, Help, About, More games, Exit)
- **T0_6 - T0_9**: Battle menu (New battle, Open battles, Battle history, Chat)
- **T0_10 - T0_28**: Online features (Players, Clans, Profile, Chat, Settings, Sound, Factions)
- **T0_29 - T0_36**: Faction names (Confederation, Rebels, Light, Medium, Heavy, Small, Large)
- **T0_37 - T0_69**: Battle controls (Continue, Save, Surrender, Sort, Attack, Defence, Speed, etc.)
- **T0_70 - T0_99**: Player management (Clan operations, Statistics, Moderation, Ban types)
- **T0_100 - T0_135**: Time durations, Moderation categories, Connection types, Ranks
- **T0_136 - T0_200**: Tournament, Rating, Forum features
- **T0_201 - T0_212**: Battle setup (Map, Side, Equipment, Base, Control gap, Time limit)
- **T0_213 - T0_249**: Unit & Building names (see below)
- **T0_250 - T0_309**: Stats, Bonuses, Avatar, Shop

### Unit & Building Names (T0_213-T0_249)

**Confederation Units**:
- T0_219: Infantry
- T0_220: Grenadier
- T0_221: Sniper
- T0_222: Coyote
- T0_223: Armadillo
- T0_224: Rhino
- T0_225: Porcupine

**Rebels Units**:
- T0_212: Assault
- T0_213: Heavy Assault
- T0_214: Flame Assault
- T0_215: AV-40 Fortress
- T0_216: T-21 Hammer
- T0_217: T-22 Zeus
- T0_218: MLRS Torrent

**Confederation Buildings**:
- T0_226: Command centre
- T0_227: Generator
- T0_228: Infantry centre
- T0_229: Machine factory
- T0_230: Technology centre
- T0_231: Bunker
- T0_232: Locator
- T0_233: Rocket launcher

**Rebels Buildings**:
- T0_234: Headquarters
- T0_235: Powerplant
- T0_236: Barracks
- T0_237: Factory
- T0_238: Laboratory
- T0_239: Bunker
- T0_240: Tower
- T0_241-242: Wall (2 variants)

**Mines**:
- T0_243: Mine Scorpio
- T0_244: Mine Frog
- T0_245: Mine Lizard

**Ranks**:
- T0_246: Major-General
- T0_247: Lieutenant-General
- T0_248: General of Army
- T0_249: Marshal

### Help/Description Text (T1_*) - 212 Strings

Includes:
- T1_0: Game credits
- T1_1-T1_9: Tutorial/control help
- T1_10-T1_13: Bonus system help
- T1_14-T1_20: Battle mechanics
- T1_21-T1_69: Dialog prompts and confirmations
- T1_70-T1_99: Technology descriptions (Confederation)
- T1_100-T1_129: Technology descriptions (continued)
- T1_130-T1_149: Building descriptions (Confederation)
- T1_150-T1_163: Unit descriptions (Confederation)
- T1_164-T1_199: Technology descriptions (Rebels)
- T1_200-T1_212: Additional game messages

### Technology Names (T1_82-T1_128)

**Confederation Technologies**:
- Energy suit, Bio suit, Enhanced firing rate, Armour-piercing bullet
- Forced light missiles, Lava flame fuel, Volcano flame gun, Heavy shells
- Reinforced engine, Energy armour, Damage diagnostics, Fast recharging
- T-22 rocket launcher, Torrent-5 MRLS, Torrent-10 MRLS
- Reinforced generator, Titanium cover, Walls Active armour
- Warning System, Infantry express-training, Upgraded assembly line
- Finance department, Incentive system, Communications system
- Titanium jacket, First-aid kit, Enhanced fire rate, Doping
- Snipers, Rifle 'Hornet-10', Heavy machine gun
- MMC 'Porcupine', Advanced 'Porcupine'

---

## 7. Binary Data Files

### File: `a` (7,001 bytes)
- **Type**: Custom encoded data
- **Purpose**: Likely game state data or additional configuration
- **Format**: Binary, starts with 0x7A ('z'), contains encoded byte patterns
- **Not standard compression** (zlib, gzip, deflate all fail)
- **Byte distribution**: 255 unique byte values, most common: 0x5D (390), 0x5C (381), 0x1C (302)
- **Hypothesis**: Custom XOR or substitution cipher encoded data table

### File: `d` (8 bytes)
- **Type**: Flag array
- **Data**: `00 01 00 01 00 01 00 00`
- **Purpose**: Display/rendering feature flags (4 boolean pairs)

### File: `f` (9 bytes)
- **Type**: ASCII text
- **Data**: `000000000`
- **Purpose**: Feature flags initialization (9 boolean flags, all off)

### File: `ml` (1,489 bytes)
- **Type**: Binary map/mission layout data
- **Structure**: Records delimited by 0xE0/0xE1 markers
- **Pattern**: `counter, 0xFD, data, 0x46, data, 0x44, data, 0xE1`
- **193 records** identified
- **Common bytes**: 0xFD (167), 0xE1 (163), 0x46 (159), 0x44 (157)
- **Hypothesis**: Map layout definitions with X/Y coordinate pairs per cell, where 0x46='F' and 0x44='D' serve as section markers

### File: `n` (2,066 bytes)
- **Type**: Custom encoded data table
- **Starts with**: 0x7A ('z') - same signature as file `a`
- **Common bytes**: 0x72='r' (58), 0x33='3' (52), 0x3E='>' (51)
- **Contains more printable ASCII** than file `a`
- **Hypothesis**: Name/data table with same encoding as file `a`

### File: `p` (3 bytes)
- **Type**: Version/platform marker
- **Data**: `00 00 01`
- **Purpose**: Platform version identifier (major=0, minor=0, patch=1) or player count

---

## 8. Loading Screens (l2)

| File | Dimensions | Size | Colors | Description |
|------|-----------|------|--------|-------------|
| l2 (top-level) | 240x320 | 45.6KB | Fire/gold | Default loading screen |
| s0/l2 | 240x320 | 45.6KB | Fire/gold | Same as top-level (s0 default) |
| s1/l2 | 480x320 | 95.2KB | Fire/red | Landscape loading screen |
| s2/l2 | 480x854 | 127.6KB | Fire/dark | Portrait HD loading screen |

All loading screens feature a warm fire/gold/red color scheme, consistent with the game's military theme. Created with Adobe ImageReady.

---

## 9. Faction Comparison

### Resolution Scaling

Assets scale proportionally across the 3 resolution tiers:

| Property | s0 (Low) | s1 (Medium) | s2 (High) | Scale Factor |
|----------|----------|-------------|-----------|-------------|
| Screen | 240x320 | 480x320 | 480x854 | - |
| Main font width | 8px | 10px | 13px | ~1.6x |
| Small font width | 4px | 7px | 8px | ~2x |
| Font image height | 12px | 17-19px | 22px | ~1.8x |
| HQ image | 170x142 | 216x181 | 216x181 | ~1.3x |
| Globe image | 105x70 | 148x98 | 148x98 | ~1.4x |
| Loading screen | 240x320 | 480x320 | 480x854 | varies |
| d0 data size | 109,318B | 109,821B | 109,318B | ~same |
| i0 sprite count | 57 | 46 | 47 | ~same |
| i0 total size | 260KB | 362KB | 526KB | ~2x |

### Key Differences

1. **s0 vs s1/s2**: s0 uses simpler sprite compositing (separate P-mode images + masks), while s1/s2 use pre-composited RGBA images for some sprites
2. **s1**: Has the largest single sprite (214x249 RGBA = 33KB) not present in s0/s2
3. **s2**: Sprites are approximately 2x the pixel dimensions of s0
4. **d0 data**: Nearly identical across factions (same game logic), only minor size differences for different map configurations
5. **s0/s2 d0**: Identical at 109,318 bytes (same map layout); s1 slightly larger at 109,821 bytes

### Sprite Comparison (First 10 sprites)

| Index | s0 Dims | s1 Dims | s2 Dims | Purpose |
|-------|---------|---------|---------|---------|
| 0 | 6x320 | 120x400 | 12x640 | Terrain tile strip |
| 1 | 179x125 | 179x125 | 358x250 | Map terrain (shared s0/s1, 2x s2) |
| 2 | 206x131 | 206x131 | 412x262 | Map terrain (shared s0/s1, 2x s2) |
| 3 | 229x114 | 229x114 | 458x228 | Map terrain (shared s0/s1, 2x s2) |
| 4 | 200x63 | 200x63 | 400x126 | Road/bridge terrain |
| 5 | 274x147 | 274x147 | 548x294 | Large unit sheet (Confed) |
| 6 | 268x140 | 268x140 | 536x280 | Large unit sheet (Rebels) |

**Note**: s0 and s1 share the same sprite dimensions for the first 19 images - s1 uses higher color depth (RGBA) for transparency instead of separate mask images.

---

## 10. Asset-to-Game-System Mapping

### Game Systems and Their Assets

| Game System | Asset Files | Description |
|-------------|-----------|-------------|
| **Rendering** | s{0-2}/i0, s{0-2}/d0 | Sprites + data tables for rendering all game objects |
| **UI/Fonts** | s{0-2}/f0_0..f3_0, f*_p | 5 font sets for all text rendering |
| **Map System** | s{0-2}/d0 byte[6], short[2-4], ml | Terrain passability, tile mapping, layout |
| **Unit System** | b/001-075, s{0-2}/i0, s{0-2}/d0 byte[0-5] | Unit icons, sprites, stats |
| **Building System** | b/061-075, s{0-2}/i0, s{0-2}/d0 byte[7-9] | Building icons, sprites, properties |
| **Technology Tree** | s{0-2}/d0 byte[14-19], short[11-13] | Tech levels, prerequisites, costs |
| **Combat System** | s{0-2}/d0 byte[1-4], short[17] | HP, armor, damage, range values |
| **Animation** | s{0-2}/d0 byte[20,23-25], short[8-9,14-15,16] | Frame indices, directions, coordinates |
| **Online/Multiplayer** | sn8p (T0_*, T1_*) | All UI text, help, server config |
| **Audio** | s0m, s1m | 2 MIDI music tracks |
| **Minimap** | s{0-2}/gg, s{0-2}/i0 sprite 48-51 | Globe image + minimap overlay sprites |
| **Loading** | s{0-2}/l2, l2 | Loading screen per resolution |
| **Screen Config** | a, d, f, p, n | Display flags, platform info |

### Asset Loading Flow (from source code)

1. **Initialization**: Check for HD content (`/b/200` exists = full pack, `/b/199` = partial)
2. **Font Loading**: Load f0_0..f3_0 definitions + f*_p image strips
3. **Sprite Loading**: Open `/i0` as DataInputStream, read 3-byte size prefix + PNG data per image, terminated by 0xFF; even indices = base image, odd = alpha mask composited on top
4. **Game Data**: Open `/d0` as DataInputStream, read 26 byte arrays + 18 short arrays with 2-byte BE length prefixes
5. **UI Images**: Load `/gg`, `/hc`, `/l2` as PNG images with configurable palette overrides
6. **Text**: Parse `sn8p` length-prefixed string table for all game text

### Faction Selection Logic

From source code (bb.java):
```java
String[] a = {"s0/", "s1/", "s2/"};  // Asset paths
String[] b = {"s0.", "s1.", "s2."};  // Class prefixes  
int[] c = {1, 0, 1};                 // Some flag per faction
```

The game selects the appropriate faction asset directory based on screen resolution, with s0 for small screens, s1 for medium, and s2 for large displays.

---

## Appendix A: File Hashes

All files preserved in `/home/z/my-project/project/assets_raw/` with original structure.

## Appendix B: Processed Assets

Processed and organized assets available in `/home/z/my-project/project/assets_processed/`:

```
assets_processed/
├── icons_unit_building/    # 200 renamed PNG icons (icon_001.png - icon_200.png)
├── faction_s0/
│   ├── sprites/           # 96 extracted PNG sprites + sprite_pack.bin
│   ├── fonts/             # 10 font files (5 data + 5 image)
│   ├── maps/              # game_data.bin (d0)
│   └── ui/                # headquarters.png, globe_minimap.png, loading_screen.png
├── faction_s1/            # Same structure as s0
├── faction_s2/            # Same structure as s0
├── music/                 # s0m.mid, s1m.mid
├── config/                # sn8p, d, f, p
├── misc/                  # a, ml, n
└── loading_screens/       # default_loading.png
```

---

*Catalog generated by asset analysis agent. All binary format descriptions derived from source code analysis and hex-level data examination.*

---

## 11. External Versions (Non-APK Distributions)

In addition to the Online APK analysed in sections 1–10 above, three further distributions of Art of War 2 have been extracted and catalogued. They are stored under `docs/RE/external_versions/` in this repository, and a dedicated per-file reference is maintained at `docs/RE/external_versions/EXTERNAL_VERSIONS.md`.

### 11.1 Why the external versions exist

The Online APK (HeroCraft 2011, 2.3 MB) is a downstream Android port of a 2009–2010 Gear Games J2ME title. Android's resource pipeline stripped several asset classes, so the APK alone is insufficient for a faithful recreation. The three external versions restore what is missing:

| What the APK is missing | Where it survives |
|-------------------------|-------------------|
| Pre-recorded **SFX** (gunfire, screams, UI clicks, explosions) | iOS v2.2 ships 72 WAV files |
| **High-quality background music** (APK only had two 10 KB MIDI files) | iOS v2.2 ships `music.mp3` (1.9 MB) + `music.wav` (2.6 MB) |
| **Episode 2 — Liberation of Peru** campaign (38 maps) | `artofwar2l_1tio5twt.jar` ships all 38 Peru maps |
| **Plain-text game strings** (APK encodes them in `sn8p` binary) | Peru JAR `0/` folder ships ASCII `t0`/`s0`/`d0`; iOS ships per-language `t0`/`s0`/`d0` |
| **1024×1024 master sprite atlas** | iOS `d1` is a 1024×1024 RGBA PNG (1.27 MB) |
| **Russian localization** | iOS ships both `English.lproj/` and `Russian.lproj/` |
| **Episode 1 pre-Android form** | `art_of_war_2_global_260793.jar` is the v1.12.0 HeroCraft J2ME release |

### 11.2 Inventory summary

| Version | Source file | Vendor | Version | Files | Size | Why it exists |
|---------|-------------|--------|---------|-------|------|---------------|
| **J2ME Global Confederation** | `art_of_war_2_global_260793.jar` | HeroCraft | 1.12.0 (2012) | 52 | 988 KB | Pre-Android J2ME source of Episode 1 — canonical reference for what the Android port changed |
| **J2ME Liberation of Peru** | `artofwar2l_1tio5twt.jar` | Gear Games | 1.0.06 (2009) | 88 | 1.5 MB | Original Episode 2 campaign — only source for the 38 Peru maps and for plain-text game strings |
| **iOS v2.2** | `Art_Of_War_2_2.2_ios_2.2.1.ipa` | Gear Games | 2.2 (2009) | 146 | 14 MB | Premium iOS release — only source for SFX, full-quality music, master sprite atlas, Russian localization |

### 11.3 Folder layout under `docs/RE/external_versions/`

```
docs/RE/external_versions/
├── EXTERNAL_VERSIONS.md                          ← per-file reference (286 files documented)
├── jar_global_confederation_v1.12.0/             ← J2ME Episode 1 (HeroCraft 2012)
│   ├── classes/      (18 .class files)
│   ├── data/         (13 binary data tables)
│   ├── fonts/        (8 font definition + image-strip pairs)
│   ├── audio/        (1 MIDI file)
│   ├── missions/     (7 mission scripts)
│   ├── sprites/      (3 PNG files: i0 sprite pack, l2 loading screen)
│   ├── text/         (1 sn8p text/string blob)
│   └── metadata/     (1 MANIFEST.MF)
├── jar_liberation_of_peru_v1.0.06/               ← J2ME Episode 2 (Gear Games 2009)
│   ├── classes/      (11 .class files)
│   ├── data/         (13 binary data tables)
│   ├── maps/         (38 map files: map1-20 + map51-68)
│   ├── missions/     (8 mission scripts: mi0-mi7)
│   ├── sprites/      (10 PNG files: i0 + r00-r05 rank insignia + l1, l2, icon.png)
│   ├── text/         (5 plain-text string files: 0_t0, 0_s0, 0_d0, n, s0)
│   ├── crack/        (2 cracker-added .class files)
│   └── metadata/     (1 MANIFEST.MF)
└── ipa_ios_v2.2/                                 ← iOS premium build (Gear Games 2009)
    ├── audio/        (74 files: 72 SFX .wav + music.mp3 + music.wav)
    ├── archives/     (1 file: add = ZIP archive containing duplicate music.mp3)
    ├── app_icons/    (2 files: Default.png + Icon.png)
    ├── sprites/      (6 files: d1 master atlas + 2 localized i0 packs + d00 + l1, l2, l3)
    ├── maps/         (6 files: m0-m5)
    ├── missions/     (37 files: mi0-mi6 generic + mi7_en through mi21_en + mi7_ru through mi21_ru)
    ├── text/         (6 files: English_t0/s0/d0 + Russian_t0/s0/d0)
    ├── data/         (6 files: a, d, n, u, ml, d00)
    ├── metadata/     (6 files: Info.plist, Art_of_War_2-Info.plist, PkgInfo, 2 .nib, Art Of War 2.CutCut)
    ├── executable/   (1 file: Art Of War 2 — Mach-O armv6 binary)
    └── crack/        (1 file: Spid3r signature from idwaneo.com)
```

### 11.4 Cross-version asset selection guide

For each asset class, use the version that ships the highest-quality instance:

| Asset class | Best source | Path |
|-------------|-------------|------|
| SFX (gunfire, screams, UI) | iOS | `ipa_ios_v2.2/audio/*.wav` |
| Background music | iOS | `ipa_ios_v2.2/audio/music.mp3` |
| Master sprite atlas (1024²) | iOS | `ipa_ios_v2.2/sprites/d1` |
| Episode 1 maps (6) | iOS | `ipa_ios_v2.2/maps/m0`–`m5` |
| Episode 2 maps (38) | Peru JAR | `jar_liberation_of_peru_v1.0.06/maps/map1`–`map20`, `map51`–`map68` |
| Mission briefings (English) | iOS | `ipa_ios_v2.2/missions/mi7_en`–`mi21_en` |
| Mission briefings (Russian) | iOS | `ipa_ios_v2.2/missions/mi7_ru`–`mi21_ru` |
| Menu/dialogue strings (English) | iOS | `ipa_ios_v2.2/text/English_*` |
| Menu/dialogue strings (Russian) | iOS | `ipa_ios_v2.2/text/Russian_*` |
| Plain-text strings (Episode 2, English) | Peru JAR | `jar_liberation_of_peru_v1.0.06/text/0_*` |
| Rank insignia (6 badges) | Peru JAR | `jar_liberation_of_peru_v1.0.06/sprites/r00`–`r05` |
| 200 mini-icons | Online APK | `assets_raw/b/001`–`200` (still the only source) |
| Network protocol source | J2ME Global | `jar_global_confederation_v1.12.0/classes/p.class` (cleanest Java) |

### 11.5 Extraction methodology

All three archives are PKZIP containers and were extracted with `unzip`. The categorization script is preserved at `scripts/organize_external_versions.py` (in the project's `scripts/` tree outside the repo) and can be re-run if any source archive is updated:

```bash
mkdir -p /tmp/aow2-extract/{jar_global,jar_low,ipa_ios}
cd /tmp/aow2-extract/jar_global && unzip -q art_of_war_2_global_260793.jar
cd /tmp/aow2-extract/jar_low    && unzip -q artofwar2l_1tio5twt.jar
cd /tmp/aow2-extract/ipa_ios    && unzip -q Art_Of_War_2_2.2_ios_2.2.1.ipa
python3 scripts/organize_external_versions.py
```

Verification: 286 files organized (52 + 88 + 146), totalling 16.5 MB. Counts match the raw extractions exactly — no files dropped, no files duplicated across version folders.
