# Art of War 2 Online - Source Code Analysis

## Executive Summary

Art of War 2 Online is an Android RTS game developed by HeroCraft. The codebase is a **J2ME MIDlet port to Android**, using a custom game engine built directly on Android Canvas/SurfaceView APIs. The code is heavily obfuscated with single/two-letter class names across 3 screen-resolution variant packages (s0, s1, s2).

**Critical Finding**: The s0/s1/s2 packages are **NOT factions** — they are **screen resolution variants** (small/medium/large screens), selected at runtime based on device screen width. The `bb` class defines the mapping:
- s0: screens ≤320px wide (portrait orientation)
- s1: screens =320px wide (landscape orientation)  
- s2: screens >320px wide (portrait orientation)

---

## Architecture Overview

### High-Level Architecture
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

### Rendering Pipeline
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

### Inheritance Hierarchy (Main Package)
```
Activity
  └── Application

SurfaceView + SurfaceHolder.Callback
  └── an (abstract midlet view)
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

---

## Complete Class Mapping - Main Package

| Class | Purpose | Key Evidence |
|-------|---------|-------------|
| `Application` | Android Activity entry point | `extends Activity`, creates `AppCtrl`, delegates lifecycle |
| `AppCtrl` | Main controller / game thread | `implements Runnable`, creates `midletview`, loads game class via reflection, manages lifecycle |
| `bh` | Abstract MIDlet base class | `extends` by `aow2ol`, reads `/rsu` manifest, verifies "HeroCraft" vendor |
| `_EMPTY_MIDLET_` | Null MIDlet stub | Empty implementations of `bh` abstract methods |
| `an` | Abstract SurfaceView (MIDlet View) | `extends SurfaceView`, `implements SurfaceHolder.Callback`, dispatches touch/key events to Canvas |
| `h` | Concrete SurfaceView renderer | `extends an`, uses `SurfaceHolder.lockCanvas()/unlockCanvasAndPost()`, translates/clips Canvas |
| `z` | Abstract game Canvas/Screen | `extends bv`, game screen base with input methods (keyPress, touch), `bb`/`bc` = width/height |
| `bv` | Abstract Displayable | Base for all displayable screens, manages visibility (`bd` flag), `ba` = command listener |
| `bu` | Canvas + Resource loader | `extends z`, asset decryption (XOR cipher), key remapping |
| `aq` | Android Graphics adapter | `extends v`, wraps `android.graphics.Canvas` for drawing operations |
| `v` | Abstract Graphics interface | Drawing primitives: `drawImage`, `drawString`, `drawRect`, `drawLine`, `clipRect`, `setClip`, color management |
| `q` | Image/Bitmap wrapper | Wraps `android.graphics.Bitmap`, factory methods for creating/loading images |
| `by` | Font manager | `Paint`/`Typeface` management, font caching via Hashtable, UTF string decoder |
| `bd` | Display Manager | Manages `currentDisplayable` / `currentCanvas`, screen switching, Vibrator service |
| `bb` | Screen Configuration | Defines s0/s1/s2 asset paths, package prefixes, orientations |
| `ad` | Command/Button | Game command with label, type (1-8), priority |
| `ag` | Command Listener interface | `void a(ad)` callback |
| `as` | Command Listener interface (alternate) | `void b(ad)` callback |
| `t` | Abstract UI Item | Base for all form items, LinearLayout wrapper, text, commands |
| `b` | Image Item | `extends t`, wraps `ImageView`, bitmap display |
| `ak` | Text Item | `extends t`, wraps `TextView`, text display |
| `cd` | Text Input Field | `extends t`, wraps `EditText`, text input with IME |
| `x` | Choice Group | `extends t`, wraps `RadioGroup`/`CheckBox`, option selection |
| `br` | Gauge/Progress | `extends t`, progress indicator, signature verification helper |
| `bz` | Abstract Dialog/Form | `extends bv`, dialog-based UI with buttons, ScrollView |
| `af` | Dialog (Form) | `extends bz`, full dialog with items, payment UI |
| `am` | Empty Dialog | `extends bz`, no-op dialog implementation |
| `be` | Dialog UI Controller | `implements Runnable, OnClickListener, OnKeyListener`, manages dialog lifecycle on UI thread |
| `bf` | Purchase Selection Dialog | `extends Dialog`, billing item selection with buttons |
| `r` | Dialog creation Runnable | Creates `bf` on UI thread |
| `ah` | Item Click Handler | `implements OnClickListener`, delegates to command listener |
| `i` | Record Store (Persistence) | File-based key-value storage using `DataInputStream`/`DataOutputStream`, `.datrms` files |
| `cg` | Purchase Record (abstract) | Fields: id, notificationId, productId, orderId, purchaseTime, developerPayload |
| `l` | Purchase Record (concrete) | `extends cg`, serializable purchase data |
| `ae` | Billing Manager | Google Play in-app billing, purchase verification, persistent purchase storage |
| `BillingService` | Android Billing Service | `extends Service`, `ServiceConnection`, handles Market billing requests |
| `ac` | Abstract Billing Request | Base for billing API calls (CHECK_BILLING_SUPPORTED, REQUEST_PURCHASE, etc.) |
| `a` | Confirm Notifications Request | `extends ac`, CONFIRM_NOTIFICATIONS billing request |
| `aa` | Restore Transactions Request | `extends ac`, RESTORE_TRANSACTIONS billing request |
| `bk` | Get Purchase Info Request | `extends ac`, GET_PURCHASE_INFORMATION billing request |
| `bx` | Request Purchase | `extends ac`, REQUEST_PURCHASE billing request, handles PendingIntent |
| `o` | Check Billing Supported | `extends ac`, CHECK_BILLING_SUPPORTED billing request |
| `bo` | Billing Service Stub | `extends Binder implements m`, IPC stub for Market billing service |
| `bc` | Billing Service Proxy | `implements m`, IPC proxy for billing service |
| `m` | Billing AIDL Interface | `IInterface`, `Bundle a(Bundle)` for billing IPC |
| `bm` | Billing Result Enum | RESULT_OK, RESULT_USER_CANCELED, RESULT_SERVICE_UNAVAILABLE, etc. |
| `s` | Purchase State Enum | PURCHASED, CANCELED, REFUNDED |
| `ar` | Security/Signature Verifier | RSA SHA1withRSA signature verification, nonce management, purchase JSON parsing |
| `at` | Billing Broadcast Handler | Routes billing intents to BillingService |
| `CommonReceiver` | Broadcast Receiver | Routes INSTALL_REFERRER and billing broadcasts |
| `aw` | Billing Callback Interface | `boolean a(int, String)` |
| `bi` | Base64 Decoder | Custom Base64 implementation for billing signature verification |
| `cf` | Base64 Exception | Custom exception for Base64 errors |
| `k` | UI Utility / Dialog Runner | AlertDialog creation, Toast display, resource stream helper, device ID |
| `n` | Dialog Key/Click Handler | `OnClickListener + OnKeyListener` for blocking dialogs |
| `c` | Connection Factory | Creates HTTP, SMS, or Socket connections based on URL scheme |
| `au` | Socket Connection | `implements bl`, TCP socket with DataInputStream/DataOutputStream, socket options |
| `ba` | HTTP Connection | `extends bt implements e`, HttpURLConnection wrapper |
| `w` | URL Connection Base | `implements az`, URLConnection wrapper for generic HTTP |
| `bt` | Content-Length Connection | `extends w implements ay`, adds `getContentLength()` |
| `al` | Connection interface | `void e()` close |
| `ao` | Output Stream interface | `DataOutputStream c()` |
| `bp` | Input Stream interface | `DataInputStream a()`, `InputStream b()` |
| `az` | Full Connection interface | `extends bp, ao` (input + output) |
| `ay` | Content-Length interface | `extends az`, `long d()` content length |
| `e` | HTTP Connection interface | `extends ay` |
| `bl` | Socket Connection interface | `extends az` |
| `y` | Connection Exception | `extends IOException` |
| `d` | Base Game Exception | `extends Exception` |
| `ab` | RecordStore Not Found | `extends d` |
| `ap` | RecordStore Full | `extends d` |
| `p` | RecordStore Invalid ID | `extends d` |
| `bq` | RecordStore Not Open | `extends d` |
| `j` | Checkbox Change Listener | `OnCheckedChangeListener` (no-op) |
| `f` | Contact Picker AsyncTask | `extends AsyncTask`, picks phone number from Contacts |
| `aj` | EditText Creator | `Runnable` that creates EditText on UI thread |
| `bj` | Text Input Handler | `TextWatcher + OnClickListener`, handles text input submit |
| `bg` | CRC Hash Helper | Wrapper combining cd, byte[], String |
| `g` | CRC32 Utility | `extends CRC32`, checksum computation |
| `cb` | Verification Helper | Static utility for signature/billing verification |
| `x` | RadioGroup/Checkbox UI | `extends t`, choice group with RadioButtons/CheckBoxes |
| `ax` | Marker Interface | Empty interface (tag/role marker) |
| `ag` | Command Listener Interface | `void a(ad)` |
| `as` | Command Listener Interface | `void b(ad)` |
| `bs` | Application Status Listener | `void d()` (resume), `void e()` (pause) |
| `av` | Zip Entry Helper | `extends ZipEntry`, CRC extraction from APK |
| `bn` | APK ZipFile | `extends ZipFile`, APK signature/CRC verification |
| `bw` | Verification Display | `extends bz`, package verification display |
| `bd` | Display/Vibrator Manager | Manages screen switching, Vibrator service, billing verification |
| `C0000R` | R class stub | Auto-generated resource ID class |
| `ai` | Request ID holder | `static long a = -1` |
| `cf` | Base64 Exception | Custom exception class |

---

## Complete Class Mapping - s0 Package (Small Screen ≤320px)

| Class | Purpose | Key Evidence |
|-------|---------|-------------|
| `aow2ol` | MIDlet Entry Point | `extends bh`, creates `k` (main game), `c` (config), `bd` (display) |
| `k` | **MAIN GAME CLASS** | `extends x implements Runnable`, massive class (~1500 lines), game loop, state machine, all game logic |
| `x` | Base Game Canvas | `extends bu`, key/touch event dispatch, one-shot paint trigger |
| `c` | Game Config / Resource Manager | HTTP URLs, version strings, RMS data, server config, asset loading, text processing |
| `a` | Game State / Screen Manager | `extends p`, initializes game data, manages screens, AI state, unit data, map data |
| `p` | Game Form Base | `implements ag`, game form with buttons/commands, screen navigation |
| `y` | **Global Game Data** | Massive static data: unit stats, map arrays, colors, game constants, server URLs |
| `w` | **Game Logic / Map Handler** | Map rendering, unit movement, AI, pathfinding, combat calculations (~large class) |
| `e` | **Network Manager** | `extends Thread`, socket connection to server, connect/disconnect, send/receive |
| `z` | **Network Request Queue** | `implements Runnable`, triple-buffered request queue, login/game request handling |
| `aa` | **HTTP Connection Handler** | `implements Runnable`, HTTP polling for game data, XOR decryption |
| `f` | Text / Font Manager | Font loading, text rendering, word wrapping, scrolling text |
| `d` | Custom Font Renderer | Bitmap font loading, character rendering, string measurement |
| `t` | Billing Callback | `implements aw`, routes billing results to game |
| `q` | Payment Handler | SMS/payment verification, code generation |
| `i` | **Audio Manager** | `implements bs`, MediaPlayer management, sound effects |
| `b` | MediaPlayer Wrapper | `extends MediaPlayer`, sound playback with loop count, seek handling |
| `m` | **Network Sender Thread** | `extends Thread`, DataOutputStream, packet framing (length prefix) |
| `o` | **Network Receiver Thread** | `extends Thread`, DataInputStream, packet reading with error recovery |
| `r` | Connection Factory Wrapper | Delegates to main `c.a()` connection factory |
| `g` | Base64 Decoder | Custom Base64 decode implementation |
| `h` | Bit Reversal / Hash | Integer bit reversal with XOR obfuscation (`-128255633` XOR key) |
| `n` | CRC32 Hash | Custom CRC32 implementation with polynomial `0xEDB88320` |
| `j` | Color Constants | Static color palette array for game rendering |
| `u` | **Ad/Interstitial Handler** | `implements Runnable, ag, as`, Inner-Active M2M ad SDK integration |
| `l` | SMS Sender | `extends BroadcastReceiver`, sends SMS for premium billing |
| `s` | Billing/AE Wrapper | Creates `ae` billing instance, wraps `AppCtrl.getResourceAsStream()` |
| `v` | Online Status Reporter | `implements Runnable`, HTTP reporting of online status to server |

---

## Complete Class Mapping - s1 Package (Medium Screen ~320px, Landscape)

| Class | Purpose | Notes |
|-------|---------|-------|
| `aow2ol` | MIDlet Entry Point | Uses `c` as main game, `u` as config |
| `c` | MAIN GAME CLASS | Same role as s0.k, extends `i` |
| `i` | Base Game Canvas | Same role as s0.x |
| `u` | Game Config / Resource Manager | Same role as s0.c |
| `a` | Game State / Screen Manager | Same role as s0.a, extends `p` |
| `p` | Game Form Base | Same as s0.p |
| `y` | Global Game Data | Same constants, different values for medium screen |
| `w` | Game Logic / Map Handler | Adapted for medium resolution |
| `n` | Network Manager | Same role as s0.e |
| `r` | Network Request Queue | Same role as s0.z |
| `aa` | HTTP Connection Handler | Same role as s0.aa |
| `f` | Text / Font Manager | Adapted font sizes for medium screen |
| `d` | Custom Font Renderer | Same logic, different bitmap sizes |
| `t` | Billing Callback | Same as s0.t |
| `q` | Payment Handler | Same as s0.q |
| `e` | Audio Manager | Same role as s0.i |
| `m` | Network Sender Thread | Same as s0.m |
| `o` | Network Receiver Thread | Same as s0.o |
| `g` | Base64 Decoder | Same as s0.g |
| `h` | Bit Reversal / Hash | Same as s0.h |
| `k` | CRC32 Hash | Same role as s0.n |
| `j` | Color Constants | Same as s0.j |
| `v` | Ad/Interstitial Handler | Same role as s0.u |
| `l` | SMS Sender | Same as s0.l |
| `s` | Billing/AE Wrapper | Same as s0.s |
| `x` | Online Status Reporter | Same role as s0.v |

---

## Complete Class Mapping - s2 Package (Large Screen >320px)

| Class | Purpose | Notes |
|-------|---------|-------|
| `aow2ol` | MIDlet Entry Point | Uses `q` as main game, `o` as config |
| `q` | MAIN GAME CLASS | Same role as s0.k, extends `p` |
| `p` | Base Game Canvas | Same role as s0.x, different class name from s1.p! |
| `o` | Game Config / Resource Manager | Same role as s0.c |
| `a` | Game State / Screen Manager | Same role as s0.a |
| `y` | Global Game Data | Adapted for large screen resolution |
| `z` | Game Logic / Map Handler | Same role as s0.w |
| `h` | Network Manager | Same role as s0.e |
| `y` | Network Request Queue | **CONFLICT**: Same name as Global Game Data! Different class. |
| `aa` | HTTP Connection Handler | Same role as s0.aa |
| `f` | Text / Font Manager | Adapted for large screen |
| `d` | Custom Font Renderer | Adapted for large screen |
| `t` | Billing Callback | Same as s0.t |
| `q` | Payment Handler | **CONFLICT**: Same name as MAIN GAME CLASS! Different class. |
| `i` | Audio Manager | Same role as s0.i |
| `m` | Network Sender Thread | Same as s0.m |
| `k` | Network Receiver Thread | Different name from s0.o |
| `g` | Base64 Decoder | Same as s0.g |
| `h` | Bit Reversal / Hash | **CONFLICT**: Same name as Network Manager! Different class. |
| `n` | CRC32 Hash | Same role as s0.n |
| `j` | Color Constants | Same as s0.j |
| `u` | Ad/Interstitial Handler | Same role as s0.u |
| `l` | SMS Sender | Same as s0.l |
| `s` | Billing/AE Wrapper | Same as s0.s |
| `w` | Online Status Reporter | Same role as s0.v |

**Note**: The s2 package appears to have naming conflicts in the decompiled output. This is likely because the obfuscator reuses names within different scopes, and the decompiler merges them. The actual bytecode would resolve these through different class references.

---

## Execution Flow Analysis

### 1. Application Startup
```
1. Android OS launches Application Activity
2. Application.onCreate() → creates AppCtrl(this)
3. AppCtrl.onCreate():
   a. Request window features (no title, fullscreen)
   b. Set window flags (FLAG_FULLSCREEN | FLAG_KEEP_SCREEN_ON)
   c. j() → Determine screen resolution, select s0/s1/s2 variant
   d. Create by instance (string decoder)
   e. Create midletview = new h(context)  [SurfaceView]
   f. setContentView(midletview)
   g. Create ad layout for billing
   h. c() → Set system properties (network operator, user agent)
   i. d() → Initialize billing if com.android.vending.BILLING permission exists
   j. e() → Start Google Analytics tracker (UA-25034252-1)
