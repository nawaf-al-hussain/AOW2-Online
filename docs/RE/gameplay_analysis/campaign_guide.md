# Art of War 2 Online — Campaign & Mission System Documentation

**Source Analysis**: Decompiled APK (com.herocraft.game.artofwar2ol)  
**Date**: 2026-03-05  
**Key Files**: `s0/k.java` (game engine), `s0/w.java` (game logic), `s0/a.java` (screen/UI), `s0/y.java` (constants), `s0/p.java` (save/load)

---

## 1. Campaign Architecture Overview

### 1.1 Episode Structure

The game supports **3 simultaneous save slots** (stored as `/s0m`, `/s1m`, `/s2m` in assets). The `Q()` method in `k.java` counts available episodes by probing for `/s{N}m` files:

```java
private static byte Q() {
    int i = 0;
    while (true) {
        InputStream d = c.d("/s" + i + "m");
        if (d == null) return (byte) i;
        d.close();
        i++;
    }
}
```

The number of episodes (stored as `k.by`) determines if premium content is unlocked (`k.B = by > 0`).

### 1.2 Episode Content

Each episode slot maps to a complete set of game data under `/s{N}/`:

| Asset Path | Purpose | Size |
|---|---|---|
| `/s{N}m` | MIDI music file for the episode | ~7–10 KB |
| `/s{N}/d0` | Core game data (unit stats, building data, scripts) | ~109 KB |
| `/s{N}/f0_0` | Font bitmap (set 0, variant 0) | ~210 B |
| `/s{N}/f0_1` | Font bitmap (set 0, variant 1) | ~219 B |
| `/s{N}/f1_0` | Font bitmap (set 1, variant 0) | ~143 B |
| `/s{N}/f2_0` | Font bitmap (set 2, variant 0) | ~42 B |
| `/s{N}/f3_0` | Font bitmap (set 3, variant 0) | ~42 B |
| `/s{N}/i0` | Image data for episode units/terrain | ~260 KB |
| `/s{N}/l2` | Language/localization strings | ~45 KB |
| `/s{N}/gg` | Game graphics | ~2 KB |
| `/s{N}/hc` | HeroCraft branding data | ~1.8 KB |
| `/s{N}/f*_p` | Font metrics/descriptors | Various |

### 1.3 Known Episodes

- **Episode 1**: Global Confederation (7 campaign missions + 15 custom missions)
- **Episode 2**: Liberation of Peru (7 campaign missions, Resistance faction)
- **Episode 3**: Online expansion (multiplayer-focused, Bluetooth/online support)

---

## 2. Mission Data Structure

### 2.1 Map Layout File (`/ml`)

The map layout file (1,489 bytes) contains 193 map records. Parsed in `k.java` method `P()`:

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
- Fields 0–4: Map dimensions and tile configuration
- Fields 5–9: Starting position data (X/Y for each player)
- Fields 10–14: Resource placement offsets
- Fields 15–19: Terrain generation seeds
- Fields 20–24: Victory condition parameters
- Fields 25–29: AI configuration parameters

### 2.2 Game Data File (`/d0`)

Each episode's `/s{N}/d0` file (~109 KB) contains the core game configuration. Parsed in `k.java` method `O()`:

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

**Data Array Mapping (inferred from usage in `w.java`)**:

| Array | Purpose |
|---|---|
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

### 2.3 Name Table (`/n`)

The name table file (2,066 bytes) is XOR-encrypted with key `93` (decrypted by `y()` method in `k.java`). Contains:
- 163 text strings (unit names, building names, UI labels)
- 9 additional short arrays for string index offsets

Decryption: `value = (encrypted_byte ^ 93)`

### 2.4 Global Data File (`/p`)

The `p` file (3 bytes: `00 00 01`) contains a single short value read at initialization:
- `q.a = (short) ((read() << 8) | read())` — Appears to be a version or episode count indicator

---

## 3. Trigger & Script System

### 3.1 Reinforcement Scheduling

Reinforcements are handled by the `d()` method in `w.java`:

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

The reinforcement data is stored in `am[][]` (6 arrays × 100 entries):
- `am[0][i]`: Event type (24, 25, 27, 29, etc.)
- `am[1][i]`: X position parameter
- `am[2][i]`: Y position parameter
- `am[3][i]`: Unit count / type parameter
- `am[4][i]`: Trigger time (in game ticks / 10)
- `am[5][i]`: Faction / ownership parameter

