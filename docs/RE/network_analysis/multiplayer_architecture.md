# Art of War 2 Online - Multiplayer Architecture

## 1. System Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                      Android Device                         │
│                                                             │
│  ┌──────────────┐   ┌──────────────┐   ┌───────────────┐   │
│  │  Game Loop    │   │  Game State  │   │  Renderer     │   │
│  │  (k.java)    │──▶│  (a.java)    │──▶│  (f.java)     │   │
│  │  Thread       │   │  (p.java)    │   │  (UI layer)   │   │
│  └──────┬───────┘   └──────┬───────┘   └───────────────┘   │
│         │                  │                                 │
│         ▼                  ▼                                 │
│  ┌──────────────┐   ┌──────────────┐                        │
│  │  Map/AI       │   │  Network     │                        │
│  │  (w.java)    │   │  Queue       │                        │
│  │              │   │  (z.java)    │                        │
│  └──────────────┘   └──────┬───────┘                        │
│                            │                                 │
│                     ┌──────┴───────┐                         │
│                     │              │                         │
│              ┌──────▼──────┐ ┌─────▼──────┐                 │
│              │  TCP Path    │ │  HTTP Path  │                │
│              │  (e.java)   │ │  (aa.java)  │                │
│              │  m (sender)  │ │            │                │
│              │  o (receiver)│ │            │                │
│              └──────┬──────┘ └─────┬──────┘                 │
│                     │              │                         │
└─────────────────────┼──────────────┼─────────────────────────┘
                      │              │
              ┌───────▼──────┐ ┌─────▼──────────┐
              │  Game Server  │ │  HTTP Servers   │
              │  TCP          │ │  (herocraft.com)│
              │  47584-47588  │ │                 │
              └──────────────┘ └─────────────────┘
```

## 2. Core Components

### 2.1 Class Hierarchy

```
Object
├── p.java (abstract base: game screen/state)
│   └── a.java (main game state: handles all game logic & net messages)
├── k.java (game engine: main loop, initialization, orchestration)
├── e.java (network manager: TCP socket thread)
│   ├── m.java (sender thread)
│   └── o.java (receiver thread)
├── z.java (request queue: triple-buffered)
├── aa.java (HTTP connection handler)
├── w.java (world/map state)
├── f.java (font/text rendering)
├── q.java (payment/licensing)
├── g.java (Base64 codec)
├── r.java (connection factory wrapper)
├── y.java (global constants & static data)
├── c.java (utility: HTTP, RMS, config)
└── u.java (InnerActive ad SDK integration)

Main package:
├── au.java (Socket connection implementation)
├── ba.java (HTTP connection implementation)
├── c.java (Connection factory)
└── al.java (Connection interface)
```

### 2.2 Thread Architecture

| Thread | Class | Priority | Purpose |
|--------|-------|----------|---------|
| Main Game Loop | k.java (D()) | Normal | Render, AI, input, state machine |
| Network Manager | e.java | Normal | Connection management, reconnection |
| TCP Sender | m.java | Normal | Outbound message queue → socket |
| TCP Receiver | o.java | Normal | Socket → inbound message queue |
| Request Queue | z.java | Normal | Triple-buffered HTTP request processing |
| HTTP Handler | aa.java | Normal | Per-request HTTP communication |
| Ad SDK | u.java | Background | InnerActive ad fetching |

### 2.3 Inter-Thread Communication

```
Game Loop ──▶ z.java ──▶ aa.java ──▶ HTTP Server
   │              │
   │              └──▶ e.java ──▶ m.java ──▶ TCP Server
   │                               │
   ▼                               ▼
   o.java ◀── TCP Server     (inbound)
   │
   ▼