```

### 2. Game Thread Launch
```
4. AppCtrl.onStart() → creates Thread(this) at priority 10
5. AppCtrl.run():
   a. i() → Track first start in Google Analytics
   b. Wait for midletview.a == true (surface created)
   c. gc() → Force garbage collection
   d. Dynamic class loading:
      Class.forName("com.herocraft.game.artofwar2ol." + b + "aow2ol")
      where b = "s0." / "s1." / "s2." based on screen size
   e. Verify MIDlet-Vendor equals "HeroCraft"
   f. Set midlet = new aow2ol instance
   g. midlet.a(1) → Start/resume game
```

### 3. Game Initialization (s0 variant)
```
6. aow2ol.a() (first call):
   a. c.a(this) → Initialize config manager
   b. bd.a() → Get display manager
   c. new k(this) → Create main game canvas
   d. k.a() → Start game loop
```

### 4. Game Loop
```
7. k.run() (continuous):
   while (running) {
     - Process network data (e.c())
     - Update game state
     - Handle AI
     - Process animations
     - Handle user input
     - Render frame via aq (Android Canvas)
     - Thread.sleep() for frame rate control
   }
```

### 5. Rendering Flow
```
8. k.b(v) → called when canvas needs painting
   a. h.a(z) → SurfaceView render method
   b. Lock Canvas from SurfaceHolder
   c. Save Canvas state
   d. Clip to viewport
   e. Translate to scroll position
   f. Set Canvas on aq
   g. z.b(aq) → Call game paint method
   h. Restore Canvas state
   i. Unlock and post Canvas
