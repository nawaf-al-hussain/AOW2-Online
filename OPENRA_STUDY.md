# OpenRA Architecture Study — Lessons for AOW2-Online

This study compares the OpenRA codebase (`/tmp/openra/`) against the corresponding AOW2-Online modules and identifies concrete, file-and-line-cited improvements we can adopt. Each section ends with specific recommendations; Section 7 ranks them by effort and impact.

---

## 1. Lockstep & Command Buffering

### 1.1 Per-frame packet pacing (the empty-packet rule)

**OpenRA** (`OpenRA.Game/Network/OrderManager.cs:202`, `:241-245`):
```csharp
bool IsReadyForNextFrame => GameStarted && pendingOrders.All(p => p.Value.Count > 0);
…
// Sanity check that we are processing the frame that we expect, so we can
// crash early instead of desyncing.
if (frameNumber != NetFrameNumber)
    throw new InvalidDataException($"Attempted to process orders from client {clientId}
        for frame {frameNumber} on frame {NetFrameNumber}");
```
Every frame **must** have a queued order packet, even if it contains no orders — the empty packet is the pacing signal that lets the simulation advance. The frame-number sanity check throws `InvalidDataException` instead of silently desyncing.

**AOW2** (`aow2-core/.../network/CommandBuffer.java:160-167`):
```java
public boolean isFrameReady() {
    List<CommandType> frame = frames[readIndex];
    boolean player0 = frame.stream().anyMatch(c -> c.playerId() == 0);
    boolean player1 = frame.stream().anyMatch(c -> c.playerId() == 1);
    return player0 && player1;
}
```
A frame advances only when **both** players have submitted an actual command. An idle opponent (no commands for 5 seconds) blocks the local simulation, which is why `LockstepEngine` had to add the heartbeat workaround (`LockstepEngine.java:243-281`). The heartbeat is a band-aid; the real fix is per-frame pacing packets.

**Recommendation:** Replace `isFrameReady()` with a model where each player must emit one packet per frame (commands or an explicit `NO_OP`/heartbeat packet). Drop `DISCONNECT_TIMEOUT_TICKS = 140` and instead use "no packet for N frames ⇒ disconnect".

### 1.2 Two-track order stream (immediate vs. tick)

**OpenRA** (`OrderManager.cs:57-58`, `:140-145`, `:224-232`):
```csharp
readonly List<Order> localOrders = [];
readonly List<Order> localImmediateOrders = [];
…
void SendImmediateOrders() {
    if (localImmediateOrders.Count != 0 && GameSaveLastFrame < NetFrameNumber)
        Connection.SendImmediate(localImmediateOrders);
    localImmediateOrders.Clear();
}
```
Two separate queues: `localOrders` is sent once per frame and processed on a specific tick; `localImmediateOrders` bypasses the frame pipeline and is processed as soon as received (chat, handshake, pause). `Order.IsImmediate` (`Order.cs:70`) gates routing.

**AOW2**: All commands go through `LockstepEngine.submitCommand()` (`LockstepEngine.java:177-190`) → `CommandBuffer.submitCommand()` → ring-buffer slot at `(writeIndex + inputDelay) % bufferSize`. There is no concept of an immediate command. Chat, pause, and handshake commands would all be delayed by `DEFAULT_INPUT_DELAY = 2` frames.

**Recommendation:** Add an `immediate` flag to `CommandType` (or a separate `ImmediateCommand` channel) and a `LockstepEngine.submitImmediate(cmd)` path that bypasses the buffer and fires `applyCommand` synchronously. This is critical for chat and pause responsiveness.

### 1.3 Server-projected order latency (the Ack trick)

**OpenRA** (`Server.cs:919-933`):
```csharp
// Non-immediate orders must be projected into the future so that all players can
// apply them on the same world tick. … sending the same data back to the client
// that sent it just to update the frame number would be wasteful. We instead send
// them a separate Ack packet that tells them to apply the order from a locally
// stored queue.
if (data.Length == 0 || data[0] != (byte)OrderType.SyncHash) {
    frame += OrderLatency;
    DispatchFrameToClient(conn, conn.PlayerIndex, CreateAckFrame(frame, 1));
    orderBuffer?.AddOrderTimestamp(conn.PlayerIndex);
    conn.LastOrdersFrame = frame;
}
DispatchOrdersToClients(conn, frame, data);
```
At game start (`Server.cs:1443-1456`) the server injects `OrderLatency` empty packets per client to prime the pipeline.

**AOW2**: Has no server-side order projection. `LockstepEngine` does input-delay locally with `CommandBuffer.submitCommand` at `(writeIndex + inputDelay)`. This works for 1v1 but cannot scale to N players because each client only knows about its own input delay, not the network RTT to every other client.

**Recommendation:** If we ever support 3+ players, the server must own the latency budget. Even for 1v1, the Ack trick halves upstream bandwidth because the sender doesn't receive their own command echoed back.

### 1.4 Adaptive tick scaling (the missing catch-up mechanism)

**OpenRA** (`Server/OrderBuffer.cs:42-113`):
```csharp
const int NumberOfFrames = 20;
const int Interval = 1000;          // 1 second
const float MaxTickScale = 1.1f;    // cap at 10% slowdown
…
public IEnumerable<(int PlayerIndex, float TickScale)> GetTickScales() {
    var now = gameTimer.ElapsedMilliseconds;
    if (now < nextUpdate) yield break;
    nextUpdate = now + Interval;
    if (deltas.Values.Any(q => q.Count != NumberOfFrames)) yield break;

    var medians = deltas.Select(d => (PlayerIndex: d.Key, Delta: Median(d.Value.ToArray()))).ToList();
    var minValue = medians.MinBy(p => p.Delta).Delta;
    var offset = minValue < 0 ? Math.Abs(minValue) : 0;

    foreach (var (playerIndex, delta) in medians) {
        var deltaPerTick = (delta + offset) / (float)ticksPerInterval;
        var tickScale = (timestep + deltaPerTick) / timestep;
        var adjustedTickScale = tickScale.Clamp(1f, MaxTickScale);
        yield return (playerIndex, adjustedTickScale);
    }
}
```
The server tracks per-player timestamp deltas over a 20-frame window, computes medians, and sends `TickScale` frames (`Server.cs:729-738`, `CreateTickScaleFrame`) to slow each client by up to 10% — absorbing jitter without stalling. Clients apply it in `OrderManager.ReceiveTickScale` (`OrderManager.cs:172-175`) and `SuggestedTimestep` (`OrderManager.cs:217-218`).

**AOW2**: Has **no** catch-up mechanism. `LockstepEngine.processFrame` (`LockstepEngine.java:318-339`) either advances the simulation at full speed or pauses entirely. There's no "slow down 5% to let the opponent catch up" mode.

**Recommendation:** Port `OrderBuffer` to the server side. Add a `tickScale` field to `LockstepEngine`, a `ReceiveTickScale(float)` method, and apply it to `TickManager`'s sleep interval. This eliminates the harsh pause/resume cycle that currently fires after `DISCONNECT_TIMEOUT_TICKS = 140`.

### 1.5 Synced disconnect via sentinel packet

**OpenRA** (`OrderManager.cs:24-25`, `:147-159`, `:247-253`):
```csharp
const OrderPacket ClientDisconnected = null;
…
public void ReceiveDisconnect(int clientId, int frame) {
    // All clients must process the disconnect on the same world tick to allow
    // synced actions to run deterministically.
    if (GameStarted)
        ReceiveOrders(clientId, (frame, ClientDisconnected));
    …
}
…
if (orders == ClientDisconnected) {
    processClientsToRemove.Add(clientId);
    World.OnClientDisconnected(clientId);
    continue;
}
```
A disconnect is a **null packet** inserted at a specific frame. All clients process it on the same tick, so any synced side-effects (e.g., transferring units, ending the game) run deterministically.

**AOW2** (`GameWebSocketHandler.java:128-156`): The server sends an `opponent_disconnected` JSON message out-of-band. The local `LockstepEngine` pauses via its own timeout check (`LockstepEngine.java:330`). The two events are not synchronized to a frame — if the local client is mid-tick when the WebSocket message arrives, the pause happens between frames, which can desync side-effects.