Game State (p.java / a.java)
```

**Synchronization mechanisms:**
- `volatile` fields on shared state (bp, bq, br, bs, bt in k.java)
- `synchronized` blocks on Vector bF (payment queue)
- Vector-based queues in m.java (sender) and o.java (receiver)
- Polling with sleep delays (100-500ms) rather than wait/notify

---

## 3. Network Manager (e.java) Details

### 3.1 Connection Lifecycle

```
                    ┌───────────────┐
                    │  Construction  │
                    │  e()           │
                    │  start()       │
                    └───────┬───────┘
                            │
                    ┌───────▼───────┐
                    │   Run Loop    │◀─────────────────┐
                    │   sleep(100)  │                  │
                    └───────┬───────┘                  │
                            │                          │
                    ┌───────▼───────┐                  │
                    │  i == true?   │                  │
                    │  (send req?)  │                  │
                    └───┬───────┬───┘                  │
                   No   │       │ Yes                  │
                        │       │                      │
                  ┌─────▼──┐  ┌─▼──────────┐          │
                  │ sleep  │  │ connected?  │          │
                  │ again  │  │ (b == true) │          │
                  └────────┘  └─┬───────┬──┘          │
                           No    │       │ Yes          │
                          ┌──────▼──┐    │             │
                          │ Connect │    │             │
                          │ a()     │    │             │
                          │ (retry  │    │             │
                          │  10x)   │    │             │
                          └────┬────┘    │             │
                               │         │             │
                          ┌────▼─────────▼──┐          │
                          │  b(j, k)        │          │
                          │  Send message    │          │
                          │  via sender      │          │
                          └────┬─────────────┘          │
                               │                        │
                          ┌────▼────────┐               │
                          │ i = false   │───────────────┘
                          │ (done)      │
                          └─────────────┘
```

### 3.2 Connection Parameters

```java
// Connection attempt:
socket = r.a("socket://" + hostname + ":" + port, 3, true);
// Equivalent to: new Socket(hostname, port)

// Port calculation:
port = 47584 + (random.nextInt() & 0xFFFFFF) % 5;
// Results in ports: 47584, 47585, 47586, 47587, 47588

// Socket options (set immediately after connect):
socket.a((byte) 2, 1);  // setKeepAlive(true)
```

### 3.3 Connection Factory (r.java / c.java)

The `r.java` class delegates to `com.herocraft.game.artofwar2ol.c`:

```java
// Protocol routing:
if (url.startsWith("http://"))  → new ba(url, mode)   // HTTP
if (url.startsWith("socket://")) → new au(url)         // TCP Socket
if (url.startsWith("sms://"))   → null                  // Not supported
else                             → new w(url, mode)     // Generic
```

---

## 4. Triple-Buffered Request Queue (z.java)

### 4.1 Architecture

The request queue uses a 3-slot circular buffer to decouple the game loop from network I/O:

```
Slot 0:  c[0] (byte[])  d[0] (short=length)  e[0] (int=player)  f[0] (int=turn)
Slot 1:  c[1] (byte[])  d[1] (short=length)  e[1] (int=player)  f[1] (int=turn)
Slot 2:  c[2] (byte[])  d[2] (short=length)  e[2] (int=player)  f[2] (int=turn)

l = write pointer (next slot to write)
m = read pointer (next slot to read)
```

### 4.2 Write Path (Game Loop → Queue)

```java
// b() method - enqueue a request:
1. If all 3 slots are full (d[0..2] all > 0), clear queue (a())
2. Find next free slot (d[i3] == 0)
3. Copy string data to slot's byte array
4. Store player ID (e[i3] = r) and turn counter (f[i3] = t)
5. Advance write pointer: l = (i3 + 1) % 3
```

### 4.3 Read Path (Queue → HTTP/Send)

```java
// g() method - process queued requests:
1. While (l == m && d[m] <= 0): return (nothing to process)
2. If d[m] > 0:
   a. Set q = 2 (sending)
   b. Copy data from slot m to local buffer g[]
   c. Record h = e[m] (player), i = f[m] (turn)
   d. Record k = t.q() (game state)
   e. Record j = m (slot index)
   f. Call h() → sends via HTTP
   g. If success: clear slot (c()), reset B counter
   h. If failure: increment B, sleep 2500ms, retry up to 10 times