### 3.2 Event Types (from `B()` method in `w.java`)

| Event Type | Description | Parameters |
|---|---|---|
| **24** | Set AI difficulty level | `am[3]`: 0=Easy, 1=Normal, 2=Hard |
| **25** | Give resources to players | `am[1]`: Player 0 amount, `am[2]`: Player 1 amount |
| **27** | Spawn reinforcement units | `am[1]`: X, `am[2]`: Y, `am[3]`: unit composition, `am[5]`: faction |
| **29** | Trigger cinematic/event | `am[3]`: event ID |

### 3.3 Campaign Flow Control

The main game loop in `k.java` manages campaign state through several key variables:

- `k.ac` (byte): Game sub-state (0=Normal play, 1=Menu active, 2=Another overlay)
- `k.c` (byte): Match phase (0=Preparation, 1=Active, 3=Between-rounds)
- `k.af` (byte): Player side (0=Player 0, 1=Player 1)
- `k.ah` (int): Game tick counter (increments each frame during active play)
- `w.ab` (byte): Victory/defeat flag (0=No result, 1=Player 0 wins, 2=Player 1 wins)

### 3.4 Victory Conditions

Victory is determined in `w.java` method `m(int i)` (unit/building destruction handler):

```java
if (this.ab == 0) {
    // Check if enemy has no buildings and few units
    if (this.ao == 0 && this.cb[i2][0] <= 0 && this.cb[i2][1] <= 7) {
        this.ab = (byte) 1;  // or (byte) 2 depending on side
    }
}
```

**Victory condition**: A side loses when it has **no remaining buildings** (`cb[side][0] == 0`) and **7 or fewer units** (`cb[side][1] <= 7`).

**Scoring on destruction**:
- Destroying enemy building: +200 score, +100 score bonus
- Losing own building: -100 score

### 3.5 Time-Based Victory

For timed multiplayer matches, the game uses:

```java
if (y.L[6][0] * 60 * 8 <= this.ah) {
    // Time limit reached
    if (this.af != 0 || y.W[0] + y.X[0] < y.W[1] + y.X[1]) {
        this.ax.ab = (byte) 2;  // Player 1 wins
    } else {
        this.ax.ab = (byte) 1;  // Player 0 wins
    }
}
```

Time limits are defined in `y.bh = {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535}` (in seconds).

---

## 4. AI Behavior System

### 4.1 AI Difficulty Levels

The AI difficulty is controlled by `w.ao` (byte):
- `-1`: Not set
- `0`: Easy
- `1`: Normal  
- `2+`: Hard/Aggressive

Difficulty affects:
- Unit sight range (`w.ai` calculation)
- Building upgrade research speed
- Resource income multiplier
- Aggressiveness thresholds

### 4.2 AI Unit Control

The AI processes units differently based on faction ownership:

```java
if (this.ae != this.aL.af) {  // If this is the AI side
    m();  // AI-specific fog of war / stealth detection
}
```

Key AI behaviors (from `w.java`):

1. **Pathfinding**: Uses `o(int)` method for direction lookup tables — calculates optimal movement direction from 31×31 directional grid
2. **Target selection**: Uses `i()` method to find nearest enemy unit by Euclidean distance
3. **Attack decisions**: `o()` method evaluates threat levels and engages when `ai > ah` (attack power > threat threshold)
4. **Resource management**: AI receives same income rates but may get difficulty bonuses via `y.Z[side]` modifiers
5. **Build order**: AI follows template arrays from `at[8]–at[10]` which define construction sequences

### 4.3 AI Reinforcement Logic

For AI-controlled sides, reinforcements follow the `B()` method triggers:
- Type 27 spawns units with random composition based on `am[3]` encoding
- Unit type selection uses faction-specific lookup tables (`bT[126+]`)
- Spawn positions are randomized within designated areas

### 4.4 Fog of War for AI

```java
private void m() {
    if (this.ca[this.af + 5151] < 0) {
        // Check if unit is within visible area
        if (((this.aL.Q[this.aL.af][1][this.ca[this.af + 0] >> 5][this.ca[this.af + 101]] 
              >> (this.ca[this.af + 0] & 31)) & 1) != 0) {
            this.ca[this.af + 5151] = 0;
        }
    } else if (this.ca[this.af + 5151] <= this.aL.at[6][289] - this.aL.at[5][289]) {
        this.ca[this.af + 5151]++;
    }
}
```

