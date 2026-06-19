# Art of War 2 Online - Network Protocol Specification

## 1. Transport Layer

### 1.1 Primary Transport: TCP Socket

| Property | Value |
|----------|-------|
| Protocol | TCP over IPv4 |
| Server Hostname | `artofwaronline.herocraft.com` |
| Alt Hostname | `aow2.ru` |
| Base Port | 47584 |
| Port Range | 47584–47588 (base + random()%5) |
| Encoding | Big-endian (network byte order) |
| Keep-Alive | Enabled (socket option 2 = true) |
| Receive Buffer Size | 1 (set via socket option 3) |
| TCP No Delay | Not explicitly set (default) |

### 1.2 Fallback Transport: HTTP

| Property | Value |
|----------|-------|
| Protocol | HTTP/1.1 over TCP |
| Connection Header | `com.herocraft.game.artofwar2ol.Connection: close` |
| Content-Type (POST) | `application/x-www-form-urlencoded` |
| Response Code Expected | 200 |
| Timeout | 10s / 5s / 30s (per phase) |
| Retry Count | Up to 5 attempts per HTTP request |

---

## 2. Packet Format (TCP)

### 2.1 Wire Format (Outbound - from m.java)

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    1     byte     Message type (0 if payload empty, else the type parameter)
0x01    2     short    Total payload length (BE) = string_data_length + 2
0x03    2     short    String data length (BE) = len(string)
0x05    N     byte[]   String data (each char cast to byte, low 8 bits only)
```

**Notes:**
- The "string" payload is the game state message serialized as a character string
- Each character is truncated to its low 8 bits (ASCII/Latin-1 compatible)
- The `writeByte(type)` call at offset 0 writes 0 if the string is empty, otherwise the provided type byte
- `writeShort(length + 2)` at offset 1 gives the total remaining bytes (including the string-length field)
- `writeShort(length)` at offset 3 gives the actual string data length
- Data is flushed after each packet

### 2.2 Wire Format (Inbound - from o.java receiver)

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    1     byte     Message type
0x01    2     short    Remaining payload length (BE)
0x03    2     short    Data length (BE)
0x05    N     byte[]   Message data
```

**Received data is enqueued in a Vector as raw byte arrays and dequeued by the game loop.**

### 2.3 Record Format (Game State Records - from p.java v() method)

