# Art of War 2 Online - Building Encyclopedia

## Building System Overview

Buildings are stored in the same unit data array (`ca`) as units, but use additional offsets for building-specific data.
Building slots use negative indices in some references: `-building_slot` maps to `ca[(-building_slot) + OFFSET + 5252]` for position data.

### Building Type IDs

| Type ID | Name | Category |
|---------|------|----------|
| 0 | (Empty/None) | - |
| 1 | Command Centre | HQ |
| 2 | Barracks / Infantry Centre | Training |
| 3 | Machine Factory | Production |
| 4 | Generator / Powerplant | Power |
| 5 | Technology Centre / Laboratory | Research |
| 6 | Headquarters | HQ (player 1) |
| 7 | Bunker (Defensive) | Defense |
| 8 | Rocket Launcher / Tower | Defense |
| 9 | Wall / Barrier | Defense |
| 10 | Locator / Radar | Intelligence |
| 11 | (Upgrade variant) | - |
| 12 | Command Centre (player 1) | HQ |

### Building Data Structure (ca array offsets)

| Offset | Field | Description |
|--------|-------|-------------|
| +5252 | gridX | Building grid X position |
| +5353 | gridY | Building grid Y position |
| +5454 | constructionHP | Construction progress (0=not built, -1=being destroyed, >0=built) |
| +5555 | guardTarget | Guard/defend target unit reference |
| +5656 | buildingType | Building type identifier |
| +5757 | garrisonUnit | Garrisoned unit reference (0=none, >0=unit slot, <0=building) |
| +5858 | ownerPlayer | Owning player ID |
| +6060 | buildFlags | Building status flags (bit 0 = under construction/producing) |
| +6161 | waypointX | Rally/waypoint X |
| +6262 | waypointY | Rally/waypoint Y |
| +6363 | buildingGridX | Building grid X (for multi-cell) |
| +6464 | buildingGridY | Building grid Y (for multi-cell) |
| +6565 | productionProgress | Production/research progress counter |
| +6666 | productionFlags | Production flags |
| +6767 | productionQueueStart | Production queue start index |
| +6868 | currentProduction | Current production item type (-1 = none) |
| +6969 | garrisonCooldown | Garrison enter/exit cooldown |
| +7070 | garrisonFacing | Garrison unit facing direction |
| +7171 | poweredFlag | Whether building has power (1=powered, 0=unpowered) |

### Building Details (from text strings and code)

#### Command Centre (Type 1 / Type 138 for player 1 variant)
- **Description**: "The commander controls the army and buildings from here. All buildings must be placed within a certain radius from the command centre. The command centre is also a financial source. Every additional command centre gives you 30% less money than the last."
- **Function**: HQ + Income source
- **Placement radius**: All buildings must be within a certain radius of the command centre
- **Income**: Base income generator; each additional CC gives 30% less
- **Upgrade**: Increases base building radius by 25% (Research)
- **Notes**: When built, triggers fog-of-war update for that area

#### Infantry Centre / Barracks (Type 2)
- **Description**: "Train infantry. You can order different types of infantry at the same time. Your order will be done consistently"
- **Function**: Infantry training
- **Production**: Can queue multiple infantry types; processed sequentially
- **Upgrades**:
  - Fast infantry training: Reduces infantry training time by 30%
  - Infantry express-training: Further training speed reduction
  - Allows training Snipers (Research: Snipers)
  - Allows training Flame Assaults (Research + tech)

#### Machine Factory (Type 3)
- **Description**: "Makes machines. You can order different types of machines at the same time. Your order will be done consistently"
- **Function**: Vehicle production
- **Production**: Can queue multiple vehicle types; processed sequentially
- **Upgrades**:
  - Reduces machinery building time by 30%
  - Upgraded assembly line: Further production speed increase
  - Allows production of specific vehicles (Research-dependent)

#### Generator / Powerplant (Type 4)
- **Description**: "Every generator produces and provides several buildings with power. If the building is lacking energy it will stop working"
- **Function**: Power generation
- **Power radius**: Provides power to nearby buildings
- **Consequence**: Buildings without power stop functioning
- **Upgrade**: Increases amount of energy produced by 30%

#### Technology Centre / Laboratory (Type 5)
- **Description**: "Research new technologies here. You can research only one technology at a time"
- **Function**: Technology research
- **Limitation**: Only one research at a time
- **Research tree**: See technology section below

#### Bunker (Type 7)
- **Description**: "Defensive building with heavy armour. Armed with a heavy machine gun with enhanced fire rate. Average overview radius and fire range"
- **Function**: Defensive structure
- **Armour**: Heavy
- **Weapon**: Heavy machine gun
- **Fire rate**: Enhanced
- **Overview radius**: Average
- **Fire range**: Average
- **Garrison**: Can garrison infantry units