3. Advance: m = (m + 1) % 3
```

### 4.4 HTTP Three-Phase Send (h() method)

```
Phase 0: cC = 1, e = 0 → Send initial request → Wait up to 10s
Phase 1: cC++, e = 1   → Send confirmation    → Wait up to 5s
Phase 2: cC++, e = 2   → Send data            → Wait up to 30s
```

If Phase 2 times out:
- If authenticated (S() > 0) and have player ID (ag > 0): reconnect
- If in lobby (aO == 14) and not in room: set u = 9, go to error screen
- Otherwise: disconnect socket

---

## 5. Sender Thread (m.java)

### 5.1 Architecture

```java
// Two Vectors for outbound messages:
Vector b = new Vector();  // Message strings
Vector c = new Vector();  // Message type bytes

// Error tracking:
int f = 0;  // Consecutive error count
```

### 5.2 Send Loop

```
while (e) {  // running flag
    if (b.isEmpty()) {
        sleep(500ms);  // Idle wait
    } else {
        // Dequeue first message:
        String msg = b.firstElement();
        Byte type = c.firstElement();
        
        // Write packet:
        if (msg.length() == 0)
            writeByte(0);           // Type 0 for empty
        else
            writeByte(type);        // Actual type byte
        writeShort(msg.length() + 2);  // Total length
        writeShort(msg.length());       // String length
        write(convertToBytes(msg));     // String data
        flush();
        
        // Remove from queue:
        b.removeElement(msg);
        c.removeElement(type);
        
        // Error tracking:
        if (error) {
            f++;
            if (f > 3) {
                // Too many errors, disconnect
                a.b();  // networkManager.disconnect()
                return;
            }
        }
        
        sleep(100ms);  // Rate limiting
    }
}
```

---

## 6. Receiver Thread (o.java)

### 6.1 Architecture

```java
DataInputStream b;     // Socket input stream
Vector e = new Vector();  // Received message queue
int d = 0;             // Error counter
boolean c = true;       // Running flag
```

### 6.2 Receive Loop (partially decompilable)

The receiver reads packets matching the sender format:
1. Read type byte
2. Read length short
3. Read data short
4. Read data bytes
5. Enqueue as byte[] in Vector e
6. If read error: increment d, if d > 3 disconnect

### 6.3 Data Consumption

```java
// b() method - called by game loop:
byte[] data = null;
if (e != null && !e.isEmpty()) {
    Object first = e.firstElement();
    e.removeElement(first);
    data = (byte[]) first;
}
return data;
```

---

## 7. Game State Machine (p.java / a.java)

### 7.1 Screen States (aO values)

```
                  ┌────────────────────┐
                  │   0/7: Main Menu   │◀─────────────────┐
                  └────────┬───────────┘                  │
                           │                              │
              ┌────────────┼────────────┐                 │
              ▼            ▼            ▼                 │
     ┌────────────┐ ┌───────────┐ ┌──────────┐          │
     │ 8: Login   │ │ 14: Lobby │ │ 54: Rank │          │
     └────────────┘ └─────┬─────┘ └──────────┘          │
                          │                               │
                    ┌─────▼──────┐                        │
                    │ 16: Room   │────────────────────┐   │
                    └─────┬──────┘                    │   │
                          │                           │   │
            ┌─────────────┼─────────────┐             │   │
            ▼             ▼             ▼             │   │
   ┌────────────┐ ┌────────────┐ ┌──────────┐        │   │
   │ 55: Search │ │ 31: Wait   │ │ 63: Err  │        │   │
   │ 56: Search │ │ 39: Match  │ │          │        │   │
   └─────┬──────┘ └─────┬──────┘ └──────────┘        │   │
         │              │                              │   │
         └──────┬───────┘                              │   │
                ▼                                      │   │
        ┌──────────────┐                               │   │
        │ 17: Game     │                               │   │
        │ 18: Units    │                               │   │
        │ 19: Battle   │                               │   │
        │ 20: Map      │                               │   │
        │ 22: Battle   │                               │   │
        │ 43: Building │                               │   │
        │ 44: Town     │                               │   │
        │ 45: World    │                               │   │
        └───────┬──────┘                               │   │
                │                                      │   │
        ┌───────▼──────┐                               │   │
        │ 41: Result   │───────────────────────────────┘   │
        │ 33: Score    │                                   │
        └──────────────┘                                   │
                                                           │
        ┌──────────────┐                                   │
        │ 9/10: Chat   │───────────────────────────────────┘
        │ 11: Chat     │
        │ 57: Clan     │
        │ 48: Profile  │
        │ 47: Shop     │
        │ 60: Editor   │
        │ 88: Alliance │
        │ 90: Allies   │
        └──────────────┘
