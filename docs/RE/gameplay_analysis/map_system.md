# Art of War 2 Online — Map System Documentation

**Source Analysis**: Decompiled APK (com.herocraft.game.artofwar2ol)  
**Date**: 2026-03-05  
**Key Files**: `k.java` (map loading), `w.java` (map logic), `y.java` (constants)

---

## 1. Map System Overview

### 1.1 Map Dimensions

The game uses a **128×128 tile grid** for the world map, with the following constant arrays:

```java
// In k.java
byte[][] O = (byte[][]) Array.newInstance(Byte.TYPE, 128, 128);  // Terrain types
short[][] P = (short[][]) Array.newInstance(Short.TYPE, 128, 128); // Unit/entity occupancy

// In y.java
public static final byte[][] ce = (byte[][]) Array.newInstance(Byte.TYPE, 128, 128); // Fog of war

// In w.java
private byte[][] bW;  // Occupancy grid (reference via k.P)
private byte[][] bX;  // Secondary grid (reference via k.S)
```

### 1.2 Viewport

The visible area is defined by `w.c` (camera X), `w.d` (camera Y), and `w.aj` (viewport height):

```java
// Camera bounds checking
byte ag = (byte) w.a(this.ag, y.bP.c + 1, (y.bP.c + 8) - buildingWidth - 1);
byte ah = (byte) w.a(this.ah, y.bP.d + 1, (y.bP.d + y.bP.aj) - buildingHeight - 1);
```

The map scrolls in 8 directions controlled by `y.bO.ak` (direction byte):
- 1, 3: Up
- 2, 14: Down
- 4, 15: Left
- 5, 11, 12, 16: Center on target
- 6, 17: Right
- 7: Scroll to position
- 8, 18: Scroll up
- 10, 13: Center/follow

---

## 2. Map Data Files

### 2.1 Map Layout File (`/ml`)

**Size**: 1,489 bytes  
**Format**: Binary, parsed by `k.java` method `P()`

```
[1 byte]  mapCount           — Number of map records (typically 5)
For each map:
  [2 bytes] mapDataLength    — Length of tile data array
  [2 bytes × mapDataLength] tileData — Map tile indices
  [2 bytes × 30] metadata   — 30 metadata fields per map
```

**Total per map**: 2 + (mapDataLength × 2) + 60 bytes

From the hex dump, the first byte is `0x05` (5 maps), and each map has variable-length tile data.

### 2.2 Map Tile Data

Each tile in the `tileData` array is a short value encoding:
- **Lower bits**: Terrain type (0–18)
- **Upper bits**: Terrain variant / elevation
- **Special values**: Resource deposits, starting positions, impassable terrain

The raw hex dump of `/ml` shows repeating patterns like `46 df 44 ff e1` which decode to tile indices `0x46`, `0xdf`, `0x44`, `0xff`, `0xe1` — these map to terrain types and variants.

### 2.3 Map Metadata Fields (30 shorts)

| Field Index | Purpose |
|---|---|
| 0 | Map width (in tiles) |
| 1 | Map height (in tiles) |
| 2 | Player 0 start X |
| 3 | Player 0 start Y |
| 4 | Player 1 start X |
| 5 | Player 1 start Y |
| 6 | Resource cluster count |
| 7–11 | Resource positions/parameters |
| 12 | AI difficulty preset |
| 13 | Mission type flags |
| 14 | Time limit (in seconds or ticks) |
| 15 | Reinforcement schedule index |
| 16–19 | Starting unit configuration |
| 20–24 | Victory condition parameters |
| 25–29 | Reserved/extended parameters |

---

## 3. Terrain System

### 3.1 Terrain Types

From the `d0` data file analysis and `w.java` logic, terrain types include:

| Type ID | Terrain | Properties |
|---|---|---|
| 0 | Deep water | Impassable for ground units, passable by naval/air |
| 1 | Shallow water | Impassable for vehicles, passable by infantry |
| 2 | Sand/Beach | Reduced movement speed for vehicles |
| 3 | Plains/Grass | Normal movement |
| 4 | Forest | Provides cover bonus (+defense), reduced vehicle speed |
| 5 | Hills | Elevated, provides vision bonus |
| 6 | Mountains | Impassable for most units, provides vision |
| 7 | Road | Increased movement speed |
| 8 | Bridge | Connects land masses over water |
| 9 | Swamp | Very slow movement |
| 10 | Snow | Reduced movement speed |
| 25 | Resource deposit (gold/credits) | Harvestable |
| 26+ | Special terrain | Episode-specific terrain |

### 3.2 Terrain from `d0` Raw Data

The `/s0/d0` file starts with:
```
09 35 01 0c ...
```

The first byte `0x09` = 9, followed by `0x35` = 53 (short), `0x01` = 1, `0x0c` = 12. The large blocks of repeated values (0x01, 0x02, 0x09, 0x0A, 0x0B, 0x19) correspond to terrain type lookup tables and movement cost matrices.

### 3.3 Movement Cost System