AI units have a "stealth detection" timer (`ca[af+5151]`) that counts up when a unit is in fog of war. When the timer exceeds a threshold, the unit becomes visible.

---

## 5. Unit Entity System

### 5.1 Entity Data Layout

All units and buildings share a common data structure in the `X` byte array (`k.X`, 7272 bytes). Each entity occupies 101 bytes with offset `entityIndex * 101`:

| Offset | Size | Field |
|---|---|---|
| +0 | 1 | X position (tile) |
| +101 | 1 | Y position (tile) |
| +202 | 1 | Target X (movement) |
| +303 | 1 | Target Y (movement) |
| +404 | 1 | Facing direction (0–7) |
| +505 | 1 | Display facing direction |
| +606 | 1 | Secondary facing |
| +707 | 1 | Target facing |
| +808 | 1 | Movement animation frame |
| +909 | 1 | Attack animation frame |
| +1010 | 1 | Waypoint index |
| +1111 | 1 | Attack state counter |
| +1212 | 1 | Weapon reload timer |
| +1313 | 1 | Production progress |
| +1414 | 1 | Action state (0=idle, 1=attacking, 2=moving, 3=casting) |
| +1515 | 1 | Attack cooldown |
| +1616 | 1 | Health / HP (current) |
| +1717 | 1 | Home X (rally point) |
| +1818 | 1 | Home Y (rally point) |
| +1919 | 1 | Garrison/transport reference |
| +2020 | 1 | Production queue slot 0 |
| +2121 | 1 | Production queue slot 1 |
| +2222 | 1 | Production queue slot 2 |
| +2323 | 1 | Unit type ID |
| +2424 | 1 | Animation counter |
| +2525 | 1 | Production timer slot 0 |
| +2626 | 1 | Production timer slot 1 |
| +2727 | 1 | Production timer slot 2 |
| +2828 | 1 | Status flags (bitfield) |
| +2929 | 1 | Command flags |
| +3232 | 1 | Production upgrade level |
| +3535 | 1 | Rally point reference |
| +4040+ | 1×3 | Upgrade levels (3 slots) |
| +4949 | 1 | Shield/armor bonus |
| +5050 | 1 | Transport cargo |
| +5151 | 1 | Stealth/visibility timer |
| +5252 | 1 | Building anchor X |
| +5353 | 1 | Building anchor Y |
| +5454 | 1 | Construction state (0=none, >0=building, -1=destroyed) |
| +5555 | 1 | Construction progress |
| +5656 | 1 | Building type ID |
| +5757 | 1 | Link/reference to another entity |
| +6060 | 1 | Building flags |
| +6161 | 1 | Building state |
| +6363 | 1 | Building top-left X |
| +6464 | 1 | Building top-left Y |
| +6565 | 1 | Resource generation timer |
| +6767 | 1 | Resource capacity |
| +6868 | 1 | Resource type / upgrade level |

### 5.2 Status Flags (offset +2828)

| Bit | Meaning |
|---|---|
| 0 | Production slot 0 active |
| 1 | Production slot 1 active |
| 2 | Production slot 2 active |
| 3 | Being attacked / targeted |
| 4 | Selected by player |
| 5 | Retreating / flee mode |
| 6 | Garrisoned / inside transport |
| 7 | Special ability ready |

### 5.3 Unit Type IDs

Based on the `y.j` and `y.k` arrays:
- `y.j = {-1, 10, 20, 40, -1, -1, 41, 41, 43, -1, 114, 83, -1, -1, 44, -1, 16, 83}` — Unit type image offsets
- `y.k = {-1, -1, 108, -1, -1, 110, 41, 43, 112, 109, 109, 91}` — Additional unit mappings
- `y.l = {83, 41, 41, 41, 41, 43, 43, 43, 43}` — Building image offsets

---

## 6. Building System

### 6.1 Building Categories

From the bitmask checks in `w.java`:
- **`16447` (0x401F)**: Military units bitmask — checks if a unit is a combat unit
- **`114688` (0x1C000)**: Building bitmask — identifies building-type entities  
- **`1011` (0x3F3)**: Military production buildings bitmask

### 6.2 Building Footprints

Building dimensions are stored in `as[18]` (width) and `as[19]` (height), indexed by building type ID. The `a.java` screen manager validates placement:

```java
this.ag = (byte) w.a(this.ag, y.bP.c + 1, 
    (y.bP.c + 8) - (this.ai >= 121 ? 1 : y.bO.as[18][this.ai]) - 1);
this.ah = (byte) w.a(this.ah, y.bP.d + 1, 
    (y.bP.d + y.bP.aj) - (this.ai >= 121 ? 1 : y.bO.as[19][this.ai]) - 1);
```

Buildings with ID >= 121 are special (1×1 footprint, likely turrets/walls).

### 6.3 Resource Generation

Buildings generate resources on a timer:

```java
if (this.bS[this.bT[106] + this.ca[this.af + 5656]] % 3 == 0 && 
    (this.aL.ah & 127) == 127) {
    this.cb[this.ae][4] += income_amount;
}
```

The modulo-3 check on building data separates income-generating buildings from military buildings. The `& 127` check ensures income ticks happen every 128 game frames.

---

## 7. Research / Upgrade System

### 7.1 Upgrade Levels

The game supports up to 3 upgrade levels per building (production slots at offsets +404, +505, +606 per slot). Upgrades are stored at entity offsets `+4040 + slot*101` through `+4040 + slot*101 + 2`.

### 7.2 Upgrade Effects

Upgrades modify unit stats through the `y.Z[side]` modifier arrays:
- `y.Z[side][0–3]`: Infantry upgrades
- `y.Z[side][4–7]`: Vehicle upgrades  
- `y.Z[side][8–11]`: Armor upgrades
- `y.Z[side][12–15]`: Production speed
- `y.Z[side][16]`: Fog of war reveal radius

---

## 8. Mission Progression

### 8.1 Campaign State Machine

The game manages campaign progression through:

1. **Save slot selection**: `k.bz` stores the selected episode index (0, 1, or 2)
2. **Mission completion**: After victory (`w.ab != 0`), the game advances
3. **Score tracking**: `y.W[]` and `y.X[]` arrays track player scores for each side
4. **Progress persistence**: Saved via the `U()` method in `p.java` to "aow2olhc" RecordStore

### 8.2 Mission Selection Screen

The `a.java` class manages the mission selection UI:
- `a.aO` (short): Current screen ID (9=Main menu, 10=Episode select, 11=Mission select, etc.)
- `a.aR` (short): Sub-screen/dialog mode (136=Building placement, 137=Unit production, 138=Building info, 141=Confirmation)
- `a.ao` (int): Currently selected item index
- `a.ap` (int): Total number of items in current list

Screen IDs (from `a.java` switch statements):
- **9**: Main menu
- **10**: Episode selection
- **11**: Mission brief
- **14**: Game in progress
- **16**: Mission list / campaign map
- **31**: Mission briefing
- **35**: Settings
- **39**: Online menu
- **40**: About/credits
- **42**: Map editor
- **53/55/56**: Dialog screens
- **57/58**: Online lobby
- **63**: Victory/defeat screen
- **91**: Loading screen
- **92**: Exit confirmation
- **116/121/122**: Tutorial screens
- **125**: Score display

### 8.3 Story Events

Story events are triggered through the `z.java` network communication handler. When the server sends negative message types:
```java
if (this.q < 0 && !y.bO.aJ && this.h != -1) {
    y.u = -this.q;  // Set screen ID for story event
    this.t.a(63, true);  // Navigate to event screen
}
```

The story progression is entirely server-driven for the Online version, with local campaign scripts encoded in the `/d0` data arrays.

---

## 9. Online Multiplayer Campaign

### 9.1 Match Flow

1. **Preparation phase** (`k.c == 0`): Both sides build bases
2. **Active phase** (`k.c == 1`): Units begin moving, AI activates
3. **Between-rounds** (`k.c == 3`): Score tallied, map scrolls
4. **Victory/defeat** (`w.ab != 0`): Match ends

### 9.2 Turn Timer

```java
// Time between rounds
y.L[4][0]  // Prep time in seconds
y.L[5][0]  // Round duration multiplier
y.L[6][0]  // Max rounds before time victory
y.Q[0]     // Current round number
```

### 9.3 Network Communication

The `e.java` class manages TCP socket connections to `artofwaronline.herocraft.com` (or `aow2.ru`) on port `47584 + (random % 5)`.

The `z.java` class manages message queues with 3-slot buffering and automatic retry (up to 10 attempts with 2.5s delays).
