# Art of War 2 Online - Pathfinding Algorithm Documentation

## Overview

The pathfinding system in Art of War 2 Online is implemented in the `w.java` class. It uses a **modified A* algorithm** with Bresenham line-of-sight optimization and terrain cost mapping.

## Core Data Structures

### Grid System
- **Map size**: 128×128 tiles
- **Tile size**: 30×20 pixels (width × height)
- **Unit occupancy grid**: `bW[y][x]` (k.P) - tracks which unit occupies each cell
  - Value 0: Empty/passable
  - Values 1-100: Unit reference (player 0: 1-50, player 1: 51-100)
  - Values 121-123: Blocking terrain (walls, obstacles)
  - Value 127: Temporary obstacle marker

### Spatial Hash Grid
- **8×8 grid** per player for fast unit lookup
- Stored in `bk[player][gridY][gridX]` as linked list
- Linked list via `bl[player][index]` - next pointer
- Offset by `bS[bT[10] + player]` to convert slot to index
- Rebuilt each tick via method `E()` (line 318)

```java
// Rebuild spatial hash
private void rebuildSpatialHash() {
    // Clear all buckets
    for (int p = 0; p < 5; p++) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                bk[p][y][x] = -1;
            }
        }
    }
    
    // Insert units into hash
    for (int p = 0; p < 4; p++) {
        int baseOffset = (p < 2) ? 0 : 5252;
        int yBase = (p < 2) ? 101 : 5353;
        
        for (int i = firstUnit; i <= lastUnit; i++) {
            if ((ca[baseOffset + i] + 1) > 1) {  // Unit exists
                int gridX = ca[i + xBase] >> 4;   // X / 16
                int gridY = ca[i + yBase] >> 4;   // Y / 16
                int index = i - firstUnit;
                bl[p][index] = bk[p][gridY][gridX];
                bk[p][gridY][gridX] = (byte) index;
            }
        }
    }
}
```

### Distance Lookup Table
- **31×31 table** stored in `bS[bT[4]]` (offset by `bT[4]`)
- Covers dx = -15 to +15, dy = -15 to +15
- **Upper 5 bits** (>>3): Distance class (0-31)
- **Lower 3 bits** (&7): Terrain cost modifier

```java
// Distance class (for range checks)
int getDistanceClass(int dx, int dy) {
    if (dx > 15 || dy > 15 || dx < -15 || dy < -15) {
        return 127;  // Out of range
    }
    return (distanceTable[(dy + 15) * 31 + dx + 15] & 255) >> 3;
}

// Terrain cost (for pathfinding)
int getTerrainCost(int dx, int dy) {
    if (Math.abs(dx) > 15 || Math.abs(dy) > 15) {
        return 0;
    }
    return distanceTable[(dy + 15) * 31 + dx + 15] & 7;
}
```

## Pathfinding Algorithm

### Main Path Calculation (method `c(int, int, int, int)`)

This is the primary pathfinding entry point:

```java
private void calculatePath(int fromX, int fromY, int toX, int toY) {
    // Initialize direction decomposition
    decomposeDirection(toX - fromX, toY - fromY);  // method b(int, int)
    
    // Initialize 3 path candidates
    for (int i = 0; i < 3; i++) {
        pathLength[i] = clamp(majorSteps + minorSteps, 0, maxPathLength);
        pathCost[i] = 0;
    }
    
    // Generate Bresenham-style paths
    generateBresenhamPaths();  // method k()
    
    // Find cheapest path
    int bestPath = 0;
    for (int i = 1; i < 3; i++) {
        if (pathCost[i] < pathCost[bestPath]) {
            bestPath = i;
        }
    }
    
    // Set path ordering
    pathOrder[0] = bestPath;
    pathOrder[1] = (bestPath + 1) % 3;
    pathOrder[2] = (bestPath + 2) % 3;
    
    // Detect obstacles and mark segments
    n = 0;
    r = 0;  // Number of obstacle segments
    
    if (pathCost[pathOrder[0]] > 0) {
        // Walk along best path, marking obstacle segments
        for (int step = 0; step < pathLength[pathOrder[0]]; step++) {
            int cellX = path[pathOrder[0]][0][step];
            int cellY = path[pathOrder[0]][1][step];
            
            int cellState = (bW[cellY][cellX] == 0) ? 0 : isPassable(cellX, cellY);
            
            if (prevState == 0 && cellState != 0) {
                // Entering obstacle - start new segment
                if (r >= 10) return;  // Max 10 obstacle segments
                segmentStartX[r] = (step == 0) ? fromX : path[pathOrder[0]][0][step - 1];
                segmentStartY[r] = (step == 0) ? fromY : path[pathOrder[0]][1][step - 1];
                segmentEndX[r] = -1;
                r++;
            } else if (prevState != 0 && cellState == 0) {
                // Exiting obstacle - end segment
                segmentEndX[r - 1] = cellX;
                segmentEndY[r - 1] = cellY;
                segmentStep[r - 1] = step;
                savedMapValue[r - 1] = O[cellY][cellX];
                O[cellY][cellX] = (byte)(256 - r);  // Mark as temporary
            }
            prevState = cellState;
        }
    }
}
```