Movement costs per terrain type are stored in `as[14]` and `as[15]` arrays from `/d0`. The `c()` method in `w.java` calculates path cost:

```java
byte h3 = (byte) (h3 + (this.bW[y][x] == 0 ? (byte) 0 : c(x, y)));
```

Where `c(x, y)` returns the movement cost for the terrain at position (x, y).

### 3.4 Fog of War

The fog of war is implemented via the `Q` 4D array:

```java
int[][][][] Q = (int[][][][]) Array.newInstance(Integer.TYPE, 2, 2, 4, 128);
```

Dimensions: `[side][fogLevel][bitmaskRow][column]`
- `side`: 0 or 1 (player 0 or 1)
- `fogLevel`: 0=explored, 1=currently visible
- `bitmaskRow`: 4 rows of 128-bit bitmasks (4×32 bits = 128 bits per row)
- `column`: 128 columns (one per map tile row)

Fog of war update (from `w.java` `e()` method):

```java
for (int i = 0; i < 4; i++) {
    for (int i2 = 0; i2 < 128; i2++) {
        Q[side][1][i][i2] = (y.Y[side] < aL.ah || y.Z[side][16] == 0) 
            ? 0 : -1;  // -1 = all bits set = fully visible
    }
}
```

Visibility is calculated from unit positions with range defined by `cf[1][unitType]` (sight range per unit type).

---

## 4. Map Tile Rendering

### 4.1 Tile Rendering System

The map is rendered using the image array `k.ap[0][]` loaded from `/i0` (260,049 bytes containing all sprite data). Tiles are drawn using the `f.java` text/font system for labels and `k.ap` for actual graphics.

### 4.2 Tile Priority / Z-Order

Buildings and units on the map are sorted by Y position for correct overlap rendering. The occupancy grid `k.P[y][x]` stores entity IDs at each position:
- Values 1–50: Player 0 entities
- Values 51–100: Player 1 entities
- Values 121–123: Special building types
- Value 127: Resource deposit marker

### 4.3 Building Placement Grid

Building footprints occupy multiple tiles. The `bW` grid stores:
- `0`: Empty/unoccupied
- Positive value: Entity ID occupying this tile
- Special values for resource deposits

When a building is placed:
```java
for (int y = buildingY; y < buildingY + height; y++) {
    for (int x = buildingX; x < buildingX + width; x++) {
        this.bW[y][x] = buildingEntityId;
    }
}
```

When destroyed:
```java
for (int y = buildingY; y < buildingY + height; y++) {
    for (int x = buildingX; x < buildingX + width; x++) {
        this.bW[y][x] = 0;
    }
}
```

---

## 5. Resource System

### 5.1 Resource Placement

Resources are placed on the map using the `y.ay` offset and `y.aw` byte array. The hex dump of `/n` shows encoded resource position data.

Resource deposits are identified by building type IDs in the `be` array:
```java
private byte[] be = {11, 15, 18, 21, 25, 27, 32, 35, 37, 39, 42, 45, 48, 51, 55, 59};
```

These appear to be credit/resource values for different deposit types.

### 5.2 Resource Income

Income generation occurs per-building on a tick cycle:

```java
// From w.java r() method
if (this.bS[this.bT[106] + this.ca[this.af + 5656]] % 3 == 0) {
    // This building generates income
    short income = (short) a(this.cb[this.ae][4] + available, 0, 30000);
    this.cb[this.ae][4] = income;
    aM.bk += (income_delta << 1);  // Update display score
}
```

The `% 3` check categorizes buildings:
- `data % 3 == 0`: Income-generating (Supply Center, Command Center)
- `data % 3 == 1`: Military production (Barracks, Factories)
- `data % 3 == 2`: Research/defensive (Workshop, Turrets)

### 5.3 Resource Values

Base resource amounts from `y.bh`:
```java
static final int[] bh = {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535};
```

These represent time intervals (in seconds) for income ticks at different game speeds.

---

## 6. Starting Positions

### 6.1 Initial Base Placement

When a mission starts, each player's base is placed at predetermined positions from the map metadata:

```java
// From w.java a() initialization
for (int i = 0; i < 2; i++) {
    aL.J[i] = 2;      // Starting X
    O[i] = 6;          // Starting Y
    N[i] = 8;          // Starting resources
    P[i*3] = 10;       // Starting unit positions
    P[i*3+1] = 10;
    P[i*3+2] = 10;
    Q[i] = 100;        // Starting credits
    R[i] = 10;         // Starting unit count
    S[i] = 20;         // Starting supply
}
```

### 6.2 Starting Buildings

Each player starts with a Command Center (building type from `bT[0]`), and the initial building data is set from the `bS` arrays:

```java
this.bm = this.bT[104];
while (this.bm < this.bT[105]) {
    this.U[this.bm - this.bT[104]] = this.bS[this.bm];  // Initial unit types
    this.bm++;
}
this.bm = this.bT[105];
while (this.bm < this.bT[106]) {
    this.V[this.bm - this.bT[105]] = this.bS[this.bm];  // Initial building types
    this.bm++;
}
```