```

### 7.2 Network-Driven State Transitions

| Trigger | From | To | Condition |
|---------|------|----|-----------|
| Type 12 (MATCH_START) | Any | 31 | Match data received |
| Type 33 (GAME_RESULT) | Game | 41 | Scores received |
| Type 30 (GAME_STATE) | Wait | Game | State sync complete |
| Type 4 (SESSION_INIT) | Login | Game/Lobby | Session established |
| Connection lost | Game | 63 | Exception in v() |
| Error response | Lobby | 63 | z.java B counter >= 10 |
| User action | Any | 125 | Dialog/menu transition |
| Type 5 (SERVER_MSG) | Any | Any | Display message |

### 7.3 Game Mode (c field in k.java)

| Value | Mode | Description |
|-------|------|-------------|
| 0 | Connecting | Waiting for initial connection |
| 1 | Pre-game | Game state loading, AI setup |
| 2 | (unused) | — |
| 3 | In-game | Active multiplayer session |

---

## 8. World State (w.java)

### 8.1 Map Data

The world state is managed by w.java, which tracks:
- Map dimensions (a, b)
- Camera position (c, d)
- Tile grid data
- Unit positions and ownership
- Resource/fog of war state

### 8.2 Synchronization

Map data is sent via:
- Type 21 (MAP_DATA): Full map blob
- Type 30 (GAME_STATE): Incremental updates with tile+unit data

---

## 9. Error Recovery & Resilience

### 9.1 Connection Error Categories

| Category | Detection | Recovery |
|----------|-----------|----------|
| Socket error | Exception in read/write | Disconnect, reconnect on next cycle |
| Server disconnect | >3 consecutive errors | Full disconnect, user must reconnect |
| HTTP timeout | Phase timeout (10/5/30s) | Retry up to 10 times with 2.5s delay |
| Authentication failure | Invalid S() result | Return to login screen |
| Matchmaking failure | Negative q values | Show error, return to lobby |
| Version mismatch | Build number check | Redirect to update URL |

### 9.2 Data Integrity

- **Turn sequence validation**: GAME_STATE (type 30) verifies turn number matches y.Q[0]
- **XOR checksum**: Built into outbound encoding (cP/cQ fields)
- **Custom Base64 encoding**: Detects corruption in 3-byte groups
- **Session key rotation**: Changes with each request for stream cipher
- **Persistent storage**: Key session data saved to RMS after each SESSION_INIT

### 9.3 Anti-Cheat Measures

1. **Server authority**: All game state changes validated server-side
2. **Encrypted protocol**: XOR stream cipher with rotating keys
3. **Custom encoding**: Non-standard Base64 alphabet prevents casual packet inspection
4. **Integrity checksum**: F() method computes game state hash for validation
5. **Payment verification**: q.java validates license via XOR hash of device ID
6. **String validation**: Input sanitization in chat (p.java a() method)
