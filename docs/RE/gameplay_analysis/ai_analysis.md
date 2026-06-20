# Art of War 2 Online - AI Behavior Analysis

## AI System Overview

The AI opponent in Art of War 2 Online operates during the game's execution phase. The AI logic is embedded in the `w.java` class, which handles all game state updates. The AI uses the same unit/building data structures as the human player.

## AI Architecture

### AI Decision Loop

The AI runs during the main game tick loop in method `b()` of w.java:

```java
// Main game tick - processes all units for both players
final void b() {
    if (this.aL.ah % 30 == 0) {  // Every 30 ticks
        this.aM.bJ = 0;           // Reset battle flag
    }
    if (this.aL.ah % 10 == 0) {  // Every 10 ticks
        d();                       // Process timed events
    }
    if ((this.aL.ah & 3) == 0) {  // Every 4 ticks
        e();                       // Update fog of war
    }
    
    for (int player = 0; player < 2; player++) {
        // Process all units for this player
        for (int unitSlot = player*50 + 1; unitSlot <= (player+1)*50; unitSlot++) {
            if (ca[unitSlot + 1616] != 0) {  // If unit exists
                processUnit(unitSlot);         // Process unit behavior
            }
        }
        
        // Check if this player's command centre was just destroyed
        if (Y[player] == gameTick) {
            rebuildBase(player);  // Reset base data
        }
    }
    
    r();  // Process buildings and resource generation
}
```

### AI Control Points

The AI is activated when `this.aJ` (isAI) is true and various conditions are met:

1. **During control phase** (`ac == 0`): AI gives orders
2. **During execution phase** (`ac != 0`): AI watches execution
3. **Speed control**: AI can accelerate when appropriate

### AI Unit Processing

For each unit, the AI processes through a state machine:

```
States:
  0 = Idle (no current action)
  1 = Moving to target
  3 = Attacking target
  10 = Dying/death animation
```

#### Unit State Machine Flow

```
[Idle] --> Check for enemies in range
  |                          |
  | No enemies               | Enemy found
  v                          v
  Check movement path     [Attacking]
  |                          |
  | Path exists              | Target destroyed
  v                          v
  [Moving]               [Idle] (search for new target)
  |
  | Path blocked
  v
  [Stuck] --> Recalculate path or attack blocker
```

### AI Attack Decision Logic

From method `o()` in w.java (line 1882):

```java
private boolean processAttackDecision() {
    // Check if unit is in siege mode (forced attack)
    if ((flags2 & 32) != 0) {
        stopMovement();
        return true;
    }
    
    // If unit has a target, process it
    if (ca[af + 1919] != 0) {
        processTargetEngagement();
    } else if (atPosition && atDestination) {
        // Unit is idle at destination
        stuckCounter = 0;
        if (hasRallyPoint) {
            moveToRallyPoint();
        }
    }
    
    // Check for weapons firing
    processWeaponFiring();
    
    // If no path and idle, search for targets
    if (pathStart >= pathEnd && distanceToTarget > moveRange) {
        // Recalculate path to target
        findPath(currentX, currentY, targetX, targetY);
    }
    
    return false;
}
```

### AI Target Selection

From method `a(byte, byte, int, int, int)` in w.java (line 2701):

```java
// Search for targets within range
final boolean searchForTargets(byte centerX, byte centerY, int mode, int range, int threshold) {
    int bestDistance = 127;
    int bestTarget = 0;
    
    // Search through spatial hash grid
    for (int gridX = searchMinX; gridX <= searchMaxX; gridX++) {
        for (int gridY = searchMinY; gridY <= searchMaxY; gridY++) {
            for each unit at (gridX, gridY) {
                if (isVisible && distanceClass <= range) {
                    if (mode <= 0) {
                        // Find closest enemy
                        int priority = isBuilding ? buildingPriority : unitPriority;
                        if (priority > bestPriority || 
                            (distanceClass < bestDistance && priority == bestPriority)) {
                            bestDistance = distanceClass;
                            bestTargetX = targetX;
                            bestTargetY = targetY;
                            bestTargetRef = unitRef;
                            bestPriority = priority;
                        }
                    } else if (mode == 1) {
                        // Attack mode: damage enemies in range
                        if (distanceClass <= range && !target.isAttacking) {
                            damageUnit(unitRef, 0, false);
                        }
                    } else {
                        // Count mode: count HP in range
                        totalHP += ca[unitRef + 1616];
                    }
                }
            }
        }
    }
    
    return (mode <= 1) && (bestDistance < 127);
}
```

### AI Target Priority System

Units prioritize targets based on:

1. **Unit type priority**: Buildings have a priority value based on `bS[bT[53] + unitType]` (same as max HP)
2. **Distance**: Closer targets are preferred when priorities are equal
3. **Visibility**: Only targets in the fog-of-war visible area are considered
4. **Infantry vs Machinery**: Units with the infantry bitmask (16447) target other infantry first; machinery targets machinery first