**Recommendation:** Add a `CommandType.Disconnect` (or a sentinel tick in the `CommandBuffer`) so the disconnect is processed in lockstep with the rest of the command stream.

### 1.6 NetFrameInterval batching

**OpenRA** (`OrderManager.cs:330-332`):
```csharp
// The server may request clients to batch multiple frames worth of orders into
// a single packet to improve robustness against network jitter at the expense
// of input latency
bool IsNetFrame => LocalFrameNumber % LobbyInfo.GlobalSettings.NetFrameInterval == 0;
```
Configurable batch interval: trade N frames of input latency for N× less network traffic and jitter tolerance.

**AOW2**: One WebSocket message per command. A player issuing 10 move commands in a single tick generates 10 separate JSON serializations and 10 separate WebSocket frames (see `GameWebSocketHandler.java:198-250` rate-limited at 20/sec).

**Recommendation:** Coalesce commands per tick into a single WebSocket frame. This also makes the rate-limit logic at `GameWebSocketHandler.java:226-241` unnecessary in the common case.

### 1.7 Bug: `processFrame` drains commands even when about to pause

**AOW2** (`LockstepEngine.java:322-339`):
```java
// Drain commands for the current frame
List<CommandType> commands = commandBuffer.drainFrame();

// Track consecutive frames without opponent activity …
if (lockstepFrame - lastOpponentActivityTick > DISCONNECT_TIMEOUT_TICKS) {
    paused = true;
    pausedAtFrame = lockstepFrame;
    …
    return List.of();   // <-- drained commands are LOST
}
```
`drainFrame()` clears the slot at `readIndex` and advances `currentTick` (`CommandBuffer.java:133-143`). When the disconnect check fires immediately after, the drained commands are discarded. On reconnect, those frames cannot be replayed.

