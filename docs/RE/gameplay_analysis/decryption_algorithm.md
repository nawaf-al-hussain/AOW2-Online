# Art of War 2 Online - Data Decryption Algorithm

## Overview

The game "Art of War 2 Online" stores its core gameplay data in encrypted binary files within the APK's `assets/` directory. This document describes the complete decryption and parsing algorithms used to extract all numeric game data.

## File Inventory

| File | Path | Size | Format | Encryption |
|------|------|------|--------|------------|
| `/a` | `assets/a` | 7001 bytes | Custom binary | XOR cipher + key rotation |
| `/d0` (×3) | `assets/s0/d0`, `assets/s1/d0`, `assets/s2/d0` | ~109K each | Java DataInputStream | None (raw binary) |
| `/sn8p` | `assets/sn8p` | 21540 bytes | UTF-8 key-value pairs | None (plain text) |
| `/d` | `assets/d` | 8 bytes | Raw binary | None |

## Decryption Algorithm for `/a` File

### Step 1: Obtain the Cipher Key

The cipher key `bm[]` (8 bytes) is derived from the **PROD_ID** property found in the `/sn8p` file.

**PROD_ID source:** The `/sn8p` file is a UTF-8 key-value property file where each entry has a 2-byte big-endian length prefix followed by the text content. The entry `PROD_ID: 82335` provides the numeric product ID.

**Key derivation (method `W()` in `k.java`):**

```java
int parseInt = Integer.parseInt(PROD_ID);  // = 82335
for (int i = 0; i < 8; i++) {
    bm[i] = (byte) ((parseInt >> ((3 - (i % 4)) * 8)) & 0xFF);
}
```

**For PROD_ID = 82335 (0x1419F):**

| Index | Shift | Result |
|-------|-------|--------|
| 0 | >> 24 | 0 |
| 1 | >> 16 | 1 |
| 2 | >> 8  | 65 |
| 3 | >> 0  | 159 |
| 4 | >> 24 | 0 |
| 5 | >> 16 | 1 |
| 6 | >> 8  | 65 |
| 7 | >> 0  | 159 |

**Derived key: `bm = [0, 1, 65, 159, 0, 1, 65, 159]`**

The default key before `W()` is called is `[71, 107, 115, 50, 56, 114, 116, 55]` but it is always overwritten at runtime.

### Step 2: Decrypt Each Byte (methods `z()` and `y()` in `k.java`)

The file is read sequentially. For each byte position `pos`:

```
z_val = bm[bn] XOR raw_byte[pos]
y_val = (z_val AND 0xFF) XOR 93
bn = (bn + 1) mod 8
```

Where `bn` starts at 0 and rotates through the 8-byte key.

**In compact form:**
```
decrypted_byte = ((bm[bn] ^ raw_byte) & 0xFF) ^ 93
```

The `& 0xFF` mask ensures unsigned byte arithmetic, and the `^ 93` is a secondary XOR with the constant 93 (0x5D).

### Step 3: Parse the Decrypted Stream

The decrypted stream is parsed on-the-fly (not as a separate step). The parsing reads:

#### 163 Byte Sections (tables K and L)

```
L[0] = 0
for i = 0 to 162:
    count = y() + (y() << 8)        // little-endian 16-bit count
    L[i+1] = L[i] + count           // cumulative offset
    for j = 0 to count-1:
        K[L[i] + j] = y()           // byte value
```

Each section stores `count` bytes into the `K[]` array (6500 bytes total), with `L[]` (166 entries) holding cumulative offsets.

#### 9 Short Sections (tables M and N)

```
N[0] = 0
for i = 0 to 8:
    count = y() + (y() << 8)        // little-endian 16-bit count
    if i < 8:
        N[i+1] = N[i] + count       // cumulative offset
    for j = 0 to count-1:
        M[N[i] + j] = y() + (y() << 8)  // little-endian 16-bit value
```

Each section stores `count` 16-bit unsigned shorts into `M[]` (1000 entries total), with `N[]` (9 entries) holding cumulative offsets.

### Verification

The `/a` file is exactly 7001 bytes. After full decryption and parsing, all 7001 bytes are consumed with no remainder, confirming the algorithm is correct.

## Additional Data from `/d` File

After parsing `/a`, the game reads 4 bytes from `/d` (which contains `[0, 1, 0, 1, 0, 1, 0, 0]`) and adds 3 more sections (indices 163-165) to the L/K arrays. These appear to be small supplementary data entries.

## d0 File Format (Per-Faction Data)

The d0 files (`s0/d0` for Confederation, `s1/d0` for Rebels, `s2/d0` for shared) are **unencrypted** Java DataInputStream format (big-endian):

```
// 26 byte arrays
for i = 0 to 25:
    count = readUnsignedShort()      // big-endian 16-bit
    byteArray[i] = new byte[count]
    for j = 0 to count-1:
        byteArray[i][j] = readByte()

// 18 short arrays
for i = 0 to 17:
    count = readUnsignedShort()      // big-endian 16-bit
    shortArray[i] = new short[count]
    for j = 0 to count-1:
        shortArray[i][j] = readShort()  // big-endian signed 16-bit
```

**File sizes:**
- `s0/d0`: 109,318 bytes (2357 entries per primary array)
- `s1/d0`: 109,821 bytes (2441 entries per primary array)
- `s2/d0`: 109,318 bytes (identical to s0)

## sn8p File Format (Text Strings)

The `/sn8p` file is a plain-text property file with a binary envelope:

```
while not EOF:
    length = readUnsignedShort()     // big-endian 16-bit
    text = readUTF8Bytes(length)     // key: value pairs
```

