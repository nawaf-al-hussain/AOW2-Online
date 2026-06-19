# Art of War 2 Online - Reverse Engineering Roadmap

## Prioritized Investigation Order

This roadmap defines the optimal investigation sequence for reverse-engineering the Art of War 2 Online game. Priorities are based on dependency chains: each phase builds upon knowledge from prior phases, and higher-priority systems are required for lower-priority ones to function.

---

## Phase 1: Core Game Loop 🔴 CRITICAL

**Goal:** Understand the main execution flow from app launch to game render loop.

**Rationale:** Every other system is driven by the game loop. Without understanding tick timing, state transitions, and the update/render cycle, no other system can be properly analyzed in context.

### 1.1 Application Bootstrap
- **Target classes:** `Application.java`, `AppCtrl.java`, `bh.java`, `an.java`
- **Tasks:**
  - [ ] Map the full `onCreate()` → `AppCtrl.onCreate()` → `Class.forName()` chain
  - [ ] Document the screen variant selection logic (s0/s1/s2)
  - [ ] Trace how `midletview` (SurfaceView) connects to the game canvas
  - [ ] Identify where the game thread starts and how it synchronizes with the UI thread
- **Key questions:**
  - What triggers the transition from the Android Activity to the J2ME MIDlet lifecycle?
  - How does `bStateReady` / `bStatePause` affect the game loop?
  - What is the exact sequence: MIDlet.startApp() → Canvas.showNotify() → run()?

### 1.2 Game Loop Mechanics
- **Target classes:** `s0.k` (Main Game Class), `s0.x` (Base Game Canvas)
- **Tasks:**
  - [ ] Decompile and document the `run()` method of `s0.k` completely
  - [ ] Identify the tick timing mechanism (100ms base, speed multiplier)
  - [ ] Map the update/render frame structure
  - [ ] Document how input events are queued and processed per tick
  - [ ] Find the game speed variable and how `m` (speed multiplier) affects tick rate
- **Key questions:**
  - What is the exact tick rate and is it fixed or variable?
  - How are rendering and logic updates interleaved?
  - What happens during a pause/resume cycle?

### 1.3 State Machine
- **Target classes:** `s0.a` (Game State Manager), `s0.p` (Game Form Base)
- **Tasks:**
  - [ ] Map all 40+ screen states and their valid transitions
  - [ ] Document the state transition triggers (user actions, network events)
  - [ ] Find the current state variable and how it's stored
  - [ ] Identify which states are online-only vs. offline-available
- **Key questions:**
  - What is the data type and location of the current state variable?
  - Are there illegal state transitions that crash the game?
  - How does the dialog system (bz/af) interact with screen states?

### Deliverables
- Complete game loop flow diagram
- State machine transition table
- Tick timing documentation with speed multiplier effects

---

## Phase 2: Unit Systems 🟠 HIGH

**Goal:** Fully understand unit data structures, movement, and behavior.

**Rationale:** Units are the primary game entities. Understanding their data layout, type system, and movement mechanics is prerequisite to understanding combat, AI, and production.

### 2.1 Unit Data Structure
- **Target:** `ca[7272]` byte array in `s0.w`
- **Tasks:**
  - [ ] Verify and complete the offset table for all 50+ fields per unit slot
  - [ ] Document the meaning of every unknown field (currently unknown1, unknown2 at offsets 2626, 2727)
  - [ ] Map how building data overlaps/extends the unit array (indices > 100)
  - [ ] Document the `flags` field (offset 2828) bit meanings
  - [ ] Document the `flags2` field (offset 2929) bit meanings
  - [ ] Trace the `pathValid` field usage at offset 4545
- **Key questions:**
  - How are buildings stored in the same array as units? (indices -N and >100)
  - What is the full bit layout of the flags fields?
  - How does the `linkedUnit` / `prevLinked` system work for garrisoned units?