**Recommendation:** Move the disconnect check **before** `drainFrame()`, or use a peek-then-drain pattern (mirroring OpenRA's `IsReadyForNextFrame` check at `OrderManager.cs:202` happening before `ProcessOrders`).

---

## 2. Sync/Desync Detection

### 2.1 Attribute-driven sync hashing via IL emit

**OpenRA** (`Sync.cs:23-27`, `:78-107`):
```csharp
[AttributeUsage(AttributeTargets.Field | AttributeTargets.Property)]
public sealed class VerifySyncAttribute : Attribute { }
public interface ISync { }
…
static Func<object, int> GenerateHashFunc(Type t) {
    var d = new DynamicMethod($"hash_{t.Name}", typeof(int), [typeof(object)], t);
    var il = d.GetILGenerator();
    …
    foreach (var field in t.GetFields(Binding).Where(x => x.HasAttribute<VerifySyncAttribute>())) {
        il.Emit(OpCodes.Ldloc, this_);
        il.Emit(OpCodes.Ldfld, field);
        EmitSyncOpcodes(field.FieldType, il);
    }
    …
    return (Func<object, int>)d.CreateDelegate(typeof(Func<object, int>));
}
```
A `[VerifySync]` attribute on a field/property auto-enrolls it in the sync hash. The hash function is **compiled to IL at first use** and cached in a `ConcurrentCache<Type, Func<object, int>>`. Custom hash functions are registered for `CPos`, `WPos`, `Actor`, `Player`, `Target` (`Sync.cs:44-57`).

**AOW2** (`SyncChecker.java:80-158`): The hash is hand-rolled, with explicit per-field XOR-rotations:
```java
hash = Long.rotateLeft(hash, 17) ^ unit.getId();
hash = Long.rotateLeft(hash, 17) ^ unit.getPosition().x();
hash = Long.rotateLeft(hash, 17) ^ unit.getPosition().y();
…
// FIX (ANALYSIS_V2 2.11): Include combat and movement state that affects
// future game state. Previously these were omitted, allowing desyncs in
// attackState, targetUnitRef, siegeMode, movementState to go undetected.
hash = Long.rotateLeft(hash, 17) ^ unit.getAttackState();
```
The codebase's own comments admit fields were forgotten and had to be added later. Adding a new `Unit` field requires editing `SyncChecker` — easy to forget, and forgetting it means silent desyncs.

**Recommendation:** Add a `@Sync` annotation (`java.lang.annotation.ElementType.FIELD`) and a `SyncHasher` class that uses `Class.getDeclaredFields()` + reflection (cached per-class) to auto-hash annotated fields. Custom hashers registered per-type. This is a Java-portable analog of OpenRA's IL-emit approach.

### 2.2 Sync hash sent every frame, not every N ticks

**OpenRA** (`OrderManager.cs:265-275`):
```csharp
if (NetFrameNumber >= GameSaveLastSyncFrame) {
    var defeatState = 0UL;
    for (var i = 0; i < World.Players.Length; i++)
        if (World.Players[i].WinState == WinState.Lost)
            defeatState |= 1UL << i;
    Connection.SendSync(NetFrameNumber, World.SyncHash(), defeatState);
} else
    Connection.SendSync(NetFrameNumber, 0, 0);
```
Sync hash is computed and sent **every frame**. A desync is detected on the very next frame after it occurs.

**AOW2** (`SyncChecker.java:166-168`, `LockstepEngine.java:356-359`):
```java
public boolean shouldCheck(long tick) {
    return tick % syncInterval == 0;   // syncInterval = 150 ticks = 15 seconds
}
…
if (syncChecker.shouldCheck(lockstepFrame)) {
    long hash = syncChecker.computeStateHash(state, entities, economySystem, researchSystem);
    syncChecker.setLocalHash(hash);
}
```
Sync is checked every 150 ticks (15 seconds). A desync at tick 151 goes undetected for up to 15 seconds, during which the clients continue diverging — making diagnosis much harder.

Worse, the modulo check `tick % syncInterval == 0` can be **missed entirely** if the engine pauses (`LockstepEngine.java:318-321`) — `lockstepFrame` does not advance during pause, so when it resumes, the modulo may never land on 0 again for a given window.

**Recommendation:** Compute and exchange sync hashes every frame (or at minimum every 10 ticks). The cost is a single `long` per frame; the benefit is sub-second desync detection.

### 2.3 Defeat state bitmap piggybacks on sync hash

**OpenRA** (`OrderManager.cs:161-170`, `:267-273`): The sync packet carries both `SyncHash` and a `ulong DefeatState` bitmask (one bit per player). The server detects end-game on the same channel as desync (`Server.cs:836-846`):
```csharp
var playerDefeatState = BitConverter.ToUInt64(packet, 1 + 4);
if (frame > lastDefeatStateFrame && lastDefeatState != playerDefeatState) {
    var newDefeats = playerDefeatState & ~lastDefeatState;
    for (var i = 0; i < worldPlayers.Count; i++)
        if ((newDefeats & (1UL << i)) != 0)
            SetPlayerDefeat(i);
    …
}
```

**AOW2** (`GameWebSocketHandler.java:303-412`): Game-over is a separate WebSocket message type with two-phase commit (`pendingGameOverClaims`, `confirmGameOver`, `finalizeGameResult`). This is needed because the server doesn't trust either client alone — but it's a 100+ line dance that exists only because there's no continuous sync channel.

**Recommendation:** Piggyback a `defeatState` bitmask on the per-frame sync hash message. The server can then declare game-over the moment both clients agree on the bitmask — no two-phase commit needed.

### 2.4 SyncReport ring buffer for post-mortem diagnosis

**OpenRA** (`SyncReport.cs:24`, `:54-103`, `:105-162`):
```csharp
const int NumSyncReports = 7;
readonly Report[] syncReports = new Report[NumSyncReports];
int curIndex = 0;
…
internal void UpdateSyncReport(IEnumerable<OrderManager.ClientOrder> orders) {
    GenerateSyncReport(syncReports[curIndex], orders);
    curIndex = ++curIndex % NumSyncReports;
}
```
On every frame, a `Report` is built containing: frame number, `SharedRandom.Last` (the synced RNG state), all `ISync`-tagged traits (with per-field `NamesValues`), all synced effects, and all orders issued. When a desync fires (`OutOfSync`), `DumpSyncReport` writes a per-frame log file with all trait field values so you can diff the two clients' states field-by-field.

**AOW2** (`SyncChecker.java:187-194`, `LockstepEngine.java:379-387`): On desync, only the hash values are logged:
```java
public boolean setRemoteHash(long hash) {
    this.remoteHash = hash;
    boolean desync = localHash != remoteHash;
    if (desync) desyncCount++;
    return desync;
}
…
log.error("Desync detected at frame {} (local: {}, remote: {})",
        lockstepFrame, syncChecker.getLocalHash(), remoteHash);
```
Two `long` values. You know they diverged, but you have **no idea which entity or field diverged**.

**Recommendation:** Keep a ring buffer of the last N frames' entity snapshots (positions, HP, attackState, etc. — at minimum for units that changed). On desync, dump the divergent frame to a file. OpenRA uses reflection-cached `Func<ISync, object>` accessors (`SyncReport.cs:191-227`) to avoid `ToString()` overhead until the report is actually needed — port this lazy-boxing trick.

### 2.5 `Sync.RunUnsynced` guards against unsynced-state mutation

**OpenRA** (`Sync.cs:166-204`):
```csharp
public static T RunUnsynced<T>(bool checkSyncHash, World world, Func<T> fn) {
    unsyncCount++;
    var sync = unsyncCount == 1 && checkSyncHash && world != null ? world.SyncHash() : 0;
    try { return fn(); }
    finally {
        unsyncCount--;
        if (unsyncCount == 0 && checkSyncHash && world != null && !world.Disposing
            && sync != world.SyncHash())
            throw new InvalidOperationException("RunUnsynced: sync-changing code may not run here");
    }
}
```
UI/render code wrapped in `RunUnsynced` will **throw** if it accidentally mutates a `[VerifySync]` field. This catches rendering bugs that would otherwise cause silent desyncs.

**AOW2**: Has no such guard. Render code in `aow2-client` could mutate `Unit` fields (e.g., `setMovementState`) without anyone noticing until a desync surfaces minutes later.

**Recommendation:** Add a `Sync.runUnsynced(Runnable)` wrapper in `aow2-core` that snapshots the hash before and after, throwing on mismatch. Wrap all client render-path code in it during dev builds.

---

## 3. Input Handling

### 3.1 IOrderGenerator: pluggable input modes

**OpenRA** (`Orders/IOrderGenerator.cs:17-29`):
```csharp
public interface IOrderGenerator {
    MouseButton ActionButton { get; }
    IEnumerable<Order> Order(World world, CPos cell, int2 worldPixel, MouseInput mi);
    void Tick(World world);
    IEnumerable<IRenderable> Render(WorldRenderer wr, World world);
    IEnumerable<IRenderable> RenderAboveShroud(WorldRenderer wr, World world);
    IEnumerable<IRenderable> RenderAnnotations(WorldRenderer wr, World world);
    string GetCursor(World world, CPos cell, int2 worldPixel, MouseInput mi);
    void Deactivate();
    bool HandleKeyPress(KeyInput e);
    void SelectionChanged(World world, IEnumerable<Actor> selected);
}
```
Each input mode (default unit orders, building placement, directional target for support powers) is a separate `IOrderGenerator`. The active generator is swapped via `world.CancelInputMode()`. Generators can render their own overlays (placement ghost, directional arrow).

**AOW2** (`InputHandler.java:51-53`, `:460-492`):
```java
public enum CommandMode {
    NORMAL, ATTACK_MOVE, PATROL, BUILD_PLACEMENT, GARRISON
}
…
String command = switch (commandMode) {
    case ATTACK_MOVE -> "attack_move";
    case PATROL -> "patrol";
    case BUILD_PLACEMENT -> "build";
    case GARRISON -> "garrison";
    case NORMAL -> "move";
};
```
Hardcoded enum + switch. Adding a new mode (e.g., "select target for airstrike") requires editing `CommandMode`, `handleRightClick`, `onKeyPressed`, and the command string set in four places.

**Recommendation:** Define an `ICommandMode` interface with `String commandName()`, `int[] cursorGlyph()`, `void onActivate()`, `void onDeactivate()`. Make `ATTACK_MOVE`, `PATROL`, etc. instances. This isolates per-mode logic and lets mods add their own.

### 3.2 Trait-driven order generation (the IIssueOrder pipeline)

**OpenRA** (`UnitOrderGenerator.cs:180-199`):
```csharp
var orders = self.TraitsImplementing<IIssueOrder>()
    .SelectMany(trait => trait.Orders.Select(x => new { Trait = trait, Order = x }))
    .OrderByDescending(x => x.Order.OrderPriority)
    .ToList();

for (var i = 0; i < 2; i++) {
    foreach (var o in orders) {
        var localModifiers = modifiers;
        string cursor = null;
        if (o.Order.CanTarget(self, target, ref localModifiers, ref cursor))
            return new UnitOrderResult(self, o.Order, o.Trait, cursor, target);
    }
    // No valid orders, so check for orders against the cell
    target = Target.FromCell(self.World, xy);
}
```
Each **trait** on an actor can register `IIssueOrder` targeters. The input handler queries all traits, sorts by `OrderPriority`, and picks the first one whose `CanTarget` returns true. Adding a new order type (e.g., "repair") means adding a trait to the actor — no input-handler changes.

**AOW2** (`InputHandler.java:468-474`): The command set is fixed by the `CommandMode` switch. Adding a new command type requires editing `handleRightClick`, `CommandProcessor` (`aow2-core/.../command/CommandProcessor.java`), and the relevant `Command*Handler`.

**Recommendation:** If we adopt a trait/effect-style component model in `aow2-core`, port the `IIssueOrder` pattern. Even without full ECS, we can define a registry of `CommandTargeter` instances that the input handler consults.

### 3.3 Hotkey rebinding via YAML manifest

**OpenRA** (`HotkeyManager.cs:18-89`, `HotkeyDefinition.cs:18-65`):
```csharp
public sealed class HotkeyDefinition {
    public readonly string Name;
    public readonly Hotkey Default = Hotkey.Invalid;
    public readonly FrozenSet<string> Types;
    public readonly FrozenSet<string> Contexts;
    public readonly bool Readonly = false;
    public bool HasDuplicates { get; internal set; }
    …
    if (nodeDict.TryGetValue("Platform", out var platformYaml)) {
        var platformOverride = platformYaml.NodeWithKeyOrDefault(Platform.CurrentPlatform.ToString());
        if (platformOverride != null)
            Default = FieldLoader.GetValue<Hotkey>("value", platformOverride.Value.Value);
    }
}
```
Hotkeys are loaded from YAML files listed in the mod manifest. Each `HotkeyDefinition` has: `Name`, `Default`, `Description` (i18n key), `Types`, `Contexts`, `Readonly` flag, and **platform-specific overrides** (e.g., `Cmd` on macOS vs `Ctrl` elsewhere). User overrides are stored in a `HotkeySettings : SettingsModule` and persisted. `HasDuplicates` detection warns about conflicting bindings.

**AOW2** (`InputHandler.java:337-443`): All hotkeys are hardcoded in a giant switch on `KeyCode`:
```java
case A -> { … commandMode = CommandMode.ATTACK_MOVE; … }
case S -> { … issueCommand("stop", -1, -1); … }
case H -> { … issueCommand("hold", -1, -1); … }
case P -> { … commandMode = CommandMode.PATROL; … }
case B -> { … commandMode = CommandMode.BUILD_PLACEMENT; … }
case G -> { … commandMode = CommandMode.GARRISON; … }
case D -> { … issueCommand("siege_mode", -1, -1); … }
case TAB -> { selectionManager.cycleUnitTypeInSelection(); }
case SPACE -> { … jump_to_event … }
case HOME -> { … center_on_base … }
```
No rebinding, no platform overrides, no contextual activation (e.g., disable `B` when no construction yard selected), no conflict detection.

**Recommendation:** Define a `hotkeys.yaml` resource. Add a `HotkeyManager` that loads `HotkeyDefinition` records and exposes `boolean isPressed(String name, KeyEvent)` / `boolean matches(String name, Hotkey)`. Replace the switch in `onKeyPressed` with a registry lookup. Persist user overrides to a `hotkeys.json` in the user config dir.

### 3.4 MultiTapCount for double-click (struct field, not hand-rolled timer)

**OpenRA** (`IInputHandler.cs:26`, `:80`):
```csharp
public record struct MouseInput(MouseInputEvent Event, MouseButton Button, int2 Location,
    int2 Delta, Modifiers Modifiers, int MultiTapCount);
…
public struct KeyInput {
    public KeyInputEvent Event;
    public Keycode Key;
    public Modifiers Modifiers;
    public int MultiTapCount;
    …
};
```
The platform layer (SDL) populates `MultiTapCount` directly. Double-click and triple-click are just `MultiTapCount >= 2`.

**AOW2** (`InputHandler.java:288-308`):
```java
long now = System.currentTimeMillis();
boolean isDoubleClick = (now - lastClickTimeMs < DOUBLE_CLICK_THRESHOLD_MS)
                     && (gx == lastClickGx && gy == lastClickGy);
…
lastClickTimeMs = now;
lastClickGx = gx;
lastClickGy = gy;
```
Hand-rolled with `System.currentTimeMillis()`, a 350 ms threshold, and grid-position equality. This breaks if the user moves the mouse 1 pixel between clicks, and it can't distinguish double-click from triple-click.

**Recommendation:** Use JavaFX's `MouseEvent.getClickCount()` which already provides multi-tap counting. Remove the hand-rolled logic.

### 3.5 Order validation pipeline

**OpenRA** (`UnitOrders.cs:417-424`):
```csharp
static void ResolveOrder(Order order, World world, OrderManager orderManager, int clientId) {
    if (order.Subject == null || order.Subject.IsDead)
        return;
    if (world.OrderValidators.All(vo => vo.OrderValidation(orderManager, world, clientId, order)))
        order.Subject.ResolveOrder(order);
}
```
A pluggable list of `IOrderValidator` instances runs before any order is applied. A mod can add validators for "no orders during cutscene", "no friendly-fire in tutorial", etc.

**AOW2** (`LockstepEngine.java:503-596`): Validation is inline in `applyCommand`:
```java
case CommandType.Move m -> {
    for (int unitId : m.unitIds()) {
        var unit = entities.getUnit(unitId);
        if (owns(unit, m.playerId())) {  // ownership check
            …
        }
    }
}
```
Only ownership is checked. There's no "is this unit stunned?", "is the game paused?", "is this tile reachable?" pre-validation. A malformed `Move` with non-existent `unitId` is silently ignored.

**Recommendation:** Add an `OrderValidator` interface and a list in `LockstepEngine`. Validators run before `applyCommand`. This is also where we'd add anti-cheat checks (e.g., "is this unit actually owned by the claimed player?").

### 3.6 GroupedActor orders for per-actor resolution

**OpenRA** (`Order.cs:277-288`, `UnitOrders.cs:406-413`):
```csharp
public static Order FromGroupedOrder(Order grouped, Actor subject) {
    return new Order(grouped.OrderString, subject, grouped.Target, grouped.TargetString,
        grouped.Queued, grouped.ExtraActors, grouped.ExtraLocation, grouped.ExtraData);
}
…
if (order.GroupedActors == null)
    ResolveOrder(order, world, orderManager, clientId);
else
    foreach (var subject in order.GroupedActors)
        ResolveOrder(Order.FromGroupedOrder(order, subject), world, orderManager, clientId);
```
A single network packet carries one `Order` with a `GroupedActors` array. On the receiving side, it's fanned out into N per-actor orders, each going through the validation pipeline individually.

**AOW2** (`CommandType.java` interface, used in `LockstepEngine.applyCommand`): Commands carry `int[] unitIds` and the handler loops over them. This works but means **all units in the group get the same resolution** — no per-unit trait check.

**Recommendation:** Minor — but if we ever add unit abilities (e.g., "rocket infantry attack, rifle infantry hold"), we'll want per-unit order resolution.

---

## 4. Lua Scripting

### 4.1 Per-game `ScriptContext` (eliminate static mutable state)

**OpenRA** (`ScriptContext.cs:119-229`):
```csharp
public sealed class ScriptContext : IDisposable {
    const int MaxUserScriptMemory = 50 * 1024 * 1024;
    const int MaxUserScriptInstructions = 1000000;
    public World World { get; }
    public WorldRenderer WorldRenderer { get; }
    readonly MemoryConstrainedLuaRuntime runtime;
    readonly LuaFunction tick;
    …
    public ScriptContext(World world, WorldRenderer worldRenderer, IEnumerable<string> scripts) {
        runtime = new MemoryConstrainedLuaRuntime();
        World = world;
        …
    }
}
```
Each game gets its own `ScriptContext` instance, which owns its own Lua runtime. `Dispose()` (`:312-319`) tears down the runtime. No static state.

**AOW2** (`GameAPI.java:38-69`):
```java
public final class GameAPI {
    private static GameState gameState;
    private static EntityManager entityManager;
    private static EconomySystem economySystem;
    private static int mapWidth = 128;
    private static int mapHeight = 128;
    private static final Map<String, String> objectives = new ConcurrentHashMap<>();
    private static final Map<String, Integer> timers = new ConcurrentHashMap<>();
    private static final Map<String, String> eventHooks = new ConcurrentHashMap<>();
    private static final List<String> messageQueue = Collections.synchronizedList(new ArrayList<>());
    …
}
```
**All state is static.** The class's own javadoc (`GameAPI.java:25-31`) admits:
> THREAD-SAFETY WARNING (H-23): All fields (gameState, entityManager, economySystem, objectives, timers, eventHooks) are static mutable shared state accessed from both the game loop thread and the Lua scripting thread. This is NOT thread-safe. A proper fix would require either: (a) synchronizing all access with locks, (b) using thread-local state, or (c) using a message-passing queue between threads. This is an architectural issue that cannot be fixed without significant refactoring.

The `reset()` method (`:435-449`) tries to mitigate by clearing state, but if two games overlap (e.g., campaign mission transition), state leaks.

**Recommendation:** Introduce a `ScriptContext` class in `aow2-modding` that holds `gameState`, `entityManager`, `economySystem`, `objectives`, `timers`, `eventHooks`, and the `Globals` instance as **instance fields**. `LuaEngine` becomes a thin wrapper that creates and owns a `ScriptContext`. `GameAPI` methods become instance methods on `ScriptContext`. This eliminates the entire class of bugs the javadoc warns about.

### 4.2 Whitelist sandbox, not blacklist

**OpenRA** (`ScriptContext.cs:163-184`):
```csharp
var allowedGlobals = new string[] {
    "ipairs", "next", "pairs",
    "pcall", "select", "tonumber", "tostring", "type", "unpack", "xpcall",
    "math", "string", "table"
};
foreach (var fieldName in runtime.Globals.Keys)
    if (!allowedGlobals.Contains(fieldName.ToString()))
        runtime.Globals[fieldName] = null;

var forbiddenMath = new string[] { "random", "randomseed" };
var mathGlobal = (LuaTable)runtime.Globals["math"];
foreach (var mathFunction in mathGlobal.Keys)
    if (forbiddenMath.Contains(mathFunction.ToString()))
        mathGlobal[mathFunction] = null;
```
Start with everything removed, add back only safe functions. Also removes `math.random` and `math.randomseed` because they cause desyncs (each client would generate different sequences).

**AOW2** (`LuaEngine.java:79-103`):
```java
globals.set("os", LuaValue.NIL);
globals.set("io", LuaValue.NIL);
globals.set("java", LuaValue.NIL);
globals.set("debug", LuaValue.NIL);
globals.set("load", LuaValue.NIL);
globals.set("loadstring", LuaValue.NIL);
globals.set("dofile", LuaValue.NIL);
globals.set("require", LuaValue.NIL);
```
Blacklist approach. If a future LuaJ version adds a new dangerous lib (e.g., `ffi`), it's automatically exposed. Also, `math.random` is **left available** — a Lua script calling `math.random()` will produce different results on each client, causing desyncs that `SyncChecker` will catch 15 seconds later.

**Recommendation:** Switch to whitelist. Explicitly construct the `Globals` from a known-safe set. Remove `math.random` and `math.randomseed`; provide `aow2.random(min, max)` instead that uses the synced RNG from `GameState`.

### 4.3 Memory + instruction limits actually enforced

**OpenRA** (`ScriptContext.cs:122-126`, `:215`):
```csharp
const int MaxUserScriptMemory = 50 * 1024 * 1024;
const int MaxUserScriptInstructions = 1000000;
…
runtime.MaxMemoryUse = runtime.MemoryUse + MaxUserScriptMemory;
```
`MemoryConstrainedLuaRuntime` enforces the memory cap. The instruction limit is exposed to scripts as `MaxUserScriptInstructions` global, and the runtime checks it per-instruction.

**AOW2** (`LuaEngine.java:137`, `:160-181`):
```java
private static final int DEFAULT_MAX_INSTRUCTIONS = 1_000_000;
…
// Install instruction-counting debug hook.
// FIX (CI verification): LuaThread.callingLuaThread is not accessible from
// outside the package in LuaJ 3.x. Use a simpler approach: run the chunk
// directly and rely on the script's own termination. The instruction limit
// is a nice-to-have but not critical for correctness — the game loop's tick
// timeout will catch runaway scripts.
final int[] count = {0};
// Note: Per-instruction hooking is not available without LuaThread access.
// The script will run to completion or throw a LuaError on its own.
```
The instruction limit is **declared but not enforced**. The code literally admits "the script will run to completion". An infinite loop in a mod script hangs the game loop. There's no memory cap at all.

**Recommendation:** LuaJ supports `LuaThread`-based instruction counting via the `debug.sethook` mechanism with `count` mode. Alternatively, run each script tick on a worker thread with a `Future.get(timeout)`. Either way, the current state is a DoS vulnerability.

### 4.4 `FatalError` ends the game; AOW2 logs and continues

**OpenRA** (`ScriptContext.cs:238-266`):
```csharp
public void FatalError(Exception e) {
    ErrorMessage = e.Message;
    Console.WriteLine($"Fatal Lua Error: {e.Message}");
    Log.Write("lua", $"Fatal Lua Error: {e.Message}");
    Log.Write("lua", e.StackTrace);
    FatalErrorOccurred = true;
    World.AddFrameEndTask(w => World.EndGame());
}
```
A Lua exception ends the game cleanly. The `Tick()` method (`:296-310`) checks `FatalErrorOccurred` and skips further tick calls.

**AOW2** (`LuaEngine.java:160-193`):
```java
try {
    return chunk.call();
} finally {
    // No hook to remove — instruction counting is not available without
    // LuaThread access (LuaJ 3.x package-private field).
}
} catch (LuaError e) {
    if (e.getMessage() != null && e.getMessage().contains("instruction limit")) {
        LOG.warn("Lua script aborted: {}", e.getMessage());
    } else {
        LOG.error("Lua inline execution error", e);
    }
    return LuaValue.NIL;
}
```
On Lua error, logs and returns `NIL`. The game continues with broken scripts — a mission that depends on `onUnitKilled` to advance objectives will soft-lock.

**Recommendation:** Add a `FatalErrorOccurred` flag to `ScriptContext`. On Lua exception, set it and trigger mission failure (or at least pause the simulation and show an error dialog).

### 4.5 Per-actor script bindings (the `ScriptActorInterface` pattern)

**OpenRA** (`ScriptActorInterface.cs:16-56`, `ScriptContext.cs:131-159`):
```csharp
public class ScriptActorInterface : ScriptObjectWrapper {
    readonly Actor actor;
    …
    void InitializeBindings() {
        var commandClasses = Context.ActorCommands[actor.Info];
        if (actor.Disposed)
            commandClasses = commandClasses.Where(c => c.HasAttribute<ExposedForDestroyedActors>()).ToArray();
        Bind(CreateObjects(commandClasses, [Context, actor]));
    }
    public void OnActorDestroyed() {
        foreach (var commandClass in Context.ActorCommands[actor.Info])
            if (!commandClass.HasAttribute<ExposedForDestroyedActors>())
                Unbind(commandClass);
    }
}
```
Each actor gets a `ScriptActorInterface` that exposes properties/commands based on its traits (`ActorCommands` is a `Cache<ActorInfo, Type[]>` filtered by `Requires<T>` interfaces). Destroyed actors get a reduced command set (`ExposedForDestroyedActors` attribute).

**AOW2** (`GameAPI.java:112-145`, `ScriptBindings.java:79-115`): Only global functions like `aow2.spawnUnit(...)`. There's no way to write `unit:getId()` or `unit:setHp(50)` in Lua — you have to do `aow2.spawnUnit(...)` and remember the returned ID.

**Recommendation:** Add a `ScriptUnitInterface` that wraps a `Unit` and exposes its methods. Register a metatable in Lua so `Actor.new(id)` returns a wrapped object. This is a significant API upgrade for modders.

### 4.6 Attribute-driven global binding discovery

**OpenRA** (`ScriptContext.cs:196-212`):
```csharp
var bindings = Game.ModData.ObjectCreator.GetTypesImplementing<ScriptGlobal>();
foreach (var b in bindings) {
    var ctor = b.GetConstructors(BindingFlags.Public | BindingFlags.Instance).FirstOrDefault(c => {
        var p = c.GetParameters();
        return p.Length == 1 && p[0].ParameterType == typeof(ScriptContext);
    });
    …
    var binding = (ScriptGlobal)ctor.Invoke([this]);
    using (var obj = binding.ToLuaValue(this))
        runtime.Globals.Add(binding.Name, obj);
}
```
Mods add new global tables by annotating a class with `[ScriptGlobal("name")]`. The framework auto-discovers and instantiates them. No central registration file.

**AOW2** (`ScriptBindings.java:79-115`): Manual registration:
```java
aow2.set("spawnUnit", new SpawnUnitFunction());
aow2.set("destroyUnit", new DestroyUnitFunction());
aow2.set("getObjective", new GetObjectiveFunction());
…
```
Adding a new binding requires editing `ScriptBindings.bindAll()` and writing a new `LibFunction` subclass.

**Recommendation:** Use Java's `ServiceLoader` or classpath scanning for `@ScriptGlobal`-annotated classes. Each binding declares its own name and parameter binding. This makes the modding API extensible without modifying `ScriptBindings`.

---

## 5. Replay System

### 5.1 Pre-start buffering (don't lose the first frames)

**OpenRA** (`ReplayRecorder.cs:27`, `:74-90`):
```csharp
MemoryStream preStartBuffer = new();
…
static bool IsGameStart(byte[] data) {
    if (!OrderIO.TryParseOrderPacket(data, out var orders))
        return false;
    return orders.Frame == 0 && orders.Orders.GetOrders(null).Any(o => o.OrderString == "StartGame");
}
…
public void Receive(int clientID, byte[] data) {
    if (disposed) return;
    if (preStartBuffer != null && IsGameStart(data)) {
        writer.Flush();
        var preStartData = preStartBuffer.ToArray();
        preStartBuffer = null;
        StartSavingReplay(preStartData);
    }
    writer.Write(clientID);
    writer.Write(data.Length);
    writer.Write(data);
}
```
The recorder is constructed before the game starts and buffers everything to memory. When the `StartGame` order is observed, it flushes the buffer to disk and switches to file-backed writes. **No orders are lost** even if the recorder is constructed late.

**AOW2** (`ReplayRecorder.java:73-100`):
```java
public void startRecording(String mapName, Faction[] playerFactions) {
    if (recording) {
        LOG.warn("Already recording, stopping previous recording first");
        stopRecording();
    }
    this.currentMapName = mapName;
    this.currentPlayerFactions = playerFactions;
    this.currentCommands = new ArrayList<>();
    this.recording = true;
}
…
public void recordCommand(CommandType command) {
    if (!recording) return;
    ReplayEntry entry = serializeCommand(command);
    currentCommands.add(entry);
}
```
Recording must be explicitly started before any commands flow. If the caller forgets (or starts late), early commands are lost. `recordCommand` silently drops commands when `!recording` (line 95: `return`).

**Recommendation:** Construct `ReplayRecorder` eagerly and buffer to an in-memory list. Start file I/O lazily when the game actually starts (or when a threshold is hit). Make `recordCommand` always buffer, even before `startRecording`.

### 5.2 Metadata at EOF with seek-back read

**OpenRA** (`FileFormats/ReplayMetadata.cs:39-107`):
```csharp
public const int MetaStartMarker = -1;
public const int MetaEndMarker = -2;
public const int MetaVersion = 0x00000001;
…
public static ReplayMetadata Read(string path) {
    using (var fs = new FileStream(path, FileMode.Open)) {
        if (fs.Length < 20) return null;
        fs.Seek(-(4 + 4), SeekOrigin.End);
        var dataLength = fs.ReadInt32();
        if (fs.ReadInt32() == MetaEndMarker) {
            fs.Seek(-(4 + 4 + dataLength + 4 + 4), SeekOrigin.Current);
            return new ReplayMetadata(fs, path);
        }
    }
    return null;
}
```
Metadata is written at the **end** of the file, framed by `MetaStartMarker`/`MetaEndMarker` sentinels. Reading metadata requires only a seek-to-end + seek-back — **no need to parse the command stream**. This is critical for replay browsers that list thousands of replays.

**AOW2** (`ReplayRecorder.java:153-206`): Metadata is written at the start:
```java
dos.write(ReplayFile.MAGIC.getBytes(StandardCharsets.UTF_8));    // Magic
dos.writeShort(replay.formatVersion());                            // Version
dos.writeInt(nameBytes.length); dos.write(nameBytes);              // Map name
dos.writeInt(replay.playerFactions().length);                      // Players
…
dos.writeInt(replay.commands().size());                            // Commands
for (ReplayEntry entry : replay.commands()) { … }
```
To read metadata, you must read past the entire command list (or know the offset). A replay browser listing 1000 replays would have to parse 1000 command streams.

**Recommendation:** Move metadata to EOF with start/end markers. Add a `ReplayMetadata.read(Path)` that seeks from the end. This is a breaking format change — bump `FORMAT_VERSION` and write a converter.

### 5.3 GameInformation: comprehensive metadata

**OpenRA** (`GameInformation.cs:21-50`):
```csharp
public string Mod;
public string Version;
public string MapUid;
public string MapTitle;
public int FinalGameTick;
public DateTime StartTimeUtc;
public DateTime EndTimeUtc;
public TimeSpan Duration => EndTimeUtc > StartTimeUtc ? EndTimeUtc - StartTimeUtc : TimeSpan.Zero;
public IList<Player> Players { get; }
public FrozenSet<int> DisabledSpawnPoints;
…
// Player record contains: PlayerName, ClientIndex, Team, Faction, Color,
// SpawnPoint, Outcome (Won/Lost/Undef), OutcomeTimestampUtc, IsBot, IsHuman
```
Mod ID, mod version, map UID, map title, final tick, start/end timestamps, duration, full player roster with outcomes and team affiliations, disabled spawn points, map generation args. Serialized to YAML (human-readable).

**AOW2** (`ReplayFile.java` — inferred from `ReplayRecorder.java:121-128`): Only:
```java
currentReplay = new ReplayFile(
    currentMapName,           // String
    currentPlayerFactions,    // Faction[]
    totalTicks,               // long
    List.copyOf(currentCommands),
    System.currentTimeMillis(),
    ReplayFile.FORMAT_VERSION
);
```
No mod version, no player names, no player outcomes (who won?), no spawn points, no end time (only `recordedAt` which equals start time).

**Recommendation:** Expand `ReplayFile` to a `GameInformation` record with: `modId`, `modVersion`, `mapUid`, `mapName`, `finalTick`, `startTimeUtc`, `endTimeUtc`, `durationSeconds`, `players` (each with `name`, `faction`, `outcome`, `team`), `disabledSpawnPoints`. Serialize as JSON (or YAML) for human readability.

### 5.4 Filename collision retry

**OpenRA** (`ReplayRecorder.cs:53-68`):
```csharp
FileStream file = null;
var id = -1;
while (file == null) {
    var fullFilename = Path.Combine(dir, id < 0 ? $"{filename}.orarep" : $"{filename}-{id}.orarep");
    id++;
    try { file = File.Create(fullFilename); }
    catch (IOException ex) {
        if (id > CreateReplayFileMaxRetryCount)
            throw new ArgumentException($"Error creating replay file \"{filename}.orarep\"", ex);
    }
}
```
Tries `name.orarep`, `name-1.orarep`, …, up to 128 retries.

**AOW2** (`ReplayRecorder.java:153-206`): Just `Files.newOutputStream(filePath)` — a collision throws `IOException`, which is caught and logged:
```java
} catch (IOException e) {
    LOG.error("Failed to save replay: {}", filePath, e);
    return false;
}
```
The replay is **silently lost**.

**Recommendation:** If `filePath` exists, append `-1`, `-2`, etc. (cap at 128).

### 5.5 Per-mod per-version directory layout

**OpenRA** (`ReplayRecorder.cs:48`):
```csharp
var dir = Path.Combine(Platform.SupportDir, "Replays", mod.Id, mod.Metadata.Version);
```
Replays are stored under `~/Library/Application Support/OpenRA/Replays/<mod>/<version>/`. Different mods and different versions never collide.

**AOW2**: Caller provides arbitrary path. No convention. Replays from version 0.1 and 0.2 are intermixed.

**Recommendation:** Standardize on `~/.aow2/replays/<modId>/<version>/`.

### 5.6 Server-side handshake recording

**OpenRA** (`Server.cs:196-225`, `ReplayRecorder.cs:29-35`):
```csharp
public void RecordFakeHandshake() {
    var request = new HandshakeRequest {
        Mod = ModData.Manifest.Id,
        Version = ModData.Manifest.Metadata.Version,
    };
    recorder.ReceiveFrame(0, 0, new Order("HandshakeRequest", null, false) {
        Type = OrderType.Handshake, IsImmediate = true,
        TargetString = request.Serialize(),
    }.Serialize());
    …
}
```
The server records a synthetic handshake at frame 0 so the replay file knows what mod/version to load. This means replays are **self-describing** — the replay player can auto-switch mods.

**AOW2**: No handshake recording. A replay file has no mod version info (see 5.3), so the player can't validate compatibility before loading.

**Recommendation:** Write a header entry at the start of every replay recording the mod ID, mod version, protocol version, and game config hash.

---

## 6. Server Architecture

### 6.1 Single-threaded event loop (no shared mutable state from I/O threads)

**OpenRA** (`Server.cs:147`, `:260-285`, `:347-401`):
```csharp
readonly BlockingCollection<IServerEvent> events = [];
…
new Thread(() => {
    while (true) {
        if (State != ServerState.WaitingPlayers) { listener.Stop(); return; }
        if (listener.Server.Poll(1000000, SelectMode.SelectRead))
            try { events.Add(new ConnectionConnectEvent(listener.AcceptSocket())); }
            catch (Exception) { /* … */ }
    }
}) { Name = $"Connection listener ({listener.LocalEndpoint})", IsBackground = true }.Start();
…
new Thread(_ => {
    while (true) {
        if (State != ServerState.ShuttingDown) {
            if (events.TryTake(out var e, 1000))
                e.Invoke(this);
            …
        }
        if (State == ServerState.ShuttingDown) { EndGame(); … break; }
    }
}) { IsBackground = true, Name = "ServerThread" }.Start();
```
All socket I/O threads do **one thing**: push events onto a `BlockingCollection<IServerEvent>`. The single `ServerThread` drains the queue and runs `e.Invoke(this)`. Connection logic, packet routing, ping handling, disconnect detection — all serialize through one thread. **No locks, no races, no `ConcurrentHashMap` workarounds.**

Events include: `ConnectionConnectEvent`, `ConnectionPacketEvent`, `ConnectionPingEvent`, `ConnectionDisconnectEvent`, `CallbackEvent` (for async auth results).

**AOW2** (`GameWebSocketHandler.java:29-88`): Spring's `TextWebSocketHandler` dispatches `handleTextMessage` directly on I/O threads. State is scattered across `ConcurrentHashMap`s:
```java
private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
private final Map<String, GameOverClaim> pendingGameOverClaims = new ConcurrentHashMap<>();
private final Map<String, Long> commandRateLimit = new ConcurrentHashMap<>();
private final Map<String, Integer> commandRateCount = new ConcurrentHashMap<>();
```
The two-phase game-over commit (`GameWebSocketHandler.java:303-412`) is 110 lines of `computeIfPresent`, `putIfAbsent`, and race-condition handling — all because the state is accessed concurrently from multiple I/O threads.

**Recommendation:** Introduce a single-threaded `GameEventLoop` (Spring's `ApplicationEventPublisher` or a `BlockingQueue<GameEvent>`). All WebSocket handlers push `GameEvent`s to the queue; the single consumer thread processes them. The four `ConcurrentHashMap`s become plain `HashMap`s. The 110-line game-over dance collapses to ~30 lines.

### 6.2 Server owns the OrderBuffer and sends TickScale frames

**OpenRA** (`Server.cs:143-144`, `:370-380`):
```csharp
OrderBuffer orderBuffer;
…
if (State == ServerState.GameStarted) {
    foreach (var (playerIndex, scale) in orderBuffer.GetTickScales()) {
        var frame = CreateTickScaleFrame(scale);
        var con = Conns.SingleOrDefault(c => c.PlayerIndex == playerIndex);
        if (con != null && con.Validated)
            DispatchFrameToClient(con, playerIndex, frame);
    }
}
```
The server continuously monitors per-player command arrival timestamps and sends `TickScale` frames to slow down clients that are falling behind. See §1.4 for the algorithm.

**AOW2** (`GameWebSocketHandler.java:198-250`): The server just relays commands. There is no server-side jitter monitoring, no adaptive rate control. The disconnect timeout (`LockstepEngine.DISCONNECT_TIMEOUT_TICKS = 140`) is the only feedback mechanism — and it's binary (full speed or full pause).

**Recommendation:** Port `OrderBuffer` to `aow2-server`. Track per-player command arrival timestamps. Every 1 second, compute tick scales and send `{"type":"tick_scale","scale":1.05}` messages to clients that are behind. Clients apply the scale in `LockstepEngine.ReceiveTickScale(float)`.

### 6.3 Server-side sync comparison (source of truth)

**OpenRA** (`Server.cs:812-850`):
```csharp
void HandleSyncOrder(int frame, byte[] packet) {
    if (syncForFrame.TryGetValue(frame, out var existingSync)) {
        if (packet.Length != existingSync.Length) { OutOfSync(frame); return; }
        for (var i = 0; i < packet.Length; i++) {
            if (packet[i] != existingSync[i]) { OutOfSync(frame); return; }
        }
    } else {
        // Update player losses based on the new defeat state.
        var playerDefeatState = BitConverter.ToUInt64(packet, 1 + 4);
        if (frame > lastDefeatStateFrame && lastDefeatState != playerDefeatState) {
            var newDefeats = playerDefeatState & ~lastDefeatState;
            for (var i = 0; i < worldPlayers.Count; i++)
                if ((newDefeats & (1UL << i)) != 0)
                    SetPlayerDefeat(i);
            lastDefeatState = playerDefeatState;
            lastDefeatStateFrame = frame;
        }
        syncForFrame.Add(frame, packet);
    }
}
```
The server is the **source of truth** for sync. It stores the first sync packet per frame; when a second packet arrives, it byte-compares. On mismatch, `OutOfSync(frame)` discards the replay and notifies both clients. The defeat-state bitmask is extracted from the same packet, so end-game detection rides on the same channel.

**AOW2** (`GameWebSocketHandler.java:261-290`, `SessionService.reportSyncHash`): The server stores both clients' hashes and compares them:
```java
boolean desync = sessionService.reportSyncHash(sessionUuid, playerId, tick, hash);
if (desync) {
    var gameSession = sessionService.getSessionForPlayer(playerId);
    if (gameSession.isPresent()) {
        // Notify both players of the desync
        Map<String, Object> desyncMsg = Map.of(
                "type", "desync", "tick", tick, "sessionUuid", sessionUuid);
        if (ws1 != null) sendToSessionId(ws1, desyncMsg);
        if (ws2 != null) sendToSessionId(ws2, desyncMsg);
    }
}
```
The server comparison works but doesn't carry defeat state — game-over is a separate flow. Also, the hash is a `long` (not a byte array), so the comparison is trivial but the diagnostic value is zero (you know they differ, not what differs).

**Recommendation:** Extend the `sync_hash` message to include a `defeatState` bitmask. Have the server declare game-over when both clients agree on the bitmask — eliminating the two-phase `game_over` commit entirely.

### 6.4 Trait-based server extensions (plugin architecture)

**OpenRA** (`Server.cs:314-317`, `:350-352`, `:367-369`, `:392-394`):
```csharp
foreach (var trait in modData.Manifest.ServerTraits)
    serverTraits.Add(modData.ObjectCreator.CreateObject<ServerTrait>(trait));
…
foreach (var t in serverTraits.WithInterface<INotifyServerStart>())
    t.ServerStarted(this);
…
foreach (var t in serverTraits.WithInterface<ITick>())
    t.Tick(this);
…
foreach (var t in serverTraits.WithInterface<INotifyServerShutdown>())
    t.ServerShutdown(this);
```
Server behaviors (lobby management, matchmaking, ban lists, replay recording, etc.) are **mods loaded from the manifest**. Interfaces: `INotifyServerStart`, `ITick`, `IClientJoined`, `IStartGame`, `IEndGame`, `INotifyServerShutdown`. A mod can add server behavior without forking the server.

**AOW2**: All server logic is hardcoded in `GameWebSocketHandler` (and `LobbyWebSocketHandler`, `ChatWebSocketHandler`). Adding new behavior (e.g., spectator mode, tournament mode) requires editing the handler.

**Recommendation:** Define `IServerPlugin` interface with `onStart`, `onTick`, `onClientJoined`, `onGameStart`, `onGameEnd`, `onShutdown`. Load plugins from a config file. Move game-over logic, rate limiting, and replay recording into plugins.

### 6.5 DropClient on send failure

**OpenRA** (`Server.cs:745-752`):
```csharp
void DispatchFrameToClient(Connection c, int client, byte[] frameData) {
    if (!c.TrySendData(frameData)) {
        DropClient(c);
        Log.Write("server", $"Dropping client {client} because dispatching orders failed!");
    }
}
```
If a send fails (client TCP buffer full, broken pipe, etc.), the client is dropped immediately.

**AOW2** (`GameWebSocketHandler.java:481-490`):
```java
private void sendToSessionId(String sessionId, Map<String, Object> data) {
    try {
        WebSocketSession ws = sessions.get(sessionId);
        if (ws != null && ws.isOpen()) {
            sendMessage(ws, data);
        }
    } catch (IOException e) {
        log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
    }
}
```
Send failures are **logged and swallowed**. The player stays in `sessions` map, continues to receive (and ignore) messages, and is never cleaned up. The opponent's `LockstepEngine` will eventually pause after 140 ticks of silence, but the server doesn't proactively drop the dead connection.

**Recommendation:** On `IOException`, call `sessionService.disconnectSession(...)` and notify the opponent. Don't leave zombie sessions.

### 6.6 Explicit server state machine

**OpenRA** (`Server.cs:33-38`, `:145-161`):
```csharp
public enum ServerState {
    WaitingPlayers = 1,
    GameStarted = 2,
    ShuttingDown = 3
}
…
volatile ServerState internalState = ServerState.WaitingPlayers;
public ServerState State { get => internalState; set => internalState = value; }
```
Three states, volatile for visibility. The listener thread checks `State != ServerState.WaitingPlayers` to stop accepting (`Server.cs:264-268`). The main loop checks `State == ServerState.ShuttingDown` to break (`Server.cs:383-389`). Behavior changes based on state — e.g., `ValidateClient` rejects connections when `State == GameStarted` (`Server.cs:470-477`).

**AOW2** (`GameSession.SessionState` — referenced but not in the handler): The handler's `switch (type)` (`GameWebSocketHandler.java:79-87`) doesn't check session state. A `command` message from a player in a `COMPLETED` session is accepted and relayed. The `handleGameOver` method has to check `gs.getState() == GameSession.SessionState.COMPLETED` inline (`:322-325`), which is fragile.

**Recommendation:** Add a state check at the top of `handleTextMessage`:
```java
switch (gameSession.getState()) {
    case WAITING -> { /* only allow auth, chat */ }
    case ACTIVE -> { /* allow command, sync_hash, heartbeat */ }
    case COMPLETED -> { /* only allow game_over confirmation */ }
    default -> sendError(session, "Session not active");
}
```

---

## 7. Actionable Improvements (Ranked)

| # | Improvement | Effort | Impact | Files to change |
|---|------------|--------|--------|-----------------|
| 1 | **Per-frame packet pacing** — replace `CommandBuffer.isFrameReady()` with "every frame must have a packet (commands or NO_OP)". Drop the heartbeat band-aid. | Medium | **Critical** — eliminates the entire false-disconnect class of bugs | `aow2-core/.../network/CommandBuffer.java:160-167`, `LockstepEngine.java:243-281, 318-339`, `aow2-server/.../GameWebSocketHandler.java:105-126` |
| 2 | **Eliminate `GameAPI` static state** — introduce per-game `ScriptContext` holding `gameState`, `entityManager`, `objectives`, `timers`, etc. as instance fields. | Large | **Critical** — fixes the documented thread-safety hole and prevents state leaks across missions | `aow2-modding/.../script/GameAPI.java:38-69`, `LuaEngine.java:62-110`, `ScriptBindings.java:56-115` |
| 3 | **Attribute-driven sync hashing** — add `@Sync` annotation, auto-discover fields via reflection (cached per-class). Custom hashers per type. | Medium | High — eliminates the "forgot to add field to hash" desync class | `aow2-core/.../network/SyncChecker.java:80-158`, new `SyncHasher.java`, annotate fields in `Unit.java`, `Building.java` |
| 4 | **Sync hash every frame** (or every 10 ticks) — change `DEFAULT_SYNC_INTERVAL` from 150 to 1 or 10. | Trivial | High — sub-second desync detection vs. 15-second | `aow2-core/.../network/LockstepEngine.java:46`, `SyncChecker.java:166-168` |
| 5 | **Single-threaded server event loop** — replace concurrent WebSocket handling with `BlockingQueue<GameEvent>` + single consumer. | Large | High — eliminates the race conditions in game-over two-phase commit and rate limiting | `aow2-server/.../websocket/GameWebSocketHandler.java` (rewrite), new `GameEventLoop.java` |
| 6 | **SyncReport ring buffer** — keep last 7 frames of entity snapshots; on desync, dump to file for diffing. | Medium | High — turns desync debugging from "two longs differ" to "Unit #42 HP differs" | `aow2-core/.../network/SyncChecker.java:187-194`, new `SyncReport.java` |
| 7 | **Whitelist Lua sandbox** — replace blacklist (`globals.set("os", NIL)`) with explicit whitelist. Remove `math.random`; add `aow2.random()` using synced RNG. | Small | High — prevents desync-unsafe `math.random()` and future-lib exposure | `aow2-modding/.../script/LuaEngine.java:79-103` |
| 8 | **Adaptive tick scaling** — port OpenRA's `OrderBuffer` to the server; send `tick_scale` messages; apply in `LockstepEngine`. | Medium | High — replaces harsh pause/resume with smooth 1.0×–1.1× slowdown | New `aow2-server/.../network/OrderBuffer.java`, `aow2-core/.../network/LockstepEngine.java` (add `ReceiveTickScale`) |
| 9 | **Defeat state bitmask on sync hash** — piggyback player win/loss bits on the per-frame sync message. Server declares game-over on agreement. | Medium | High — eliminates the 110-line two-phase game-over commit | `aow2-core/.../network/SyncChecker.java`, `aow2-server/.../websocket/GameWebSocketHandler.java:303-473` |
| 10 | **Hotkey rebinding via YAML** — load hotkeys from config, support user overrides, platform-specific defaults, conflict detection. | Medium | Medium — quality-of-life and accessibility | `aow2-client/.../input/InputHandler.java:337-443`, new `HotkeyManager.java`, new `hotkeys.yaml` |
| 11 | **Replay metadata at EOF with seek-back read** — move metadata to end of file with start/end markers; add `ReplayMetadata.read(Path)`. | Medium | Medium — enables fast replay browser listing; bumps format version | `aow2-core/.../replay/ReplayRecorder.java:153-206`, `ReplayFile.java`, `ReplayPlayer.java` |
| 12 | **Expand `ReplayFile` metadata** — add mod version, player names, outcomes, start/end time, duration, spawn points. | Small | Medium — replays become self-describing | `aow2-core/.../replay/ReplayFile.java`, `ReplayRecorder.java:121-128` |
| 13 | **IOrderGenerator pluggable input modes** — replace `CommandMode` enum switch with `ICommandMode` interface. | Medium | Medium — makes adding new command modes (airstrike, repair, etc.) a one-class change | `aow2-client/.../input/InputHandler.java:51-53, 460-492` |
| 14 | **Two-track order stream** — add `immediate` flag to `CommandType` and a `submitImmediate()` path bypassing the buffer. | Small | Medium — chat/pause/handshake stop waiting 2 frames | `aow2-core/.../network/LockstepEngine.java:132-145`, `CommandBuffer.java`, `CommandType.java` |
| 15 | **`DropClient` on send failure** — on `IOException` in `sendToSessionId`, disconnect the session and notify the opponent. | Trivial | Medium — eliminates zombie sessions | `aow2-server/.../websocket/GameWebSocketHandler.java:481-490` |
| 16 | **Server state machine enforcement** — gate message handling on `GameSession.SessionState`. | Small | Medium — prevents commands in completed sessions | `aow2-server/.../websocket/GameWebSocketHandler.java:69-88` |
| 17 | **Fix `processFrame` drain-then-pause bug** — move disconnect check before `drainFrame()`. | Trivial | Medium — prevents command loss on disconnect | `aow2-core/.../network/LockstepEngine.java:322-339` |
| 18 | **Pre-start replay buffering** — buffer commands to memory before `startRecording`; flush on game start. | Small | Medium — no more lost early commands | `aow2-core/.../replay/ReplayRecorder.java:73-100` |
| 19 | **Filename collision retry for replays** — append `-1`, `-2`, etc. on collision. | Trivial | Low — prevents silent replay loss | `aow2-core/.../replay/ReplayRecorder.java:153-160` |
| 20 | **`FatalError` ends game on Lua exception** — set `FatalErrorOccurred` flag, trigger mission failure instead of logging and continuing. | Small | Medium — prevents soft-locks from broken scripts | `aow2-modding/.../script/LuaEngine.java:160-193` |
| 21 | **Order validation pipeline** — add `OrderValidator` interface, run validators before `applyCommand`. | Medium | Medium — enables anti-cheat, cutscene-mode, paused-mode checks | `aow2-core/.../network/LockstepEngine.java:503-596`, new `OrderValidator.java` |
| 22 | **`Sync.RunUnsynced` guard** — snapshot hash before/after UI code; throw on mutation. Dev-build only. | Medium | Medium — catches render-path desync bugs early | New `aow2-core/.../network/SyncGuard.java`, wrap client render calls |
| 23 | **Per-actor script bindings** — `ScriptUnitInterface` wrapping `Unit`; metatable in Lua for `Actor.new(id)`. | Large | Medium — major modding API upgrade | New `aow2-modding/.../script/ScriptUnitInterface.java`, `ScriptBindings.java` |
| 24 | **NetFrameInterval batching** — coalesce per-tick commands into one WebSocket frame. | Small | Low — reduces traffic, simplifies rate limiting | `aow2-server/.../websocket/GameWebSocketHandler.java:198-250` |
| 25 | **Trait-based server plugins** — `IServerPlugin` interface, loaded from config. | Large | Low (for 1v1) — enables mods to extend server behavior | `aow2-server/.../websocket/GameWebSocketHandler.java`, new `IServerPlugin.java` |

### Priority lanes

- **Do first** (high impact, low/medium effort): #4, #7, #15, #17, #19, #20 — all trivial fixes that close real bugs or security holes.
- **Do next** (high impact, medium effort): #1, #3, #6, #8, #9, #11 — architectural improvements that pay off repeatedly.
- **Plan for** (high impact, large effort): #2, #5 — significant refactors that unblock future work.
- **When needed**: #10, #13, #14, #21, #23, #25 — quality-of-life and extensibility.

### Key takeaway

OpenRA's design philosophy is **"the server is the source of truth, the simulation is deterministic, and every mechanism has a sync/audit hook."** AOW2-Online currently treats the server as a dumb relay and pushes sync, timing, and disconnect detection to the clients. This works for 1v1 but creates fragility (false disconnects, lost commands, undiagnosable desyncs). The single highest-leverage change is to make the server own the timing budget (OrderBuffer + TickScale) and the sync comparison — items #5, #8, and #9 together.
