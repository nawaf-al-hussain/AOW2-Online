# Art of War 2 Online - Session Lifecycle Documentation

## 1. Complete Session Lifecycle Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  1. APP LAUNCH                                                       │
│     ├── Load resources (images, fonts, maps)                         │
│     ├── Read RMS data (player ID, settings, payment state)           │
│     ├── Initialize game engine (k.java constructor → C())            │
│     │   ├── Create game state (a.java)                               │
│     │   ├── Create world state (w.java)                              │
│     │   ├── Create font system (f.java)                              │
│     │   ├── Create request queue (z.java)                            │
│     │   ├── Create HTTP handler (aa.java)                            │
│     │   ├── Create player info (q.java)                              │
│     │   └── Create network manager (e.java)                          │
│     └── Enter main loop (D())                                        │
│                                                                      │
│  2. MAIN MENU (aO=0/7)                                               │
│     ├── Display splash/intro                                         │
│     ├── User selects "Online Game"                                   │
│     └── Transition to Login/Connect                                  │
│                                                                      │
│  3. LOGIN & CONNECT                                                   │
│     ├── Construct login string                                       │
│     ├── e.a(hostname, type) → TCP connect                            │
│     │   ├── Resolve: artofwaronline.herocraft.com                    │
│     │   ├── Port: 47584 + random(0-4)                               │
│     │   ├── Socket options: keepAlive=true                           │
│     │   ├── Start sender thread (m.java)                             │
│     │   ├── Start receiver thread (o.java)                           │
│     │   └── Retry up to 10 times on failure                          │
│     ├── Send login message (type=1)                                  │
│     ├── Receive SESSION_INIT (type=4)                                │
│     │   ├── Parse game ID, player data                               │
│     │   ├── Validate player ID (S() - 12321)                        │
│     │   ├── Parse game parameters                                    │
│     │   └── Persist to RMS (U())                                     │
│     └── Transition to Lobby (aO=14)                                  │
│                                                                      │
│  4. LOBBY (aO=14)                                                    │
│     ├── Receive LOBBY_LIST (type=20/102)                             │
│     ├── User actions:                                                │
│     │   ├── Browse rooms                                             │
│     │   ├── Create room → aO=16                                      │
│     │   ├── Join room → aO=16                                        │
│     │   ├── Quick match → aO=55                                      │
│     │   └── View rankings → aO=54                                    │
│     └── Periodic lobby refresh                                       │
│                                                                      │
│  5. GAME ROOM (aO=16)                                                │
│     ├── Room chat enabled                                            │
│     ├── Host configures game settings                                │
│     ├── Receive PLAYER_INFO (types 10,11,13,14,15,32,91)            │
│     ├── Host starts game                                             │
│     │   ├── Send game start request                                  │
│     │   └── Wait for MATCH_START (type=12)                           │
│     └── Transition to Match Wait                                     │
│                                                                      │
│  6. MATCHMAKING                                                       │
│     ├── Quick Match (aO=55/56):                                      │
│     │   ├── Send search request                                      │
│     │   ├── Wait for MATCH_START (type=12)                           │
│     │   └── 60s timeout → return to lobby                           │
│     ├── Custom Match (aO=31/39):                                     │
│     │   ├── Send join request                                        │
│     │   ├── Receive game configuration                               │
│     │   └── Wait for all players ready                               │
│     └── On MATCH_START:                                              │
│         ├── Parse game mode, map data                                │
│         ├── Initialize world state                                   │
│         └── Transition to Game (aO=17)                               │
│                                                                      │
│  7. GAME SESSION                                                      │
│     ├── Initialization:                                              │
│     │   ├── Receive MAP_DATA (type=21)                               │
│     │   ├── Receive GAME_PARAMETERS (type=25)                        │
│     │   ├── Receive VICTORY_VALUES (type=23)                         │
│     │   ├── Receive GAME_STATE (type=30)                             │
│     │   ├── Receive UNIT_LIST (type=22)                              │
│     │   ├── Receive ARMY_LIST (type=40/72)                           │
│     │   └── Receive PLAYER_INFO variants                             │
│     │                                                                 │
│     ├── Active Game Loop:                                            │
│     │   ├── Periodic sync: every (sync_interval) ms                  │
│     │   │   ├── Build game state message (p() method)                │
│     │   │   ├── Apply XOR stream cipher                              │
│     │   │   ├── Apply custom Base64 encoding                         │
│     │   │   ├── Enqueue in triple buffer (z.b())                     │
│     │   │   └── Send via TCP (m.java) or HTTP (aa.java)             │
│     │   │                                                            │
│     │   ├── Receive server updates:                                  │
│     │   │   ├── GAME_STATE (type=30): Full state sync                │
│     │   │   ├── PLAYER_INFO (types 2,10-15,32,91): Player updates    │
│     │   │   ├── GAME_TICK (type=101): Turn marker                    │
│     │   │   ├── CHAT_HISTORY (type=61): Chat messages                │
│     │   │   └── TOWN_DATA (types 50,51,100): Town updates            │
│     │   │                                                            │
│     │   └── Turn advancement:                                        │
│     │       ├── Verify turn number matches y.Q[0]                    │
│     │       ├── Apply server-authoritative state                     │
│     │       └── Advance local turn counter                           │
│     │                                                                 │
│     ├── Sub-screens:                                                 │
│     │   ├── aO=17: Main game screen                                  │
│     │   ├── aO=18: Unit selection/management                         │
│     │   ├── aO=19: Battle view                                       │
│     │   ├── aO=20/107: Map overview                                  │
│     │   ├── aO=22/120: Battle view (detailed)                        │
│     │   ├── aO=43/111: Building management                           │
│     │   ├── aO=44/112: Town management                               │
│     │   └── aO=45: World map                                         │
│     │                                                                 │
│     └── Game end:                                                    │
│         ├── Receive GAME_RESULT (type=33)                            │
│         ├── Parse scores (y.W[0], y.W[1])                           │
│         └── Transition to Result (aO=41)                             │
│                                                                      │
│  8. RESULT SCREEN (aO=41)                                            │
│     ├── Display final scores                                         │
│     ├── Show ranking changes                                         │
│     ├── User continues → return to Lobby (aO=14)                    │
│     └── Save stats to RMS                                            │
│                                                                      │
│  9. DISCONNECTION (any state)                                        │
│     ├── Triggered by:                                                │
│     │   ├── Receiver errors > 3 (o.java)                            │
│     │   ├── Sender errors > 3 (m.java)                              │
│     │   ├── HTTP request failures > 10 (z.java)                     │
│     │   ├── Sync timeout (no GAME_STATE for 60s)                    │
│     │   └── User-initiated quit                                      │
│     ├── Error state:                                                 │
│     │   ├── y.u = 7 (connection error)                              │
│     │   ├── y.u = 9 (matchmaking error)                             │
│     │   └── Transition to Error (aO=63)                              │
│     ├── Cleanup:                                                     │
│     │   ├── Stop sender thread                                       │
│     │   ├── Stop receiver thread                                     │
│     │   ├── Close input/output streams                               │
│     │   ├── Close socket                                             │
│     │   ├── Clear request queue                                      │
│     │   └── Log "Disconnected"                                       │
│     └── Recovery:                                                    │
│         ├── User can retry connection                                │
│         └── Network manager thread continues running for reconnect   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. Detailed Authentication Flow