After the network frame is stripped, the payload contains one or more **records**:

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    4     int32    Record size (BE) - includes the 5-byte header
0x04    1     byte     Record type (command ID)
0x05    ...   ...      Record body (size - 5 bytes)
```

The game loop reads records sequentially from the payload buffer (`cg[]`):

```java
// Pseudocode from p.v()
while (cv < cu && cy) {
    int recordSize = (readByte() << 24) | (readByte() << 16) | (readByte() << 8) | readByte();
    byte recordType = readByte();
    recordSize--;  // Adjust (the size includes the type byte)
    switch (recordType) {
        case 2:  parse6Bytes(); break;
        case 3:  parsePlayerCount(); break;
        case 4:  parseSessionInit(); break;
        case 5:  parseServerMessage(); break;
        // ... see Section 3
    }
    cw += recordSize;  // Advance past record body
    cv++;              // Increment record counter
}
```

---

## 3. Message Types (Record Types)

### 3.1 Complete Message Type Table

| Type | Name | Handler | Direction | Description |
|------|------|---------|-----------|-------------|
| 1 | ACK | (internal) | S→C | Acknowledgment; re-reads if (cB & 1) == 0 |
| 2 | PLAYER_INFO_SHORT | f(6) | S→C | Player info update (6-byte payload) |
| 3 | PLAYER_COUNT | C() | S→C | Player count update |
| 4 | SESSION_INIT | y() | S→C | Full session initialization data |
| 5 | SERVER_MESSAGE | z() | S→C | Server text message (chat/system) |
| 10 | PLAYER_INFO_FULL_A | f(1) | S→C | Full player info (team A) |
| 11 | PLAYER_INFO_FULL_B | f(0) | S→C | Full player info (team B) |
| 12 | MATCH_START | B() | S→C | Match starting with map data |
| 13 | PLAYER_INFO_3 | f(3) | S→C | Player info variant (3-byte) |
| 14 | PLAYER_INFO_4 | f(4) | S→C | Player info variant (4-byte) |
| 15 | PLAYER_INFO_7 | f(7) | S→C | Player info variant (7-byte) |
| 20 | LOBBY_LIST | w() | S→C | Lobby/room list |
| 21 | MAP_DATA | x() | S→C | Map data blob |
| 22 | UNIT_LIST_INIT | j(0) | S→C | Unit list initialization (cE=0 then 1) |
| 23 | VICTORY_VALUES | H() | S→C | Victory condition values |
| 25 | GAME_PARAMETERS | I() | S→C | Game parameters (7 x short) |
| 26 | MAP_VALIDATION | J() | S→C | Map validation data |
| 30 | GAME_STATE | D() | S→C | Full game state sync |
| 32 | PLAYER_INFO_2 | f(2) | S→C | Player info variant (2-byte) |
| 33 | GAME_RESULT | G() | S→C | Game result (scores) |
| 34 | PLAYER_INFO_8 | f(8) | S→C | Player info variant (8-byte) |
| 40 | ARMY_LIST_INIT | k(0) | S→C | Army list initialization (cE=0 then 1) |
| 41 | MAP_LIST | K() | S→C | Map list for selection |
| 42 | UPGRADE_DATA | L() | S→C | Upgrade/tech data |
| 50 | TOWN_DATA_SINGLE | N() | S→C | Single town data update |
| 51 | TOWN_DATA_FULL | M() | S→C | Full town data batch |
| 60 | PLAYER_BRIEF | m(0) | S→C | Brief player summary |
| 61 | CHAT_HISTORY | P() | S→C | Chat message history |
| 62 | CHAT_ENABLE | (flag) | S→C | Enable chat (set k=1) |
| 70 | RANK_DATA | A() | S→C | Ranking data |
| 71 | PLAYER_DETAILED | l(0) | S→C | Detailed player info |
| 72 | ARMY_LIST_FULL | O() | S→C | Full army list |
| 90 | ALLY_LIST | Q() | S→C | Ally/coalition list |
| 91 | PLAYER_INFO_5 | f(5) | S→C | Player info variant (5-byte) |
| 100 | TOWN_LIST | R() | S→C | Town list (large batch) |
| 101 | GAME_TICK | (flag) | S→C | Game tick marker (set l=1) |
| 102 | LOBBY_LIST_ALT | w() | S→C | Lobby list alternative path |

### 3.2 Negative Message Types (Error/System)

The `z.java` request queue checks for negative q values:

| Type | Name | Description |
|------|------|-------------|
| -2 | ERR_MATCHMAKING | Matchmaking error |
| -5 | ERR_CONNECTION | Connection error |
| -8 | ERR_TIMEOUT | Timeout error |

When negative types are received:
- If `S() == 0` (offline) or `y.ag == 0` (not authenticated), the error triggers cleanup
- If not in a game room (`aO != 16`), error `y.u` is set to the absolute value and screen transitions to error page (63)
- `cu` (cursor position) is reset to 0

---

## 4. Message Type Details

### 4.1 Type 4: SESSION_INIT (y())

The most important initialization message. Contains full game session data:

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    4     int32    Game ID (BE)
0x04    1     byte     Status flags (S)
0x05    1     byte     Turn counter (cn)
0x06    1     byte     Player count (ag)
0x07    ag    byte[]   Player IDs (af[])
0x07+ag 4     int32    Turn income (ci)
0x0B+ag 4     int32    Turn expenses (cj)
0x0F+ag 4     int32    Turn delta (ck)
0x13+ag 6     byte[]   Production levels (aQ[0..5])
0x19+ag 6     short[]  Production caps (aR[0..2], 3 x short BE)
0x1F+ag 4     int32    Current player ID (cm)
0x23+ag 1     byte     Alliance count (at)
0x24+ag at    byte[]   Alliance data (as[])
0x24+ag+at 1  byte     Feature count (ai)
0x25+ag+at ai byte[]   Feature data (ah[])
0x25+ag+at+ai 2  short Server string length prefix (BE)
0x27+ag+at+ai N  byte[] Server string (cz)
0x27+ag+at+ai+N 1 byte Feature flags (T)
0x28+ag+at+ai+N 1 byte Sub-features (U)
0x29+ag+at+ai+N 1 byte Alliance flags (al)
0x2A+ag+at+ai+N 1 byte Name count (an)
0x2B+ag+at+ai+N an byte[] Names (am[])
...     4     int32    Rank score (cl)
...     1     byte     Sync interval (seconds, *1000 → ms) → y
...     1     byte     Disconnect timeout (seconds, *1000 → ms) → D
...     1     byte     Matchmaking timeout (seconds, *1000 → ms) → C
...     1     byte     Game mode flags (V)
...     1     byte     (padding)
...     2     short    Unknown (co)
...     3     byte[]   Map size (aV[0..2])
...     1     byte     Server name length
...     N     byte[]   Server name string
```

