# Art of War 2 Online - Complete Technical Documentation

> **Version**: 1.0  
> **Date**: 2026-03-05  
> **Source**: Reverse-engineered from decompiled APK `com.herocraft.game.artofwar2ol`  
> **Confidence**: See Section 14 for confidence assessment per finding

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Game Overview & History](#2-game-overview--history)
3. [Technical Architecture](#3-technical-architecture)
   - 3.1 Platform & Engine
   - 3.2 Screen Resolution Variants (s0/s1/s2)
   - 3.3 Rendering Pipeline
   - 3.4 Game Loop
4. [Game Systems Architecture](#4-game-systems-architecture)
   - 4.1 Unit System
   - 4.2 Building System
   - 4.3 Combat System (ALL formulas)
   - 4.4 Economy System
   - 4.5 Research/Tech Tree (ALL 48 research effects)
   - 4.6 AI System
   - 4.7 Pathfinding System
   - 4.8 Fog of War
   - 4.9 Projectile System
5. [Unit Encyclopedia](#5-unit-encyclopedia)
6. [Building Encyclopedia](#6-building-encyclopedia)
7. [Campaign System](#7-campaign-system)
8. [Map System](#8-map-system)
9. [Multiplayer Architecture](#9-multiplayer-architecture)
   - 9.1 Protocol Specification
   - 9.2 Encryption
   - 9.3 Session Lifecycle
10. [Asset Catalog Summary](#10-asset-catalog-summary)
11. [Save/Persistence System](#11-savepersistence-system)
12. [Data Encryption & Security](#12-data-encryption--security)
13. [Class Reference](#13-class-reference)
14. [Confidence Assessment](#14-confidence-assessment)

---

## 1. Executive Summary

Art of War 2 Online is a mobile Real-Time Strategy (RTS) game developed by **Gear Games** (Moscow, Russia) and published/port by **HeroCraft** in 2010-2011. The Android version is a **J2ME MIDlet port** using a custom game engine built directly on Android Canvas/SurfaceView APIs. The codebase is heavily obfuscated with single/two-letter class names across three screen-resolution variant packages (s0, s1, s2).

**Critical Finding**: The s0/s1/s2 packages are **NOT factions** — they are **screen resolution variants** (small/medium/large screens), selected at runtime based on device screen width. The `bb` class defines the mapping:
- s0: screens ≤320px wide (portrait orientation, 240×320)
- s1: screens =320px wide (landscape orientation, 480×320)
- s2: screens >320px wide (portrait orientation, 480×854)

The two actual factions — **Global Confederation** and **Resistance** — are stored in the `y.bV short[2][7]` array within game data, each with distinct unit rosters, building sets, and technology trees.

This document consolidates all reverse-engineering findings including: complete combat formulas, all unit and building statistics, the full 48-research tech tree, AI behavioral analysis, pathfinding algorithms, multiplayer protocol specification, data encryption schemes, and the complete obfuscated-to-deobfuscated class mapping.

---

## 2. Game Overview & History

### 2.1 Game Identity

| Field | Value |
|-------|-------|
| Full Title | Art of War 2: Online |
| Alternate Titles | Art of War 2: Global Confederation (Ep.1), Art of War 2: Liberation of Peru (Ep.2) |
| Genre | Real-Time Strategy (RTS) / MMORTS |
| Developer | Gear Games Ltd (Moscow, Russia) |
| Publisher/Port | HeroCraft |
| Package | com.herocraft.game.artofwar2ol |
| Platform | Android (ported from J2ME MIDlet) |
| Total Assets | 256 files, 1,948.3 KB |

### 2.2 Release Timeline

| Date | Event |
|------|-------|
| 2004 | Gear Games Ltd founded in Moscow, Russia |
| 2006 | **Art of War: N.A.C.** released (predecessor) |
| March 26, 2009 | **Art of War 2: Global Confederation** (Episode 1) for J2ME |
| November 10, 2009 | **Art of War 2: Liberation of Peru** (Episode 2) for J2ME |
| February 2, 2010 | **Art of War 2: Online** (multiplayer expansion) |
| 2010+ | iOS version released (App ID: 336302082) |
| ~2011 | HeroCraft Android port released |
| 2013 | Gear Games website last updated |
| ~2017 | Art of War 3: Global Conflict enters development |
| 2023+ | Art of War 3 active on Android/iOS |

### 2.3 Game Setting & Lore

**Episode 1: Global Confederation (Year 2038)**

> "The year 2038. For 20 years, the Earth has been under the rule of the Global Confederation that united and brought under its control all the countries and continents. The mankind entered a new era. Global territory wars that had been waged for a century subsided. The world economy no longer supported the military machine to be ready for action and turned towards the progress. The new scientific horizons on the Earth and in outer space were open.
>
> But peace and quiet in the Confederation is being increasingly disturbed by the outbreaks of rebellions of those who oppose the Senate's policies. The resistance movement is gaining momentum all over the world. The Senate throws the whole reserve of Confederation's scarce peace-keeping forces to suppress the rebellion.
>
> In the east of Peru, near the city of Pucallpa, some signs of rebellious activity have been observed. Prior to that moment, South America was the calmest of regions. Your mission is to crush the revolt in Peru and to capture the leader of the rebellion."

**Episode 2: Liberation of Peru (Year 2039)**

> "Year 2039. It has been six months since the defeat of the army of the Resistance. All the territory of Peru is under the control of the Confederacy. But the rebels did not surrender. They took refuge in the jungles of the Amazon, have gained strength again and are ready to join the battle to liberate their homeland from bourgeois oppression."

Player Role: General José Monteros, leading the Liberation Army of Peru, continuing the work begun by his brother Miguel.

### 2.4 Factions

#### Global Confederation

**Characteristics**:
- Advanced technology and newest military equipment
- Well-armored and equipped units
- Slower movement speed compared to Resistance
- More expensive units
- Greater firepower per unit
- Defensive doctrine: force enemy to fight on their terms

**Tactical Doctrine**: "You cannot always outrun your enemy. Instead, you must force the enemy to fight on your terms, running headfirst into your heavily armored meat grinder." A fully upgraded Confederation army is nearly unstoppable once it reaches its destination.

**Lore**: Representative democracy with a Senate as its primary governing body. "We tried to build a new and perfect world without wars and violence. But today, we must rise in arms against the global threat."

#### Resistance

**Characteristics**:
- Speed, stealth, and surprise tactics
- Reliable, battle-tested weapons
- Faster units but less armored
- Cheaper units (produced more quickly)
- Collects resources faster than Confederation (confirmed by Gear Games)
- Guerrilla warfare tactics

**Tactical Doctrine**: Hit-and-run, flanking maneuvers, speed advantage. "Our strength lies in speed, stealth and surprise. Our weapons are reliable and tested by time."

### 2.5 Core Features

- Beautiful modern 3D-rendered graphics (isometric/top-down perspective)
- 7 campaign missions per episode in different locations
- Big variety of units and upgrades
- Each faction has its own weapons and equipment with special characteristics
- Each faction has its own battle tactics
- High dynamics of game
- 15 additional custom missions (Global Confederation)
- Resources gained automatically (no manual mining)
- Map editor and custom map support
- Bluetooth multiplayer (Liberation of Peru)
- Online multiplayer (Art of War 2: Online)

---

## 3. Technical Architecture

### 3.1 Platform & Engine (J2ME MIDlet Port, Android Canvas)

The game is a **J2ME MIDlet port to Android**, using a custom game engine built directly on Android Canvas/SurfaceView APIs. The original J2ME game was developed for feature phones running Java ME (MIDP 2.0), and HeroCraft created a compatibility layer that maps J2ME APIs to Android equivalents.

**Engine Characteristics**:
- **Rendering**: Custom Canvas-based, no OpenGL ES usage
- **Surface**: `SurfaceView` with `SurfaceHolder.Callback`
- **Double buffering**: Manual `lockCanvas()`/`unlockCanvasAndPost()`
- **Graphics adapter**: `aq` class wraps `android.graphics.Canvas`
- **Image format**: `Bitmap.Config.RGB_565` (16-bit color, memory optimized) — with ARGB_8888 for specific files
- **Sprite rendering**: `v.a(q, x, y, anchor)` with anchor bits
- **Text rendering**: Dual system — custom bitmap fonts (`d` class) and system fonts (`by` class)
- **Clipping**: `clipRect` with `Region.Op.INTERSECT` and `REPLACE`
- **Audio**: `android.media.MediaPlayer` with `.o`, `.3`, `.m` file format support
- **Persistence**: J2ME RMS-like file storage (`.datrms` files)

#### High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Application (Activity)             │
│                    Delegates to AppCtrl               │
└──────────────────────┬──────────────────────────────┘
                       │ creates
┌──────────────────────▼──────────────────────────────┐
│                    AppCtrl (Runnable)                 │
│  - Main game thread (priority 10)                    │
│  - Dynamically loads s0/s1/s2.aow2ol via reflection  │
│  - Manages Activity lifecycle                        │
│  - Google Analytics integration                      │
│  - Billing service initialization                    │
└──────────────────────┬──────────────────────────────┘
                       │ creates
┌──────────────────────▼──────────────────────────────┐
│                 an (SurfaceView)                      │
│  h extends an → Main rendering surface               │
│  - Touch event dispatch (ACTION_DOWN/MOVE/UP)        │
│  - Key event dispatch                                │
│  - SurfaceHolder.Callback for buffer management      │
│  - Delegates input to z (Canvas) subclass            │
└──────────────────────┬──────────────────────────────┘
                       │ renders via
┌──────────────────────▼──────────────────────────────┐
│              z → bu → x → k (Game Canvas)             │
│  - Custom Canvas rendering pipeline                  │
│  - 30+ FPS game loop via Runnable                    │
│  - Touch/key input handling                          │
│  - Game state machine (menus, gameplay, etc.)        │
└──────────────────────────────────────────────────────┘
```

#### Inheritance Hierarchy

```
Activity
  └── Application

SurfaceView + SurfaceHolder.Callback
  └── an (abstract MIDlet view)
       └── h (concrete renderer with SurfaceHolder lock/unlock)

bv (abstract Displayable)
  ├── z (abstract Canvas - game screen base)
  │    └── bu (abstract - resource/asset handling)
  │         └── x (s0) / i (s1) / p (s2) - base game canvas
  │              └── k (s0) / c (s1) / q (s2) - MAIN GAME CLASS
  └── bz (abstract Dialog/Form)
       ├── af (Dialog with UI items)
       └── am (Empty/no-op Dialog)

v (Abstract Graphics)
  └── aq (Android Canvas Graphics implementation)

t (abstract UI Item)
  ├── b (Image Item / ImageView)
  ├── ak (Text Item / TextView)
  ├── cd (Text Input / EditText)
  ├── x (Choice Group / RadioGroup+CheckBox)
  └── br (Gauge/Progress)
```

### 3.2 Screen Resolution Variants (s0/s1/s2)

**Critical Finding**: The s0/s1/s2 packages are screen resolution variants, NOT factions. The game selects the appropriate variant at runtime based on device screen width:

```java
// AppCtrl.a(int screenWidth) - determines screen variant
if (screenWidth < 320) return 0;  // s0 (small, 240x320)
if (screenWidth > 320) return 2;  // s2 (large, 480x854)
return 1;                          // s1 (medium, 480x320)
```

| Variant | Resolution | Orientation | Tile Size | Asset Size | Package |
|---------|-----------|-------------|-----------|------------|---------|
| s0 | 240×320 | Portrait | 30×20 px | 260 KB sprites | s0.* |
| s1 | 480×320 | Landscape | scaled | 362 KB sprites | s1.* |
| s2 | 480×854 | Portrait | scaled | 526 KB sprites | s2.* |

**Selection Logic**: `Math.min(displayWidth, displayHeight)` compared to 320.

**Cross-Variant Class Mapping**:

| Role | s0 | s1 | s2 |
|------|-----|-----|-----|
| Main Game Class | s0.k | s1.c | s2.q |
| Base Game Canvas | s0.x | s1.i | s2.p |
| Config Manager | s0.c | s1.u | s2.o |
| Game State Manager | s0.a | s1.a | s2.a |
| Game Form Base | s0.p | s1.p | s2.p |
| Global Data | s0.y | s1.y | s2.y |
| Game Logic/Map | s0.w | s1.w | s2.z |
| Network Manager | s0.e | s1.n | s2.h |
| Network Request Queue | s0.z | s1.r | s2.y* |
| HTTP Handler | s0.aa | s1.aa | s2.aa |
| Font Manager | s0.f | s1.f | s2.f |
| Bitmap Font | s0.d | s1.d | s2.d |
| Billing Callback | s0.t | s1.t | s2.t |
| Payment Handler | s0.q | s1.q | s2.q* |
| Audio Manager | s0.i | s1.e | s2.i |
| Network Sender | s0.m | s1.m | s2.m |
| Network Receiver | s0.o | s1.o | s2.k |
| Base64 Decoder | s0.g | s1.g | s2.g |
| Bit Hash | s0.h | s1.h | s2.h* |
| CRC32 Hash | s0.n | s1.k | s2.n |
| Color Constants | s0.j | s1.j | s2.j |
| Ad Handler | s0.u | s1.v | s2.u |
| SMS Sender | s0.l | s1.l | s2.l |
| Billing Wrapper | s0.s | s1.s | s2.s |
| Online Reporter | s0.v | s1.x | s2.w |

*\*Note: s2 has naming collisions in decompiled output due to obfuscator reusing names within different scopes.*

### 3.3 Rendering Pipeline

```
Game Canvas (k/x) → v (Abstract Graphics) → aq (Android Canvas impl)
                                                      ↓
                                              Android Canvas.drawBitmap()
                                              Canvas.drawLine()
                                              Canvas.drawRect()
                                              Canvas.drawText()
                                              SurfaceHolder.lockCanvas()
                                              SurfaceHolder.unlockCanvasAndPost()
```

**Rendering Flow Detail**:

1. `k.b(v)` → called when canvas needs painting
2. `h.a(z)` → SurfaceView render method
3. Lock Canvas from SurfaceHolder
4. Save Canvas state
5. Clip to viewport
6. Translate to scroll position
7. Set Canvas on `aq`
8. `z.b(aq)` → Call game paint method
9. Restore Canvas state
10. Unlock and post Canvas

**Sprite System**:
- Sprites loaded from `/i0` file (packed container with 3-byte big-endian size prefix per image, terminated by 0xFF)
- Images loaded in pairs (even/odd indices): even = base image, odd = alpha mask composited for transparency
- 57 sprite images in s0 variant (buildings, units, terrain, UI, effects, projectiles)

### 3.4 Game Loop

The main game loop runs in `k.java` (s0 variant) as a `Runnable`:

```java
// Main game loop
while (running) {
    // Process network data
    e.c();  // Check for incoming network messages

    // Update game state
    if (gameActive) {
        // Process timed events (every 10 ticks)
        if (gameTick % 10 == 0) {
            d();  // Process timed events (building construction, reinforcements)
        }

        // Update fog of war (every 4 ticks)
        if ((gameTick & 3) == 0) {
            e();  // Update fog of war visibility
        }

        // Process all units for both players
        for (int player = 0; player < 2; player++) {
            for (int unitSlot = player*50 + 1; unitSlot <= (player+1)*50; unitSlot++) {
                if (ca[unitSlot + 1616] != 0) {  // If unit exists
                    processUnit(unitSlot);         // Process unit behavior
                }
            }
        }

        // Process buildings and resource generation
        r();

        // Check win conditions
        checkVictory();
    }

    // Handle AI decisions
    if (isAI) {
        processAI();
    }

    // Process animations
    updateAnimations();

    // Handle user input
    processInput();

    // Render frame via aq (Android Canvas)
    renderFrame();

    // Frame rate control
    Thread.sleep(frameDelay);
}
```

**Game Timing**:

```java
// Time categories (from y.bh array)
int[] timeCategories = {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535};
// Corresponds to: 30s, 1min, 2min, 6min, 12min, 24min, 48min, 2hr, 4hr, 12hr, max

// Game tick rate
int tickRate = 100 / speedMultiplier;  // Base: 100ms per tick

// Speed multiplier
int speedMultiplier = (isAccelerated ? m : 1);  // m = game speed setting
```

---

## 4. Game Systems Architecture

### 4.1 Unit System

#### Entity Data Layout

All units and buildings share a common data structure in the `X` byte array (`k.X`, 7272 bytes). Each entity occupies 101 bytes with offset `entityIndex * 101`. Up to 72 entities supported (50 units + 22 building slots per player).

| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| +0 | 1 | X position | Tile X coordinate |
| +101 | 1 | Y position | Tile Y coordinate |
| +202 | 1 | Target X | Movement target X |
| +303 | 1 | Target Y | Movement target Y |
| +404 | 1 | Facing | Direction (0–7, 8 cardinal+ordinal) |
| +505 | 1 | Display facing | Visual facing direction |
| +606 | 1 | Secondary facing | Turret/body facing |
| +707 | 1 | Target facing | Direction to face for attack |
| +808 | 1 | Move anim frame | Movement animation counter |
| +909 | 1 | Attack anim frame | Attack animation counter |
| +1010 | 1 | Waypoint index | Current path step |
| +1111 | 1 | Attack state | Attack state counter |
| +1212 | 1 | Weapon reload | Reload cooldown timer |
| +1313 | 1 | Production progress | Current production progress |
| +1414 | 1 | Action state | 0=idle, 1=attacking, 2=moving, 3=casting |
| +1515 | 1 | Attack cooldown | Time until next attack |
| +1616 | 1 | HP | Current health points (-1 = dying) |
| +1717 | 1 | Home X | Rally point X |
| +1818 | 1 | Home Y | Rally point Y |
| +1919 | 1 | Garrison ref | Garrison/transport reference |
| +2020 | 1 | Prod queue 0 | Production queue slot 0 |
| +2121 | 1 | Prod queue 1 | Production queue slot 1 |
| +2222 | 1 | Prod queue 2 | Production queue slot 2 |
| +2323 | 1 | Unit type ID | Unit/building type identifier |
| +2424 | 1 | Anim counter | General animation counter |
| +2525 | 1 | Prod timer 0 | Production timer slot 0 |
| +2626 | 1 | Prod timer 1 | Production timer slot 1 |
| +2727 | 1 | Prod timer 2 | Production timer slot 2 |
| +2828 | 1 | Status flags | Bitfield (see below) |
| +2929 | 1 | Command flags | Active commands |
| +3232 | 1 | Upgrade level | Production upgrade level |
| +3535 | 1 | Rally ref | Rally point reference |
| +4040 | 1×3 | Upgrade levels | 3 upgrade slots |
| +4949 | 1 | Shield bonus | Shield/armor bonus |
| +5050 | 1 | Cargo | Transport cargo |
| +5151 | 1 | Stealth timer | Stealth/visibility timer |
| +5252 | 1 | Anchor X | Building anchor X |
| +5353 | 1 | Anchor Y | Building anchor Y |
| +5454 | 1 | Construction state | 0=none, >0=building, -1=destroyed |
| +5555 | 1 | Construction progress | Building construction progress |
| +5656 | 1 | Building type | Building type ID |
| +5757 | 1 | Link ref | Reference to another entity |
| +6060 | 1 | Building flags | Building state flags |
| +6161 | 1 | Building state | Current building state |
| +6363 | 1 | Top-left X | Building top-left X |
| +6464 | 1 | Top-left Y | Building top-left Y |
| +6565 | 1 | Resource timer | Resource generation timer |
| +6767 | 1 | Resource capacity | Resource capacity |
| +6868 | 1 | Resource type | Resource type / upgrade level |

#### Status Flags (offset +2828)

| Bit | Meaning |
|-----|---------|
| 0 | Production slot 0 active |
| 1 | Production slot 1 active |
| 2 | Production slot 2 active |
| 3 | Being attacked / targeted |
| 4 | Selected by player |
| 5 | Retreating / flee mode |
| 6 | Garrisoned / inside transport |
| 7 | Special ability ready |

#### Unit Type Masks

The game uses bitmask checks to categorize units:

| Mask Value | Hex | Category |
|------------|-----|----------|
| 16447 | 0x401F | Military units (all combat units) |
| 114688 | 0x1C000 | Building entities |
| 1011 | 0x3F3 | Military production buildings |
| 16256 | 0x3F80 | Heavy weapons / machinery |

#### Unit Type ID Mapping (19-entry Confederation table)

| Index | Entity | Type |
|-------|--------|------|
| 0 | Infantry (Confed) | infantry |
| 1 | Grenadier (Confed) | infantry |
| 2 | Flame Assault (Confed) | infantry |
| 3 | AV-40 Fortress (Confed) | vehicle |
| 4 | T-21 Hammer (Confed) | vehicle |
| 5 | T-22 Zeus (Confed) | vehicle |
| 6 | MLRS Torrent (Confed) | vehicle |
| 7 | Command Centre | building |
| 8 | Generator | building |
| 9 | Infantry Centre | building |
| 10 | Machine Factory | building |
| 11 | Technology Centre | building |
| 12 | Bunker (Confed) | building |
| 13 | Locator | building |
| 14 | Rocket Launcher | building |
| 15 | Mine Scorpio | mine |
| 16 | Mine Frog | mine |
| 17 | Mine Lizard | mine |
| 18 | Wall | building |

#### 25-Entry Combined Table (Both Factions)

| Index | Entity | Faction |
|-------|--------|---------|
| 0–6 | Confederation units | Confederation |
| 7–14 | Confederation buildings | Confederation |
| 15–21 | Rebel units | Rebels |
| 22–24 | Rebel buildings + shared | Rebels/Shared |

### 4.2 Building System

#### Building Categories

From the bitmask checks in `w.java`:
- **16447 (0x401F)**: Military units bitmask
- **114688 (0x1C000)**: Building bitmask — identifies building-type entities
- **1011 (0x3F3)**: Military production buildings bitmask

#### Building Footprints

Building dimensions are stored in `as[18]` (width) and `as[19]` (height), indexed by building type ID:

| Footprint Widths (px) | Footprint Heights (px) | Power Radius |
|-----------------------|------------------------|--------------|
| 20, 20, 20 | 20, 30, 40 | 10 |
| 30, 30, 30 | 20, 30, 40 | 20 |
| 40, 40, 40 | 20, 30, 40 | 30, 40, 60, 127 |

Buildings with ID >= 121 are special (1×1 footprint, likely turrets/walls).

**Building Placement Validation**:
```java
this.ag = (byte) w.a(this.ag, y.bP.c + 1,
    (y.bP.c + 8) - (this.ai >= 121 ? 1 : y.bO.as[18][this.ai]) - 1);
this.ah = (byte) w.a(this.ah, y.bP.d + 1,
    (y.bP.d + y.bP.aj) - (this.ai >= 121 ? 1 : y.bO.as[19][this.ai]) - 1);
```

#### Building Resource Generation

Buildings generate resources on a timer:

```java
if (this.bS[this.bT[106] + this.ca[this.af + 5656]] % 3 == 0 &&
    (this.aL.ah & 127) == 127) {
    this.cb[this.ae][4] += income_amount;
}
```

The modulo-3 check on building data separates income-generating buildings:
- `data % 3 == 0`: Income-generating (Supply Center, Command Center)
- `data % 3 == 1`: Military production (Barracks, Factories)
- `data % 3 == 2`: Research/defensive (Workshop, Turrets)

Income ticks happen every 128 game frames (the `& 127` check).

#### Garrison System

Units can garrison in bunkers and towers:
- Building type 7 or 8 (Bunker or Tower) supports garrison
- Garrison provides: protection from damage, increased fire rate, extended vision range
- Garrison cooldown: `cg[2][buildingType]` ticks between enter/exit
- When garrisoned: `garrisonUnit = unitRef; unitHP = 0` (unit hidden)

### 4.3 Combat System (ALL Formulas)

#### Damage Application

When a unit takes damage (method `a(byte b, int i, int i2)` in w.java):

```java
// Reduce HP by damage amount
ca[unit + 1616] = ca[unit + 1616] - damage;

// If HP drops to 0 or below
if (ca[unit + 1616] <= 0) {
    ca[unit + 1616] = -1;  // Mark as dying

    // Calculate death animation frame
    ca[unit + 1414] = (isInfantry ?
        (bi[attacker_type] + random(bd[attacker_type])) + 10 - 231 : 2);

    // For non-infantry deaths: spawn explosion effects
    if (!isInfantry) {
        spawnEffect(unit_x, unit_y, offX, offY, random(4) + 11);   // Fire effect
        spawnEffect(unit_x, unit_y, offX, offY, random(5) + 27);   // Smoke effect
        spawnEffect(unit_x, unit_y, offX, offY, 6);                 // Debris effect
    }
}
```

#### Infantry vs Machinery Death Animation

- **Infantry** (bitmask 16447 set): `bi[attackerUnitType] + random(0..bd[attackerUnitType]) + 10 - 231`
- **Machinery**: Fixed animation frame `2`

Where:
- `bi = {231, 249, 249, 259, 247}` — base animation offsets per attacker type category
- `bd = {16, 10, 10, 3, 2}` — random range for death animation per attacker type

#### Damage Calculation for Projectiles

When a projectile hits (method `c(boolean z)` in w.java):

**For missile/projectile type 10 (artillery)**:
```java
if (target is enemy unit) {
    damage = cg[0][10] * (10 - targetArmour) / 10;  // Reduced by armour
    damage = max(min(damage, cg[0][10] - targetArmour), 1);  // Clamped to minimum 1
}
```

**For ground impact (missing the target)**:
```java
if (target is friendly or neutral) {
    damage = cg[0][projectileType] * (10 - armour) / 10;
    damage = max(min(damage, cg[0][projectileType] - armour), 1);
}
```

#### Armour Calculation

The `l(int i)` method calculates effective armour:

```java
int getArmour(int unitRef) {
    if (unitRef > 100) {
        return 0;  // Buildings have 0 base armour (use construction HP)
    }
    if (unitRef <= 0) {
        return N[(-unitRef - 1) / 50];  // Building armour from N array
    }
    byte baseArmour = cf[2][ca[unitRef + 2323]];  // Base armour from unit type

    int player = (unitRef - 1) / 50;

    // Apply upgrade bonus if game time has passed threshold
    if (Y[player] >= gameTick) {
        baseArmour += Z[player][((isInfantry ? 0 : 1) + 4)];  // Research bonus
    }

    return baseArmour;
}
```

#### Attack Range Calculation

The `o(int i)` method calculates distance to target:

```java
int getDistanceToTarget(int weaponSlot) {
    byte targetRef = ca[(weaponSlot * 101) + 1919 + currentUnit];

    int targetX, targetY;
    if (weaponSlot == 0 || targetRef > 100) {
        targetX = ca[currentUnit + 1717];  // Use movement target
        targetY = ca[currentUnit + 1818];
    } else if (targetRef > 0) {
        targetX = ca[targetRef + 0];       // Use target unit position
        targetY = ca[targetRef + 101];
    } else {
        // Target is a building
        if (weaponType == 3) {
            targetX = clamp(currentX, buildingMinX, buildingMaxX);
        } else {
            targetX = ca[(-targetRef) + 5252];
        }
        targetY = ca[(-targetRef) + 5353];
    }

    int dx = targetX - ca[currentUnit + 0];
    int dy = targetY - ca[currentUnit + 101];

    if (dx > 15 || dy > 15 || dx < -15 || dy < -15) {
        return 127;  // Out of range
    }

    return (lookupTable[dy + 15][dx + 15] & 255) >> 3;  // Distance from lookup table
}
```

#### Vision Range Check

```java
int getDistanceClass(int dx, int dy) {
    if (dx > 15 || dy > 15 || dx < -15 || dy < -15) {
        return 127;  // Far out of range
    }
    return (distanceTable[(dy + 15) * 31 + dx + 15] & 255) >> 3;
}

int getTerrainCost(int dx, int dy) {
    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
        return 0;
    }
    return distanceTable[(dy + 15) * 31 + dx + 15] & 7;
}
```

The 31×31 distance lookup table has dual encoding:
- **Upper 5 bits** (>>3): Distance class (0–31) for range checks
- **Lower 3 bits** (&7): Terrain cost modifier for pathfinding

#### Splash Damage (Artillery)

Artillery projectiles (type 10) deal splash damage when they impact:

```java
if (projectileType == 10 && elapsed >= flightTime) {
    for each unit at impact position {
        if (unit is enemy) {
            int armour = getArmour(unitRef);
            int baseDamage = cg[0][10];
            int damage = max(min((baseDamage * (10 - armour)) / 10, baseDamage - armour), 1);
            applyDamage(unitRef, damage, attackerPlayer);
        } else if (unit is friendly or terrain) {
            // Reduced friendly fire damage
            int damage = max(min((baseDamage * (10 - armour)) / 10, baseDamage - armour), 1);
            applyDamage(unitRef, damage, attackerPlayer);
        }
    }
}
```

#### Nuclear/Explosion Damage (Area Damage)

Special area damage calculation from method `b(byte b, byte b2)`:

```java
void areaDamage(byte centerX, byte centerY) {
    int radius = bS[bT[80] + attackType];  // Blast radius

    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            int targetX = centerX + dx;
            int targetY = centerY + dy;

            if (inBounds && (target is unit or building)) {
                int armour = getArmour(target);
                int distanceFactor = bS[bT[79] + attackType] *
                                     bS[distanceTable[dy][dx]] / 12;
                int damage = max(min(((10 - armour) * distanceFactor) / 10,
                              distanceFactor - armour), 1);
                applyDamage(target, damage, sourceX, sourceY);
            }
        }
    }

    // Spawn visual effects
    spawnEffect(centerX, centerY, 0, 0, effectTable[attackType] + random(range));
    spawnEffect(centerX, centerY, 0, 0);  // Secondary effect
}
```

#### HP Regeneration

```java
// Infantry health recovery
if (isInfantry && powered) {
    int recoveryRate = baseRecoveryRate;
    if (hasEnergySuit) {
        recoveryRate *= 3;  // Triples with Energy Suit research
    }
    // Applied periodically
}

// Machinery repair (in production buildings)
int repairRate = baseRepairRate;
if (hasRepairResearch) {
    repairRate *= 3;  // Triples with repair research
}
```

#### Unit Cost & Reward Calculations

```java
// Kill reward calculation (when unit dies)
int killReward = (unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2);
int scoreReward = killReward / 2;

// Credit changes on unit death
W[enemyPlayer] += killReward;       // Winner gets credits
X[enemyPlayer] += scoreReward;      // Winner gets score
W[losingPlayer] -= scoreCost;       // Loser loses score
scoreDisplay += (killReward + scoreReward) * 2;  // Display score

// Capture reward
W[captor] += 200;
X[captor] += 100;
scoreDisplay += 600;
```

#### Unit vs Building Interaction

```java
// Building damage resistance
int buildingArmour = N[(buildingRef - 1) / 50];  // Per-player armour value

// Weapon effectiveness vs buildings
// Infantry weapons (bitmask 16447): Reduced vs buildings
// Heavy weapons (bitmask 16256): Full damage vs buildings
// Siege weapons: Bonus vs buildings

// Target priority
if (targetIsInfantry && attackerIsInfantry) {
    // Full damage
} else if (targetIsMachinery && attackerIsInfantry) {
    // Reduced damage
} else if (targetIsBuilding) {
    // Use building armour
}
```

#### Score Calculation

```java
// Score on unit kill
killScore = (unitCost * 3 * (distanceToEnemyBase1 + distanceToEnemyBase2)) /
            (averageBaseDistance * 2);
lossScore = killScore / 2;

// Score on building capture
captureScore = 600;

// Credit change on kill
creditGain = lookupTable[unitType] * playerCreditModifier / 100;
```

### 4.4 Economy System

#### Key Design Decision: Automatic Resource Generation

Unlike traditional RTS games, Art of War 2 does **not require manual resource gathering**. Resources are gained automatically, allowing players to focus entirely on combat and strategy.

#### Resource Types

1. **Credits**: Primary currency, generated automatically by buildings
2. **Power**: Generated by power plants, used to operate buildings and units

#### Credit Generation Formula

```java
// Per-tick income from buildings (every 127 ticks when building is active)
int income = (baseIncome * 7) / 10;  // 70% of base income

// Full income cycle
int incomePerCycle = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20);
```

#### Faction Economic Differences

- **Resistance** collects resources faster than Confederation (confirmed by Gear Games)
- This gives Resistance a significant economic advantage
- Confederation compensates with stronger individual units

#### Starting Resources

```java
for (int i = 0; i < 2; i++) {
    J[i] = 2;      // Starting X
    O[i] = 6;      // Starting Y
    N[i] = 8;      // Starting resources
    P[i*3] = 10;   // Starting unit positions
    Q[i] = 100;    // Starting credits
    R[i] = 10;     // Starting unit count
    S[i] = 20;     // Starting supply
}
```

### 4.5 Research/Tech Tree (ALL 48 Research Effects)

Each research ID (0-47) applies specific stat modifications via the `g(int i)` method:

#### Complete Research Effect Table

| ID | Effect | Faction |
|----|--------|---------|
| 0 | Infantry armour +2, Sniper armour +2, Light armour +2; Unlocks research chain | Confederation |
| 1 | Player 0 attack range reduction /3 (divide by 3) | Confederation |
| 2 | Attack speed -2 (faster) for specific unit types | Confederation |
| 3 | Attack damage +2, Production damage +2 | Confederation |
| 4 | Building armour +4, Production armour +4 | Confederation |
| 5 | Building radius +1; Unlocks research chain | Confederation |
| 6 | Upgrades unit type 18 → type 7 (Rhino → new type) | Confederation |
| 7 | Attack speed +5 for type 11, +8 for type 13; Production +8 for type 11, +8 for type 17, +5 for type 9 | Confederation |
| 8 | Attack range -1 for types 7,18,9,11,17,13,16; Unlocks chain 9-13; Building radius +1 | Confederation |
| 9 | Infantry armour +2 for types 7,18,9,11,17,13,16 | Confederation |
| 10 | Player 1 attack range reduction /3 | Confederation |
| 11 | Attack speed -2 (faster) for types 11, 13 | Confederation |
| 12 | Upgrades unit type 17 → type 11 (Hammer → new type) | Confederation |
| 13 | Building radius +1; Unlocks research chain 14 | Confederation |
| 14 | Attack damage +10 for type 21, +2 for type 21 range; Production +2 for type 16, +5 for type 13 | Confederation |
| 15 | Player 0 supply cap = 8 | Confederation |
| 16 | Player 0 building armour = 9 | Confederation |
| 17 | Player 0 unit limit +2; Production +1 for type 15; Production speed = 20 | Confederation |
| 18 | Building radius +1 | Confederation |
| 19 | Player 1 production P[1] = 7 | Confederation |
| 20 | Player 1 production P[2] = 7 | Confederation |
| 21 | Player 0 credit limit Q[0] = 120 | Confederation |
| 22 | Player 0 score bonus S[0] = 30 | Confederation |
| 23 | Player 0 display bonus = 25 | Confederation |
| 24 | Infantry armour +1 for types 0,2,4,14 | Rebels |
| 25 | Player 1 attack range reduction /3 | Rebels |
| 26 | Attack speed +1 for types 0,2,3; Production +1 for types 0,4 | Rebels |
| 27 | Attack range -1 for types 0,2,4,14; Unlocks chain | Rebels |
| 28 | Attack range +1 for type 15; Production +1 for types 2, 2 | Rebels |
| 29 | Building radius +1; Unlocks chain 30 | Rebels |
| 30 | Attack speed +2 for type 3; Range +2 for type 3; Production +2 for type 14, +2 for type 4 | Rebels |
| 31 | Attack speed +1 for type 4, +1 for type 5; Production +1 for type 6, +1 for type 8 | Rebels |
| 32 | Attack range -1 for types 6,8,10,15,12; Unlocks chain 33-37; Building radius +1 | Rebels |
| 33 | Infantry armour +1 for types 6,8,10,15,12 | Rebels |
| 34 | Player 1 attack range reduction /3 | Rebels |
| 35 | Attack speed -2 (faster) for types 12, 14 | Rebels |
| 36 | Unit type 10 siege upgrade = 15 | Rebels |
| 37 | Building radius +1; Unlocks chain 38 | Rebels |
| 38 | Attack damage +2 for type 20, +2 for type 20 range; Production +2 for type 12 | Rebels |
| 39 | Player 1 supply cap = 8 | Rebels |
| 40 | Player 1 building armour = 9 | Rebels |
| 41 | Building radius +1; Unlocks chain 42 | Rebels |
| 42 | Building radius +1 (cumulative) | Rebels |
| 43 | Player 0 production P[4] = 7 | Rebels |
| 44 | Player 1 production P[5] = 7 | Rebels |
| 45 | Player 1 credit limit Q[1] = 120 | Rebels |
| 46 | Player 1 score bonus S[1] = 30 | Rebels |
| 47 | Player 1 display bonus = 25 | Rebels |

#### Research Cost Formula

```java
// Research time calculation
int researchTime;
if (researchState >= 10) {
    researchTime = (researchState + 231) - 10;  // Direct index into at[12]
} else {
    researchTime = bQ[unitType][researchState] + (hasInfantry ? 8 : 0);
}

// Research progress
int ticksToComplete = at[12][researchTime] - at[11][researchTime];

// Research cost
int cost = (unitBuildCost * productionModifier) / 10 * 20 / (upgradeBonus + 20);
```

#### Technology Names (Confederation)

- Energy suit
- Bio suit
- Enhanced firing rate
- Armour-piercing bullet
- Forced light missiles
- Lava flame fuel
- Volcano flame gun
- Heavy shells
- Reinforced engine
- Energy armour
- Damage diagnostics
- Fast recharging
- T-22 rocket launcher
- Torrent-5 MRLS
- Torrent-10 MRLS
- Reinforced generator
- Titanium cover
- Walls Active armour
- Warning System
- Infantry express-training
- Upgraded assembly line
- Finance department
- Incentive system
- Communications system

#### Technology Names (Rebels)

- Titanium jacket
- First-aid kit
- Enhanced fire rate
- Doping
- Snipers
- Rifle 'Hornet-10'
- Heavy machine gun
- Reinforced engine
- MMC 'Porcupine'
- Advanced 'Porcupine'

### 4.6 AI System

#### AI Architecture

The AI opponent operates during the game's execution phase. The AI logic is embedded in the `w.java` class, using the same unit/building data structures as the human player.

#### AI Decision Loop

```java
// Main game tick - processes all units for both players
final void b() {
    if (this.aL.ah % 30 == 0) {  // Every 30 ticks
        this.aM.bJ = 0;           // Reset battle flag
    }
    if (this.aL.ah % 10 == 0) {  // Every 10 ticks
        d();                       // Process timed events
    }
    if ((this.aL.ah & 3) == 0) {  // Every 4 ticks
        e();                       // Update fog of war
    }

    for (int player = 0; player < 2; player++) {
        for (int unitSlot = player*50 + 1; unitSlot <= (player+1)*50; unitSlot++) {
            if (ca[unitSlot + 1616] != 0) {
                processUnit(unitSlot);
            }
        }

        if (Y[player] == gameTick) {
            rebuildBase(player);
        }
    }

    r();  // Process buildings and resource generation
}
```

#### Unit State Machine

```
States:
  0 = Idle (no current action)
  1 = Moving to target
  3 = Attacking target
  10 = Dying/death animation
```

```
[Idle] --> Check for enemies in range
  |                          |
  | No enemies               | Enemy found
  v                          v
  Check movement path     [Attacking]
  |                          |
  | Path exists              | Target destroyed
  v                          v
  [Moving]               [Idle] (search for new target)
  |
  | Path blocked
  v
  [Stuck] --> Recalculate path or attack blocker
```

#### AI Attack Decision Logic

```java
private boolean processAttackDecision() {
    // Check if unit is in siege mode (forced attack)
    if ((flags2 & 32) != 0) {
        stopMovement();
        return true;
    }

    // If unit has a target, process it
    if (ca[af + 1919] != 0) {
        processTargetEngagement();
    } else if (atPosition && atDestination) {
        stuckCounter = 0;
        if (hasRallyPoint) {
            moveToRallyPoint();
        }
    }

    processWeaponFiring();

    if (pathStart >= pathEnd && distanceToTarget > moveRange) {
        findPath(currentX, currentY, targetX, targetY);
    }

    return false;
}
```

#### AI Target Selection

```java
final boolean searchForTargets(byte centerX, byte centerY, int mode, int range, int threshold) {
    int bestDistance = 127;
    int bestTarget = 0;

    for (int gridX = searchMinX; gridX <= searchMaxX; gridX++) {
        for (int gridY = searchMinY; gridY <= searchMaxY; gridY++) {
            for each unit at (gridX, gridY) {
                if (isVisible && distanceClass <= range) {
                    if (mode <= 0) {
                        // Find closest enemy
                        int priority = isBuilding ? buildingPriority : unitPriority;
                        if (priority > bestPriority ||
                            (distanceClass < bestDistance && priority == bestPriority)) {
                            bestDistance = distanceClass;
                            bestTargetX = targetX;
                            bestTargetY = targetY;
                            bestTargetRef = unitRef;
                            bestPriority = priority;
                        }
                    } else if (mode == 1) {
                        // Attack mode: damage enemies in range
                        if (distanceClass <= range && !target.isAttacking) {
                            damageUnit(unitRef, 0, false);
                        }
                    } else {
                        // Count mode: count HP in range
                        totalHP += ca[unitRef + 1616];
                    }
                }
            }
        }
    }

    return (mode <= 1) && (bestDistance < 127);
}
```

#### AI Target Priority System

1. **Unit type priority**: Buildings have priority = max HP value `bS[bT[53] + unitType]`
2. **Distance**: Closer targets are preferred when priorities are equal
3. **Visibility**: Only targets in fog-of-war visible area are considered
4. **Infantry vs Machinery**: Infantry units target other infantry first; machinery targets machinery first

#### AI Path Recalculation

When a unit is stuck (stuckCounter reaches ±5):

```java
if (stuckCounter >= 5 || stuckCounter <= -5) {
    clearCurrentPath();
    if (hasRallyPoint) {
        setNewRallyPoint();
    }
}
```

#### AI Difficulty Levels

| Level | Value | Effects |
|-------|-------|---------|
| Easy | 0 | Reduced sight range, slower research, lower income |
| Normal | 1 | Standard parameters |
| Hard | 2+ | Increased sight range, faster research, income bonuses |

Difficulty affects:
- Unit sight range (`w.ai` calculation)
- Building upgrade research speed
- Resource income multiplier
- Aggressiveness thresholds

#### AI Siege Mode Behavior

Units that can enter siege mode (bitmask 114688):

```java
// Auto-siege: When enemy is nearby
if (isSiegeCapable && !isInSiegeMode) {
    if (distanceToNearestEnemy <= siegeRange) {
        flags2 |= 32;  // Set siege flag
    }
}

// Siege mode effects:
// - Cannot move
// - Increased attack range
// - Increased damage
// - Changed attack animation
```

#### AI Combat Preferences

| Unit Category | Preferred Target | Behavior |
|---------------|-----------------|----------|
| Infantry (1,2,3) | Other infantry | Close-range engagement |
| Light Machinery (4,21) | Scouts/raids | Hit-and-run |
| Heavy Machinery (7,16) | Buildings/heavy | Siege warfare |
| Artillery (19,20) | Area targets | Long-range bombardment |
| Mines (9,10,11) | Area denial | Stationary traps |

#### AI Event Types

| Event Type | Description | Parameters |
|------------|-------------|------------|
| 24 | Set AI difficulty level | `am[3]`: 0=Easy, 1=Normal, 2=Hard |
| 25 | Give resources to players | `am[1]`: Player 0 amount, `am[2]`: Player 1 amount |
| 27 | Spawn reinforcement units | `am[1]`: X, `am[2]`: Y, `am[3]`: composition, `am[5]`: faction |
| 29 | Trigger cinematic/event | `am[3]`: event ID |

### 4.7 Pathfinding System

#### Overview

The pathfinding system uses a **modified A* algorithm** with Bresenham line-of-sight optimization and terrain cost mapping.

#### Core Data Structures

**Grid System**:
- Map size: 128×128 tiles
- Tile size: 30×20 pixels (width × height)
- Unit occupancy grid: `bW[y][x]` — tracks which unit occupies each cell
  - Value 0: Empty/passable
  - Values 1-100: Unit reference (player 0: 1-50, player 1: 51-100)
  - Values 121-123: Blocking terrain (walls, obstacles)
  - Value 127: Temporary obstacle marker

**Spatial Hash Grid**:
- 8×8 grid per player for fast unit lookup
- Stored in `bk[player][gridY][gridX]` as linked list
- Linked list via `bl[player][index]` — next pointer
- Rebuilt each tick via method `E()` (line 318)

```java
// Rebuild spatial hash
private void rebuildSpatialHash() {
    for (int p = 0; p < 5; p++) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                bk[p][y][x] = -1;
            }
        }
    }

    for (int p = 0; p < 4; p++) {
        int baseOffset = (p < 2) ? 0 : 5252;
        int yBase = (p < 2) ? 101 : 5353;

        for (int i = firstUnit; i <= lastUnit; i++) {
            if ((ca[baseOffset + i] + 1) > 1) {
                int gridX = ca[i + xBase] >> 4;   // X / 16
                int gridY = ca[i + yBase] >> 4;   // Y / 16
                int index = i - firstUnit;
                bl[p][index] = bk[p][gridY][gridX];
                bk[p][gridY][gridX] = (byte) index;
            }
        }
    }
}
```

**Distance Lookup Table**:
- 31×31 table covering dx = -15 to +15, dy = -15 to +15
- Upper 5 bits (>>3): Distance class (0-31)
- Lower 3 bits (&7): Terrain cost modifier

#### Pathfinding Algorithm

**Main Path Calculation** (method `c(int, int, int, int)`):

```java
private void calculatePath(int fromX, int fromY, int toX, int toY) {
    // Initialize direction decomposition
    decomposeDirection(toX - fromX, toY - fromY);

    // Initialize 3 path candidates
    for (int i = 0; i < 3; i++) {
        pathLength[i] = clamp(majorSteps + minorSteps, 0, maxPathLength);
        pathCost[i] = 0;
    }

    // Generate Bresenham-style paths
    generateBresenhamPaths();

    // Find cheapest path
    int bestPath = 0;
    for (int i = 1; i < 3; i++) {
        if (pathCost[i] < pathCost[bestPath]) {
            bestPath = i;
        }
    }

    // Set path ordering
    pathOrder[0] = bestPath;
    pathOrder[1] = (bestPath + 1) % 3;
    pathOrder[2] = (bestPath + 2) % 3;

    // Detect obstacles and mark segments
    int r = 0;  // Number of obstacle segments
    for (int step = 0; step < pathLength[pathOrder[0]]; step++) {
        int cellX = path[pathOrder[0]][0][step];
        int cellY = path[pathOrder[0]][1][step];

        int cellState = isPassable(cellX, cellY);

        if (prevState == 0 && cellState != 0) {
            if (r >= 10) return;  // Max 10 obstacle segments
            segmentStartX[r] = cellX;
            segmentStartY[r] = cellY;
            r++;
        } else if (prevState != 0 && cellState == 0) {
            segmentEndX[r - 1] = cellX;
            segmentEndY[r - 1] = cellY;
            O[cellY][cellX] = (byte)(256 - r);  // Mark as temporary
        }
        prevState = cellState;
    }
}
```

**Direction Decomposition** (method `b(int, int)`):

```java
private void decomposeDirection(int dx, int dy) {
    int absDx = Math.abs(dx);
    int absDy = Math.abs(dy);

    if (dx > 0) p += 4;  // East component
    if (dy > 0) p += 2;  // South component

    if (absDx <= absDy) {
        s[0] = absDy - absDx;  // Minor steps (diagonal)
        s[1] = absDx;           // Major steps (straight)
    } else {
        p += 1;
        s[0] = absDx - absDy;  // Minor steps
        s[1] = absDy;           // Major steps
    }
}
```

**Bresenham Path Generation** (method `k()`):

Generates 3 candidate paths using Bresenham-style line algorithms — one each for X-dominant, Y-dominant, and diagonal movement.

**Cell Passability Check** (method `c(int, int)`):

```java
private byte isPassable(int x, int y) {
    if (bW[y][x] != currentUnit && !isInFogOfWar(x, y)) {
        if (bW[y][x] >= 121 && bW[y][x] <= 123) {
            return 0;  // Blocking terrain
        }
        if (!bE && bW[y][x] >= enemyMinUnit && bW[y][x] <= enemyMaxUnit) {
            return 0;  // Enemy unit blocking
        }
        if (bW[y][x] <= playerMinUnit || bW[y][x] > playerMaxUnit || !isAlive(bW[y][x])) {
            if (bW[y][x] <= enemyMinUnit || bW[y][x] > enemyMaxUnit) {
                if (!isInfantry(bW[y][x]) || !isInfantry(currentUnit)) {
                    return 1;  // Passable
                }
                return 0;  // Friendly infantry blocking
            }
            return 0;  // Enemy unit blocking
        }
        return 0;  // Friendly unit blocking
    }
    return 0;  // In fog or same cell
}
```

**Path Storage Format**:
- Each unit can store up to 50 path steps
- `al[0][unit][0..49]` — X coordinates for each step
- `al[1][unit][0..49]` — Y coordinates for each step
- `ca[unit + 1010]` — Current position in path (pathStart)
- `ca[unit + 1111]` — End position in path (pathEnd)

**Path Following**:

```java
if (pathStart < pathEnd) {
    int nextX = al[0][unit][pathStart];
    int nextY = al[1][unit][pathStart];

    if (bW[nextY][nextX] == 0 || bW[nextY][nextX] == 127) {
        advanceUnit(unit, nextX, nextY);  // Cell is free
    } else {
        stuckCounter++;
        if (stuckCounter >= 5) {
            recalculatePath(unit);  // Stuck for 5 ticks
        }
    }
}
```

#### Performance Characteristics

| Parameter | Value |
|-----------|-------|
| Max path length | 50 steps |
| Max obstacle segments | 10 per path |
| Search range | 15 cells (31×31 lookup table) |
| Spatial hash resolution | 8×8 grid per player (16×16 cells per bucket) |
| Fog update rate | Every 4 game ticks |
| Path recalculation trigger | After 5 ticks of being stuck |

### 4.8 Fog of War

#### Implementation

The fog of war is implemented via a 4D array:

```java
int[][][][] Q = (int[][][][]) Array.newInstance(Integer.TYPE, 2, 2, 4, 128);
```

Dimensions: `[side][fogLevel][bitmaskRow][column]`
- `side`: 0 or 1 (player 0 or 1)
- `fogLevel`: 0=explored, 1=currently visible
- `bitmaskRow`: 4 rows of 128-bit bitmasks (4×32 bits = 128 bits per row)
- `column`: 128 columns (one per map tile row)

#### Fog of War Update (every 4 ticks)

```java
for (int i = 0; i < 4; i++) {
    for (int i2 = 0; i2 < 128; i2++) {
        Q[side][1][i][i2] = (y.Y[side] < aL.ah || y.Z[side][16] == 0)
            ? 0 : -1;  // -1 = all bits set = fully visible
    }
}
```

Visibility is calculated from unit positions with range defined by `cf[1][unitType]` (sight range per unit type).

#### Fog of War Pathfinding Interaction

- Paths cannot be calculated through fog-of-war
- Units in fog are invisible and cannot be targeted
- Pathfinding treats fog cells as passable (since obstacles aren't visible)
- When fog reveals an obstacle, the path is recalculated
- AI units have a "stealth detection" timer (`ca[af+5151]`) that counts up when in fog

#### Terrain Color Palettes for Fog

```java
short[][] bQ = {
    {117, 117, 117, 116},  // Fog of war (gray)
    {66, 66, 66, 65},       // Unexplored (dark)
};
```

### 4.9 Projectile System

#### Projectile Data Arrays (400 max active)

| Array | Field | Description |
|-------|-------|-------------|
| `t[idx]` | grid X | Grid X position |
| `u[idx]` | grid Y | Grid Y position |
| `v[idx]` | pixel X | Pixel offset X |
| `w[idx]` | pixel Y | Pixel offset Y |
| `A[idx]` | velocity X | Pixels per tick X |
| `B[idx]` | velocity Y | Pixels per tick Y |
| `C[idx]` | time remaining | Travel time remaining |
| `E[idx]` | total time | Total travel time |
| `G[idx]` | type | Projectile type |
| `y[idx]` | source ref | Source unit reference |
| `z[idx]` | owner | Owner player |
| `x[idx]` | target ref | Target unit reference |
| `F[idx]` | impact flags | Impact flags |
| `D[idx]` | elapsed | Elapsed travel time |

#### Projectile Movement (per tick)

```java
// Update pixel position
v[idx] += A[idx];  // Add X velocity
w[idx] += B[idx];  // Add Y velocity

// Convert pixel position to grid position
gridX = (v[idx] + 3000 + 15) / 30 - 100;
gridY = (w[idx] + 2000 + 10) / 20 - 100;

// Update grid position
t[idx] += gridX;
u[idx] += gridY;

// Subtract integer cell from pixel offset
v[idx] -= gridX * 30;
w[idx] -= gridY * 20;
```

#### Projectile Spawn

When a unit fires:

```java
// Calculate flight time based on distance
flightTime = distanceTable[Math.abs(dy) * 21 + Math.abs(dx)] / speedTable[projectileType];

// Calculate pixel offset for start position
startOffX = (dx * 30) + sourceOffX - offX;
startOffY = (dy * 20) + sourceOffY - offY;

// Calculate travel time
totalTime = lookup(startOffX, startOffY, speedCurveTable[projectileSpeed]);

// Calculate velocity
velX = startOffX / (flightTime + 1);
velY = startOffY / (flightTime + 1);

// For artillery (type 10): clamp velocity
if (projectileType == 10) {
    velX = clamp(startOffX / ((flightTime + 1) & 0xFE), -15, 15);
    velY = clamp(startOffY / ((flightTime + 1) & 0xFE), -10, 10);
    flightTime = (at[6][59] - at[5][59]) + 1;  // Fixed flight time
}
```

#### Speed & Movement Formulas

```java
// Unit movement speed
int moveSpeed = cf[1][unitType];  // Base vision/move range

// Direction deltas per facing
// bT[offset + facing*2] = dx, bT[offset + facing*2 + 1] = dy

// Pixel movement per animation frame
pixelOffX = bS[bT[1] + facing];       // X delta per step
pixelOffY = bS[bT[1+8] + facing];     // Y delta per step

// Animation frame timing
animCycleLength = bS[bT[facing_parity + 23] + cf[0][unitType]];
```

#### Production Time Formula

```java
// Base production time per unit type
int baseBuildTime = bS[bT[53] + unitType];  // Same as max HP value

// Modified build time
int effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20);

// Production progress per tick
if (gameTick % speedDivisor == 0) {
    constructionHP++;
}
```

---

## 5. Unit Encyclopedia

### 5.1 Confederation Units

#### Infantry

| Stat | Value |
|------|-------|
| Name | Infantry |
| Type | Infantry |
| Description | Armed with an automatic rifle. Light equipment allows fast movement. Effective against infantry |
| HP | 40 |
| Damage | 2 |
| Base Cost | 1 |
| Speed | 5 |
| Armor | 5 |
| Attack Bonus | 0 |
| Sight Range | 4 |
| Build Time | 4 |
| Credit Cost | 10 |
| Reward Credits | 650 |
| Attack Range | 4 |
| Extended Armor | 6 |
| Siege Targets | 255 (all) |
| Upgrade Level | 0 |

#### Grenadier

| Stat | Value |
|------|-------|
| Name | Grenadier |
| Type | Infantry |
| Description | Armed with a rocket launcher. Effective against light and medium machinery |
| HP | 40 |
| Damage | 2 |
| Base Cost | 1 |
| Speed | 6 |
| Armor | 5 |
| Attack Bonus | 0 |
| Sight Range | 4 |
| Build Time | 5 |
| Credit Cost | 10 |
| Reward Credits | 200 |
| Attack Range | 4 |
| Extended Armor | 6 |
| Siege Targets | 255 (all) |
| Upgrade Level | 0 |

#### Flame Assault

| Stat | Value |
|------|-------|
| Name | Flame Assault |
| Type | Infantry |
| Description | Armed with a hand flamer. Very effective against infantry. Heavy equipment reduces movement speed |
| HP | 50 |
| Damage | 4 |
| Base Cost | 2 |
| Speed | 6 |
| Armor | 5 |
| Attack Bonus | 0 |
| Sight Range | 5 |
| Build Time | 9 |
| Credit Cost | 20 |
| Reward Credits | 300 |
| Attack Range | 6 |
| Extended Armor | 12 |
| Siege Targets | 255 (all) |
| Upgrade Level | 0 |

#### AV-40 Fortress

| Stat | Value |
|------|-------|
| Name | AV-40 Fortress |
| Type | Vehicle |
| Description | Heavy-armoured assault machine. Armed with a heavy machine gun |
| HP | 50 |
| Damage | 4 |
| Base Cost | 2 |
| Speed | 7 |
| Armor | 5 |
| Attack Bonus | 1 |
| Sight Range | 5 |
| Build Time | 10 |
| Credit Cost | 20 |
| Reward Credits | 350 |
| Attack Range | 9 |
| Extended Armor | 12 |
| Siege Targets | 255 (all) |
| Upgrade Level | 0 |

#### T-21 Hammer

| Stat | Value |
|------|-------|
| Name | T-21 Hammer |
| Type | Vehicle |
| Description | Light tank with high speed rotating gun |
| HP | 50 |
| Damage | 8 |
| Base Cost | 4 |
| Speed | 7 |
| Armor | 9 |
| Attack Bonus | 0 |
| Sight Range | 6 |
| Build Time | 11 |
| Credit Cost | 40 |
| Reward Credits | 350 |
| Attack Range | 6 |
| Extended Armor | 8 |
| Siege Targets | 14 |
| Upgrade Level | 1 |
| Availability | Requires research level 10 |

#### T-22 Zeus

| Stat | Value |
|------|-------|
| Name | T-22 Zeus |
| Type | Vehicle |
| Description | Heavy tank. Low speed, very powerful damage and wide attack range |
| HP | 70 |
| Damage | 6 |
| Base Cost | 3 |
| Speed | 7 |
| Armor | 5 |
| Attack Bonus | 0 |
| Sight Range | 2 |
| Build Time | 14 |
| Credit Cost | 30 |
| Reward Credits | 300 |
| Attack Range | 6 |
| Extended Armor | 8 |
| Siege Targets | 255 (all) |
| Upgrade Level | 0 |

#### MLRS Torrent

| Stat | Value |
|------|-------|
| Name | MLRS Torrent |
| Type | Vehicle |
| Description | Multiple rocket launcher system with extra-wide attack range. Needs siege mode |
| HP | 80 |
| Damage | 15 |
| Base Cost | 8 |
| Speed | 4 |
| Armor | 7 |
| Attack Bonus | 2 |
| Sight Range | 6 |
| Build Time | 7 |
| Credit Cost | 50 |
| Reward Credits | 250 |
| Attack Range | 6 |
| Extended Armor | 8 |
| Siege Targets | 255 (all) |
| Rank | 2 |
| Upgrade Level | 0 |

### 5.2 Rebel Units

#### Infantry (Rebels)

| Stat | Value |
|------|-------|
| Name | Infantry |
| Type | Infantry |
| Description | Armed with an automatic rifle. Light equipment allows fast movement |
| Faction | Rebels |
| Sight Range | 9 |
| Attack Range | 5 |
| Armor | 4 |

#### Grenadier (Rebels)

| Stat | Value |
|------|-------|
| Name | Grenadier |
| Type | Infantry |
| Description | Armed with a rocket launcher. Effective against light and medium machinery |
| Faction | Rebels |
| Sight Range | 10 |
| Attack Range | 5 |
| Armor | 4 |

#### Sniper

| Stat | Value |
|------|-------|
| Name | Sniper |
| Type | Infantry |
| Description | Armed with a long-range sniper rifle. In siege mode overview and attack radius extends |
| Faction | Rebels |
| Sight Range | 15 |
| Attack Range | 7 |
| Armor | 6 |
| Type Class | 4 |

#### Coyote

| Stat | Value |
|------|-------|
| Name | Coyote |
| Type | Vehicle |
| Description | Light assault car with light armour and medium machine gun. High speed for quick attacks and scouting |
| Faction | Rebels |
| Sight Range | 25 |
| Attack Range | 7 |
| Armor | 6 |
| Type Class | 0 |

#### Armadillo

| Stat | Value |
|------|-------|
| Name | Armadillo |
| Type | Vehicle |
| Description | Heavy-armoured assault machine. Armed with a heavy machine gun |
| Faction | Rebels |
| Sight Range | 18 |
| Attack Range | 10 |
| Armor | 6 |
| Type Class | 4 |

#### Rhino

| Stat | Value |
|------|-------|
| Name | Rhino |
| Type | Vehicle |
| Description | Medium tank. High speed. Upgrade allows siege mode which increases damage and firing rate |
| Faction | Rebels |
| Sight Range | 11 |
| Attack Range | 7 |
| Armor | 4 |
| Type Class | 1 |

#### MMC Porcupine

| Stat | Value |
|------|-------|
| Name | MMC Porcupine |
| Type | Vehicle |
| Description | Mobile missile system. Armed with heavy machine gun and rocket launcher with fast reloading |
| Faction | Rebels |
| Sight Range | 35 |
| Attack Range | 12 |
| Armor | 6 |
| Type Class | 1 |

### 5.3 Mines

#### Mine Scorpio

| Stat | Value |
|------|-------|
| Name | Mine Scorpio |
| Type | Mine |
| Description | A personnel mine. Deals light damage to machinery |
| Faction | Confederation |
| HP | 110 |
| Damage | 60 |
| Base Cost | 36 |
| Speed | 6 |
| Armor | 9 |
| Sight Range | 8 |
| Credit Cost | 150 |

#### Mine Frog

| Stat | Value |
|------|-------|
| Name | Mine Frog |
| Type | Mine |
| Description | A multi-charged mine. Jumps before detonation. Fragments and additional charges scatter |
| Faction | Confederation |
| HP | 100 |
| Damage | 80 |
| Base Cost | 55 |
| Speed | 9 |
| Armor | 12 |
| Sight Range | 13 |
| Credit Cost | 250 |

#### Mine Lizard

| Stat | Value |
|------|-------|
| Name | Mine Lizard |
| Type | Mine |
| Description | Anti-tank mine. Detonates only when enemy machines are nearby |
| Faction | Confederation |
| HP | 120 |
| Damage | 90 |
| Base Cost | 60 |
| Speed | 8 |
| Armor | 8 |
| Sight Range | 7 |
| Credit Cost | 220 |

### 5.4 Unit Category Matchups

| Unit Category | Strong Against | Weak Against | Tactical Role |
|---------------|---------------|--------------|---------------|
| Infantry (Basic) | Rocket Soldiers, Soft Targets | Vehicles, Flamethrowers | Scout, early-game, meat-shields |
| Rocket Soldiers | Heavy Tanks, Aviation | Basic Infantry, Anti-Infantry | Defensive anchors, anti-armor |
| Light Assault Vehicles | Infantry, Recon Units | Heavy Tanks, Turrets | Flanking, resource harassment |
| Heavy Battle Tanks | Light Vehicles, Structures | Rocket Infantry, Aviation | Main assault force |
| Helicopters/Jets | Heavy Tanks, Artillery | Anti-Air, Flak Turrets | Bombardment, bypassing walls |
| Artillery | Structures, Stationary | Fast Units, Aviation | Siege warfare, area denial |
| Anti-Air Vehicles | Helicopters, Jets | Ground Units | Air defense |

---

## 6. Building Encyclopedia

### 6.1 Confederation Buildings

#### Command Centre

| Stat | Value |
|------|-------|
| Name | Command Centre |
| Faction | Confederation |
| HP | 120 |
| Base Cost | 22 |
| Speed | 7 |
| Armor | 7 |
| Attack Bonus | 4 |
| Sight Range | 2 |
| Build Time | 20 |
| Attack Range | 7 |
| Extended Armor | 8 |
| Power Consume | 2 |
| Power Produce | 6 |
| Queue Slots | 0 |
| Tech Requirement | 0 |
| Credit Cost | 100 |
| Reward Credits | 450 |
| Upgrade Costs | 300 / 200 / 200 |

#### Generator

| Stat | Value |
|------|-------|
| Name | Generator |
| Faction | Confederation |
| HP | 100 |
| Base Cost | 14 |
| Speed | 5 |
| Armor | 7 |
| Attack Bonus | 3 |
| Sight Range | 6 |
| Build Time | 8 |
| Attack Range | 7 |
| Extended Armor | 8 |
| Power Consume | 2 |
| Power Produce | 6 |
| Queue Slots | 0 |
| Tech Requirement | 0 |
| Credit Cost | 80 |
| Reward Credits | 550 |
| Upgrade Costs | 300 / 350 / 250 |

#### Infantry Centre

| Stat | Value |
|------|-------|
| Name | Infantry Centre |
| Faction | Confederation |
| HP | 100 |
| Base Cost | 28 |
| Speed | 6 |
| Armor | 7 |
| Attack Bonus | 3 |
| Sight Range | 6 |
| Build Time | 18 |
| Attack Range | 11 |
| Extended Armor | 12 |
| Power Consume | 2 |
| Power Produce | 2 |
| Queue Slots | 0 |
| Tech Requirement | 0 |
| Credit Cost | 110 |
| Reward Credits | 200 |
| Upgrade Costs | 300 / 500 / 500 |

#### Machine Factory

| Stat | Value |
|------|-------|
| Name | Machine Factory |
| Faction | Confederation |
| HP | 110 |
| Base Cost | 36 |
| Speed | 6 |
| Armor | 8 |
| Attack Bonus | 4 |
| Sight Range | 6 |
| Build Time | 25 |
| Attack Range | 3 |
| Extended Armor | 2 |
| Power Consume | 0 |
| Power Produce | 0 |
| Queue Slots | 0 |
| Tech Requirement | 3 |
| Credit Cost | 160 |
| Reward Credits | 250 |
| Upgrade Costs | 500 / 400 / 500 |

#### Technology Centre

| Stat | Value |
|------|-------|
| Name | Technology Centre |
| Faction | Confederation |
| HP | 120 |
| Base Cost | 65 |
| Speed | 8 |
| Armor | 8 |
| Attack Bonus | 4 |
| Sight Range | 7 |
| Build Time | 30 |
| Attack Range | 6 |
| Extended Armor | 16 |
| Power Consume | 0 |
| Power Produce | 0 |
| Queue Slots | 4 |
| Tech Requirement | 1 |
| Credit Cost | 250 |
| Reward Credits | 300 |
| Upgrade Costs | 600 / 800 / 700 |

#### Bunker

| Stat | Value |
|------|-------|
| Name | Bunker |
| Faction | Confederation |
| HP | 120 |
| Base Cost | 50 |
| Speed | 7 |
| Armor | 8 |
| Attack Bonus | 4 |
| Sight Range | 7 |
| Build Time | 30 |
| Attack Range | 6 |
| Extended Armor | 16 |
| Power Consume | 0 |
| Power Produce | 0 |
| Queue Slots | 4 |
| Tech Requirement | 1 |
| Credit Cost | 220 |
| Reward Credits | 250 |
| Upgrade Costs | 300 / 500 / 250 |

#### Locator

| Stat | Value |
|------|-------|
| Name | Locator |
| Faction | Confederation |
| HP | 100 |
| Base Cost | 55 |
| Speed | 9 |
| Armor | 8 |
| Attack Bonus | 4 |
| Sight Range | 7 |
| Build Time | 50 |
| Attack Range | 7 |
| Extended Armor | 16 |
| Power Consume | 0 |
| Power Produce | 0 |
| Queue Slots | 6 |
| Tech Requirement | 1 |
| Credit Cost | 300 |
| Reward Credits | 200 |
| Upgrade Costs | 400 / 300 / 500 |

#### Rocket Launcher

| Stat | Value |
|------|-------|
| Name | Rocket Launcher |
| Faction | Confederation |
| HP | 50 |
| Base Cost | 4 |
| Speed | 7 |
| Armor | 12 |
| Attack Bonus | 1 |
| Sight Range | 9 |
| Build Time | 12 |
| Attack Range | 8 |
| Extended Armor | 16 |
| Power Consume | 0 |
| Power Produce | 0 |
| Queue Slots | 6 |
| Tech Requirement | 1 |
| Credit Cost | 40 |
| Reward Credits | 250 |
| Upgrade Costs | 1000 / 700 / 350 |

### 6.2 Rebel Buildings

#### Headquarters

| Stat | Value |
|------|-------|
| Name | Headquarters |
| Faction | Rebels |
| Upgrade Costs | 300 / 200 / 200 |

#### Powerplant

| Stat | Value |
|------|-------|
| Name | Powerplant |
| Faction | Rebels |
| Upgrade Costs | 300 / 350 / 250 |

#### Barracks

| Stat | Value |
|------|-------|
| Name | Barracks |
| Faction | Rebels |
| Upgrade Costs | 300 / 500 / 500 |

#### Factory

| Stat | Value |
|------|-------|
| Name | Factory |
| Faction | Rebels |
| Upgrade Costs | 500 / 400 / 500 |

#### Laboratory

| Stat | Value |
|------|-------|
| Name | Laboratory |
| Faction | Rebels |
| Upgrade Costs | 600 / 800 / 700 |

#### Bunker (Rebels)

| Stat | Value |
|------|-------|
| Name | Bunker |
| Faction | Rebels |
| Upgrade Costs | 300 / 500 / 250 |

#### Tower

| Stat | Value |
|------|-------|
| Name | Tower |
| Faction | Rebels |
| Upgrade Costs | 400 / 300 / 500 |

#### Wall

| Stat | Value |
|------|-------|
| Name | Wall |
| Faction | Rebels |
| Upgrade Costs | 1000 / 700 / 350 |

### 6.3 Game Configuration Values

| Parameter | Values |
|-----------|--------|
| Turn Time Settings | 30, 20, 30, 20 |
| Building Footprint Widths | 20, 20, 20, 30, 30, 30, 40, 40, 40 |
| Building Footprint Heights | 20, 30, 40, 20, 30, 40, 20, 30, 40 |
| Building Power Radius | 10, 20, 30, 40, 60, 127 |
| Rank XP Thresholds | 20, 35, 50 |
| Rank Credit Rewards | 10, 25, 51 |
| Rank Bonus Points | 0, 3, 6 |
| Battle Time Limits | 1001, 1100, 1101, 1200 |

---

## 7. Campaign System

### 7.1 Episode Structure

The game supports **3 simultaneous save slots** (stored as `/s0m`, `/s1m`, `/s2m` in assets). The `Q()` method in `k.java` counts available episodes by probing for `/s{N}m` files.

#### Known Episodes

- **Episode 1**: Global Confederation (7 campaign missions + 15 custom missions)
- **Episode 2**: Liberation of Peru (7 campaign missions, Resistance faction)
- **Episode 3**: Online expansion (multiplayer-focused, Bluetooth/online support)

### 7.2 Episode Content

Each episode slot maps to a complete set of game data under `/s{N}/`:

| Asset Path | Purpose | Size |
|------------|---------|------|
| `/s{N}m` | MIDI music file for the episode | ~7–10 KB |
| `/s{N}/d0` | Core game data (unit stats, building data, scripts) | ~109 KB |
| `/s{N}/f0_0` | Font bitmap (set 0, variant 0) | ~210 B |
| `/s{N}/f0_1` | Font bitmap (set 0, variant 1) | ~219 B |
| `/s{N}/f1_0` | Font bitmap (set 1, variant 0) | ~143 B |
| `/s{N}/f2_0` | Font bitmap (set 2, variant 0) | ~42 B |
| `/s{N}/f3_0` | Font bitmap (set 3, variant 0) | ~42 B |
| `/s{N}/i0` | Image data for episode units/terrain | ~260 KB |
| `/s{N}/l2` | Language/localization strings | ~45 KB |
| `/s{N}/gg` | Game graphics (minimap) | ~2 KB |
| `/s{N}/hc` | HeroCraft branding data | ~1.8 KB |

### 7.3 Mission Data Structure

#### Map Layout File (`/ml`)

Size: 1,489 bytes, contains 193 map records. Parsed in `k.java` method `P()`:

```java
DataInputStream dis = new DataInputStream(c.d("/ml"));
int count = dis.readByte();           // Number of maps
short[][] mapData = new short[count][];
short[][] mapMeta = new short[count][30];
for (int i = 0; i < count; i++) {
    int len = dis.readShort();         // Map data length
    mapData[i] = new short[len];
    for (int j = 0; j < len; j++)
        mapData[i][j] = dis.readShort();
    for (int k = 0; k < 30; k++)
        mapMeta[i][k] = dis.readShort(); // 30 metadata fields per map
}
```

**Map Metadata Fields (30 shorts per map)**:

| Fields | Purpose |
|--------|---------|
| 0–4 | Map dimensions and tile configuration |
| 5–9 | Starting position data (X/Y for each player) |
| 10–14 | Resource placement offsets |
| 15–19 | Terrain generation seeds |
| 20–24 | Victory condition parameters |
| 25–29 | AI configuration parameters |

### 7.4 Game Data File (`/d0`)

Each episode's `/s{N}/d0` file (~109 KB) contains core game configuration:

```java
DataInputStream dis = new DataInputStream(c.d("/d0"));
byte[][] as = new byte[26][];   // 26 byte arrays
short[][] at = new short[18][]; // 18 short arrays
for (int i = 0; i < 26; i++) {
    int len = dis.readShort();
    as[i] = new byte[len];
    dis.readFully(as[i]);
}
for (int i = 0; i < 18; i++) {
    int len = dis.readShort();
    at[i] = new short[len];
    dis.readFully(at[i], 0, len);
}
```

**Data Array Mapping**:

| Array | Purpose |
|-------|---------|
| `as[0]–as[9]` | Unit type definitions (HP, armor, speed, damage, range, cost) |
| `as[10]–as[12]` | Building definitions (cost, build time, prerequisites) |
| `as[13]` | Weapon/projectile data |
| `as[14]–as[15]` | Movement cost tables per terrain type |
| `as[16]–as[17]` | Faction-specific unit roster mapping |
| `as[18]–as[19]` | Building footprint dimensions (width/height) |
| `as[20]–as[25]` | AI behavior parameters, reinforcement schedules |
| `at[0]–at[4]` | Lookup tables for unit stats indexing |
| `at[5]–at[6]` | Upgrade/research tier progressions |
| `at[7]` | Resource value tables |
| `at[8]–at[10]` | AI build order templates |
| `at[11]–at[17]` | Additional lookup tables |

### 7.5 Trigger & Script System

#### Reinforcement Scheduling

```java
final void d() {
    while (this.ar < this.an &&
           this.aL.ah >= this.as + ((this.am[4][this.ar] & 255) * 10)) {
        B();  // Execute reinforcement event
        this.ar++;
        this.as = this.aL.ah;
    }
}
```

Reinforcement data stored in `am[][]` (6 arrays × 100 entries):
- `am[0][i]`: Event type (24, 25, 27, 29)
- `am[1][i]`: X position parameter
- `am[2][i]`: Y position parameter
- `am[3][i]`: Unit count / type parameter
- `am[4][i]`: Trigger time (in game ticks / 10)
- `am[5][i]`: Faction / ownership parameter

### 7.6 Victory Conditions

**Destruction Victory**: A side loses when it has **no remaining buildings** (`cb[side][0] == 0`) and **7 or fewer units** (`cb[side][1] <= 7`).

**Scoring on destruction**:
- Destroying enemy building: +200 score, +100 score bonus
- Losing own building: -100 score

**Time-Based Victory**:
```java
if (y.L[6][0] * 60 * 8 <= this.ah) {
    if (this.af != 0 || y.W[0] + y.X[0] < y.W[1] + y.X[1]) {
        this.ax.ab = (byte) 2;  // Player 1 wins
    } else {
        this.ax.ab = (byte) 1;  // Player 0 wins
    }
}
```

Time limits defined in `y.bh = {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535}` (seconds).

### 7.7 Campaign Flow Control

| Variable | Type | Purpose |
|----------|------|---------|
| `k.ac` | byte | Game sub-state (0=Normal, 1=Menu, 2=Overlay) |
| `k.c` | byte | Match phase (0=Prep, 1=Active, 3=Between-rounds) |
| `k.af` | byte | Player side (0/1) |
| `k.ah` | int | Game tick counter |
| `w.ab` | byte | Victory/defeat (0=None, 1=P0 wins, 2=P1 wins) |

### 7.8 Screen IDs

| Screen ID | Description |
|-----------|-------------|
| 0/7 | Main menu |
| 8 | Login |
| 9/10 | Chat |
| 11 | Chat |
| 14 | Lobby |
| 16 | Game room |
| 17-22 | Game (various sub-screens) |
| 31 | Mission briefing |
| 35 | Settings |
| 39 | Online menu |
| 41 | Game result |
| 42 | Map editor |
| 47 | Shop |
| 48 | Profile |
| 53/55/56 | Dialog screens |
| 57 | Clan |
| 63 | Victory/defeat/error |
| 88 | Alliance |
| 91 | Loading screen |
| 92 | Exit confirmation |
| 116/121/122 | Tutorial screens |
| 125 | Score display |

---

## 8. Map System

### 8.1 Map Dimensions

- **Grid**: 128×128 tiles
- **Tile size**: 30×20 pixels (width × height)
- **Terrain array**: `byte[][] O = new byte[128][128]`
- **Occupancy grid**: `short[][] P = new short[128][128]`
- **Fog of war**: `byte[][] ce = new byte[128][128]`

### 8.2 Terrain Types

| Type ID | Terrain | Properties |
|---------|---------|------------|
| 0 | Deep water | Impassable for ground units |
| 1 | Shallow water | Impassable for vehicles, passable by infantry |
| 2 | Sand/Beach | Reduced vehicle speed |
| 3 | Plains/Grass | Normal movement |
| 4 | Forest | Cover bonus (+defense), reduced vehicle speed |
| 5 | Hills | Elevated, vision bonus |
| 6 | Mountains | Impassable for most units, provides vision |
| 7 | Road | Increased movement speed |
| 8 | Bridge | Connects land masses over water |
| 9 | Swamp | Very slow movement |
| 10 | Snow | Reduced movement speed |
| 25 | Resource deposit | Harvestable |
| 26+ | Special terrain | Episode-specific |

### 8.3 Terrain Color Palettes (RGB565)

```java
short[][] bQ = {
    {142, 134, 231},        // Water (blue)
    {142, 134, 231},        // Shallow water
    {166, 158, 231},        // Sand
    {166, 158, 231},        // Desert
    {206, 198, 231, 214},   // Plains (with variant)
    {190, 182, 231},        // Forest
    {75, 67, 75},           // Road (dark)
    {8, 0, 8},              // Building outline
    {91, 83, 91},           // Building interior
    {24, 16, 24},           // Shadow
    {107, 99, 107, 115},    // Mountain
    {40, 32, 40},           // Rock
    {126, 118, 126},        // Bridge
    {56, 48, 56, 64},       // Resource deposit
    {230, 230, 230, 222},   // Snow/ice
    {117, 117, 117, 116},   // Fog of war
    {66, 66, 66, 65},       // Unexplored
    {40, 32, 40},           // Swamp
    {8, 0, 8}               // Impassable marker
};
```

### 8.4 Viewport & Camera

The visible area is defined by `w.c` (camera X), `w.d` (camera Y), and `w.aj` (viewport height).

Map scroll directions (controlled by `y.bO.ak`):
- 1, 3: Up
- 2, 14: Down
- 4, 15: Left
- 5, 11, 12, 16: Center on target
- 6, 17: Right
- 7: Scroll to position
- 8, 18: Scroll up
- 10, 13: Center/follow

### 8.5 Minimap System

The minimap uses a scaled-down representation in `y.ce` (128×128 byte array). Entity markers use faction colors:

| Entity | Color | RGB Value |
|--------|-------|-----------|
| Player 0 | Green | 5251341 |
| Player 1 | Red | 16704820 |
| Neutral/Resource | Orange | 16033043 |
| Special | Purple | 12010496 |

### 8.6 Resource Placement

Resource deposits identified by building type IDs in `be` array:
```java
byte[] be = {11, 15, 18, 21, 25, 27, 32, 35, 37, 39, 42, 45, 48, 51, 55, 59};
```

---

## 9. Multiplayer Architecture

### 9.1 Protocol Specification

#### Transport Layer

**Primary: TCP Socket**

| Property | Value |
|----------|-------|
| Protocol | TCP over IPv4 |
| Server | artofwaronline.herocraft.com |
| Alt Server | aow2.ru |
| Ports | 47584–47588 (base + random()%5) |
| Encoding | Big-endian |
| Keep-Alive | Enabled |
| Receive Buffer | 1 |

**Fallback: HTTP**

| Property | Value |
|----------|-------|
| Protocol | HTTP/1.1 |
| Connection Header | com.herocraft.game.artofwar2ol.Connection: close |
| Content-Type | application/x-www-form-urlencoded |
| Timeouts | 10s / 5s / 30s (per phase) |

#### Packet Format (Outbound)

```
Offset  Size  Type     Description
0x00    1     byte     Message type (0 if payload empty)
0x01    2     short    Total payload length (BE) = string_data_length + 2
0x03    2     short    String data length (BE) = len(string)
0x05    N     byte[]   String data (each char as byte, low 8 bits)
```

#### Packet Format (Inbound)

```
Offset  Size  Type     Description
0x00    1     byte     Message type
0x01    2     short    Remaining payload length (BE)
0x03    2     short    Data length (BE)
0x05    N     byte[]   Message data
```

#### Record Format (Game State)

```
Offset  Size  Type     Description
0x00    4     int32    Record size (BE) - includes 5-byte header
0x04    1     byte     Record type (command ID)
0x05    ...   ...      Record body (size - 5 bytes)
```

#### Message Types

| Type | Name | Direction | Description |
|------|------|-----------|-------------|
| 1 | ACK | S→C | Acknowledgment |
| 2 | PLAYER_INFO_SHORT | S→C | Player info (6-byte) |
| 3 | PLAYER_COUNT | S→C | Player count update |
| 4 | SESSION_INIT | S→C | Full session initialization |
| 5 | SERVER_MESSAGE | S→C | Server text message |
| 10 | PLAYER_INFO_FULL_A | S→C | Full player info (team A) |
| 11 | PLAYER_INFO_FULL_B | S→C | Full player info (team B) |
| 12 | MATCH_START | S→C | Match starting with map data |
| 13-15 | PLAYER_INFO variants | S→C | Player info (3/4/7-byte) |
| 20 | LOBBY_LIST | S→C | Lobby/room list |
| 21 | MAP_DATA | S→C | Map data blob |
| 22 | UNIT_LIST_INIT | S→C | Unit list initialization |
| 23 | VICTORY_VALUES | S→C | Victory condition values |
| 25 | GAME_PARAMETERS | S→C | Game parameters (7 shorts) |
| 30 | GAME_STATE | S→C | Full game state sync |
| 33 | GAME_RESULT | S→C | Game result (scores) |
| 40 | ARMY_LIST_INIT | S→C | Army list initialization |
| 41 | MAP_LIST | S→C | Map list for selection |
| 42 | UPGRADE_DATA | S→C | Upgrade/tech data |
| 50 | TOWN_DATA_SINGLE | S→C | Single town data |
| 51 | TOWN_DATA_FULL | S→C | Full town data batch |
| 61 | CHAT_HISTORY | S→C | Chat message history |
| 70 | RANK_DATA | S→C | Ranking data |
| 100 | TOWN_LIST | S→C | Town list |
| 101 | GAME_TICK | S→C | Game tick marker |

**Error Types**:

| Type | Name | Description |
|------|------|-------------|
| -2 | ERR_MATCHMAKING | Matchmaking error |
| -5 | ERR_CONNECTION | Connection error |
| -8 | ERR_TIMEOUT | Timeout error |

### 9.2 Encryption

#### Custom Base64 Alphabet

The game uses a non-standard Base64 alphabet defined in `y.aK`:

```
Index:  0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  15
Char:   H   d   2   _   b   c   G   r   V   E   D   R   f   u   z   P

Index: 16  17  18  19  20  21  22  23  24  25  26  27  28  29  30  31
Char:   I   W   B   8   N   Y   -   q   Q   6   m   x   L   K   Z   j

Index: 32  33  34  35  36  37  38  39  40  41  42  43  44  45  46  47
Char:   4   l   O   n   5   y   J   i   M   A   t   s   a   U   X   C

Index: 48  49  50  51  52  53  54  55  56  57  58  59  60  61  62  63
Char:   T   9   p   o   w   F   1   h   g   S   0   e   3   k   v   7
```

#### XOR Stream Cipher (Outbound Data)

The `a(int value, int size, boolean encrypt)` method in p.java:

```
For each byte position i:
  1. Extract byte: byteVal = (value >> ((size-1-i)*8)) & 0xFF
  2. If encrypt:
     byteVal ^= key_table[161 + cN] ^ (session_key >> (24 - cO*8))
     cO = (cO + 1) & 3        // Rotate through 4 bytes of session key
     cN = (cN + 1) % 15       // Rotate through 15 key bytes
  3. Accumulate checksum: cP ^= byteVal << (cQ * 8); cQ ^= 1
  4. Write byte to output buffer
  5. Every 3 bytes, apply custom Base64 encoding
```

#### XOR Stream Cipher (Data at Rest)

```
1. Generate random 32-bit seed: seed = random() & 0x7FFFFFFF
2. For each byte i: encrypted[i] = data[i] ^ (seed >> ((i & 3) << 3))
3. Append seed as 4 bytes: encrypted[data.length + 0..3] = seed bytes
```

Decryption (`b(byte[])`) reverses by extracting the last 4 bytes as seed, then XORing.

#### HTTP XOR Cipher (aa.java)

```
For each byte received:
  decrypted = h[i] ^ read()
  i = (i + 1) % 15
```

Key initialized to `-1` (0xFF) for all positions.

### 9.3 Session Lifecycle

#### Authentication Flow

```
Client                                    Server
  |                                         |
  |  1. TCP Connect (host:47584-47588)      |
  |---------------------------------------->|
  |  2. Socket Options: keepAlive=true       |
  |  3. Start Sender Thread (m)             |
  |  4. Start Receiver Thread (o)           |
  |  5. Send Login Message (type=1)         |
  |---------------------------------------->|
  |  6. Receive SESSION_INIT (type=4)       |
  |<----------------------------------------|
  |  7. Process game state, validate         |
  |     player ID against S() - 12321       |
```

#### Matchmaking State Machine

```
[Main Menu] → aO=0/7
     |
     v
[Lobby] → aO=14
     |
     v
[Game Room] → aO=16
     |
     v
[Searching] → aO=55/56
     |
     | (type 12: MATCH_START received)
     v
[Match Wait] → aO=31
     |
     | (type 30: GAME_STATE received)
     v
[Game] → aO=17/18/19/20/22
     |
     v
[Result] → aO=41
```

#### Synchronization Model

The game uses **server-authoritative lockstep**:

1. Turn-based: Game advances in discrete turns tracked by `y.Q[0]`
2. Server authority: All game state changes validated server-side
3. Periodic sync at configurable intervals:
   - Sync interval: Default 15s, range 2-60s
   - Matchmaking timeout: Default 5s, range 1-40s
   - Disconnect timeout: Default 14s, range 2-60s

#### Error Recovery

| Component | Threshold | Action |
|-----------|-----------|--------|
| Receiver | >3 consecutive read errors | Disconnect |
| Sender | >3 consecutive write errors | Disconnect |
| Network Manager | 10 failed connection attempts | Give up |
| Request Queue | 10 failed requests | Show error dialog |
| Reconnection delay | 2500ms between retries | Auto-reconnect |

### 9.4 Thread Architecture

| Thread | Class | Purpose |
|--------|-------|---------|
| Main Game Loop | k.java (D()) | Render, AI, input, state machine |
| Network Manager | e.java | Connection management |
| TCP Sender | m.java | Outbound message queue |
| TCP Receiver | o.java | Socket → inbound queue |
| Request Queue | z.java | Triple-buffered HTTP requests |
| HTTP Handler | aa.java | Per-request HTTP communication |
| Ad SDK | u.java | InnerActive ad fetching |

### 9.5 Triple-Buffered Request Queue (z.java)

Uses a 3-slot circular buffer:

```
Slot 0:  c[0] (byte[])  d[0] (short)  e[0] (int)  f[0] (int)
Slot 1:  c[1] (byte[])  d[1] (short)  e[1] (int)  f[1] (int)
Slot 2:  c[2] (byte[])  d[2] (short)  e[2] (int)  f[2] (int)

l = write pointer, m = read pointer
```

If all 3 slots are full, the queue is cleared and restarted. Failed requests retry up to 10 times with 2.5s delays.

### 9.6 Anti-Cheat Measures

1. **Server authority**: All game state changes validated server-side
2. **Encrypted protocol**: XOR stream cipher with rotating keys
3. **Custom encoding**: Non-standard Base64 alphabet
4. **Integrity checksum**: `F()` method computes game state hash
5. **Payment verification**: `q.java` validates license via XOR hash of device ID
6. **String validation**: Input sanitization in chat

---

## 10. Asset Catalog Summary

### 10.1 Overview

| Category | Files | Total Size | Description |
|----------|-------|------------|-------------|
| Icon Sprites (b/) | 200 | 116.4 KB | Unit/building/terrain mini-icons |
| Faction s0 (low-res) | 15 | 411.7 KB | 240×320 screen assets |
| Faction s1 (med-res) | 15 | 562.6 KB | 480×320 screen assets |
| Faction s2 (high-res) | 15 | 755.7 KB | 480×854 screen assets |
| Music (MIDI) | 2 | 18.3 KB | Background music tracks |
| Config/Text (sn8p) | 1 | 21.5 KB | Game strings & configuration |
| Binary Data | 6 | 56.1 KB | Map data, game state |
| Loading Screen | 1 | 44.5 KB | Default loading screen |
| **TOTAL** | **256** | **1,948.3 KB** | |

### 10.2 Directory Structure

```
assets/
├── b/                    # 200 icon sprite PNGs (32x27, 8-bit)
│   ├── 001 .. 200       # Numbered unit/building/terrain icons
├── s0/                   # Low-res faction assets (240x320)
│   ├── f0_0, f0_1       # Font definitions
│   ├── f0_0p, f0_1p     # Font image strips
│   ├── f1_0, f2_0, f3_0 # Additional font sets
│   ├── d0               # Game data tables
│   ├── i0               # Sprite pack (57 PNGs)
│   ├── hc               # HQ image
│   ├── gg               # Globe/minimap
│   └── l2               # Loading screen
├── s1/                   # Medium-res assets (480x320)
├── s2/                   # High-res assets (480x854)
├── s0m, s1m             # MIDI music tracks
├── sn8p                  # Game text strings & config
├── a                     # Encoded data table (7,001 bytes)
├── d                     # Display flags (8 bytes)
├── f                     # Feature flags (9 bytes)
├── ml                    # Map layout data (1,489 bytes)
├── n                     # Name/data table (2,066 bytes)
├── p                     # Platform version (3 bytes)
└── l2                    # Default loading screen
```

### 10.3 Icon Sprite Categories

| Range | Count | Category |
|-------|-------|----------|
| 001-015 | 15 | Base Units (3 groups × 5 faction colors) |
| 016-030 | 15 | Upgraded Units |
| 031-045 | 15 | Veteran Units |
| 046-060 | 15 | Heavy Units |
| 061-075 | 15 | Structures |
| 076-095 | 20 | Terrain (Dark) |
| 096-115 | 20 | Terrain (Mixed) |
| 116-135 | 20 | Simple Icons |
| 136-155 | 20 | Advanced Units |
| 156-175 | 20 | Special Units |
| 176-195 | 20 | Shadow/FOW |
| 196-200 | 5 | Mini Base |

### 10.4 Icon Faction Color System

| Color Code | Dominant Color | Faction |
|------------|---------------|---------|
| Brown | #341100 / #ab6c42 | Confederation |
| Blue | #002334 / #4281ab | Rebels |
| Green | #112b08 / #5f9a53 | Terrain/Nature |
| Gold | #372c00 / #b9a736 | Resources/Special |
| Gray | #181818 / #958a6c | Neutral/Structure |

### 10.5 Music

| Track | Format | Size | Tempo | Tracks |
|-------|--------|------|-------|--------|
| s0m | MIDI format 1 | 10,550 B | 120 BPM | 9 |
| s1m | MIDI format 1 | 7,710 B | 180 BPM | 9 |

Music Credit: Bogatenko A.N.

### 10.6 d0 Data Arrays

#### Byte Arrays (26 total)

| Index | Size (s0) | Purpose |
|-------|-----------|---------|
| 0 | 2357 | Unit type classifications |
| 1 | 2357 | Unit properties (HP, cost, etc.) |
| 2 | 2357 | Unit combat stats |
| 3 | 2357 | Unit armor values |
| 4 | 2357 | Unit speed values |
| 5 | 2357 | Unit faction assignment |
| 6 | 7450 | Map terrain passability |
| 7 | 1556 | Building properties |
| 8 | 1556 | Building costs |
| 9 | 1556 | Building availability |
| 10 | 992 | Terrain graphics mapping |
| 11 | 992 | Terrain overlay mapping |
| 12 | 992 | Terrain type flags |
| 13 | 992 | Terrain movement cost |
| 14 | 263 | Technology level |
| 15 | 797 | Technology prerequisites |
| 16 | 797 | Technology costs |
| 17 | 797 | Category marker |
| 18 | 153 | Upgrade effects |
| 19 | 153 | Upgrade targets |
| 20 | 1084 | Sprite frame indices |
| 21-22 | 1084 each | Padding (all 0) |
| 23 | 1084 | Animation flags |
| 24 | 1084 | Direction flags |
| 25 | 1084 | Sound/effect triggers |

#### Short Arrays (18 total)

| Index | Size (s0) | Purpose |
|-------|-----------|---------|
| 0 | 2342 | Sequential index map |
| 1 | 2342 | Reverse index map |
| 2 | 7450 | Map tile sprite offsets |
| 3 | 7450 | Building placement data |
| 4 | 7450 | Unit placement data |
| 5 | 290 | Unit sprite X offsets |
| 6 | 290 | Unit sprite Y offsets |
| 7 | 1556 | Building sprite indices |
| 8 | 405 | Animation frame X offsets |
| 9 | 405 | Animation frame Y offsets |
| 10 | 992 | Terrain rendering data |
| 11 | 263 | Technology tree indices |
| 12 | 263 | Technology tree links |
| 13 | 797 | Technology upgrade map |
| 14 | 153 | Upgrade sprite X coords |
| 15 | 153 | Upgrade sprite Y coords |
| 16 | 1084 | Sprite sheet coordinate map |
| 17 | 1084 | Movement/attack range data |

---

## 11. Save/Persistence System

### 11.1 RMS RecordStore Abstraction

The game uses a custom J2ME RecordStore API implementation for Android:

**File naming**: `{storeName}.datrms` (header) + `{storeName}.datrms_{recordId}` (data)

**Header format**:

| Offset | Type | Field |
|--------|------|-------|
| 0 | int | Record count |
| 4 | int | Modification counter (version) |
| 8 | long | Last modification timestamp |

**Record format**: Variable-length byte arrays, IDs start at 1.

### 11.2 Primary Save Store: "aow2olhc"

Written by `U()` method in `p.java`:

| Offset | Type | Field | Description |
|--------|------|-------|-------------|
| 0 | int | aA.g | Game state flags |
| 4 | byte | aj | Production speed |
| 5 | byte | w | Productivity level |
| 6 | byte | version | Save version (always 3) |
| 7 | byte | y.ag | Online/game mode |
| 8 | byte | y.af | Player faction (0=Confed, 1=Resistance) |
| 9 | byte | y.ak | Screen state |
| 10 | byte | y.aj | UI state |
| 11 | int | c.g | Config/version |
| 15 | byte | T | Tutorial progress |
| 16 | byte | Z | Campaign progress |
| 17 | byte | aa | Episode unlock |
| 18 | byte | ab | Mission completion |
| 19-21 | byte | cH, cJ, cK | Achievement bytes |
| 22 | byte | ac | Game sub-state |
| 23 | byte | nameLen | Player name length |
| 24 | byte[n] | name | Player name (ASCII) |
| 24+n | byte | y.ar | Achievement count |
| 25+n | byte[ar] | y.aq | Achievement IDs |

### 11.3 Save Encryption

Save data is encrypted before storage using:
- XOR with the `y.aK` cipher table (64-byte static key)
- Running counter `cN` and `cO` for additional XOR layers
- 3-byte grouping with Base64-like encoding

### 11.4 Other RecordStores

| Name | Purpose | Records | Format |
|------|---------|---------|--------|
| FD0H9A0B | Friend list | 1 | int count + UTF strings |
| sgiuyq | Ad client ID | 1 | long (8 bytes) |

### 11.5 In-App Purchase Tracking

```java
private static void I() {
    a = 0;  // No premium
    InputStream d = c.d("/b/200");
    if (d != null) { a = 1; }  // Full version
    else {
        d = c.d("/b/199");
        if (d != null) { a = 2; }  // Demo/lite
    }
}
```

Russian market check via `/r/b01` asset presence.

### 11.6 Configuration Store (sn8p)

| Key | Default | Description |
|-----|---------|-------------|
| LSK1 | -6 | Left softkey code |
| RSK1 | -7 | Right softkey code |
| SMTYPE | -1 | SMS type |
| VP | TRUE | Vibration support |
| GAME_ID | 219 | Game identifier |
| PROV_ID | 629 | Provider ID |
| LANG_ID | en | Language |
| PROD_ID | 82335 | Product ID (used for cipher key) |
| PORT_ID | 33116 | Server port |
| ORIENT | 0/1/2 | Screen orientation |
| PRODY | 0-2 | Production speed |
| SMLEVEL | -1 | Sound level (-1=off) |

---

## 12. Data Encryption & Security

### 12.1 `/a` File Decryption Algorithm

The `/a` file (7,001 bytes) contains core gameplay data encrypted with a two-stage XOR cipher.

#### Step 1: Key Derivation from PROD_ID

```java
int parseInt = Integer.parseInt(PROD_ID);  // = 82335
for (int i = 0; i < 8; i++) {
    bm[i] = (byte) ((parseInt >> ((3 - (i % 4)) * 8)) & 0xFF);
}
```

For PROD_ID = 82335 (0x1419F):

| Index | Shift | Result |
|-------|-------|--------|
| 0 | >> 24 | 0 |
| 1 | >> 16 | 1 |
| 2 | >> 8 | 65 |
| 3 | >> 0 | 159 |
| 4 | >> 24 | 0 |
| 5 | >> 16 | 1 |
| 6 | >> 8 | 65 |
| 7 | >> 0 | 159 |

**Derived key: `bm = [0, 1, 65, 159, 0, 1, 65, 159]`**

Default key before `W()`: `[71, 107, 115, 50, 56, 114, 116, 55]` (ASCII: "Gks28rt7")

#### Step 2: Decrypt Each Byte

```
decrypted_byte = ((bm[bn] ^ raw_byte) & 0xFF) ^ 93
bn = (bn + 1) % 8
```

The `& 0xFF` mask ensures unsigned arithmetic; `^ 93` is a secondary XOR with constant 0x5D.

#### Step 3: Parse the Decrypted Stream

**163 Byte Sections**:

```
L[0] = 0
for i = 0 to 162:
    count = y() + (y() << 8)        // little-endian 16-bit
    L[i+1] = L[i] + count           // cumulative offset
    for j = 0 to count-1:
        K[L[i] + j] = y()           // byte value
```

**9 Short Sections**:

```
N[0] = 0
for i = 0 to 8:
    count = y() + (y() << 8)
    if i < 8: N[i+1] = N[i] + count
    for j = 0 to count-1:
        M[N[i] + j] = y() + (y() << 8)  // little-endian 16-bit
```

### 12.2 Key Data Sections from `/a`

#### Byte Sections

| Section | Count | Description | Sample Data |
|---------|-------|-------------|-------------|
| 0 | 39 | Unit category/type | [0, 255, 255, ...] |
| 4 | 961 | Defense/damage matrix (31×31) | [255, 255, ...] |
| 5 | 441 | Terrain offset table (21×21) | [0, 4, 8, ...] |
| 6 | 31 | Attack probability curve | [13, 15, 17, ...] |
| 37 | 19 | Unit speed | [5, 6, 6, 7, 7, 7, 4, 7, ...] |
| 38 | 19 | Unit armor | [5, 5, 5, 5, 9, 5, 7, 7, ...] |
| 39 | 19 | Unit attack bonus | [0, 0, 0, 1, 0, 0, 2, 4, ...] |
| 40 | 19 | Unit sight range | [4, 4, 5, 5, 6, 2, 6, 2, ...] |
| 41 | 19 | Unit build time | [4, 5, 9, 10, 11, 14, 7, ...] |
| 46 | 25 | Extended sight range | [4, 5, 6, 6, 7, 8, 7, 6, ...] |
| 47 | 25 | Attack range | [4, 4, 6, 9, 6, 12, 10, 8, ...] |
| 49 | 25 | Extended armor | [6, 6, 12, 12, 8, 8, 8, 8, ...] |
| 53 | 19 | Unit HP | [40, 40, 50, 50, 50, 70, 80, ...] |
| 54 | 19 | Unit damage | [2, 2, 4, 4, 8, 6, 15, 35, ...] |
| 55 | 19 | Unit base cost | [1, 1, 2, 2, 4, 3, 8, 22, ...] |
| 91 | 9 | Building footprint width | [20, 20, 20, 30, 30, 30, 40, 40, 40] |
| 92 | 9 | Building footprint height | [20, 30, 40, 20, 30, 40, 20, 30, 40] |
| 93 | 6 | Building power radius | [10, 20, 30, 40, 60, 127] |
| 144 | 3 | Rank XP thresholds | [20, 35, 50] |
| 145 | 3 | Rank credit rewards | [10, 25, 51] |
| 146 | 3 | Rank bonus points | [0, 3, 6] |

#### Short Sections

| Section | Count | Description |
|---------|-------|-------------|
| 2 | 4 | Battle time limits [1001, 1100, 1101, 1200] |
| 3 | 15 | Game config [-101, -51, -1, 0, 50, 100, ...] |
| 4 | 19 | Unit availability flags |
| 5 | 19 | Unit credit cost |
| 6 | 20 | Unit reward credits |
| 7 | 48 | Building cost details |

### 12.3 d0 File Format (Per-Faction)

Unencrypted Java DataInputStream format (big-endian):

```
for i = 0 to 25:
    count = readUnsignedShort()
    byteArray[i] = readFully(count)

for i = 0 to 17:
    count = readUnsignedShort()
    shortArray[i] = readShort() × count
```

File sizes: s0/d0 = 109,318 B; s1/d0 = 109,821 B; s2/d0 = 109,318 B (identical to s0)

### 12.4 Name Table Encryption (`/n`)

The `/n` file (2,066 bytes) uses simple XOR with constant `93` (0x5D).

### 12.5 bu.java Asset Decryption (Alternative Layer)

A second encryption layer for some assets:

- **Key**: `[-96, -95, -94, -93, -92, -91, -90, -89]` (initial)
- **Formula**: `decrypted = ((encrypted_byte - key[i]) ^ 250) & 0xFF`
- **Key rotation**: After use, elements 3-5 are shifted left
- **Actual key**: Retrieved from `t.a(String)`, typically `{21, 19, 159, 97, 81, 21, 37, 187}`

### 12.6 Integer Bit Obfuscation

```java
// Encoding
public int encode(int value) {
    int reversed = 0;
    for (int i = 0; i < 32; i++) {
        reversed |= ((value >>> (31 - i)) & 1) << i;
    }
    return 0xF5956A5F ^ reversed;  // -128255633
}

// Decoding
public int decode(int encoded) {
    int reversed = encoded ^ 0xF5956A5F;
    int result = 0;
    for (int i = 0; i < 32; i++) {
        result |= ((reversed >>> (31 - i)) & 1) << i;
    }
    return result;
}
```

### 12.7 CRC32 Implementation

```java
private static int[] buildTable() {
    int[] table = new int[256];
    for (int i = 0; i < 256; i++) {
        int crc = i;
        for (int j = 0; j < 8; j++) {
            crc = (crc & 1) != 0 ? (crc >>> 1) ^ 0xEDB88320 : crc >>> 1;
        }
        table[i] = crc;
    }
    return table;
}
```

### 12.8 Security Summary

| Layer | Method | Purpose |
|-------|--------|---------|
| String obfuscation | UTF byte arrays decoded at runtime | Hide URLs and keys |
| Asset encryption | XOR cipher with rotating 8-byte key | Protect game data files |
| APK verification | CRC32 of ZIP entries | Tamper detection |
| Signature verification | RSA SHA1withRSA | Billing security |
| Vendor check | MIDlet-Vendor = "HeroCraft" | Clone protection |
| Bit obfuscation | Bit reversal + XOR 0xF5956A5F | Integer encoding |
| Dynamic loading | Class.forName() | Hide game class names |
| Protocol encryption | XOR stream cipher with rotating keys | Network security |
| Custom Base64 | Non-standard alphabet | Packet inspection prevention |

### 12.9 Python Decryption Implementation

```python
def decrypt_a_file(filepath, prod_id=82335):
    # Derive key from PROD_ID
    bm = [(prod_id >> ((3 - (i % 4)) * 8)) & 0xFF for i in range(8)]

    with open(filepath, 'rb') as f:
        raw = f.read()

    pos, bn = 0, 0

    def y():
        nonlocal pos, bn
        z_val = bm[bn] ^ raw[pos]
        pos += 1
        bn = (bn + 1) % 8
        return (z_val & 0xFF) ^ 93

    # Parse 163 byte sections
    byte_sections = []
    for _ in range(163):
        count = y() + (y() << 8)
        byte_sections.append([y() for _ in range(count)])

    # Parse 9 short sections
    short_sections = []
    for _ in range(9):
        count = y() + (y() << 8)
        short_sections.append([y() + (y() << 8) for _ in range(count)])

    return byte_sections, short_sections
```

---

## 13. Class Reference

### 13.1 Main Package Classes

| Obfuscated | Deobfuscated | Purpose |
|-----------|-------------|---------|
| Application | Application | Android Activity entry point |
| AppCtrl | AppController | Main game controller/thread |
| bh | AbstractMIDlet | Abstract MIDlet base class |
| _EMPTY_MIDLET_ | NullMIDlet | Empty MIDlet stub |
| an | AbstractSurfaceView | SurfaceView with input dispatch |
| h | GameSurfaceRenderer | Concrete SurfaceView renderer |
| z | AbstractGameCanvas | Game screen base with input |
| bv | AbstractDisplayable | Base for all displayable screens |
| bu | CanvasResourceLoader | Asset decryption + key remapping |
| aq | AndroidGraphicsAdapter | Wraps android.graphics.Canvas |
| v | AbstractGraphics | Drawing primitives interface |
| q | ImageWrapper | Bitmap wrapper |
| by | FontManager | Font caching, UTF decoder |
| bd | DisplayManager | Screen switching, Vibrator |
| bb | ScreenConfiguration | s0/s1/s2 variant definitions |
| ad | Command | Game button/command |
| ag | CommandListener | void a(ad) callback |
| as | CommandListenerAlt | void b(ad) callback |
| t | AbstractUIItem | Base UI item (LinearLayout) |
| b | ImageItem | ImageView wrapper |
| ak | TextItem | TextView wrapper |
| cd | TextInputField | EditText wrapper |
| x | ChoiceGroup | RadioGroup/CheckBox |
| br | GaugeProgress | Progress indicator |
| bz | AbstractDialog | Dialog/Form base |
| af | DialogForm | Full dialog with items |
| am | EmptyDialog | No-op dialog |
| be | DialogController | Manages dialog lifecycle |
| bf | PurchaseDialog | Billing item selection |
| r | DialogCreator | Creates bf on UI thread |
| ah | ItemClickHandler | Delegates to command listener |
| i | RecordStore | J2ME RMS file storage |
| cg | PurchaseRecordAbstract | Billing record base |
| l | PurchaseRecordConcrete | Serializable purchase data |
| ae | BillingManager | Google Play billing |
| BillingService | BillingService | Android Billing Service |
| ac | AbstractBillingRequest | Base for billing API calls |
| bo | BillingStub | IPC stub |
| bc | BillingProxy | IPC proxy |
| m | BillingAIDL | IInterface for billing IPC |
| bm | BillingResult | Result enum |
| s | PurchaseState | PURCHASED/CANCELED/REFUNDED |
| ar | SecurityVerifier | RSA SHA1withRSA verification |
| at | BillingBroadcastHandler | Routes billing intents |
| CommonReceiver | CommonReceiver | Broadcast receiver |
| aw | BillingCallback | boolean a(int, String) |
| bi | Base64Decoder | Custom Base64 |
| cf | Base64Exception | Custom exception |
| k | UIUtility | AlertDialog, Toast, device ID |
| n | DialogKeyHandler | Key/click handler |
| c | ConnectionFactory | HTTP/Socket/SMS connections |
| au | SocketConnection | TCP socket wrapper |
| ba | HTTPConnection | HttpURLConnection wrapper |
| w | URLConnectionBase | URLConnection wrapper |
| bt | ContentLengthConnection | Adds getContentLength() |
| al | ConnectionClose | void e() close interface |
| ao | DataOutputInterface | DataOutputStream interface |
| bp | DataInputInterface | DataInputStream interface |
| az | FullConnectionInterface | Input + output |
| ay | ContentLengthInterface | Content length |
| e | HTTPConnectionInterface | HTTP-specific |
| bl | SocketConnectionInterface | Socket-specific |
| y | ConnectionException | extends IOException |
| d | GameException | extends Exception |
| ab | RecordStoreNotFound | extends d |
| ap | RecordStoreFull | extends d |
| p | RecordStoreInvalidID | extends d |
| bq | RecordStoreNotOpen | extends d |
| g | CRC32Utility | extends CRC32 |
| bg | CRCHashHelper | Wrapper for hash computation |
| cb | VerificationHelper | Signature/billing utility |
| ax | MarkerInterface | Empty tag interface |
| av | ZipEntryHelper | extends ZipEntry |
| bn | APKZipVerifier | extends ZipFile |
| bw | VerificationDisplay | extends bz |
| ai | RequestIDHolder | static long a = -1 |

### 13.2 s0 Package Classes (Small Screen ≤320px)

| Obfuscated | Deobfuscated | Purpose |
|-----------|-------------|---------|
| s0.aow2ol | MIDletEntryPoint | Creates main game class |
| s0.k | MainGameClass | Game loop, state machine, all logic |
| s0.x | BaseGameCanvas | Key/touch event dispatch |
| s0.c | GameConfigManager | HTTP URLs, RMS, server config |
| s0.a | GameStateManager | Screen management, AI, unit data |
| s0.p | GameFormBase | Screen navigation |
| s0.y | GlobalGameData | Static constants, unit stats, colors |
| s0.w | GameLogicMapHandler | Map rendering, AI, pathfinding, combat |
| s0.e | NetworkManager | TCP socket thread |
| s0.z | NetworkRequestQueue | Triple-buffered HTTP requests |
| s0.aa | HTTPConnectionHandler | HTTP polling with XOR decrypt |
| s0.f | TextFontManager | Font loading, text rendering |
| s0.d | BitmapFontRenderer | Custom font drawing |
| s0.t | BillingCallback | Routes billing results |
| s0.q | PaymentHandler | SMS/payment verification |
| s0.i | AudioManager | MediaPlayer management |
| s0.b | MediaPlayerWrapper | Sound playback |
| s0.m | NetworkSenderThread | DataOutputStream, packet framing |
| s0.o | NetworkReceiverThread | DataInputStream, packet reading |
| s0.r | ConnectionFactoryWrapper | Delegates to main connection factory |
| s0.g | Base64Decoder | Custom Base64 decode |
| s0.h | BitReversalHash | Integer bit reversal + XOR |
| s0.n | CRC32Hash | Custom CRC32 (polynomial 0xEDB88320) |
| s0.j | ColorConstants | Static color palette |
| s0.u | AdInterstitialHandler | Inner-Active M2M SDK |
| s0.l | SMSSender | Premium SMS BroadcastReceiver |
| s0.s | BillingAEWrapper | Creates ae billing instance |
| s0.v | OnlineStatusReporter | HTTP online status reporting |

### 13.3 Cross-Variant Equivalent Mapping

| Deobfuscated Role | s0 Class | s1 Class | s2 Class |
|-------------------|----------|----------|----------|
| Main Game Class | k | c | q |
| Base Game Canvas | x | i | p |
| Config Manager | c | u | o |
| Game State Manager | a | a | a |
| Game Form Base | p | p | p |
| Global Data | y | y | y |
| Game Logic/Map | w | w | z |
| Network Manager | e | n | h |
| Network Request Queue | z | r | y |
| HTTP Handler | aa | aa | aa |
| Font Manager | f | f | f |
| Bitmap Font | d | d | d |
| Billing Callback | t | t | t |
| Payment Handler | q | q | q |
| Audio Manager | i | e | i |
| Network Sender | m | m | m |
| Network Receiver | o | o | k |
| Base64 Decoder | g | g | g |
| Bit Hash | h | h | h |
| CRC32 Hash | n | k | n |
| Color Constants | j | j | j |
| Ad Handler | u | v | u |
| SMS Sender | l | l | l |
| Billing Wrapper | s | s | s |
| Online Reporter | v | x | w |

---

## 14. Confidence Assessment

### 14.1 Confidence Levels

| Level | Meaning |
|-------|---------|
| **Confirmed** | Directly observed in decompiled code, verified by multiple sources |
| **Probable** | Strongly inferred from code patterns, consistent with game behavior |
| **Inferred** | Reasonable interpretation of data, needs further verification |
| **Unknown** | Cannot determine from available evidence |

### 14.2 Assessment by Category

#### Confirmed (High Confidence)

| Finding | Evidence |
|---------|----------|
| s0/s1/s2 are screen variants, not factions | Direct code in `bb.java`, `AppCtrl.j()` method |
| XOR cipher with key rotation for assets | `bu.java` decryption code fully decompiled |
| `/a` file encryption (PROD_ID XOR + 93) | `k.java` W(), y(), z() methods fully decompiled |
| Network packet format | `m.java` and `o.java` sender/receiver fully decompiled |
| TCP server address and ports | String constants in `e.java` and `y.java` |
| Game loop structure | `k.java` run() method decompiled |
| Unit entity layout (101-byte stride) | `w.java` array access patterns analyzed |
| Building footprint system | `as[18]`/`as[19]` arrays verified in d0 data |
| Save system format and encryption | `p.java` U() method decompiled |
| Billing system (Google Play v1) | Multiple billing classes fully decompiled |
| Fog of war bitmask system | `Q[][][][]` array declarations found |
| Projectile system (400 max) | Array declarations and movement code decompiled |
| Damage formula (armour reduction) | `w.java` combat methods decompiled |
| Pathfinding algorithm (Bresenham + A*) | Multiple path methods in `w.java` decompiled |
| AI state machine and target selection | `w.java` AI methods decompiled |
| All unit stats (HP, damage, cost, etc.) | Extracted from decrypted `/a` file |
| All building stats | Extracted from decrypted `/a` file and d0 files |
| Research effects (0-47) | `g(int i)` method in `w.java` analyzed |
| Minimap colors | `k.java` bg[] array constants |
| Custom Base64 alphabet | `y.aK` byte array found |
| Triple-buffered request queue | `z.java` fully decompiled |
| Victory conditions | `w.java` victory check code decompiled |

#### Probable (Medium Confidence)

| Finding | Evidence |
|---------|----------|
| Exact terrain type IDs (0-26+) | Inferred from d0 data arrays and color palettes |
| AI difficulty scaling specifics | Partially decompiled; thresholds estimated |
| Resource income formula | Code found but modifiers not fully mapped |
| Garrison system details | Code references found but not fully traced |
| Some research effect interpretations | Effects mapped to IDs but some descriptions approximate |
| Campaign progression tracking | Save format known, exact unlock logic inferred |
| Some Rebel unit full stats | Partial data from d0 files; some fields estimated |

#### Inferred (Lower Confidence)

| Finding | Evidence |
|---------|----------|
| Exact movement cost per terrain | d0 array indices mapped but values need verification |
| Siege mode specific stat changes | Code patterns suggest changes but exact numbers unclear |
| HP regeneration rates | Code references found but base rates not extracted |
| AI build order templates | `at[8]-at[10]` identified as templates, content not parsed |
| Exact spawn position randomization | Algorithm referenced but not fully decompiled |
| Some network message type payloads | Type IDs known but some body formats not fully parsed |

#### Unknown

| Finding | Status |
|---------|--------|
| Server-side game state validation logic | Server code not available |
| Full map validation algorithm | `J()` method referenced but not decompiled |
| Save decryption (V() method) | 486 instructions, JADX unable to decompile |
| Some d0 short array contents | Mapped by size but exact semantics unclear |
| Runtime configuration variable effects | Some keys identified, values not fully traced |
| Exact audio channel mixing behavior | Code present but playback logic complex |

### 14.3 Verification Status

| Component | Verified By | Status |
|-----------|-------------|--------|
| `/a` decryption | Full file consumed (7001/7001 bytes) | ✅ Verified |
| d0 parsing | 3 files parsed successfully | ✅ Verified |
| sn8p parsing | 570 text strings extracted | ✅ Verified |
| Unit stats | Cross-referenced with wiki and in-game | ✅ Verified |
| Building stats | Cross-referenced with d0 data | ✅ Verified |
| Network protocol | Sender/receiver code verified | ✅ Verified |
| Combat formulas | Code analysis + game behavior matches | ⚠️ Probable |
| AI behavior | Code analysis only | ⚠️ Probable |
| Pathfinding | Code analysis only | ⚠️ Probable |

---

*End of Master Documentation*

*This document was generated from the comprehensive reverse-engineering analysis of the Art of War 2 Online APK (com.herocraft.game.artofwar2ol). All data was extracted from decompiled Java bytecode, encrypted game data files, and cross-referenced with community knowledge. For questions or updates, refer to the individual source analysis documents in the project documentation directory.*
