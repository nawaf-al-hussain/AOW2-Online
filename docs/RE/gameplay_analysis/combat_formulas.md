# Art of War 2 Online - Combat Formulas & Calculations

## Combat Resolution

### Damage Application

When a unit takes damage (method `a(byte b, int i, int i2)` in w.java, line 750):

```java
// Reduce HP by damage amount
ca[unit + 1616] = ca[unit + 1616] - damage;

// If HP drops to 0 or below
if (ca[unit + 1616] <= 0) {
    ca[unit + 1616] = -1;  // Mark as dying
    
    // Calculate death animation frame
    ca[unit + 1414] = (isInfantry ? (bi[attacker_type] + random(bd[attacker_type])) + 10 - 231 : 2);
    
    // For non-infantry deaths: spawn explosion effects
    if (!isInfantry) {
        spawnEffect(unit_x, unit_y, offX, offY, random(4) + 11);   // Fire effect
        spawnEffect(unit_x, unit_y, offX, offY, random(5) + 27);   // Smoke effect
        spawnEffect(unit_x, unit_y, offX, offY, 6);                 // Debris effect
    }
}
```

### Infantry vs Machinery Death Animation

The death animation offset is calculated differently for infantry vs machinery:
- **Infantry** (bitmask 16447 set): `bi[attackerUnitType] + random(0..bd[attackerUnitType]) + 10 - 231`
- **Machinery**: Fixed animation frame `2`

Where:
- `bi = {231, 249, 249, 259, 247}` (base animation offsets per attacker type category)
- `bd = {16, 10, 10, 3, 2}` (random range for death animation per attacker type)

### Damage Calculation for Projectiles

When a projectile hits (method `c(boolean z)` in w.java, line 1299):

For **missile/projectile type 10** (artillery):
```java
if (target is enemy unit) {
    damage = cg[0][10] * (10 - targetArmour) / 10;  // Reduced by armour
    damage = max(min(damage, cg[0][10] - targetArmour), 1);  // Clamped
}
```

For **ground impact** (missing the target):
```java
if (target is friendly or neutral) {
    damage = cg[0][projectileType] * (10 - armour) / 10;
    damage = max(min(damage, cg[0][projectileType] - armour), 1);
}
```

### Armour Calculation

The `l(int i)` method (line 1666) calculates effective armour:

```java
int getArmour(int unitRef) {
    if (unitRef > 100) {
        return 0;  // Buildings have 0 base armour (use construction HP)
    }
    if (unitRef <= 0) {
        return N[(-unitRef - 1) / 50];  // Building armour from N array
    }
    byte baseArmour = cf[2][ca[unitRef + 2323]];  // Base armour from unit type
    
    int player = (unitRef - 1) / 50;
    
    // Apply upgrade bonus if game time has passed threshold
    if (Y[player] >= gameTick) {
        baseArmour += Z[player][((isInfantry ? 0 : 1) + 4)];  // Research bonus
    }
    
    return baseArmour;
}
```

### Attack Range Calculation

The `o(int i)` method (line 1872) calculates distance to target:

```java
int getDistanceToTarget(int weaponSlot) {
    byte targetRef = ca[(weaponSlot * 101) + 1919 + currentUnit];
    
    int targetX, targetY;
    if (weaponSlot == 0 || targetRef > 100) {
        targetX = ca[currentUnit + 1717];  // Use movement target
        targetY = ca[currentUnit + 1818];
    } else if (targetRef > 0) {
        targetX = ca[targetRef + 0];       // Use target unit position
        targetY = ca[targetRef + 101];
    } else {
        // Target is a building
        if (weaponType == 3) {
            targetX = clamp(currentX, buildingMinX, buildingMaxX);
        } else {
            targetX = ca[(-targetRef) + 5252];
        }
        targetY = ca[(-targetRef) + 5353];
    }
    
    int dx = targetX - ca[currentUnit + 0];
    int dy = targetY - ca[currentUnit + 101];
    
    if (dx > 15 || dy > 15 || dx < -15 || dy < -15) {
        return 127;  // Out of range
    }
    
    return (lookupTable[dy + 15][dx + 15] & 255) >> 3;  // Distance from lookup table
}
```

