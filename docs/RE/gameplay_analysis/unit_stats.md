# Art of War 2 Online - Unit Encyclopedia

## Unit Type Index System

Units are identified by a **unit type ID** stored at offset `+2323` in the unit data array (`ca`).
The bitmask `16447` (0x403F = bits 0,1,2,3,5,14) identifies **infantry** unit types.
The bitmask `16256` (0x3F80 = bits 7-13) identifies **mechanical/machinery** unit types.
The bitmask `114688` (0x1C000 = bits 14,15,16) identifies **buildings that produce units**.
The bitmask `65536` (0x10000 = bit 16) identifies units with **2-cell collision** (large units).

### Unit Type IDs (from code and text string analysis)

| Type ID | Name | Category | Infantry Bit |
|---------|------|----------|-------------|
| 0 | (Empty/None) | - | - |
| 1 | Infantry | Infantry | Yes (bit 0) |
| 2 | Grenadier | Infantry | Yes (bit 1) |
| 3 | Sniper | Infantry | Yes (bit 2) |
| 4 | Light Assault | Infantry | Yes (bit 3) |
| 5 | (Reserved) | - | - |
| 6 | (Reserved) | - | - |
| 7 | Heavy Assault | Infantry | Yes (bit 5) |
| 8 | Flame Assault | Infantry | - |
| 9 | Mine Frog | Mine | - |
| 10 | Mine Lizard | Mine | - |
| 11 | Mine Scorpio | Mine | - |
| 12 | (Reserved/Upgrade) | - | - |
| 13 | (Reserved/Upgrade) | - | - |
| 14 | (Reserved/Upgrade) | - | - |
| 15 | Coyote | Machinery | Yes (bit 14) |
| 16 | T-22 Zeus | Machinery | - |
| 17 | T-21 Hammer | Machinery | - |
| 18 | Rhino | Machinery | - |
| 19 | AV-40 Fortress | Machinery | - |
| 20 | MLRS Torrent | Machinery | - |
| 21 | Armadillo | Machinery | - |
| 22 | Porcupine | Machinery | - |

### Unit Stats Structure (from `ca` array offsets)

Each unit occupies a slot from 1-50 (player 0) or 51-100 (player 1).
The base index is `unit_slot * 1` and data is accessed at `base + OFFSET`:

| Offset | Field | Description |
|--------|-------|-------------|
| +0 | posX | Grid X position |
| +101 | posY | Grid Y position |
| +202 | offX | Sub-grid pixel offset X (within cell) |
| +303 | offY | Sub-grid pixel offset Y (within cell) |
| +404 | facing | Current facing direction (0-7, 8 compass points) |
| +505 | facingAlt | Alternate/backup facing direction |
| +606 | facing2 | Facing direction copy |
| +707 | moveFacing | Movement facing direction |
| +808 | facingBak | Facing direction backup |
| +909 | randomSeed | Random seed value (0-7) |
| +1010 | pathStart | Current position on movement path |
| +1111 | pathEnd | End position on movement path |
| +1212 | animFrame | Animation frame counter |
| +1313 | attackCooldown | Attack cooldown counter |
| +1414 | attackState | Attack state machine state (0=idle, 1=moving, 3=attacking) |
| +1515 | stuckCounter | Stuck detection counter (-5 to +5) |
| +1616 | hp | Hit points (0=empty slot, >0=alive, <0=dead/dying) |
| +1717 | targetX | Target/destination X position |
| +1818 | targetY | Target/destination Y position |
| +1919 | targetUnit | Target unit reference (0=none, >0=unit slot, <0=building, >100=building type) |
| +2020 | weapon1Type | Primary weapon type |
| +2121 | weapon1Ammo | Primary weapon ammo/cooldown |
| +2222 | weapon2Ammo | Secondary weapon ammo |
| +2323 | unitType | Unit type identifier |
| +2424 | attackCycle | Attack cycle counter (modulo cf[0][unitType]) |
| +2525 | weaponCooldown | Weapon cooldown timer |
| +2626 | unknown1 | Reserved |
| +2727 | unknown2 | Reserved |
| +2828 | flags | Status flags byte |
| +2929 | flags2 | Extended status flags |
| +3030 | owner | Owning player (0 or 1) |
| +3131 | ownerBak | Owner backup |
| +3535 | rallyX | Rally point X |
| +3636 | rallyY | Rally point Y |
| +4545 | pathValid | Path validity counter |
| +4949 | linkedUnit | Linked/transported unit |
| +5050 | prevLinked | Previous linked unit |

### Flag Bits (offset +2828)