### 2.2 Unit Type System
- **Target:** `cf[][]` arrays, unit type masks
- **Tasks:**
  - [ ] Document the complete `cf[0][type]` animation type mapping
  - [ ] Document `cf[1][type]` vision/move range values
  - [ ] Document `cf[2][type]` base armour values
  - [ ] Map the infantry bitmask (16447) to actual unit type IDs
  - [ ] Map the machinery bitmask (16256) to actual unit type IDs
  - [ ] Determine what `type_class` field means for Rebels units
  - [ ] Find and document all 22+ unit type definitions
- **Key questions:**
  - What are unit types 5, 6, 12, 13, 14? (gaps in known types)
  - How does the siege_capable_mask work?
  - What determines the `availability_flag` values?

### 2.3 Movement and Pathfinding
- **Target:** `s0.w` pathfinding methods
- **Tasks:**
  - [ ] Reverse the pathfinding algorithm (likely A* or similar on 8-direction grid)
  - [ ] Document terrain cost calculation (`distanceTable & 7`)
  - [ ] Map the direction delta tables (`bT[offset + facing*2]`)
  - [ ] Understand the stuck detection mechanism (threshold: 5)
  - [ ] Document how path data is stored (pathStart/pathEnd offsets)
  - [ ] Trace pixel movement per animation frame
- **Key questions:**
  - What pathfinding algorithm is used?
  - How are obstacles and other units handled during pathfinding?
  - What triggers a path recalculation?

### Deliverables
- Complete unit data field reference
- Unit type classification table with all stat values
- Pathfinding algorithm documentation

---

## Phase 3: Combat Systems 🟠 HIGH

**Goal:** Fully understand damage calculation, projectile physics, and combat resolution.

**Rationale:** Combat is the core gameplay mechanic. Understanding it is essential for balance analysis, AI understanding, and any future server implementation.

### 3.1 Damage Calculation
- **Target:** `s0.w` methods `a(byte, int, int)` and `c(boolean)`
- **Tasks:**
  - [ ] Verify the damage formula: `max(min(baseDamage * (10 - armour) / 10, baseDamage - armour), 1)`
  - [ ] Document all damage tables (`cg[][]` array)
  - [ ] Map projectile type to damage values (`cg[0][type]`)
  - [ ] Document how weapon type affects damage vs different target categories
  - [ ] Trace the infantry vs machinery damage modifiers
  - [ ] Document building damage calculation (buildings have 0 base armour)
- **Key questions:**
  - Are there critical hits or random damage variation?
  - How does the `attack_bonus` field modify damage?
  - What are the exact damage multipliers for infantry→machinery attacks?

### 3.2 Armour System
- **Target:** `s0.w` method `l(int)` (line 1666)
- **Tasks:**
  - [ ] Document the full armour calculation including research bonuses
  - [ ] Map how `Y[player]` and `Z[player]` arrays store upgrade state
  - [ ] Trace the `hasInfantry` flag in upgrade bonus calculation
  - [ ] Document building armour from the `N[]` array
  - [ ] Verify the Confederation `fireproof_armour` effect
- **Key questions:**
  - How exactly does `fireproof_armour` affect Confederation units?
  - Can armour go below 0 or above 10?
  - Is there a hard minimum damage of 1?

### 3.3 Projectile System
- **Target:** Projectile arrays (t, u, v, w, A, B, C, E, G, etc.) in `s0.w`
- **Tasks:**
  - [ ] Document all 400 projectile slots and their field layout
  - [ ] Trace the projectile spawn calculation (velocity, flight time)
  - [ ] Document the pixel-to-grid conversion: `(v[idx] + 3000 + 15) / 30 - 100`
  - [ ] Map projectile types and their visual/audio effects
  - [ ] Document the artillery (type 10) special clamping behavior
  - [ ] Trace splash damage radius and falloff calculation
- **Key questions:**
  - What are all projectile types and their properties?
  - How is splash damage radius determined per projectile type?
  - What happens when a projectile's target moves or dies mid-flight?