```

### 6. Input Flow
```
9. an.dispatchTouchEvent():
   ACTION_DOWN → z.a(x, y)     → k.a(x, y)   → game touch handler
   ACTION_UP   → z.b(x, y)     → k.b(x, y)   → game release handler
   ACTION_MOVE → z.c(x, y)     → k.c(x, y)   → game drag handler
   (5px deadzone on MOVE to avoid jitter)
```

### 7. Network Flow
```
10. Game → e.a(server, type) → connect to socket://artofwaronline.herocraft.com:47584-47588
    Server: artofwaronline.herocraft.com
    Ports: 47584 + (random % 5)  → ports 47584-47588
    Protocol: Custom binary over TCP
    - m (Sender): writes [byte type][short length][short strLen][byte[] data]
    - o (Receiver): reads packets, queues them
    - z (Request Queue): triple-buffered async request processor
    - aa (HTTP): polling fallback via HTTP
```

---

## System Identification

### Rendering System
- **Engine**: Custom, built directly on Android Canvas API
- **Surface**: `SurfaceView` with `SurfaceHolder.Callback`
- **Double buffering**: Manual `lockCanvas()`/`unlockCanvasAndPost()`
- **Graphics adapter**: `aq` wraps `android.graphics.Canvas`
- **Image format**: `Bitmap.Config.RGB_565` (16-bit color, memory optimized)
- **Sprite rendering**: `v.a(q, x, y, anchor)` with anchor bits (LEFT/CENTER/RIGHT, TOP/BASELINE/BOTTOM)
- **Region drawing**: `v.a(q, sx, sy, sw, sh, dx, dy, transform)` with Matrix transforms
- **Text rendering**: Dual system - custom bitmap fonts (`d` class) and system fonts (`by` class)
- **Clipping**: `clipRect` with `Region.Op.INTERSECT` and `REPLACE`

### Input System
- **Touch**: `MotionEvent` dispatched to Canvas subclass
  - `a(x,y)` = pointerPressed (ACTION_DOWN)
  - `b(x,y)` = pointerReleased (ACTION_UP)
  - `c(x,y)` = pointerDragged (ACTION_MOVE, 5px deadzone)
- **Keyboard**: `KeyEvent` mapped via lookup table
  - Key codes 7-16 → digits 0-9
  - Key codes 17-18 → * #
  - Soft keys mapped to game actions
  - Volume keys (24, 25) filtered out

### Networking System
- **Protocol**: Custom binary TCP over raw sockets
- **Server**: `artofwaronline.herocraft.com` / `aow2.ru`
- **Ports**: 47584-47588 (randomly selected)
- **Connection**: `au` class wraps `java.net.Socket`
- **Packet format**: `[1 byte type][2 bytes total length][2 bytes string length][N bytes payload]`
- **Sender thread**: `m` class, queues messages in Vector
- **Receiver thread**: `o` class, reads into byte arrays
- **Error recovery**: Up to 3 errors before disconnect, auto-reconnect with 10 attempts
- **HTTP fallback**: `aa` class polls via HTTP for game data
- **XOR encryption**: `bu.b()` decrypts assets with rotating XOR key `[-96, -95, -94, -93, -92, -91, -90, -89]`
- **HTTP Ad SDK**: Inner-Active M2M ad integration at `m2m1.inner-active.com`

### Game Logic System
- **Main game class**: `k` (s0) / `c` (s1) / `q` (s2) - massive class with all game state
- **Map/Unit data**: `w` class handles map rendering, unit movement, combat
- **Game data**: `y` class holds all static configuration
- **AI**: Implemented within `w` class (RTS AI logic)
- **State machine**: Game screens managed via `a` class and `p` (form) subclasses
- **Game loop**: Thread-based with `System.currentTimeMillis()` timing

### Audio System
- **Engine**: `android.media.MediaPlayer`
- **Manager**: `i` class manages sound pool
- **Player**: `b` class extends `MediaPlayer` with loop counting
- **File formats**: `.o`, `.3`, `.m` extensions (loaded from assets)
- **Pause/resume**: Tracks current sound, resumes on app focus

### Payment System
- **Google Play Billing**: Full v1 API implementation via `ae`, `BillingService`, `ac` subclasses
- **SMS Billing**: `l` class sends premium SMS via `SmsManager`
- **Payment Handler**: `q` class manages payment codes and verification
- **Premium verification**: Code XOR, CRC32 hash, server-side validation

### Persistence System
- **Record Store**: `i` class implements J2ME RMS-like persistence
- **Storage**: Files named `{name}.datrms` and `{name}.datrms_{recordId}`
- **Data format**: `DataOutputStream` with `int` record count, `int` version, `long` timestamp
- **Purchase persistence**: `ae` class stores purchases in RMS, serialized with `DataOutputStream`

### Obfuscation / Anti-Tamper System
- **String obfuscation**: UTF-encoded byte arrays decoded at runtime via `by.a(byte[], true)`
  - Example: `{0, 27, 119, 105, 114, 101, 108, 101, 115, 115, ...}` → `"wireless.messaging.sms.smsc"`
- **Asset encryption**: XOR cipher with rotating 8-byte key for game assets
- **APK verification**: `bn`/`av` verify APK CRC32 against expected value
- **Signature verification**: `ar` class verifies RSA signatures on purchase data
- **Vendor check**: `bh.a("MIDlet-Vendor")` must equal `"HeroCraft"`
- **Bit obfuscation**: `h` class uses bit reversal + XOR (`-128255633`) for integer encoding
- **Hash verification**: `n` class implements CRC32 for code verification
- **Code verification**: Payment codes verified via CRC hash of device ID + game state

---

## Key Algorithms and Formulas

### 1. Screen Resolution Selection
```java
// AppCtrl.a(int i2) - determines screen variant
if (i2 < 320) return 0;  // s0 (small)
if (i2 > 320) return 2;  // s2 (large)
return 1;                  // s1 (medium)
```

### 2. Network Packet Format
```
[1 byte: message type]
[2 bytes: total packet length (big-endian)]
[2 bytes: string payload length (big-endian)]
[N bytes: string payload (UTF-8 char values as bytes)]
```

### 3. Asset Decryption (XOR Cipher)
```java
// bu.b() - decrypts game assets
byte[] key = {-96, -95, -94, -93, -92, -91, -90, -89};
int keyIndex = 0;
while ((read = inputStream.read()) != -1) {
    output.write(decrypt((byte)(read & 0xFF), key[keyIndex]));
    keyIndex = (keyIndex + 1 < key.length) ? keyIndex + 1 : 0;
}
// Key rotation after decryption: shift elements 3-5
for (int i = 3; i < 6; i++) key[i] = key[i + 1];
```

### 4. Integer Bit Obfuscation
```java
// h class - encodes integers with bit reversal + XOR
public int encode(int value) {
    int reversed = 0;
    for (int i = 0; i < 32; i++) {
        reversed |= ((value >>> (31 - i)) & 1) << i;
    }
    return 0xF5956A5F ^ reversed;  // -128255633
}

