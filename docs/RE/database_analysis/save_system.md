# Art of War 2 Online — Save System & Persistence Documentation

**Source Analysis**: Decompiled APK (com.herocraft.game.artofwar2ol)  
**Date**: 2026-03-05  
**Key Files**: `i.java` (RMS abstraction), `p.java` (game save logic), `k.java` (main game), `c.java` (config)

---

## 1. RMS RecordStore Abstraction

### 1.1 Architecture Overview

The game uses a custom implementation of J2ME's RecordStore API, adapted for Android's file system. The primary class is `com.herocraft.game.artofwar2ol.i` (NOT `s0.i` which handles audio).

**Class**: `com.herocraft.game.artofwar2ol.i`

```
File naming convention: {storeName}.datrms          (header/index file)
                        {storeName}.datrms_{recordId}  (record data files)
```

### 1.2 RecordStore Header File Format

The header file (`{name}.datrms`) stores metadata:

| Offset | Type | Field | Description |
|---|---|---|---|
| 0 | int (4 bytes) | `e` | Total number of records |
| 4 | int (4 bytes) | `f` | Modification counter (version) |
| 8 | long (8 bytes) | `g` | Last modification timestamp |

### 1.3 Record Data File Format

Each record is stored as a separate file: `{name}.datrms_{recordId}`

- Record IDs start at **1** (not 0)
- Records are variable-length byte arrays
- No internal structure imposed by the RecordStore layer — interpretation is left to the caller

### 1.4 Key Methods

```java
// Open a RecordStore (create if needed when createIfNecessary=true)
public static i a(String name, boolean createIfNecessary) throws d, ap, ab

// Add a new record
public int a(byte[] data, int offset, int length) throws bq, d, ap
// Returns: record ID of new record

// Get a record by ID
public byte[] a(int recordId) throws bq, p, d

// Set/update a record
public void a(int recordId, byte[] data, int offset, int length) throws bq, p, d, ap

// Delete a record
private void b(int recordId) throws bq, p, d

// Get record count
public int b() throws bq

// Close the RecordStore
public void a() throws bq, d

// Delete entire RecordStore
public static void a(String name) throws d, ab
```

### 1.5 Instance Caching

RecordStores are cached in a static `Hashtable`:

```java
private static final Hashtable a = new Hashtable();
```

When opened, an instance is looked up by name. If found, its reference count (`h`) is incremented. When closed, the reference count is decremented. When it reaches 0, the instance is removed from the cache.

### 1.6 Thread Safety

All operations are synchronized on the static `Hashtable a`, ensuring thread-safe access across the application.

---

## 2. Game Save Data

### 2.1 Primary Save Store: "aow2olhc"

The main game save is stored in RecordStore `"aow2olhc"`. It is written by method `U()` in `p.java`:

```java
final void U() {
    // Delete old save
    com.herocraft.game.artofwar2ol.i.a("aow2olhc");
    
    // Create new save
    i rs = com.herocraft.game.artofwar2ol.i.a("aow2olhc", true);
    
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    
    // Write save data
    dos.writeInt(y.bO.aA.g);       // Game state flags
    dos.writeByte(y.bO.aj);         // Production speed
    dos.writeByte(y.bO.w);          // Productivity level
    dos.writeByte(3);               // Save format version
    dos.writeByte(y.ag);            // Online/game mode
    dos.write(y.af);                // Player faction
    dos.writeByte(y.ak);            // Screen state
    dos.write(y.aj);                // UI state
    dos.writeInt(c.g);              // Config version
    dos.writeByte(this.T);          // Tutorial progress
    dos.writeByte(this.Z);          // Campaign progress flags
    dos.writeByte(this.aa);         // Episode unlock state
    dos.writeByte(this.ab);         // Mission completion
    dos.writeByte(this.cH);         // Achievement byte 1
    dos.writeByte(this.cJ);         // Achievement byte 2
    dos.writeByte(this.cK);         // Achievement byte 3
    dos.writeByte(this.ac);         // Game sub-state
    dos.writeByte(this.A.length()); // Player name length
    for (int i = 0; i < this.A.length(); i++) {
        dos.writeByte(this.A.charAt(i));  // Player name (byte chars)
    }
    dos.writeByte(y.ar);            // Achievement count
    for (int i2 = 0; i2 < y.ar; i2++) {
        dos.writeByte(y.aq[i2]);    // Achievement IDs
    }
    
    byte[] encrypted = a(baos.toByteArray());  // Encrypt
    dos.close();
    rs.a(encrypted, 0, encrypted.length);  // Store as record 1
    rs.a();  // Close
}
```

### 2.2 Save Data Format (Decrypted)