After parsing, `U()` is called to persist session state to RMS (Record Management Store).

### 4.2 Type 5: SERVER_MESSAGE (z())

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    2     short    Message length (BE)
0x02    N     byte[]   Message text (Latin-1)
```

### 4.3 Type 12: MATCH_START (B())

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    1     byte     Game mode (m)
0x01    4     int32    Game ID (n)
0x05    1     byte     Server string length
0x06    N     byte[]   Server string (bT)
0x06+N  2     short    Map data length (BE)
0x08+N  M     byte[]   Map data
```

Triggers transition to screen 31 (matchmaking wait).

### 4.4 Type 30: GAME_STATE (D())

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    2     short    Turn number (BE) - must match y.Q[0]
0x02    1     byte     (separator)
0x03    2+N   var      Map tile data (length-prefixed)
...     2+M   var      Unit data (length-prefixed)
```

Contains two length-prefixed blobs copied to `y.bS[]`:
1. Map/tile state data
2. Unit/army state data

After copying, `E()` is called which parses the bS buffer into tile and unit structures.

### 4.5 Type 33: GAME_RESULT (G())

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    4     int32    Player 0 score (BE) → y.W[0]
0x04    4     int32    Player 1 score (BE) → y.W[1]
```

Sets `u = 0`, triggers transition to screen 41 (game result).

### 4.6 Type 3: PLAYER_COUNT (C())

```
Offset  Size  Type     Description
------  ----  -------  -----------
0x00    1     byte     Player count (if >= 1)
0x01    1     byte     Team assignment (T)
```

---

## 5. Encryption & Obfuscation

### 5.1 Custom Base64 Alphabet

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

This is used in the `a(byte[], int, int)` method to encode 3 bytes into 4 characters.

### 5.2 XOR Stream Cipher (Outbound Data)

The `a(int value, int size, boolean encrypt)` method in p.java applies a stream cipher to outbound data:

```
For each byte position i:
  1. Extract byte: byteVal = (value >> ((size-1-i)*8)) & 0xFF
  2. If encrypt:
     byteVal ^= key_table[161 + cN] ^ (session_key >> (24 - cO*8))
     cO = (cO + 1) & 3        // Rotate through 4 bytes of session key
     cN = (cN + 1) % 15       // Rotate through 15 key bytes
  3. Accumulate checksum: cP ^= byteVal << (cQ * 8); cQ ^= 1
  4. Write byte to output buffer
  5. Every 3 bytes, apply custom Base64 encoding (a(s, t-3, 3))
```

**Key components:**
- `key_table`: Game data table (`y.bO.K[]`) at offset `L[161]`
- `session_key` (`t`): Random 32-bit value generated per request
- `cN`: Rotating index (0..14) into key table
- `cO`: Rotating index (0..3) into session key bytes
- `cP/cQ`: Checksum accumulator

### 5.3 XOR Stream Cipher (Data at Rest)

The `a(byte[])` method encrypts data for storage:

```
1. Generate random 32-bit seed: seed = random() & 0x7FFFFFFF
2. For each byte i: encrypted[i] = data[i] ^ (seed >> ((i & 3) << 3))
3. Append seed as 4 bytes: encrypted[data.length + 0..3] = seed bytes
```

Decryption (`b(byte[])`) reverses by extracting the last 4 bytes as seed, then XORing.

### 5.4 HTTP XOR Cipher (aa.java)

The HTTP handler uses a 15-byte XOR key (`h[15]`) for communication:

```
For each byte received:
  decrypted = h[i] ^ read()
  i = (i + 1) % 15
```

The key is initialized to `-1` (0xFF) for all positions by default.