public int decode(int encoded) {
    int reversed = encoded ^ 0xF5956A5F;
    int result = 0;
    for (int i = 0; i < 32; i++) {
        result |= ((reversed >>> (31 - i)) & 1) << i;
    }
    return result;
}
```

### 5. CRC32 Implementation
```java
// n class - custom CRC32 with standard polynomial
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

### 6. Font Rendering (Bitmap Font)
```java
// d.a(v, str, x, y, anchor) - renders text using bitmap font
// Font data loaded from "/f0", "/f1", "/f2", "/f3" image + data files
// Character width stored in r[][] arrays
// Tracking/spacing added via u + s fields
// Special chars: 186 = color prefix, 187 = color suffix
```

### 7. Server Connection with Retry
```java
// e.a() - connect with up to 10 attempts, random port
public boolean connect() {
    boolean connected = false;
    int attempts = 0;
    while (!connected && attempts < 10) {
        connected = connect("artofwaronline.herocraft.com", 
                           47584 + (random.nextInt() & 0xFFFFFF) % 5);
        attempts++;
        if (connected) sleep(200);
    }
    return connected;
}
```

### 8. Game State Encoding
```java
// y class constants
// Unit type indices: ODB=0, RZA=1, GZA=2, METH=3
// Resource amounts: {10, 25, 50, 100} and {25, 50, 100}
// Time intervals: {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535} (minutes)
// Map tile size encoded in font metrics
```