### 3.4 Death and Rewards
- **Target:** Death handling in `s0.w`
- **Tasks:**
  - [ ] Document death animation selection (infantry vs machinery)
  - [ ] Map the `bi[]` and `bd[]` animation offset tables
  - [ ] Trace the kill reward calculation fully
  - [ ] Document score calculation on kills and captures
  - [ ] Map the effect spawning system (fire, smoke, debris effects)
- **Key questions:**
  - How does the `siege_targets` bitmask work?
  - What are all possible effect types and their IDs?
  - How is `distanceToEnemyBase` calculated for reward scaling?

### Deliverables
- Complete combat resolution flowchart
- Damage formula verification with examples
- Projectile physics documentation
- Reward/scoring system documentation

---

## Phase 4: Resource Systems 🟡 MEDIUM

**Goal:** Understand credit economy, power supply, and production capacity.

**Rationale:** Resources drive the strategic layer. Understanding income, costs, and power mechanics is needed for understanding build orders and AI decision-making.

### 4.1 Credit Economy
- **Target:** `W[]` and `X[]` arrays, `cb[][]` player stats
- **Tasks:**
  - [ ] Document the full income cycle (every 127 ticks)
  - [ ] Map the income formula: `(baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)`
  - [ ] Document credit costs for all units, buildings, and technologies
  - [ ] Trace the credit limit system (`Q[player]` = 120 with upgrade)
  - [ ] Document the kill reward credit flow
  - [ ] Map the capture reward system (200 credits, 100 score)
- **Key questions:**
  - What is the base income per building type?
  - How does the `displayBonus` affect displayed vs actual credits?
  - What happens when credits exceed the limit?

### 4.2 Power Supply System
- **Target:** `power_consume` / `power_produce` building fields, `poweredFlag` at offset 7171
- **Tasks:**
  - [ ] Document the power generation formula per Generator level
  - [ ] Map the power radius system (radii: 10, 20, 30, 40, 60, 127)
  - [ ] Trace what happens when buildings lose power
  - [ ] Document the relationship between `power_consume` and `power_produce` values
  - [ ] Determine if power affects unit production or just building function
- **Key questions:**
  - How is the power network calculated (grid-based or radius-based)?
  - What specific effects does losing power have on buildings?
  - Can a building be partially powered?

### 4.3 Production Capacity
- **Target:** `cb[player][2]` production capacity, `queue_slots` field
- **Tasks:**
  - [ ] Document the supply cap system (base 6, upgradeable to 8)
  - [ ] Map the production queue mechanics (max 60 queue slots)
  - [ ] Trace the build time formula with all modifiers
  - [ ] Document what `tech_requirement` values mean for each building
  - [ ] Map the production progress tracking (constructionHP at offset 5454)
- **Key questions:**
  - How is supply cap calculated (units vs buildings vs both)?
  - What happens when production queue overflows?
  - How do production modifiers from research stack?

### Deliverables
- Credit income/cost spreadsheet
- Power network documentation
- Production system flow diagram

---

## Phase 5: Multiplayer Systems 🟡 MEDIUM

**Goal:** Understand the full network protocol, session management, and synchronization.

**Rationale:** Multiplayer is essential for the online game. A complete protocol understanding enables server emulation and offline play.

### 5.1 Connection and Session Management
- **Target:** `s0.e` (Network Manager), `s0.z` (Request Queue)
- **Tasks:**
  - [ ] Document the full connection establishment sequence
  - [ ] Map the triple-buffer mechanism in the Request Queue
  - [ ] Trace the error recovery flow (3 errors → disconnect, 10 reconnect attempts)
  - [ ] Document the HTTP fallback mechanism and when it activates
  - [ ] Map the session key exchange and encryption handshake
  - [ ] Document the timing: 500ms sender idle, 100ms rate limit, 100ms poll
- **Key questions:**
  - What is the login/authentication sequence?
  - How are session keys negotiated?
  - What triggers a switch from TCP to HTTP fallback?