| Bit | Meaning |
|-----|---------|
| 0 | Weapon 1 active |
| 1 | Weapon 2 active |
| 2 | Weapon 3 active |
| 3 | Linked to another unit |
| 4 | Moving between cells |
| 5 | Stuck/redirecting |
| 6 | Building production queue active |
| 7 | Siege mode / special state |

### Flag Bits (offset +2929)

| Bit | Meaning |
|-----|---------|
| 5 | Context menu available |
| 6 | Unit selected/active |
| 7 | Attack ground mode |

### Direction System (facing: 0-7)

```
    7  0  1
     \ | /
   6 - X - 2
     / | \
    5  4  3
```

Movement deltas per direction (from bS/bT table at offset groups):
- Direction 0: dx=0, dy=-1 (North)
- Direction 1: dx=1, dy=-1 (NE)
- Direction 2: dx=1, dy=0 (East)
- Direction 3: dx=1, dy=1 (SE)
- Direction 4: dx=0, dy=1 (South)
- Direction 5: dx=-1, dy=1 (SW)
- Direction 6: dx=-1, dy=0 (West)
- Direction 7: dx=-1, dy=-1 (NW)

### Unit Type Details (from text strings and code analysis)

#### Infantry (Type 1)
- **Description**: "Armed with an automatic rifle. Light equipment allows fast movement. Effective against infantry"
- **Category**: Light infantry
- **Effective against**: Other infantry
- **Movement**: High speed
- **Armour**: Light
- **Overview radius**: Small
- **Fire range**: Small
- **Trained at**: Infantry Centre

#### Grenadier (Type 2)
- **Description**: "Armed with a hand flamer. Very effective against infantry. Heavy equipment reduces movement speed"
- **Category**: Heavy infantry
- **Effective against**: Infantry
- **Movement**: Reduced speed (heavy equipment)
- **Armour**: Medium
- **Overview radius**: Medium
- **Fire range**: Medium
- **Trained at**: Infantry Centre
- **Upgrade**: Grenadier's overview radius and fire range increases by 1 (Research: Snipers)

#### Sniper (Type 3)
- **Description**: "Armed with a long-range sniper rifle. In siege mode overview and attack radius extends"
- **Category**: Special infantry
- **Effective against**: Infantry (long range)
- **Movement**: Medium
- **Armour**: Light
- **Overview radius**: Wide (extends in siege mode)
- **Fire range**: Long (extends in siege mode)
- **Siege mode**: Increases overview and attack radius
- **Trained at**: Infantry Centre
- **Upgrade**: Sniper's overview radius and fire range increases by 2, damage increases by 20% (Research: Snipers + upgrade)

#### Light Assault (Type 4)
- **Description**: "Light assault car with light armour and medium machine gun. Has high speed which makes it perfect for quick attacks and scouting. Overview radius and attack range are small"
- **Category**: Light machinery
- **Effective against**: Quick attacks, scouting
- **Movement**: Very high speed
- **Armour**: Light
- **Overview radius**: Small
- **Fire range**: Small
- **Produced at**: Machine Factory

#### Heavy Assault (Type 7)
- **Description**: "Heavy-armoured assault machine. Armed with a heavy machine gun. Has a medium speed, fire range and overview radius"
- **Category**: Heavy machinery
- **Effective against**: General combat
- **Movement**: Medium speed
- **Armour**: Heavy
- **Overview radius**: Medium
- **Fire range**: Medium
- **Produced at**: Machine Factory
- **Upgrade**: Assault's damage increases by 40% (Research), fire rate increases by 50% (Research)

#### Flame Assault (Type 8)
- **Description**: "Heavy flamethrower installs on AV-40 'Fortress'"
- **Note**: "Flamethrower causes no damage to Confederation units due to fireproof armour"
- **Category**: Special machinery
- **Effective against**: Infantry (area damage)
- **Produced at**: Machine Factory
- **Research required**: Reinforced engine + technology

#### Coyote (Type 15)
- **Description**: "Light tank with high speed rotating gun. Rotating gun allows shooting in any direction whilst moving. Has medium firing range and overview radius"
- **Category**: Light tank
- **Movement**: High speed
- **Armour**: Light
- **Overview radius**: Medium
- **Fire range**: Medium
- **Special**: Rotating turret (can fire while moving in any direction)
- **Upgrade**: Increases damage by 15% (Research)

#### T-22 Zeus (Type 16)
- **Description**: "Heavy tank. Has low speed because of armour, but very powerful damage and wide attack range. Upgrade allows you to install rocket launcher onto it"
- **Category**: Heavy tank
- **Movement**: Low speed
- **Armour**: Very heavy
- **Overview radius**: Wide
- **Fire range**: Wide
- **Special**: Upgradeable with rocket launcher
- **Upgrade**: Increases damage by 25% (Research); Rotating rocket launcher with heavy missile (Research: Reinforced engine)