---

## String Constants and Contexts

### Decoded String Constants (from byte arrays)

| Byte Array | Decoded String | Context |
|------------|---------------|---------|
| `{0, 27, 119, 105, ...}` | `"wireless.messaging.sms.smsc"` | System property for SMS |
| `{0, 21, 109, 105, ...}` | `"microedition.platform"` | System property for platform |
| `{0, 14, 104, 116, ...}` | `"http.keepAlive"` | HTTP connection pooling |
| `{0, 27, 99, 111, 109, ...}` | `"com.android.vending.BILLING"` | Billing permission |
| `{0, 13, 85, 65, 45, ...}` | `"UA-25034252-1"` | Google Analytics tracking ID |
| `{0, 9, 67, 56, 57, ...}` | `"C89FyDc47"` | Billing public key fragment |
| `{0, 2, 119, 117}` | `"wu"` | Verification key |
| `{0, 4, 47, 109, 46, 42}` | `"/m.*"` | Resource pattern |
| `{0, 6, -48, -67, -48, ...}` | `"Арт" (Cyrillic: "Art")` | Game title prefix |
| `{0, 11, 99, 108, 97, ...}` | `"classes.dex"` | DEX file reference |
| `{0, 1, 49}` | `"1"` | Version check |

### Game Server URLs