#### Tower / Rocket Launcher (Type 8)
- **Description**: "Has wide visibility radius, because of sniper in the tower"
- **Function**: Long-range defense
- **Overview radius**: Wide
- **Armour**: Medium

#### Wall / Barrier (Type 9)
- **Description**: "The barrier. Made for stopping enemy units"
- **Function**: Blocks movement
- **Armour**: Medium
- **HP**: Moderate
- **Upgrade**: Doubles strength of walls; Increases armour for all buildings by 50%
- **Special**: Walls have Active armour upgrade available

#### Locator / Radar (Type 10)
- **Description**: "Discovers territory with wide overview radius"
- **Function**: Intelligence/reconnaissance
- **Overview radius**: Very wide
- **Special**: Reveals fog of war over large area
- **Research required**: Allows building Locators (Research prerequisite)

### Building Placement Rules

1. **Command Centre radius**: All buildings must be placed within a certain radius from the player's command centre
2. **Grid occupancy**: Buildings occupy multiple grid cells based on their size (as[18] for width, as[19] for height)
3. **Map unit grid**: The `bW` (k.P) 128x128 grid tracks which unit/building occupies each cell
4. **Blocking**: Buildings block movement (value 121-123 in grid = blocking terrain)
5. **Power dependency**: Buildings need to be within power range of a generator to function
6. **Construction HP**: Buildings have a construction phase where `constructionHP` increments from 0 to max

### Building Construction

- **Construction progress**: Incremented each game tick when `constructionHP < maxHP`
- **Construction rate**: Base rate modified by research upgrades and player bonuses
- **Construction formula**: Progress rate = `(base_rate * player_production_bonus) / (upgrade_modifier + 20) * 20`
- **Speed modifier**: `300 / ((Y[player].upgrade_bonus + 20))` controls tick rate for construction
- **Completion**: When `constructionHP >= maxHP`, building becomes active
- **Destruction**: When `constructionHP` drops to 0, building is removed

### Building Production

- **Production queue**: Stored in `K[]` and `L[]` arrays (60 slots per player)
- **Queue entry**: Each entry has a unit type and remaining count
- **Production progress**: Incremented by `(unit_build_time * production_modifier) / (upgrade_modifier + 20) * 20`
- **Speed calculation**: `300 / ((Y[player].upgrade_bonus + 20))` ticks between progress increments
- **Completion check**: When progress reaches threshold, unit is spawned
- **Cost deduction**: Unit cost is deducted from player credits when production starts
- **Spawn position**: New units appear at the building's rally point or adjacent free cell

### Resource / Credit System

- **Credits (cb[player][4])**: Main currency, capped at 30,000
- **Income sources**:
  - Command Centre base income
  - Destroying enemy units (reward based on distance and unit type)
  - Capturing buildings
- **Expenditures**:
  - Building construction
  - Unit production
  - Research
- **Income formula**: 
  - Base: `(player_production_modifier * 100) / 100 * 20 / (upgrade + 20)`
  - Kill reward: `(unit_cost * 3 * distance_factor) / (base_distance * 2)`
  - Building capture: ±200 credits + score adjustments
- **Score system**: 
  - y.W[player]: Win points
  - y.X[player]: Score points
  - y.Y[player]: Game time tracking
  - Score changes are multiplied by 2 (shifted left by 1) for display

### Player Stats Array (cb[player][0-10])

| Index | Field |
|-------|-------|
| 0 | Unit count (alive units) |
| 1 | Building count |
| 2 | Production capacity (supply) |
| 3 | Power supply |
| 4 | Credits |
| 5 | Units killed |
| 6 | Buildings destroyed |
| 7 | Units lost |
| 8 | Buildings lost |
| 9 | Total unit cost lost |
| 10 | (Reserved) |

### Fog of War

- **Grid**: `Q[player][layer][x32][y]` bitfield (4 blocks of 128 bits each)
- **Layer 0**: Cumulative visibility (permanent once seen)
- **Layer 1**: Current visibility (updates each tick)
- **Reveal radius**: Based on unit's vision range (`cf[1][unitType]` for units, `W[buildingType]` for buildings)
- **Building reveal**: Buildings under construction reveal area when `6060 & 1 == 0`
- **Garrison reveal**: Garrisoned buildings with units reveal their area
- **Update frequency**: Every 4 game ticks (`(ah & 3) == 0`)
- **Clear condition**: Layer 1 is cleared each update cycle, then rebuilt from unit positions