| Offset | Type | Field | Description |
|---|---|---|---|
| 0 | int | `aA.g` | Game state flags |
| 4 | byte | `aj` | Production speed setting |
| 5 | byte | `w` | Productivity level |
| 6 | byte | version | Save format version (always 3) |
| 7 | byte | `y.ag` | Online/game mode flag |
| 8 | byte | `y.af` | Player faction (0=Confederation, 1=Resistance) |
| 9 | byte | `y.ak` | Current screen state |
| 10 | byte | `y.aj` | UI state |
| 11 | int | `c.g` | Config/version number |
| 15 | byte | `T` | Tutorial completion flags |
| 16 | byte | `Z` | Campaign progress (which episode unlocked) |
| 17 | byte | `aa` | Episode unlock state |
| 18 | byte | `ab` | Mission completion bitmask |
| 19 | byte | `cH` | Achievement byte 1 |
| 20 | byte | `cJ` | Achievement byte 2 |
| 21 | byte | `cK` | Achievement byte 3 |
| 22 | byte | `ac` | Game sub-state |
| 23 | byte | nameLen | Player name length |
| 24 | byte[nameLen] | name | Player name (ASCII) |
| 24+nameLen | byte | `y.ar` | Achievement count |
| 25+nameLen | byte[ar] | `y.aq` | Achievement IDs |

### 2.3 Save Data Encryption

The save data is encrypted before storage using the `a(byte[])` method in `p.java`. The encryption uses:
- XOR with the `y.aK` cipher table (64-byte static key)
- Running counter `cN` and `cO` for additional XOR layers
- 3-byte grouping with Base64-like encoding

The decryption is handled by `V()` method in `p.java` (JADX was unable to decompile — 486 instructions).

### 2.4 Save Loading

Save loading is performed by method `V()` in `p.java`. The method:
1. Opens "aow2olhc" RecordStore
2. Reads record 1
3. Decrypts the data
4. Parses all fields in reverse order of `U()`
5. Restores game state to `y.*` static variables

---

## 3. Configuration Store: "FD0H9A0B"

A separate RecordStore `"FD0H9A0B"` stores the player's friend/buddy list for online play.

### 3.1 Friend List Format

Written by `ab()` in `k.java`:

```java
ByteArrayOutputStream baos = new ByteArrayOutputStream();
DataOutputStream dos = new DataOutputStream(baos);
int size = friendList.size();
dos.writeInt(size);
for (int i = 0; i < size; i++) {
    dos.writeUTF((String) friendList.elementAt(i));
}
```

Read by `aa()` in `k.java`:

```java
DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rs.a(1)));
int count = dis.readInt();
for (int i = 0; i < count; i++) {
    friendList.addElement(dis.readUTF());
}
```

The store name `"FD0H9A0B"` is derived from the obfuscated byte array:
```java
private static final byte[] bG = {0, 8, 70, 68, 48, 72, 57, 65, 48, 66};
// Decodes to: "\0\10FD0H9A0B" (8-char name after length prefix)
```

---

## 4. Advertising Client ID Store: "sgiuyq"

The advertising/analytics module stores a persistent client ID in RecordStore `"sgiuyq"`:

### 4.1 Client ID Format

```java
// Write client ID
private static final void b(long clientId) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(baos);
    dos.writeLong(clientId);   // 8-byte long
    dos.close();
    
    i rs = i.a("sgiuyq", true);
    if (rs.b() != 0) {
        rs.a(1, data, 0, data.length);  // Update record 1
    } else {
        rs.a(data, 0, data.length);     // Add as record 1
    }
    rs.a();
}

// Read client ID
private static final long c() {
    i rs = i.a("sgiuyq", true);
    if (rs.b() != 0) {
        ByteArrayInputStream bais = new ByteArrayInputStream(rs.a(1));
        long id = new DataInputStream(bais).readLong();
        bais.close();
        rs.a();
        return id;
    }
    rs.a();
    return 0L;
}
```

---

## 5. Config File (sn8p)

### 5.1 Format

The `sn8p` file (21,540 bytes) is a key-value configuration store:

```
\0 {length_byte} {key}: {value} \0 {length_byte} {key}: {value} ...
```

Each entry consists of:
- 1 byte: entry length
- Key-value pair separated by `: `
- Null-terminated

### 5.2 Known Configuration Keys