### 5.2 Protocol Message Format
- **Target:** All message types in `packet_formats.json`
- **Tasks:**
  - [ ] Decode the full SESSION_INIT message structure (message 4)
  - [ ] Decode the GAME_STATE message format (message 30) — most critical
  - [ ] Document the MATCH_START map data format (message 12)
  - [ ] Decode all PLAYER_INFO variants (messages 2, 10, 11, 13, 14, 15, 32, 34, 91)
  - [ ] Map the UPGRADE_DATA format (message 42)
  - [ ] Decode TOWN_DATA structures (messages 50, 51, 100)
  - [ ] Document error message handling (-2, -5, -8)
- **Key questions:**
  - What is the exact format of the GAME_STATE tile and unit data?
  - How are game commands sent from client to server? (outbound format unknown)
  - What is the game record sub-structure within messages?

### 5.3 Client-to-Server Commands
- **Target:** Unknown — needs discovery
- **Tasks:**
  - [ ] Identify all outbound message types (currently undocumented)
  - [ ] Trace user actions to network sends (unit move, attack, build, research)
  - [ ] Document the command acknowledgment system
  - [ ] Map the command batching and throttling mechanism
  - [ ] Determine if commands are sent as raw bytes or encoded strings
- **Key questions:**
  - What commands does the client send?
  - Are commands acknowledged by the server?
  - Is there client-side prediction or is it purely server-authoritative?

### 5.4 Encryption Layer
- **Target:** `s0.h`, `s0.n`, `s0.g`, XOR stream cipher
- **Tasks:**
  - [ ] Document the XOR stream cipher key derivation (from `y.bO.K` at offset L[161])
  - [ ] Trace the 15-byte key rotation mechanism
  - [ ] Document the session key (random 32-bit) usage and 4-byte rotation
  - [ ] Map the XOR-based checksum accumulator (cP/cQ)
  - [ ] Verify the custom Base64 alphabet decoding
  - [ ] Document the data-at-rest encryption for saved games
- **Key questions:**
  - How is the initial encryption key derived?
  - Is the stream cipher bidirectional (same encode/decode)?
  - Can we replay captured network traffic?

### Deliverables
- Complete protocol specification document
- Message format reference with byte-level detail
- Encryption layer documentation with key derivation
- Client command catalog

---

## Phase 6: Campaign Systems 🟢 LOWER

**Goal:** Understand the online campaign meta-game, towns, alliances, and progression.

**Rationale:** The campaign layer provides long-term motivation but is built on top of the multiplayer and resource systems.

### 6.1 Town System
- **Target:** TOWN_DATA messages (50, 51, 100), screen states 44-45
- **Tasks:**
  - [ ] Decode the town data structure from network messages
  - [ ] Document town ownership, defense, and resource generation
  - [ ] Map the World Map screen (state 45) data sources
  - [ ] Trace the Town View (state 44) rendering and interaction
  - [ ] Document town capture mechanics
- **Key questions:**
  - How are towns represented in the game data?
  - What determines town defense strength?
  - How does territory control affect income?

### 6.2 Alliance System
- **Target:** ALLY_LIST message (90), screen state 88
- **Tasks:**
  - [ ] Decode the alliance data structure
  - [ ] Document alliance formation, membership, and hierarchy
  - [ ] Map the alliance screen (state 88) functionality
  - [ ] Document alliance benefits (if any mechanical advantages)
- **Key questions:**
  - What mechanical benefits do alliances provide?
  - Is there an alliance leader/hierarchy system?
  - Can alliances declare war or form pacts?

### 6.3 Rank and Progression
- **Target:** RANK_DATA message (70), screen states 48, 54
- **Tasks:**
  - [ ] Document the ranking system and ELO/skill calculation
  - [ ] Map the rank score formula
  - [ ] Document the Profile screen (state 48) data
  - [ ] Trace the Rankings screen (state 54) data source
  - [ ] Map the rank_exp_thresholds: [20, 35, 50] and rank_credit_rewards: [10, 25, 51]
- **Key questions:**
  - What ranking algorithm is used?
  - How do rank bonuses (0, 3, 6) affect gameplay?
  - Is there a season/reset system?

### Deliverables
- Campaign meta-game documentation
- Alliance system specification
- Ranking/progression documentation