### Vision Range Check

The `C()` method (line 300) and `D()` method (line 309) check visibility:

```java
int getDistanceClass() {
    int dx = targetX - sourceX;
    int dy = targetY - sourceY;
    if (dx > 15 || dy > 15 || dx < -15 || dy < -15) {
        return 127;  // Far out of range
    }
    return (distanceTable[(dy + 15) * 31 + dx + 15] & 255) >> 3;
}

int getTerrainCost() {
    int dx = targetX - sourceX;
    int dy = targetY - sourceY;
    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
        return 0;
    }
    return distanceTable[(dy + 15) * 31 + dx + 15] & 7;
}
```

### Projectile System

Projectiles are tracked in arrays (400 max active):
- `t[idx]` = grid X position
- `u[idx]` = grid Y position
- `v[idx]` = pixel offset X
- `w[idx]` = pixel offset Y
- `A[idx]` = velocity X (pixels per tick)
- `B[idx]` = velocity Y (pixels per tick)
- `C[idx]` = travel time remaining
- `E[idx]` = total travel time
- `G[idx]` = projectile type
- `y[idx]` = source unit reference
- `z[idx]` = owner player
- `x[idx]` = target unit reference
- `F[idx]` = impact flags
- `D[idx]` = elapsed travel time

### Projectile Movement (per tick)

```java
// Update pixel position
v[idx] += A[idx];  // Add X velocity
w[idx] += B[idx];  // Add Y velocity

// Convert pixel position to grid position
gridX = (v[idx] + 3000 + 15) / 30 - 100;
gridY = (w[idx] + 2000 + 10) / 20 - 100;

// Update grid position
t[idx] += gridX;
u[idx] += gridY;

// Subtract integer cell from pixel offset
v[idx] -= gridX * 30;
w[idx] -= gridY * 20;
```

### Projectile Spawn

When a unit fires (method `a(byte b, byte b2, int i3, int i4, int i5, int i6, int i7, byte b8, int i8, int i9)`):

```java
// Calculate flight time based on distance
flightTime = distanceTable[Math.abs(dy) * 21 + Math.abs(dx)] / speedTable[projectileType];

// Calculate pixel offset for start position
startOffX = (dx * 30) + sourceOffX - offX;
startOffY = (dy * 20) + sourceOffY - offY;

// Calculate travel time
totalTime = lookup(startOffX, startOffY, speedCurveTable[projectileSpeed]);

// Calculate velocity
velX = startOffX / (flightTime + 1);
velY = startOffY / (flightTime + 1);

// For artillery (type 10): clamp velocity
if (projectileType == 10) {
    velX = clamp(startOffX / ((flightTime + 1) & 0xFE), -15, 15);
    velY = clamp(startOffY / ((flightTime + 1) & 0xFE), -10, 10);
    flightTime = (at[6][59] - at[5][59]) + 1;  // Fixed flight time
}
```

### Splash Damage (Artillery)

Artillery projectiles (type 10) deal splash damage when they impact:

```java
if (projectileType == 10 && elapsed >= flightTime) {
    // Impact at grid position
    for each unit at impact position {
        if (unit is enemy) {
            int armour = getArmour(unitRef);
            int baseDamage = cg[0][10];
            int damage = max(min((baseDamage * (10 - armour)) / 10, baseDamage - armour), 1);
            applyDamage(unitRef, damage, attackerPlayer);
        } else if (unit is friendly or terrain) {
            // Reduced friendly fire damage
            int damage = max(min((baseDamage * (10 - armour)) / 10, baseDamage - armour), 1);
            applyDamage(unitRef, damage, attackerPlayer);
        }
    }
}
```

### Nuclear/Explosion Damage (from method `b(byte b, byte b2)`)

Special area damage calculation:

```java
void areaDamage(byte centerX, byte centerY) {
    int radius = bS[bT[80] + attackType];  // Blast radius
    
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            int targetX = centerX + dx;
            int targetY = centerY + dy;
            
            if (inBounds && (target is unit or building)) {
                int armour = getArmour(target);
                int distanceFactor = bS[bT[79] + attackType] * bS[distanceTable[dy][dx]] / 12;
                int damage = max(min(((10 - armour) * distanceFactor) / 10, distanceFactor - armour), 1);
                applyDamage(target, damage, sourceX, sourceY);
            }
        }
    }
    
    // Spawn visual effects
    spawnEffect(centerX, centerY, 0, 0, effectTable[attackType] + random(range));
    spawnEffect(centerX, centerY, 0, 0);  // Secondary effect
}
```

### HP Regeneration

```java
// Infantry health recovery
if (isInfantry && powered) {
    // Recovery rate modified by research upgrades
    int recoveryRate = baseRecoveryRate;
    if (hasEnergySuit) {
        recoveryRate *= 3;  // Triples with Energy Suit research
    }
    // Applied periodically
}

// Machinery repair (in production buildings)
int repairRate = baseRepairRate;
if (hasRepairResearch) {
    repairRate *= 3;  // Triples with repair research
}
```

### Unit Cost & Reward Calculations

```java
// Kill reward calculation (when unit dies)
int killReward = (unitCost * 3 * distanceToEnemyBase) / (baseDistance * 2);
int scoreReward = killReward / 2;

// Credit changes on unit death
W[enemyPlayer] += killReward;       // Winner gets credits
X[enemyPlayer] += scoreReward;      // Winner gets score
W[losingPlayer] -= scoreCost;       // Loser loses score
scoreDisplay += (killReward + scoreReward) * 2;  // Display score

// Capture reward
W[captor] += 200;
X[captor] += 100;
scoreDisplay += 600;
```

### Research/Upgrade Effects

Each research ID (0-47) applies specific stat modifications via the `g(int i)` method:

| Research ID | Effect |
|-------------|--------|
| 0 | Infantry armour +2, Sniper armour +2, Light armour +2; Unlocks research chain |
| 1 | Player 0 attack range reduction /3 (divide by 3) |
| 2 | Attack speed -2 (faster) for specific unit types |
| 3 | Attack damage +2, Production damage +2 |
| 4 | Building armour +4, Production armour +4 |
| 5 | Building radius +1; Unlocks research chain |
| 6 | Upgrades unit type 18 → type 7 (Rhino → new type) |
| 7 | Attack speed +5 for type 11, +8 for type 13; Production +8 for type 11, +8 for type 17, +5 for type 9 |
| 8 | Attack range -1 for types 7,18,9,11,17,13,16; Unlocks research chain 9-13; Building radius +1 |
| 9 | Infantry armour +2 for types 7,18,9,11,17,13,16 |
| 10 | Player 1 attack range reduction /3 |
| 11 | Attack speed -2 (faster) for types 11, 13 |
| 12 | Upgrades unit type 17 → type 11 (Hammer → new type) |
| 13 | Building radius +1; Unlocks research chain 14 |
| 14 | Attack damage +10 for type 21, +2 for type 21 range; Production +2 for type 16, +5 for type 13 |
| 15 | Player 0 supply cap = 8 |
| 16 | Player 0 building armour = 9 |
| 17 | Player 0 unit limit +2; Production +1 for type 15; Production speed = 20 |
| 18 | Building radius +1 |
| 19 | Player 1 production P[1] = 7 |
| 20 | Player 1 production P[2] = 7 |
| 21 | Player 0 credit limit Q[0] = 120 |
| 22 | Player 0 score bonus S[0] = 30 |
| 23 | Player 0 display bonus = 25 |
| 24 | Infantry armour +1 for types 0,2,4,14 |
| 25 | Player 1 attack range reduction /3 |
| 26 | Attack speed +1 for types 0,2,3; Production +1 for types 0,4 |
| 27 | Attack range -1 for types 0,2,4,14; Unlocks chain |
| 28 | Attack range +1 for type 15; Production +1 for types 2, 2 |
| 29 | Building radius +1; Unlocks chain 30 |
| 30 | Attack speed +2 for type 3; Range +2 for type 3; Production +2 for type 14, +2 for type 4 |
| 31 | Attack speed +1 for type 4, +1 for type 5; Production +1 for type 6, +1 for type 8 |
| 32 | Attack range -1 for types 6,8,10,15,12; Unlocks chain 33-37; Building radius +1 |
| 33 | Infantry armour +1 for types 6,8,10,15,12 |
| 34 | Player 1 attack range reduction /3 |
| 35 | Attack speed -2 (faster) for types 12, 14 |
| 36 | Unit type 10 siege upgrade = 15 |
| 37 | Building radius +1; Unlocks chain 38 |
| 38 | Attack damage +2 for type 20, +2 for type 20 range; Production +2 for type 12 |
| 39 | Player 1 supply cap = 8 |
| 40 | Player 1 building armour = 9 |
| 41 | Building radius +1; Unlocks chain 42 |
| 42 | Building radius +1 (cumulative) |
| 43 | Player 0 production P[4] = 7 |
| 44 | Player 1 production P[5] = 7 |
| 45 | Player 1 credit limit Q[1] = 120 |
| 46 | Player 1 score bonus S[1] = 30 |
| 47 | Player 1 display bonus = 25 |