### 2.1 Step-by-Step

```
Time    Client                                          Server
─────   ──────                                          ──────
T+0     Load player ID from RMS ("new_id")                
T+1     Initialize payment check (q.java)                 
T+2     Compute device hash (XOR of all chars)            
T+3     Verify license (q.a(int) method)                  
T+4     TCP Connect to host:port                          
        ──────────────────────────────────────────────►   
T+5     Set socket options (keepAlive)                     
T+6     Start sender/receiver threads                      
T+7     Build login string:                                
          - Player ID                                      
          - Device identifier                              
          - Game version (c.g = version as int)            
          - Platform info (c.f)                            
          - Payment status                                 
T+8     Encrypt login string:                              
          - Generate random session key (t)                
          - Apply XOR stream cipher                        
          - Apply custom Base64 encoding                   
T+9     Send login packet (type=1)                         
        ──────────────────────────────────────────────►   
T+10                                                    Receive login
T+11                                                    Validate credentials
T+12                                                    Build session init
T+13    Receive SESSION_INIT (type=4)                      
        ◄──────────────────────────────────────────────   
T+14    Parse session data:                                
          - Game ID                                        
          - Player assignments                             
          - Game parameters                                
          - Timing configuration                           
T+15    Validate player ID (S() - 12321)                   
T+16    Persist session to RMS (U())                        
T+17    Set game mode = 3 (online)                          
T+18    Transition to lobby screen                         
```