### 5.5 String Obfuscation

Most strings are stored as byte arrays and decoded at runtime:
- Byte arrays like `{72, 101, 108, 108, 111}` are converted character-by-character
- HTTP URLs are stored with length-prefix byte format: `{0, N, byte1, byte2, ...}`
- Template placeholders use `[bracket]` syntax: `[uid]`, `[gid]`, etc.

---

## 6. Authentication Flow

### 6.1 Connection Sequence

```
Client                                    Server
  |                                         |
  |  1. TCP Connect (host:47584-47588)      |
  |---------------------------------------->|
  |                                         |
  |  2. Socket Options: keepAlive=true       |
  |     recvBufSize=1                       |
  |                                         |
  |  3. Start Sender Thread (m)             |
  |  4. Start Receiver Thread (o)           |
  |                                         |
  |  5. Send Login Message (type=1)         |
  |---------------------------------------->|
  |                                         |
  |  6. Receive SESSION_INIT (type=4)       |
  |<----------------------------------------|
  |                                         |
  |  7. Process game state, validate         |
  |     player ID against S() - 12321       |
  |                                         |
```

### 6.2 Login Message Construction

The login string is built from:
- Server host selection (y.dG or y.dH)
- Player ID (stored in RMS "new_id" or read from file "f")
- Session key from payment system (q.java)

The `aD.a(message, type)` call in k.java enqueues the login message:
- `message` = concatenated server/player info string
- `type` = 1 (for login) or other type codes

### 6.3 Player ID Validation

The `S()` method returns `Q.a() - 12321`, which is the player's unique ID derived from a stored value minus the constant 12321.

### 6.4 Payment Verification (q.java)

The payment/License verification:
1. Computes hash of device ID + game version
2. XORs all character values together
3. Compares against expected checksum
4. Sets `e` flag (licensed/not licensed)

---

## 7. Matchmaking Flow

### 7.1 Matchmaking State Machine

```
[Main Menu] → aO=0/7
     |
     | (user selects "Online Game")
     v
[Lobby] → aO=14
     |
     | (user selects "Find Game" or "Create Room")
     v
[Game Room] → aO=16
     |
     | (sends matchmaking request)
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
```

### 7.2 Matchmaking Request Construction (p() method)

The `p(int)` method (not fully decompilable, 2618 instructions) constructs the outbound game message. Based on the `r` parameter (y.bR.r):

| r Value | Message Type | Description |
|---------|-------------|-------------|
| 0 | General update | Periodic game state update |
| 30 | Game state sync | Full game state synchronization |
| 31 | Turn update | Turn-based game update |
| 36 | Quick match | Quick matchmaking request |
| 38 | Match search | Search for match |
| 39 | Match join | Join a match |
| 60 | Room message | Chat/room message |
| 66 | Auth/validation | Authentication/validation request |

---

## 8. Game Session Synchronization Model

### 8.1 Synchronization Approach

The game uses a **server-authoritative lockstep** model:

1. **Turn-based**: The game advances in discrete turns tracked by `y.Q[0]`
2. **Server authority**: All game state changes are validated by the server
3. **Periodic sync**: Game state is synchronized at configurable intervals
   - `y` (sync interval): Default 15 seconds, range 2–60 seconds
   - `C` (matchmaking timeout): Default 5 seconds, range 1–40 seconds  
   - `D` (disconnect timeout): Default 14 seconds, range 2–60 seconds

### 8.2 Sync Timing

The game loop (k.java D() method) manages timing:

```java
// Offline/single-player mode:
if (c == 0 && (d || b + L[4]*1000 <= now)) {
    // Generate AI moves, advance turn
    c = 1;
    aB.b();  // Queue network message
}

// Online multiplayer mode:
if (c == 3 && ah >= (Q[0]+1) * L[5]*8) {
    // Advance turn
    Q[0]++;
    c = 0;
    b = now;
}
```

### 8.3 State Synchronization

When a GAME_STATE (type 30) message arrives:
1. Verify turn number matches `y.Q[0]`
2. Copy map data and unit data to `y.bS[]` buffer
3. Call `E()` to parse the buffer into tile/unit structures
4. Set game mode to 3 (synced)
5. Reset counters

### 8.4 Error Recovery