---

## Phase 7: UI Systems 🟢 LOWER

**Goal:** Document the complete UI rendering and interaction system.

**Rationale:** UI understanding is needed for modification and reconstruction but doesn't block understanding of core game mechanics.

### 7.1 Screen Rendering
- **Target:** `s0.k` paint methods, `cls_aq` (Graphics adapter), `cls_q` (Image wrapper)
- **Tasks:**
  - [ ] Document the rendering pipeline from game state to screen pixels
  - [ ] Map the sprite/tile rendering system
  - [ ] Document the animation frame system
  - [ ] Trace the effect rendering (explosions, projectiles, status effects)
  - [ ] Document the minimap rendering
- **Key questions:**
  - How are sprites packed and loaded from encrypted assets?
  - What is the tile rendering order?
  - How does the camera/viewport system work?

### 7.2 Input Handling
- **Target:** `cls_an` (SurfaceView), `s0.k` pointer/key methods
- **Tasks:**
  - [ ] Document touch-to-game-coordinate mapping
  - [ ] Map the 5px deadzone on ACTION_MOVE
  - [ ] Trace how touch events become game commands
  - [ ] Document the unit selection and command UI flow
  - [ ] Map the building placement and production UI
- **Key questions:**
  - How are touch coordinates mapped to grid cells?
  - What is the unit selection/deselection logic?
  - How does the right-click/context menu work on touch?

### 7.3 Dialog System
- **Target:** `cls_bz`, `cls_af`, `cls_be`, `cls_ad`
- **Tasks:**
  - [ ] Document the dialog creation and lifecycle
  - [ ] Map the command listener chain (ag interface)
  - [ ] Document the UI thread synchronization mechanism
  - [ ] Trace payment dialog flow (bf, r classes)
  - [ ] Map all dialog types and their data bindings
- **Key questions:**
  - How are dialogs created on the UI thread?
  - What is the complete command listener callback chain?
  - How does the payment verification dialog work?

### Deliverables
- Rendering pipeline documentation
- Input mapping specification
- Dialog system reference

---

## Phase 8: Asset Systems 🔵 LOWEST

**Goal:** Document the asset loading, encryption, and format systems.

**Rationale:** Asset understanding enables visual/audio modification and content extraction but is not needed for gameplay mechanics understanding.

### 8.1 Asset Loading and Decryption
- **Target:** `cls_bu` (Canvas + Resource Loader), XOR cipher
- **Tasks:**
  - [ ] Document the full asset loading pipeline
  - [ ] Trace the XOR cipher decryption with key rotation
  - [ ] Map the key rotation: "Elements 3-5 shifted left after use"
  - [ ] Document the RGB565 vs ARGB_8888 selection logic
  - [ ] Extract and catalog all encrypted assets
  - [ ] Create a decryption tool
- **Key questions:**
  - What is the full list of encrypted assets?
  - How does the key rotation work precisely?
  - What triggers the ARGB_8888 path (currently only /bg.png known)?

### 8.2 Sprite and Animation Data
- **Target:** `s0.w` animation methods, sprite tables
- **Tasks:**
  - [ ] Document the sprite sheet format
  - [ ] Map the animation frame system (direction-based sprites)
  - [ ] Document the `animCycleLength` calculation
  - [ ] Extract and decode all unit sprites
  - [ ] Extract and decode all building sprites
  - [ ] Map the effect sprite system
- **Key questions:**
  - How are sprite sheets organized (per unit, per faction, shared)?
  - What is the frame timing for each animation type?
  - How many frames per direction per unit?

### 8.3 Audio System
- **Target:** `s0.i` (Audio Manager), `s0.b` (MediaPlayer wrapper)
- **Tasks:**
  - [ ] Document the audio file formats (.o, .3, .m)
  - [ ] Map which sounds play for which events
  - [ ] Extract and convert audio assets
  - [ ] Document the audio playback API (bs interface)