### 2.2 Login Failure Paths

| Failure | Detection | Result |
|---------|-----------|--------|
| TCP connect fails | Exception in e.a() | Retry up to 10 times, then show error |
| No SESSION_INIT | 60s timeout in k.java | y.u=7, show error screen 63 |
| Invalid player ID | S() returns 0 | Reconnect attempt or show login |
| Version mismatch | c.g comparison | Redirect to update URL |
| Payment failure | q.b() returns 0 | Restrict to demo mode |

## 3. Matchmaking Flow Detail

### 3.1 Quick Match (aO=55)

```
Client                                          Server
  │                                                │
  │  1. User selects "Quick Match"                 │
  │     a(55, true) → transition to screen 55     │
  │                                                │
  │  2. Send search request                        │
  │     bR.r = 38                                  │
  │     p(-1) → build + send message               │
  │──────────────────────────────────────────────► │
  │                                                │
  │  3. Wait for match                             │
  │     Timer: 60 seconds (bR.r == 60)             │
  │     If timeout → return to lobby               │
  │                                                │
  │  4. Receive MATCH_START (type=12)              │
  │◄────────────────────────────────────────────── │
  │                                                │
  │  5. Parse match data:                          │
  │     - Game mode (m)                            │
  │     - Game ID (n)                              │
  │     - Server string (bT)                       │
  │     - Map data                                 │
  │                                                │
  │  6. Transition to screen 31 (match wait)       │
  │     a(31, true)                                │
  │                                                │
  │  7. Receive GAME_STATE (type=30)               │
  │◄────────────────────────────────────────────── │
  │                                                │
  │  8. Initialize game world                      │
  │     - Parse map tiles                          │
  │     - Parse unit positions                     │
  │     - Set game mode = 3                        │
  │                                                │
  │  9. Transition to game screen (aO=17)          │
  │                                                │
```

### 3.2 Custom Match (aO=16)

```
Client                                          Server
  │                                                │
  │  1. User creates/joins room (aO=16)            │
  │                                                │
  │  2. Receive LOBBY_LIST (type=20)               │
  │◄────────────────────────────────────────────── │
  │                                                │
  │  3. Room chat enabled                          │
  │     Send chat messages (type=1 via aD.a())     │
  │──────────────────────────────────────────────► │
  │                                                │
  │  4. Host configures settings                   │
  │     - Map selection                            │
  │     - Game mode                                │
  │     - Player slots                             │
  │                                                │
  │  5. Receive PLAYER_INFO updates                │
  │◄────────────────────────────────────────────── │
  │                                                │
  │  6. Host starts game                           │
  │     bR.r = 39                                  │
  │──────────────────────────────────────────────► │
  │                                                │
  │  7. Continue as Quick Match from step 4        │
  │                                                │
```

## 4. Game Synchronization Detail

### 4.1 Turn-Based Sync Cycle

```
            ┌─────────────────────────────────────────────┐
            │           Game Sync Cycle                    │
            │                                              │
Time: 0ms   │  1. Game loop tick begins                    │
            │     ├── Read input                           │
            │     ├── Process AI (if c==0 && timer)        │
            │     └── Build state message                  │
            │                                              │
Time: ~5ms  │  2. Enqueue state update                     │
            │     ├── z.b() → triple buffer write          │
            │     └── z.g() → HTTP send (if TCP fails)     │
            │                                              │
Time: ~50ms │  3. Receive server state                     │
            │     ├── o.b() → dequeue from receiver        │
            │     ├── Parse records (v() method)           │
            │     └── Apply server-authoritative state     │
            │                                              │
Time: ~80ms │  4. Render frame                             │
            │     ├── Update display                       │
            │     └── Frame rate control (100ms target)    │
            │                                              │
Time: ~100ms│  5. Sleep until next tick                    │
            │     └── c.a(100 - elapsed)                   │
            │                                              │
            └─────────────────────────────────────────────┘
```