When synchronization fails:
- `y.u = 7` → Display connection error
- `y.u = 9` → Display matchmaking error  
- If `aO == 14` (lobby) and not in game room, transition to screen 63 (error)
- The `B` counter in z.java tracks consecutive failures (max 10 before giving up)

---

## 9. Disconnection Handling

### 9.1 Automatic Detection

| Component | Threshold | Action |
|-----------|-----------|--------|
| Receiver (o.java) | >3 consecutive read errors | Disconnect, log "Server disconnected" |
| Sender (m.java) | >3 consecutive write errors | Disconnect, log "too many errors" |
| Network Manager (e.java) | 10 failed connection attempts | Give up connecting |
| Request Queue (z.java) | 10 failed requests | Set `v = false`, show error dialog |

### 9.2 Reconnection Logic

The e.java thread manages reconnection:

```java
while (h) {  // Main thread loop
    sleep(100ms);
    if (i) {  // Reconnection requested
        if (!b) {  // Not connected
            a();   // Attempt connection (up to 10 retries)
        }
        b(j, k);  // Send queued message
        i = false;
    }
}
```

### 9.3 Graceful Disconnect

The `b()` method in e.java:
1. Set `b = false` (connected flag)
2. Stop sender thread (`g.a()`)
3. Stop receiver thread (`f.a()`)
4. Sleep 100ms
5. Close DataInputStream
6. Close DataOutputStream
7. Close socket connection (`c.e()`)
8. Log "Socket closed" / "Disconnected"

---

## 10. HTTP Fallback Protocol

### 10.1 Three-Phase HTTP Communication

The `aa.java` HTTP handler executes a 3-phase protocol:

```
Phase 0 (e=0): Initial handshake
  - Send HTTP request
  - Wait up to 10 seconds for response
  - Increment request counter (d++)

Phase 1 (e=1): Authentication/confirmation
  - Send HTTP request
  - Wait up to 5 seconds for response
  - Increment request counter (d++)

Phase 2 (e=2): Data exchange
  - Send HTTP request
  - Wait up to 30 seconds for response
  - If timeout: disconnect
```

### 10.2 HTTP Data Encoding

HTTP responses are decoded using the 15-byte XOR cipher:
- Each byte is XORed with the rotating key
- The key index advances by 1 for each byte, wrapping at 15

### 10.3 HTTP URLs

| Purpose | URL Pattern |
|---------|-------------|
| Login info | `http://wap.herocraft.com/login/?show_info=1` |
| Payment donate | `http://content.herocraft.com/cds/servlet/payment/gmarket/donate?oid=[o]&uid=[u]&sid=[s]` |
| Payment info | `http://content.herocraft.com/cds/servlet/payments/inpinfo?gid=[gid]&system=[sys]&m=[mcc]&c=[cc]&uid=[uid]&pl=[p]&t=[t]&ch=[ch]&l=[l]` |
| Game credits | `aow2online_v2_credits_` (byte array D) |
| Game rank | `aow2online_v2_rank_` (byte array E) |
| Update check | `http://update.herocraft.com/jad/` |

---

## 11. Ad SDK Integration (InnerActive)

### 11.1 InnerActive M2M Protocol

The game integrates the InnerActive (now ironSource) mobile ad SDK:

**Request URL:** `http://m2m1.inner-active.com/simpleM2M/clientRequestAd`

**Parameters:**
| Param | Value |
|-------|-------|
| aid | Application ID |
| v | `Sm2m-1.5.1` (SDK version) |
| po | Port number |
| w | Screen width (optional) |
| h | Screen height (optional) |
| cid | Client ID (persistent, stored in RMS "sgiuyq") |
| ua | User agent (from system property) |

### 11.2 Ad Display Flow

1. **Request ad**: HTTP GET to InnerActive server
2. **Parse XML response**: Extract `<tns:Image>` (ad image URL), `<tns:URL>` (click URL), `<tns:Text>` (ad text)
3. **Download ad image**: HTTP GET to image URL
4. **Display**: Show image with click-through URL
5. **Cache client ID**: Store `Client Id` attribute in RMS for subsequent requests

### 11.3 Error Handling

- XML parsing errors: Log "error ia [message]"
- Invalid error codes: Throw "ia-stat-[code]"
- Network failures: Silently fail, retry on next cycle