- **Key questions:**
  - What are the .o, .3, .m audio formats?
  - Is there background music or only sound effects?
  - How is audio prioritized when multiple events trigger?

### 8.4 Font System
- **Target:** `s0.f` (Font Manager), `s0.d` (Bitmap Font Renderer)
- **Tasks:**
  - [ ] Document the bitmap font format (/f0, /f1, /f2, /f3)
  - [ ] Map character widths and kerning
  - [ ] Extract the font bitmap data
  - [ ] Document the string localization system (description_key references like T1_150)
  - [ ] Map the UTF-8 byte array string decoding
- **Key questions:**
  - What languages are supported?
  - How are string keys mapped to translated text?
  - Are fonts shared across screen variants?

### Deliverables
- Asset decryption tool
- Sprite format specification
- Audio format documentation
- Font extraction tool

---

## Cross-Phase Dependencies

```
Phase 1: Core Game Loop
  ↓ (required by all)
Phase 2: Unit Systems
  ↓ (required by combat, AI, production)
Phase 3: Combat Systems ←── depends on Phase 2
  ↓ (required by AI, balance)
Phase 4: Resource Systems ←── depends on Phase 2, 3
  ↓ (required by campaign, AI)
Phase 5: Multiplayer Systems ←── depends on Phase 1, 2, 3
  ↓ (required by campaign)
Phase 6: Campaign Systems ←── depends on Phase 4, 5
  ↓
Phase 7: UI Systems ←── partially independent, depends on Phase 1
  ↓
Phase 8: Asset Systems ←── mostly independent
```

## Quick-Win Investigations

These tasks can be completed quickly and yield high-value knowledge regardless of phase:

1. **Extract the `bS[]` and `bT[]` lookup tables** from `s0.w` — these encode most game constants
2. **Decode the `cf[][]` unit type data arrays** — direct stat table access
3. **Map the `Y[]` and `Z[]` research state arrays** — reveals all upgrade effects
4. **Document the `cb[][]` player stats indices** — 11 known indices, likely more
5. **Extract the `bi[]` and `bd[]` death animation tables** — small, well-defined arrays
6. **Decode the distance lookup table** — 31x31 grid, used by both combat and pathfinding
7. **Map the `at[][]` data tables** — referenced by research time and production calculations
8. **Document the `N[]` building armour array** — small array with building defense values

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Server offline (artofwaronline.herocraft.com) | Cannot test multiplayer protocol | Use captured traffic; focus on offline mechanics first |
| Obfuscated class names change between versions | Analysis may not apply to other versions | Anchor analysis on s0 package (primary variant) |
| Dynamic class loading prevents static analysis | Some code paths only discoverable at runtime | Emulate the Class.forName() path statically |
| String obfuscation hides key constants | Slows down understanding | Build automated deobfuscation using by.a() method |
| XOR cipher key changes per asset | Each asset needs individual decryption | Document and automate the key rotation algorithm |

## Tools Needed

1. **Java decompiler** (jadx, procyon, or CFR) for .class → .java
2. **APK extraction tool** (apktool) for resource access
3. **Network sniffer** (Wireshark) for protocol analysis (if server is live)
4. **Hex editor** for binary asset inspection
5. **Custom XOR decryption tool** for asset extraction
6. **Emulator** (Android emulator or J2ME emulator) for runtime analysis
7. **Bytecode editor** for patching and testing modifications

## Milestones

- [ ] **M1:** Game loop fully documented (Phase 1 complete)
- [ ] **M2:** All unit types and stats extracted (Phase 2 complete)
- [ ] **M3:** Combat formulas verified with test cases (Phase 3 complete)
- [ ] **M4:** Resource economy fully modeled (Phase 4 complete)
- [ ] **M5:** Protocol specification enables server emulation (Phase 5 complete)
- [ ] **M6:** Campaign meta-game documented (Phase 6 complete)
- [ ] **M7:** UI can be reconstructed or modified (Phase 7 complete)
- [ ] **M8:** All assets extracted and decrypted (Phase 8 complete)
- [ ] **FINAL:** Complete game reconstruction possible