| URL | Purpose |
|-----|---------|
| `http://wap.herocraft.com/login/?show_info=1` | Login/info page |
| `http://content.herocraft.com/cds/servlet/payments/inpinfo?gid=[gid]&system=[sys]&m=[mcc]&c=[cc]&uid=[uid]&pl=[p]&t=[t]&ch=[ch]&l=[l]` | Payment info |
| `http://m2m1.inner-active.com/simpleM2M/clientRequestAd?aid=[id]&v=Sm2m-1.5.1&po=[port]` | Ad request (Inner-Active M2M) |
| `http://aow2.ru/srv/` | Game server base URL |
| Server: `artofwaronline.herocraft.com` | Game server hostname |

### Key Identifiers

| Value | Purpose |
|-------|---------|
| `"HeroCraft"` | Vendor verification string |
| `"GA_INST"` | Google Analytics first-start flag (SharedPreferences) |
| `"_nullUTFnull_"` | Null string encoding for network protocol |
| `"sgiuyq"` | Internal-Active ad storage key |
| `1161` | Premium SMS short code |
| `47584` | Base server port |

---

## Network Protocol Analysis

### Connection Establishment
1. Client resolves server: `artofwaronline.herocraft.com`
2. Random port selection: `47584 + (random % 5)` → ports 47584-47588
3. TCP socket connection with `setKeepAlive(true)`, `setTcpNoDelay(true)`
4. Login sequence initiated by `z` (request queue) class