### Research Cost Formula

```java
// Research time calculation
int researchTime;
if (researchState >= 10) {
    researchTime = (researchState + 231) - 10;  // Direct index into at[12]
} else {
    researchTime = bQ[unitType][researchState] + (hasInfantry ? 8 : 0);
}

// Research progress
int ticksToComplete = at[12][researchTime] - at[11][researchTime];

// Research cost
int cost = (unitBuildCost * productionModifier) / 10 * 20 / (upgradeBonus + 20);
```

### Speed & Movement Formulas

```java
// Unit movement speed
int moveSpeed = cf[1][unitType];  // Base vision/move range

// Direction deltas per facing
// bT[offset + facing*2] = dx, bT[offset + facing*2 + 1] = dy

// Pixel movement per animation frame
pixelOffX = bS[bT[1] + facing];       // X delta per step
pixelOffY = bS[bT[1+8] + facing];     // Y delta per step

// Animation frame timing
animCycleLength = bS[bT[facing_parity + 23] + cf[0][unitType]];
```

### Production Time Formula

```java
// Base production time per unit type
int baseBuildTime = bS[bT[53] + unitType];  // Same as max HP value

// Modified build time
int effectiveBuildTime = (baseBuildTime * productionModifier) / 10 * 20 / (upgradeBonus + 20);

// Production progress per tick
if (gameTick % speedDivisor == 0) {
    constructionHP++;
}
```

### Credit Generation Formula

```java
// Per-tick income from buildings (every 127 ticks when building is active)
int income = (baseIncome * 7) / 10;  // 70% of base income

// Full income cycle
int incomePerCycle = (baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20);
```

### Score Calculation

```java
// Score on unit kill
killScore = (unitCost * 3 * (distanceToEnemyBase1 + distanceToEnemyBase2)) / 
            (averageBaseDistance * 2);
lossScore = killScore / 2;

// Score on building capture
captureScore = 600;

// Credit change on kill
creditGain = lookupTable[unitType] * playerCreditModifier / 100;
```

### Game Timing

```java
// Time categories (from y.bh array)
int[] timeCategories = {30, 60, 120, 360, 720, 1440, 2880, 7200, 14400, 43200, 65535};
// Corresponds to: 30s, 1min, 2min, 6min, 12min, 24min, 48min, 2hr, 4hr, 12hr, max

// Game tick rate
int tickRate = 100 / speedMultiplier;  // Base: 100ms per tick

// Speed multiplier
int speedMultiplier = (isAccelerated ? m : 1);  // m = game speed setting
```

### Unit vs Building Interaction

```java
// Building damage resistance
int buildingArmour = N[(buildingRef - 1) / 50];  // Per-player armour value

// Weapon effectiveness vs buildings
// Infantry weapons (bitmask 16447): Reduced vs buildings
// Heavy weapons (bitmask 16256): Full damage vs buildings
// Siege weapons: Bonus vs buildings

// Target priority
if (targetIsInfantry && attackerIsInfantry) {
    // Full damage
} else if (targetIsMachinery && attackerIsInfantry) {
    // Reduced damage
} else if (targetIsBuilding) {
    // Use building armour
}
```