### Direction Decomposition (method `b(int, int)`)

Decomposes movement vector into major and minor steps:

```java
private void decomposeDirection(int dx, int dy) {
    p = 0;  // Direction octant
    
    // Determine primary axis and octant
    int absDx = (dx > 0) ? dx : -dx;
    int absDy = (dy > 0) ? dy : -dy;
    
    if (dx > 0) p += 4;  // East component
    if (dy > 0) p += 2;  // South component
    
    if (absDx <= absDy) {
        // Y-dominant: more vertical than horizontal
        s[0] = absDy - absDx;  // Minor steps (diagonal)
        s[1] = absDx;           // Major steps (straight)
    } else {
        // X-dominant: more horizontal than vertical
        p += 1;
        s[0] = absDx - absDy;  // Minor steps
        s[1] = absDy;           // Major steps
    }
}
```

### Bresenham Path Generation (method `k()`)

Generates 3 candidate paths using Bresenham-style line algorithms:

```java
private void generateBresenhamPaths() {
    // Initialize all 3 paths
    for (int i = 0; i < 3; i++) {
        pathLength[i] = clamp(s[0] + s[1], 0, maxPathLength);
        pathCost[i] = 0;
    }
    
    // Generate paths for each axis preference
    for (int axis = 0; axis < 2; axis++) {
        int step = 0;
        int pos = axis;
        
        // Walk along axis
        while (pos != targetAxis) {
            for (int minor = 0; minor < s[pos] && step < maxPathLength; minor++) {
                pathX[axis * 2][step] = currentX + dxTable[p * 4 + pos * 2];
                pathY[axis * 2][step] = currentY + dyTable[p * 4 + pos * 2];
                pathCost[axis * 2] += isPassable(pathX, pathY) ? 0 : 1;
                step++;
            }
            pos = 1 - pos;  // Alternate axis
        }
    }
    
    // Generate diagonal path
    int diagStep = 0;
    int error = (s[1] > 0) ? (s[0] * 50) / s[1] : 1000000;
    int errorAccum = error;
    
    for (int i = 0; i < s[0] + s[1] && i < maxPathLength; i++) {
        if ((errorAccum < 50 || diagMajor >= s[0]) && diagMinor < s[1]) {
            // Minor step
            pathX[2][i] = currentX + dxTable[p * 4 + 2];
            pathY[2][i] = currentY + dyTable[p * 4 + 3];
            diagMinor++;
            errorAccum += error;
        } else {
            // Major step
            pathX[2][i] = currentX + dxTable[p * 4 + 0];
            pathY[2][i] = currentY + dyTable[p * 4 + 1];
            diagMajor++;
            errorAccum -= 50;
        }
        pathCost[2] += isPassable(pathX[2][i], pathY[2][i]) ? 0 : 1;
    }
}
```

### Path Optimization (method `i()`)

After finding a path, the algorithm optimizes it by finding shortcuts:

```java
private void optimizePath() {
    // Calculate distance from current position to path segment
    int distSq = (targetX - segmentX)² + (targetY - segmentY)²;
    bestShortcut = -1;
    
    if (distSq > moveRange) {
        // Search for shortcuts along alternative paths
        int searchLength = pathLength[shorterPath] * 2;
        
        for (int i = 0; i < searchLength; i++) {
            int altPath = (i & 1) + 1;  // Alternate between path 1 and 2
            if (pathLength[altPath] > (i >> 1)) {
                int shortcutDist = (targetX - path[altPath][0][i/2])² + 
                                   (targetY - path[altPath][1][i/2])²;
                
                if (shortcutDist < distSq) {
                    distSq = shortcutDist;
                    bestShortcut = i;
                    
                    // Early exit if close enough
                    if (getDistanceClass(path[altPath][0][i/2], path[altPath][1][i/2]) <= moveRange) {
                        return;
                    }
                }
            }
        }
    }
}
```

### Path Merging (method `a(boolean)`)

When obstacles are encountered, path segments are merged:

```java
private void mergePathSegments(boolean fullMerge) {
    // Merge cheaper path first
    if (!bu[0] || (pathLength[k[1]] > pathLength[k[2]] && bu[1])) {
        // Swap to process cheaper path
        int temp = k[1]; k[1] = k[2]; k[2] = temp;
    }
    
    // Copy best path to merged result
    for (int i = 0; i <= segmentLength[bm]; i++) {
        mergedPath[0][i] = path[k[0]][0][i];
        mergedPath[1][i] = path[k[0]][1][i];
    }
    mergedLength = segmentLength[bm] + 1;
    
    // Append second path
    for (int i = 0; i <= pathLength[k[1]] && i + mergedLength < 50; i++) {
        mergedPath[0][i + mergedLength] = path[k[1]][0][i];
        mergedPath[1][i + mergedLength] = path[k[1]][1][i];
    }
    mergedLength = clamp(mergedLength + pathLength[k[1]], 0, 50);
    
    // Full merge: append tail of best path after obstacle
    if (fullMerge && mergedLength < 50) {
        int tailStart = segmentLength[h[k[1]]];
        for (int i = 0; i <= pathLength[k[2]] - tailStart && i + mergedLength < 50; i++) {
            mergedPath[0][i + mergedLength] = path[k[2]][0][i + tailStart];
            mergedPath[1][i + mergedLength] = path[k[2]][1][i + tailStart];
        }
        mergedLength = clamp(mergedLength + pathLength[k[2]] - tailStart, 0, 50);
    }
}
```

