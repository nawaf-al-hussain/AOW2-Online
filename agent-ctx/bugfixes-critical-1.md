# Task: Apply 8 Critical Bug Fixes to aow2-core

## Summary

All 8 fixes have been applied successfully to the aow2-core module:

### Fix 1: Add addMine() to EntityManager
- Added `private final List<Mine> mines = new ArrayList<>()` field
- Added `addMine(Mine)`, `getMines()`, `getMine(int)` (returns Optional) methods
- Added mine cleanup to `removeDeadEntities()` (removes mines with hp <= 0)
- Added imports for `Mine` and `Optional`

### Fix 2: Add attackCooldown field to Building + fix CombatSystem
- **Building.java**: Added `private int attackCooldown` field, initialized to 0 in constructor
- **Building.java**: Added `getAttackCooldown()`, `setAttackCooldown(int)`, `decrementAttackCooldown()` methods
- **Building.java**: Also added `setCurrentProduction(UnitType)`, `clearProductionQueue()`, `setProductionProgress(int)` (needed for Fix 7)
- **CombatSystem.java**: Replaced `building.getConstructionProgress()` → `building.getAttackCooldown()` for cooldown check
- **CombatSystem.java**: Replaced `building.setConstructionProgress(x - 1)` → `building.decrementAttackCooldown()` for tick decrement
- **CombatSystem.java**: Replaced `building.setConstructionProgress(BUILDING_ATTACK_COOLDOWN)` → `building.setAttackCooldown(BUILDING_ATTACK_COOLDOWN)` for reset
- Construction progress usage is untouched (only used in `isUnderConstruction()` and its own getter/setter)

### Fix 3: Remove double tick advancement in GameLoop
- Removed `gameState.advanceTick()` from the game loop's inner while loop
- Added comment explaining that TickManager.processTick() already calls advanceTick()
- Double-advancement would have caused the game to run at 2x speed

### Fix 4: Replace Math.random() with seeded RNG in DamageCalculator
- Added `private static final long SEED = 42L` and `private static final java.util.Random RNG = new java.util.Random(SEED)`
- Replaced `Math.random()` with `RNG.nextDouble()` in `calculateDeathAnimationFrame()`
- Added comment: `// Seeded RNG for lockstep determinism`

### Fix 5: Fix LockstepEngine to route ALL command types
- Added imports for CommandProcessor, CombatSystem, EconomySystem, ProductionSystem, BuildingPlacementSystem, MovementSystem, ResearchSystem, GameMap
- Added `CommandProcessor commandProcessor` field, initialized in constructor
- Added cases for Build, Produce, Research, Garrison, Ungarrison, Cancel (routed to CommandProcessor)
- Added Patrol case (moves units to waypoint directly, logged for future MovementSystem integration)
- Removed `default` case that was just logging deferred commands

### Fix 6: Fix AI activeTaskCount never decrementing
- `taskCompleted()` already existed — kept as-is
- Added `resetTaskCount()` method that sets `activeTaskCount = 0`
- Added `resetTaskCount()` call at the start of each decision cycle (before pipeline execution)
- Added `taskCompleted()` calls after each decision phase: economy, research, military

### Fix 7: Fix ProductionSystem cancelProduction - actually remove from queue
- After computing the remaining list, now calls `producer.clearProductionQueue()` and re-enqueues remaining items via `producer.enqueueProduction(type)`
- Handles cancelling the current production item: if `queueIndex == 0`, calls `producer.setCurrentProduction(null)` and `producer.setProductionProgress(0)`
- Note: since `enqueueProduction` auto-starts if `currentProduction == null`, the first remaining item will automatically become the new current production

### Fix 8: Fix ReplayPlayer.deserializeCommand - implement actual deserialization
- Added `import com.aow2.core.network.CommandSerializer`
- Replaced the `return null` stub with actual implementation
- Reconstructs the full CommandSerializer wire format from ReplayEntry fields
- Manually writes typeId, tick (long, big-endian), playerId (int, big-endian) into a byte array
- Copies the entry payload (command-specific fields) after the header
- Delegates to `CommandSerializer.deserialize(fullData)` for actual parsing
- Wraps in try-catch to return null on deserialization errors