Entries are in the format `KEY: VALUE` with entries like:
- `PROD_ID: 82335` - Product ID (used for cipher key)
- `GAME_ID: 219`
- `LANG_ID: en`
- `T0_0` through `T0_228` - Game UI strings
- `T1_0` through `T1_212` - Help/tutorial text
- etc.

## bu.java Asset Decryption (Alternative Path)

There is a second encryption layer in `bu.java` that may apply to some assets loaded through `AppCtrl.getResourceAsStream()`. This uses:

- **Initial key:** `[-96, -95, -94, -93, -92, -91, -90, -89]` (signed) = `[160, 161, 162, 163, 164, 165, 166, 167]` (unsigned)
- **Decryption formula:** `decrypted = ((encrypted_byte - key[i]) ^ 250) & 0xFF`
- **Key rotation:** After use, elements 3-5 are shifted left (element 3 ← element 4, element 4 ← element 5, element 5 ← element 6)
- **Actual key:** Retrieved from `t.a(String)` method, typically `{21, 19, 159, 97, 81, 21, 37, 187}`

The raw `/a` file was successfully decrypted without this layer, indicating the game data files bypass the `bu.b()` decryption or are not encrypted with it.

## Data Section Mapping

### Key Byte Sections (from `/a`)

| Section | Count | Description | Sample Data |
|---------|-------|-------------|-------------|
| 0 | 39 | Unit category/type table | [0, 255, 255, 255, 255, 0, ...] |
| 4 | 961 | Defense/damage matrix (31×31) | [255, 255, 255, ...] |
| 5 | 441 | Terrain offset table (21×21) | [0, 4, 8, 12, ...] |
| 6 | 31 | Attack probability curve | [13, 15, 17, 19, ...] |
| 37 | 19 | **Unit speed** | [5, 6, 6, 7, 7, 7, 4, 7, ...] |
| 38 | 19 | **Unit armor** | [5, 5, 5, 5, 9, 5, 7, 7, ...] |
| 39 | 19 | **Unit attack bonus** | [0, 0, 0, 1, 0, 0, 2, 4, ...] |
| 40 | 19 | **Unit sight range** | [4, 4, 5, 5, 6, 2, 6, 2, ...] |
| 41 | 19 | **Unit build time** | [4, 5, 9, 10, 11, 14, 7, ...] |
| 46 | 25 | **Extended sight range** | [4, 5, 6, 6, 7, 8, 7, 6, ...] |
| 47 | 25 | **Attack range** | [4, 4, 6, 9, 6, 12, 10, 8, ...] |
| 49 | 25 | **Extended armor** | [6, 6, 12, 12, 8, 8, 8, 8, ...] |
| 53 | 19 | **Unit HP** | [40, 40, 50, 50, 50, 70, 80, ...] |
| 54 | 19 | **Unit damage** | [2, 2, 4, 4, 8, 6, 15, 35, ...] |
| 55 | 19 | **Unit base cost** | [1, 1, 2, 2, 4, 3, 8, 22, ...] |
| 56 | 25 | Building power consumption | [4, 4, 0, 0, 3, 3, ...] |
| 57 | 25 | Building power production | [4, 4, 0, 0, 4, 8, 6, 6, ...] |
| 91 | 9 | Building footprint width | [20, 20, 20, 30, 30, 30, 40, 40, 40] |
| 92 | 9 | Building footprint height | [20, 30, 40, 20, 30, 40, 20, 30, 40] |
| 93 | 6 | Building power radius | [10, 20, 30, 40, 60, 127] |
| 144 | 3 | Rank XP thresholds | [20, 35, 50] |
| 145 | 3 | Rank credit rewards | [10, 25, 51] |
| 146 | 3 | Rank bonus points | [0, 3, 6] |

### Key Short Sections (from `/a`)

| Section | Count | Description | Sample Data |
|---------|-------|-------------|-------------|
| 0 | 16 | Level/rank thresholds | [-10153, -3297, ..., 10153] |
| 2 | 4 | Battle time limits | [1001, 1100, 1101, 1200] |
| 3 | 15 | Game config | [-101, -51, -1, 0, 50, 100, ...] |
| 4 | 19 | **Unit availability flags** | [-1, -1, -1, -1, 10, -1, ...] |
| 5 | 19 | **Unit credit cost** | [10, 10, 20, 20, 40, 30, 50, 100, ...] |
| 6 | 20 | **Unit reward credits** | [650, 200, 300, 350, ...] |
| 7 | 48 | **Building cost details** | [300, 200, 200, 300, ...] |

## Entity Index Mapping

### 19-Entry Tables (Confederation + Shared)

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

### 25-Entry Tables (Both Factions)

| Index | Entity | Faction |
|-------|--------|---------|
| 0-6 | Confed units | Confederation |
| 7-14 | Confed buildings | Confederation |
| 15-21 | Rebel units | Rebels |
| 22-24 | Rebel buildings + shared | Rebels/Shared |

## Python Implementation

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

## Summary of Findings

1. **Encryption successfully broken** using PROD_ID=82335 extracted from the unencrypted `/sn8p` file
2. **All 7001 bytes** of `/a` are accounted for in the parsed output
3. **Three faction d0 files** parsed successfully as raw big-endian binary
4. **570 text strings** extracted from `/sn8p`
5. The game uses a **two-layer encryption**: bu.java XOR cipher (for some assets) + k.java XOR-93 cipher (for `/a`)
6. Key game balance data identified: unit HP, damage, cost, speed, armor, sight range, attack range, and building stats