### Packet Structure (Outgoing - m class)
```
Byte 0:     Message type (0 = empty, or custom type byte)
Bytes 1-2:  Total packet length (short, big-endian, includes header)
Bytes 3-4:  String payload length (short, big-endian)
Bytes 5-N:  String payload (each char as single byte)
```

### Request Queue (z class)
- Triple-buffered circular queue (3 slots)
- Each slot: `byte[] data`, `short length`, `int type`, `int timestamp`
- Priority-based processing
- Retry logic: up to 10 retries with 2.5s delay
- Error code 10401 shown on persistent failure

### HTTP Fallback (aa class)
- XOR-encrypted HTTP polling
- 15-byte rotating XOR key `h[]`
- Multi-step authentication:
  1. Connect + send credentials (10s timeout)
  2. Receive session (5s timeout)
  3. Data exchange (30s timeout)
- Receives game state updates via HTTP when socket unavailable

### Data Flow
```
Game Logic → z.b(str) → Queue request
           → m.run()   → Send packet [type][len][data]
           
Socket → o.run() → Receive packet → Queue byte[]
      → e.c()    → Dequeue byte[] → Game Logic processing
```

---

## Data Structure Analysis

### Game State (k/c/q class - main game)
- `int[][][][] Q` - 4D array: map tile data (layer, x, y, property)
- `byte[][] S` - Sprite/frame data
- `short[][] p` - Path/position data
- `com.herocraft.game.artofwar2ol.q[][] ap` - Image/sprite cache
- `short[][] aq, ar` - Unit position/health arrays
- `byte[][] as` - Unit type/state arrays