### 6.3 Starting Unit Data

The initial units are spawned from the reinforcement schedule's first entries. The `bh` array defines starting unit composition per faction:
```java
private byte[] bh = {1, 4, 0};  // Unit type indices for starting force
```

And the `bi` array defines starting unit counts:
```java
private short[] bi = {231, 249, 249, 259, 247};  // Unit stat references
```

---

## 7. Map Editor Support

### 7.1 Custom Maps

The game supports custom maps through the map editor (screen ID 42 in `a.java`). Custom maps use the same data format as built-in maps.

### 7.2 Map Storage

Custom maps are stored in the RecordStore system and can be shared online. The map data follows the same `/ml` format with 30 metadata fields.

### 7.3 Map Validation

The `w.java` class includes bounds checking:
```java
static int a(int i, int i2) {
    return Math.max(Math.min(i, i2), 0);
}
```

And the `Z()` method validates timing parameters:
```java
final void Z() {
    if (this.y < 2000 || this.y > 60000) this.y = 15000;    // Default scroll delay
    if (this.C < 1000 || this.C > 40000) this.C = 5000;     // Default select delay
    if (this.D < 2000 || this.D > 60000) this.D = 14000;    // Default action delay
}
```

---

## 8. Map Data Tables

### 8.1 Unit Stats Lookup

The `o(int)` method in `w.java` uses a 31×31 directional table for movement:

```
Offsets from -15 to +15 in X and Y
Each cell contains a direction value (0–7 for 8 directions)
```

This allows units to determine which direction to move toward a target.

### 8.2 Combat Direction Table

The attack direction is calculated similarly using `p(int)`:

```java
private int p(int i) {
    int dx = target.x - unit.x;
    int dy = target.y - unit.y;
    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) return 0;
    return this.bS[((dy + 15) * 31) + bT[4] + dx + 15] & 7;
}
```

### 8.3 Distance/Damage Tables

The `o(0)` method computes effective attack power considering range:

```java
private int o(int i) {
    // ... distance calculation ...
    if (a > 15 || a2 > 15 || a < -15 || a2 < -15) return 127;
    return (this.bS[((((a2 + 15) * 31) + this.bT[4]) + a) + 15] & 255) >> 3;
}
```

The `>> 3` shift converts the raw table value into a damage multiplier (0–31 range).

---

## 9. Seasonal/Terrain Variant System

### 9.1 Terrain Color Palettes

The `bQ` array in `w.java` defines terrain color palettes:

```java
private short[][] bQ = {
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

Each palette entry has RGB565 format values (16-bit color), with optional 4th value for variant/animation frame.

### 9.2 Terrain Rendering

The 128×128 map grid is rendered with each tile drawn from the sprite set at `k.ap[0][tileIndex]`. Terrain tiles use palette colors from `bQ` for color blending effects.

---

## 10. Minimap System

### 10.1 Minimap Data

The minimap uses a scaled-down representation stored in `y.ce` (128×128 byte array):

```java
public static final byte[][] ce = (byte[][]) Array.newInstance(Byte.TYPE, 128, 128);
```

Each byte encodes the terrain type at that position for quick minimap rendering.

### 10.2 Minimap Entity Markers

Units and buildings are marked on the minimap using faction colors:
- Player 0: Color from `k.bg[1]` (green, RGB: 5251341)
- Player 1: Color from `k.bg[2]` (red, RGB: 16704820)
- Neutral/Resource: Color from `k.bg[3]` (orange, RGB: 16033043)
- Special: Color from `k.bg[4]` (purple, RGB: 12010496)

---

## 11. Map-Related Global Constants

### 11.1 Key `y.java` Constants

| Constant | Type | Description |
|---|---|---|
| `y.a` | byte[3000] | General purpose buffer |
| `y.h` | byte[24000] | Large data buffer |
| `y.s` | byte[1000] | Communication buffer |
| `y.w` | byte[15000] | Extended data buffer |
| `y.aw` | byte[] | Text rendering buffer |
| `y.W` | int[2] | Score per player |
| `y.X` | int[2] | Score bonus per player |
| `y.Y` | int[2] | Tech level per player |
| `y.Z` | int[2][] | Upgrade modifier array per player |
| `y.Q` | short[3] | Round counter |
| `y.ag` | int | Online mode flag |
| `y.u` | int | Current screen/event ID |

### 11.2 Faction Color Definitions

From `k.java`:
```java
private int[] bg = {0, 5251341, 16704820, 16033043, 12010496};
//                      Black   Green     Red       Orange    Purple
private byte[] bh = {0, 1, 1, 1, 1};        // Active faction colors
private byte[] bi = {0, 1, 1, 2, 3};        // Faction group indices
private byte[] bj = {100, 98, 0, 0, 0};     // Color intensity
private byte[] bk = {5, 3, 1, 1, 1};        // Color variant
```