| Key | Default | Description |
|---|---|---|
| `LSK1` | -6 | Left softkey code |
| `LSK2` | 0 | Left softkey alternate |
| `RSK1` | -7 | Right softkey code |
| `RSK2` | 0 | Right softkey alternate |
| `SMTYPE` | -1 | Sound/music type |
| `VP` | TRUE | Vibration on |
| `SMPRIOR` | (empty) | Sound priority |
| `CMDLR` | FALSE | Commander mode |
| `INV_CMDLR` | 0 | Invincible commander |
| `WAP_JAVA` | 1 | WAP connectivity mode |
| `SMS_JAVA` | 4 | SMS mode |
| `GAME_ID` | 219 | Game identifier |
| `PROV_ID` | 629 | Provider ID |
| `LANG_ID` | en | Language |
| `PROD_ID` | 82335 | Product ID |
| `PORT_ID` | 33116 | Server port |
| `UMG` | market://search?q=herocraft | Market search URL |
| `UMGD` | market://search?q=pub:"HeroCraft Ltd" | Developer page URL |
| `HC-sys` | (empty) | HeroCraft system URL |
| `HC-prf` | http://wap.herocraft.com/... | Profile URL |

### 5.3 Runtime Configuration Variables

These are stored via `c.a()` / `c.a(key, value)` and persisted:

| Key | Type | Description |
|---|---|---|
| `ORIENT` | int (0/1/2) | Screen orientation |
| `IGKEYE` | boolean | In-game key emulation |
| `SNDCHK` | boolean | Sound enabled |
| `PRODY` | int (0–2) | Production speed |
| `PRODMAX` | int | Max production speed |
| `PRODMIN` | int | Min production speed |
| `INS` | int | Instance/install counter |
| `SMLEVEL` | int | Sound level (-1=off) |
| `SCREEN_WI` | String[6] | Screen width variants |

---

## 6. Sound Data Files

### 6.1 Episode Music (`/s{N}m`)

Each episode includes a MIDI music file:
- `s0m`: Episode 1 music (10,550 bytes)
- `s1m`: Episode 2 music (7,710 bytes)
- `s2m`: Episode 3 / Online music (8,424 bytes)

Format: Standard MIDI (MThd/MTrk chunks), confirmed by `MThd` header bytes.

### 6.2 Sound System (`s0/i.java`)

The sound system loads audio from asset paths with extensions `o`, `3`, `m` (Ogg, MP3, MIDI):
```java
private static String[] c = {"o", "3", "m"};
```

It supports 3 simultaneous audio channels (one per sound file path).

---

## 7. Data Encryption

### 7.1 Data File Encryption

The `/a` and `/d` asset files are XOR-encrypted with a rotating key derived from `k.bm`:

```java
private int y() {
    return (z() & 255) ^ 93;
}

private int z() {
    int i = this.bm[this.bn] ^ this.bl.read();
    this.bn = (this.bn + 1) % this.bm.length;
    return i;
}
```

The initial key `k.bm` is 8 bytes: `{71, 107, 115, 50, 56, 114, 116, 55}` (ASCII: "Gks28rt7"), derived from the version string.

### 7.2 Name Table Encryption

The `/n` file uses simple XOR with constant `93` (0x5D).

### 7.3 Config Encryption

Config values are obfuscated using the `y.aK` cipher table (64-byte substitution table):
```java
public static final byte[] aK = {72, 100, 50, 95, 98, 99, 71, 114, ...};
```

---

## 8. File System Layout

### 8.1 Android-Specific Paths

The RecordStore implementation uses Android's internal file storage:
```java
AppCtrl.context.openFileOutput(name + ".datrms", 0)       // Header
AppCtrl.context.openFileOutput(name + ".datrms_" + id, 0)  // Record data
AppCtrl.context.openFileInput(name + ".datrms")             // Read header
AppCtrl.context.openFileInput(name + ".datrms_" + id)       // Read record
AppCtrl.context.deleteFile(name + ".datrms")                 // Delete store
AppCtrl.context.deleteFile(name + ".datrms_" + id)           // Delete record
```

Files are stored in the app's private data directory: `/data/data/com.herocraft.game.artofwar2ol/files/`

### 8.2 Complete Save File Inventory

| RecordStore Name | Purpose | Records | Format |
|---|---|---|---|
| `aow2olhc` | Game progress & settings | 1 | Encrypted binary (see §2.2) |
| `FD0H9A0B` | Online friend list | 1 | int count + UTF strings |
| `sgiuyq` | Ad client ID | 1 | long (8 bytes) |

### 8.3 In-App Purchase Tracking

The game checks for premium episodes via asset presence:
```java
private static void I() {
    a = 0;  // No premium
    InputStream d = c.d("/b/200");
    if (d != null) { a = 1; }  // Full version
    else {
        d = c.d("/b/199");
        if (d != null) { a = 2; }  // Demo/lite version
    }
    c.a(d);
}
```

And for Russian market:
```java
private static void J() {
    bf = false;
    InputStream d = c.d("/r/b01");
    if (d != null) { bf = true; }  // Russian premium
}
```