### 4.2 State Message Construction (p() method)

The `p(int)` method constructs outbound messages with the following structure:

```
Outbound Message Structure:
┌────────────────────────────────────────────────────┐
│  1. Random seed (4 bytes)                          │
│     t = random() & 0x7FFFFFFF                      │
├────────────────────────────────────────────────────┤
│  2. Message type identifier                        │
│     r = 0 (general), 30 (game state), etc.         │
├────────────────────────────────────────────────────┤
│  3. Encrypted game state data                      │
│     - For each field:                              │
│       a. Extract byte value                        │
│       b. XOR with key_table[161 + cN]              │
│       c. XOR with session_key >> (24 - cO*8)      │
│       d. Rotate cN (mod 15) and cO (mod 4)        │
│       e. Accumulate checksum (cP/cQ)              │
│       f. Every 3 bytes → custom Base64 encode     │
├────────────────────────────────────────────────────┤
│  4. Checksum (4 bytes, custom Base64 encoded)      │
│     - Built from cP accumulator                    │
├────────────────────────────────────────────────────┤
│  5. Player ID (variable, encrypted)                │
│     - XOR encrypted same as step 3                 │
├────────────────────────────────────────────────────┤
│  6. Turn counter (variable, encrypted)             │
│     - XOR encrypted same as step 3                 │
└────────────────────────────────────────────────────┘
```

### 4.3 Turn Advancement Logic

```java
// From k.java D() method:

// Single-player / AI mode:
if (c == 0 && (d || b + L[4]*1000 <= now)) {
    d = false;
    l = now;
    az.g();       // Generate AI moves
    az.c();       // Calculate result
    az.ab();      // Determine winner
    
    if (az.ab == 1) {
        // Player wins
        a.g(Q[0] < 2 ? 3 : ((Q[0]-2) % 14) + 4);
    }
    c = 1;         // Switch to waiting state
    aB.b();        // Queue network message
    az.w = now;    // Update last action time
}

// Multiplayer mode:
if (c == 3 && ah >= (Q[0]+1) * L[5]*0 * 8) {
    Q[0]++;        // Advance turn
    if (az.ab == 1) {
        a.g(Q[0] < 2 ? 1 : -1);
    }
    c = 0;         // Switch back to active
    b = now;       // Reset timer
}
```

## 5. Disconnection & Reconnection

### 5.1 Disconnect Triggers

| Trigger | Detection Point | Error Code | Recovery |
|---------|----------------|------------|----------|
| Socket closed | o.java read error | d > 3 | e.b() full disconnect |
| Write failure | m.java write error | f > 3 | e.b() full disconnect |
| HTTP phase timeout | aa.java | z.B >= 10 | Show error dialog |
| Server kick | Type -5/-8 response | y.u = abs(q) | Return to lobby |
| Sync timeout | 60s no GAME_STATE | y.u = 7 | Error screen 63 |
| App background | Android lifecycle | aO preserved | Resume on return |

### 5.2 Reconnection Sequence

```
1. Detect disconnection
   ├── Receiver: >3 errors → e.b()
   ├── Sender: >3 errors → e.b()
   └── Game loop: exception in v() → y.u = 7

2. Cleanup
   ├── Stop sender (g.a() → e = false)
   ├── Stop receiver (f.a() → c = false)
   ├── Close DataInputStream
   ├── Close DataOutputStream
   ├── Close socket (c.e())
   └── Log "Disconnected"

3. User notification
   ├── Show error screen (aO=63)
   └── Display error message

4. User-initiated reconnect
   ├── User taps "Retry"
   ├── e.a(login_string, type) → new connection
   │   ├── New socket to host:port
   │   ├── New sender/receiver threads
   │   └── Send login message
   └── Receive new SESSION_INIT
```

### 5.3 Session Persistence

When a SESSION_INIT (type=4) is received, the game persists critical data:

```java
// U() method - Save to RMS "aow2olhc"
DataOutputStream.write(game_id);          // int32
DataOutputStream.writeByte(player_turn);   // byte
DataOutputStream.writeByte(game_week);     // byte
DataOutputStream.writeByte(3);             // version
DataOutputStream.writeByte(player_count);  // byte
DataOutputStream.write(alliance_flags);    // byte[]
DataOutputStream.writeByte(feature_count); // byte
DataOutputStream.write(feature_data);      // byte[]
DataOutputStream.writeInt(version_code);   // int32
DataOutputStream.writeByte(status_flags);  // byte
DataOutputStream.writeByte(various_flags); // byte × 7
DataOutputStream.writeByte(server_name_len);
DataOutputStream.write(server_name);
DataOutputStream.writeByte(alliance_count);
DataOutputStream.write(alliance_data);

// Encrypt before storing:
encrypted = a(byteArrayOutputStream.toByteArray());
// → XOR with random seed, append seed
```

This allows the game to restore state after an unexpected disconnection or app restart.

## 6. HTTP Fallback Detail

### 6.1 When HTTP Path Is Used

The HTTP path via z.java + aa.java is used when:
1. TCP connection is unavailable
2. TCP sends fail repeatedly
3. The game is in a state where HTTP is preferred (lobby updates, chat)

### 6.2 Three-Phase HTTP Protocol

```
Phase 0: Handshake (timeout: 10s)
┌────────┐                         ┌────────┐
│ Client │─── POST/GET ──────────► │ Server │
│        │                         │        │
│        │◄── Response (200) ──────│        │
└────────┘                         └────────┘

Phase 1: Authentication (timeout: 5s)
┌────────┐                         ┌────────┐
│ Client │─── POST/GET ──────────► │ Server │
│        │  (with auth token)      │        │
│        │                         │        │
│        │◄── Response (200) ──────│        │
└────────┘                         └────────┘

Phase 2: Data Exchange (timeout: 30s)
┌────────┐                         ┌────────┐
│ Client │─── POST/GET ──────────► │ Server │
│        │  (with game data)       │        │
│        │                         │        │
│        │◄── Response (200) ──────│        │
│        │  (XOR-encrypted data)   │        │
└────────┘                         └────────┘
```

### 6.3 Error Handling in HTTP Path

```
if Phase 2 times out:
    if authenticated and has_player_id:
        reconnect()  // Try TCP reconnect
    if in_lobby and not_in_room:
        y.u = 9      // Matchmaking error
        a(63, true)  // Error screen
    else:
        disconnect_socket()
```

## 7. Chat System

### 7.1 Chat Message Flow

```
User types message (p.java a(ad) method)
    │
    ├── Filter characters:
    │   - Allow: a-z, A-Z, 0-9, '.', '_', '@', '#'
    │   - Replace: \, {, |, }, \x90, \x91, \x92 → 'A'
    │   - Convert: Cyrillic (1040-1103) → shift by -848
    │   - Truncate: max 25 chars (online) or 50 chars (local)
    │
    ├── Store in chat buffer: y.aw[y.az + i]
    │
    ├── If in game room (aO=16):
    │   ├── Set cR = true (send pending)
    │   ├── Set ak = 16
    │   └── Trigger immediate send
    │
    └── Send via network:
        ├── Build message string
        ├── Enqueue in e.a(message, type=1)
        └── Sender thread transmits
```

### 7.2 Chat Reception

Chat messages arrive as:
- Type 5 (SERVER_MESSAGE): System notifications
- Type 61 (CHAT_HISTORY): Chat history batch

Chat text uses `&` as line separator and `&\n` for paragraph breaks.

## 8. Payment & Monetization Flow

### 8.1 SMS Payment (c.java)

```
1. Check if SMS is available: "SMS_JAVA" property
2. Get phone number from "wireless.messaging.sms.smsc"
3. Build payment URL with device info
4. HTTP GET to payment server
5. Parse response: b=base_url, p=partner_code
6. Send SMS with payment code
```

### 8.2 Google Play Payment (q.java)

```
1. Check payment availability (q.a())
2. Get partner code from server
3. Display payment dialog with options
4. Process via Google Play IAP
5. Verify purchase on server
```

### 8.3 InnerActive Ads (u.java)

```
1. Initialize with app ID and screen dimensions
2. HTTP GET to InnerActive ad server
3. Parse XML response:
   - <tns:Image>: Ad banner image URL
   - <tns:URL>: Click-through URL
   - <tns:Text>: Ad text
   - Client Id: Persistent client identifier
4. Download and display ad image
5. On click: Open browser with click URL
6. Cache client ID in RMS "sgiuyq"
```