### Complete Pathfinding Call Sequence

For a unit that needs to move from A to B:

1. **`c(fromY, fromX, toY, toX)`** - Calculate initial path
   - Calls `b(dx, dy)` - Decompose direction
   - Calls `k()` - Generate 3 Bresenham candidates
   - Detects obstacles along the path
   - Marks obstacle segments on the map

2. **`a(fromX, fromY, range, player)`** - Find detour around obstacles
   - Uses spatial hash for fast neighbor lookup
   - Evaluates cells within vision range
   - Finds best alternative target

3. **`b(fromX, fromY, toX, toY)`** - Calculate actual movement path
   - Calls `c()` for initial path
   - If obstacles found, calls `a()` for each obstacle
   - Merges path segments via `a(boolean)`

4. **Unit movement** - Path stored in `al[0][unit][]` and `al[1][unit][]`
   - X coordinates: `al[0][unit][step]`
   - Y coordinates: `al[1][unit][step]`

### Cell Passability Check (method `c(int, int)`)

```java
private byte isPassable(int x, int y) {
    // Check if cell is valid and visible
    if (bW[y][x] != currentUnit && !isInFogOfWar(x, y)) {
        if (bW[y][x] >= 121 && bW[y][x] <= 123) {
            return 0;  // Blocking terrain - not passable
        }
        if (!bE && bW[y][x] >= enemyMinUnit && bW[y][x] <= enemyMaxUnit) {
            return 0;  // Enemy unit blocking - not passable
        }
        if (bW[y][x] <= playerMinUnit || bW[y][x] > playerMaxUnit || !isAlive(bW[y][x])) {
            // Not a friendly unit or dead unit
            if (bW[y][x] <= enemyMinUnit || bW[y][x] > enemyMaxUnit) {
                // Not an enemy combat unit either
                if (!isInfantry(bW[y][x]) || !isInfantry(currentUnit)) {
                    return 1;  // Passable
                }
                return 0;  // Friendly infantry blocking
            }
            return 0;  // Enemy unit blocking
        }
        return 0;  // Friendly unit blocking
    }
    return 0;  // In fog or same cell
}
```

### Direction Table System

Movement uses a pre-computed direction delta table stored in `bS` at offset `bT[0]`:

```
Direction deltas (8 directions × 4 sub-directions):
Offset bT[0] + (octant * 4 + subDir * 2) = dx
Offset bT[0] + (octant * 4 + subDir * 2 + 1) = dy

Where octant = p (0-7), subDir = 0 (major), 1 (minor), 2 (diag major), 3 (diag minor)
```

### Fog of War Pathfinding Interaction

- Paths cannot be calculated through fog-of-war
- The `Q[player][0][x32][y]` bitfield tracks visibility
- Units in fog are invisible and cannot be targeted
- Pathfinding treats fog cells as passable (since obstacles aren't visible)
- When fog reveals an obstacle, the path is recalculated

### Path Storage Format

Each unit can store up to 50 path steps:
- `al[0][unit][0..49]` - X coordinates for each step
- `al[1][unit][0..49]` - Y coordinates for each step
- `ca[unit + 1010]` - Current position in path (pathStart)
- `ca[unit + 1111]` - End position in path (pathEnd)

### Path Following

During each tick, the unit advances along its path:

```java
// Movement tick
if (pathStart < pathEnd) {
    // Check if next cell is passable
    int nextX = al[0][unit][pathStart];
    int nextY = al[1][unit][pathStart];
    
    if (bW[nextY][nextX] == 0 || bW[nextY][nextX] == 127) {
        // Cell is free - move to it
        advanceUnit(unit, nextX, nextY);
    } else {
        // Cell is blocked - try to handle
        stuckCounter++;
        if (stuckCounter >= 5) {
            recalculatePath(unit);
        }
    }
}
```

### Performance Characteristics

- **Max path length**: 50 steps
- **Max obstacle segments**: 10 per path
- **Search range**: 15 cells (31×31 lookup table)
- **Spatial hash resolution**: 8×8 grid per player (16×16 cells per bucket)
- **Fog update rate**: Every 4 game ticks
- **Path recalculation trigger**: After 5 ticks of being stuck

### Known Limitations

1. Paths are limited to 50 steps - long-distance paths may be truncated
2. Only 10 obstacle segments can be routed around per path calculation
3. The 15-cell search radius limits tactical awareness
4. Fog of war can cause path invalidation when obstacles are revealed
5. The algorithm uses Bresenham lines rather than true A*, which can produce suboptimal paths in complex terrain