### AI Path Recalculation

When a unit is stuck (stuckCounter reaches ±5):

```java
// Stuck handling
if (stuckCounter >= 5 || stuckCounter <= -5) {
    // Recalculate path
    clearCurrentPath();
    
    if (hasRallyPoint) {
        setNewRallyPoint();
    }
}
```

### AI Building Construction

The AI builds structures using the timed event system (method `d()` in w.java):

```java
// Process timed events (building construction, etc.)
final void processTimedEvents() {
    while (eventIndex < eventCount && gameTick >= eventStartTime + (eventDelay * 10)) {
        processEvent(currentEvent);
        eventIndex++;
        eventStartTime = gameTick;
    }
}
```

Event types (from method `B()`):
- **Event 24**: Set AI difficulty/aggressiveness
- **Event 25**: Resource injection (adds credits to both players)
- **Event 27**: Spawn units at specific locations (reinforcements)
- **Event 29**: Set win condition / game mode

### AI Resource Management

The AI manages resources through building construction and unit production:

```java
// Credit generation per building cycle (every 127 ticks when building is active)
int incomePerCycle = (baseIncome * productionModifier) / 10 * 20 / (upgradeBonus + 20);

// Production cost calculation
int unitCost = (baseCost * productionModifier) / 10;
if (playerUnitCount >= 50 || playerCredits < unitCost) {
    // Cannot produce - skip
}
```

### AI Difficulty Scaling

From the `r()` method, AI difficulty affects:

1. **Production speed**: Scales with `y.V[6]` (game speed modifier) and `y.aU[player][5]` (player-specific modifier)
2. **Build time**: `effectiveBuildTime = (baseTime * modifier * 100 / 100) * 20 / (upgradeBonus + 20)`
3. **Credit generation**: Modified by `y.V[2]` and `y.aU[player][3]`

### AI Win/Lose Detection

```java
// Win condition check (in unit death handler)
if (ab == 0) {  // No win state yet
    // Check if enemy player lost all units and buildings
    if (enemyPlayerUnits == 0 && enemyBuildings <= 7) {
        ab = 1;  // AI wins (enemy destroyed)
    }
    // Check if friendly player lost all units and buildings  
    if (friendlyPlayerUnits == 0 && friendlyBuildings <= 7) {
        ab = 2;  // AI loses (friendly destroyed)
    }
}

// Time-based win condition
if (gameTick >= maxTurns * turnsPerPhase * 8) {
    // Compare scores
    if (player0Score > player1Score) {
        ab = 2;  // Player 0 wins on points
    } else {
        ab = 1;  // Player 1 wins on points
    }
}
```

### AI Siege Mode Behavior

Units that can enter siege mode (bitmask 114688):

```java
// Auto-siege: When enemy is nearby
if (isSiegeCapable && !isInSiegeMode) {
    if (distanceToNearestEnemy <= siegeRange) {
        // Enter siege mode automatically
        flags2 |= 32;  // Set siege flag
    }
}

// Manual siege: Player/AI activates siege mode
if (isInSiegeMode) {
    // Siege mode effects:
    // - Cannot move
    // - Increased attack range
    // - Increased damage
    // - Changed attack animation
}
```

### AI Garrison Behavior

```java
// Units can garrison in bunkers/buildings
if (buildingType == 7 || buildingType == 8) {  // Bunker or Tower
    // Garrison provides:
    // - Protection from damage
    // - Increased fire rate
    // - Extended vision range
    
    // Garrison enter/exit cooldown
    garrisonCooldown = cg[2][buildingType];  // Ticks between enter/exit
    
    // Garrison capacity check
    if (garrisonUnit == 0) {
        // Building is empty, unit can enter
        garrisonUnit = unitRef;
        unitHP = 0;  // Hide unit
    }
}
```

### AI Movement Patterns

1. **Direct path**: Units move directly toward target when path is clear
2. **Pathfinding**: A* variant with terrain costs (method `a(int, int, int, int, int, int, int)`)
3. **Formation**: Units try to maintain facing direction toward target
4. **Stuck recovery**: After 5 ticks of being stuck, unit recalculates path
5. **Rally points**: Produced units move to building's rally point

### AI Fog of War Exploitation

The AI has access to the same fog-of-war system as the player:
- Only targets visible units
- Cannot shoot through fog
- Reveals terrain as units move
- Uses locator buildings for extended vision

### AI Combat Preferences

Based on unit type bitmask analysis:

| Unit Category | Preferred Target | Behavior |
|---------------|-----------------|----------|
| Infantry (1,2,3) | Other infantry | Close-range engagement |
| Light Machinery (4,21) | Scouts/raids | Hit-and-run |
| Heavy Machinery (7,16) | Buildings/heavy | Siege warfare |
| Artillery (19,20) | Area targets | Long-range bombardment |
| Mines (9,10,11) | Area denial | Stationary traps |