#### T-21 Hammer (Type 17)
- **Description**: "Medium tank. Has high speed, damage in standard mode is medium. Upgrade allows to switch to the siege mode which increases damage and firing rate"
- **Category**: Medium tank
- **Movement**: High speed
- **Armour**: Medium
- **Fire range**: Medium
- **Special**: Siege mode (increases damage and firing rate)
- **Upgrade**: Increases damage by 25% (Research); Reduces reload speed (Research: Reinforced engine)

#### Rhino (Type 18)
- **Description**: "Medium tank. Has high speed, damage in standard mode is medium"
- **Category**: Medium tank
- **Movement**: High speed
- **Armour**: Medium
- **Special**: Siege mode available
- **Upgrade**: Increases speed for all machinery by 1 (Research); Allows production (Research); Reduces reload speed (Research: Reinforced engine); Siege mode activation (Research)

#### AV-40 Fortress (Type 19)
- **Description**: "Multiple rocket launcher system with extra-wide attack range. One rocket salvo covers a large territory but needs a lot of time to recharge. For rocket salvo you need to activate siege mode"
- **Category**: Artillery
- **Movement**: Low speed
- **Armour**: Heavy
- **Fire range**: Extra-wide (area attack)
- **Special**: Siege mode required for rocket salvo; covers large area
- **Upgrade**: Heavy flamethrower variant (Research: Reinforced engine)

#### MLRS Torrent (Type 20)
- **Description**: "Rocket complex armed with long-distance heavy rockets. High visibility radius"
- **Category**: Artillery
- **Movement**: Medium
- **Armour**: Medium
- **Fire range**: Long distance
- **Overview radius**: High
- **Upgrade**: Increases missile fire range by 15% and damage by 30% (Research: Torrent-5 MRLS); Upgrade to Torrent-10 (Research)

#### Armadillo (Type 21)
- **Description**: "Light assault car with light armour and medium machine gun"
- **Category**: Light machinery
- **Movement**: High speed
- **Upgrade**: Increases damage by 15% (Research)

#### Porcupine (Type 22)
- **Description**: "Mobile missile system. Armed with heavy machine gun and rocket launcher with super fast reloading system, which allows the firing of rockets in any direction without turning. Has heavy armour and medium speed"
- **Category**: Mobile missile system
- **Movement**: Medium speed
- **Armour**: Heavy
- **Special**: Super fast reloading; fires in any direction without turning
- **Upgrade**: Advanced Porcupine - doubles firing range and increases damage by 15% (Research: MMC Porcupine + upgrade)

### Mine Types

#### Mine Frog (Type 9)
- **Description**: "A personnel mine. Deals light damage to machinery"
- **Effect**: Light damage, primarily anti-personnel
- **Jump mine**: "Jumps and detonates, damaging infantry and machinery within 1 cell"

#### Mine Lizard (Type 10)
- **Description**: "A multi-charged mine. Jumps before detonation. When it detonates, small fragments and some additional charges scatter"
- **Effect**: Multi-charge, area denial

#### Mine Scorpio (Type 11)
- **Description**: "Anti-tank mine. Detonates only when enemy machines are nearby"
- **Effect**: Heavy anti-machinery damage, only triggers on vehicles
- **Note**: "Heavy mine, detonates only when machinery rides on it"

### Movement System

- **Grid-based**: Units move on a tile grid
- **Sub-pixel movement**: Units have pixel offsets (offX, offY) within their grid cell
- **Pixel scale**: 30 pixels per X cell, 20 pixels per Y cell
- **8-directional movement**: Diagonal movement is supported
- **Collision**: Large units (bit 16 of unit type mask) occupy 2x2 cells
- **Path following**: Units follow pre-calculated paths stored in `al[0][unit]` and `al[1][unit]` arrays

### Attack System

- **Attack cycle**: Controlled by `cf[0][unitType]` (number of frames per attack cycle)
- **Attack states**: 0=idle, 1=moving to target, 3=attacking
- **Multi-weapon**: Up to 3 weapons per unit (flags bits 0-2)
- **Siege mode**: Some units can enter siege mode (bit 7 of flags) which changes attack properties
- **Auto-siege**: Units with bitmask 114688 auto-enter siege mode when enemies are nearby

### Unit Production

- **Max units per player**: 50 (unit slots 1-50 for player 0, 51-100 for player 1)
- **Production queue**: Up to 60 items per player (K/L/M arrays)
- **Production cost**: Deducted from player's credits (cb[player][4])
- **Production time**: Scales with upgrade research and building count