### Global Constants (y class)
- `byte[] a` (3000) - General purpose buffer
- `short[] b` (21) - Unit stats
- `byte[] h` (24000) - Map data
- `byte[][] g` (17×20) - Animation frames
- `byte[][] ce` (128×128) - Map tile lookup
- `short[][] bV` (2×7) - Faction data
- `byte[] bd` (16) - Unit ID mapping
- `byte[] aH = {1, 2, 5, 10, 0, 0, 0, 0}` - Resource costs/amounts
- `String[][] aJ` (3×50) - Localized strings
- `byte[] aK` (64) - Base64-like encoding table for obfuscation

### Unit Type System (d class)
- Static type IDs: `h=0, i=1, j=2, k=3, l=4, m=5`
- Type names: `{ODB, RZA, GZA, METH, "", ""}` 
- Sprite references: `{aO, aR, aU, aV, aZ, ba}`
- Units likely represent: **O**DB (base building?), **R**ZA (rifleman?), **G**ZA (gunner?), **METH** (method/vehicle?)

### Font Data (d class)
- Loaded from assets: `/f0`, `/f1`, `/f2`, `/f3`
- 4 font variants: Regular, Bold, Numeric, Symbol
- Character maps for Latin, Cyrillic, Digits, Special
- Anti-aliased rendering via system Paint when no bitmap font available

---

## Faction Differences Analysis

**KEY FINDING**: s0/s1/s2 are NOT game factions. They are **screen resolution variants** of the SAME game code.

### Evidence:
1. `bb.b = {"s0.", "s1.", "s2."}` are package prefixes, not faction identifiers
2. `bb.a = {"s0/", "s1/", "s2/"}` are asset directory paths for different resolution sprites
3. `bb.c = {1, 0, 1}` are screen orientations (1=portrait, 0=landscape)
4. `AppCtrl.j()` selects variant based on `Math.min(width, height)` compared to 320
5. Each package contains the SAME game with different pixel dimensions

### Resolution Variant Differences:
| Aspect | s0 (Small ≤320px) | s1 (Medium ~320px) | s2 (Large >320px) |
|--------|-------------------|--------------------|--------------------|
| Orientation | Portrait (1) | Landscape (0) | Portrait (1) |
| Asset path | s0/ | s1/ | s2/ |
| Main class | k extends x | c extends i | q extends p |
| Config class | c | u | o |
| Font size | Smallest | Medium | Largest |
| Sprite size | Smallest | Medium | Largest |
| Tile size | Smallest | Medium | Largest |

### Actual Game Factions (within the game logic):
The game data in `y` class contains:
- `short[][] bV = new short[2][7]` - **2 factions with 7 attributes each**
- This is the REAL faction system, stored within the game data arrays
- Factions appear to be differentiated by unit stats, not by code packages

---

## Anti-Piracy / License Verification

1. **Vendor Check**: Game verifies `MIDlet-Vendor == "HeroCraft"` on startup
2. **APK CRC Verification**: `bn`/`av` classes compute CRC32 of APK entries
3. **Signature Verification**: RSA SHA1withRSA for billing purchases
4. **Code Obfuscation**: All strings encoded as byte arrays, decoded at runtime
5. **Asset Encryption**: XOR cipher with rotating key for game data files
6. **Integer Obfuscation**: Bit reversal + XOR for sensitive values
7. **Dynamic Class Loading**: Game class loaded via reflection, harder to static analyze
8. **Billing Verification**: Server-side purchase validation with nonce-based protocol

---

## J2ME MIDlet Port Artifacts

The codebase clearly originated as a J2ME MIDlet and was ported to Android:
- `bh` = MIDlet base class
- `z` = Canvas (javax.microedition.lcdui.Canvas)
- `bv` = Displayable
- `bz` = Form/Dialog
- `t` = Item
- `ad` = Command
- `ag` = CommandListener
- `i` = RecordStore
- `c` = Connector
- `au` = SocketConnection
- `ba` = HttpConnection
- `q` = Image
- `v` = Graphics
- `d` = Font
- `bd` = Display
- `_EMPTY_MIDLET_` = placeholder MIDlet

The port maintains the J2ME architecture while wrapping Android APIs underneath.
