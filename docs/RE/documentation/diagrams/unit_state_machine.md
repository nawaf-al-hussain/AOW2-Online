# Unit State Machine

## Unit States: Idle, Moving, Attacking, Dying, Producing, Siege Mode, Garrisoned

```mermaid
stateDiagram-v2
    direction TB

    [*] --> Spawning : Unit produced<br/>or placed

    Spawning --> Idle : Spawn complete<br/>HP > 0

    state "Idle" as Idle {
        [*] --> SearchingForTarget
        SearchingForTarget --> HasTarget : Enemy found<br/>in vision range
        SearchingForTarget --> WaitingForOrders : No enemies
        WaitingForOrders --> SearchingForTarget : Periodic scan
        HasTarget --> EvaluatingRange : Target acquired
    }

    Idle --> Moving : Move command received<br/>or moving to target
    Idle --> Attacking : Target in range<br/>auto-engage
    Idle --> SiegeMode : Siege command<br/>(siege-capable units)
    Idle --> Garrisoned : Enter building<br/>(bunker/tower)
    Idle --> Dying : HP ≤ 0

    state "Moving" as Moving {
        [*] --> FollowingPath
        FollowingPath --> AdvancingCell : Next cell passable
        AdvancingCell --> FollowingPath : Cell reached
        FollowingPath --> StuckDetection : Cell blocked
        StuckDetection --> Recalculating : stuckCounter ≥ 5
        StuckDetection --> WaitingForClear : stuckCounter < 5
        WaitingForClear --> FollowingPath : Cell cleared
        Recalculating --> FollowingPath : New path found
        Recalculating --> Idle : No path found
    }

    Moving --> Idle : Destination reached<br/>or no path
    Moving --> Attacking : Enemy enters range<br/>while moving
    Moving --> Dying : HP ≤ 0<br/>while moving
    Moving --> Idle : Move command cancelled

    state "Attacking" as Attacking {
        [*] --> RangeCheck
        RangeCheck --> InRange : Target within<br/>attack range
        RangeCheck --> ClosingRange : Target out of range
        ClosingRange --> Moving : Move toward target
        Moving --> RangeCheck : Position updated
        InRange --> WeaponCooldown : Fire weapon
        WeaponCooldown --> AttackCycle : Cooldown active
        AttackCycle --> InRange : Cooldown complete<br/>cf[0][unitType] cycle
        InRange --> TargetDestroyed : Target HP ≤ 0
    }

    Attacking --> Idle : Target destroyed<br/>or lost sight
    Attacking --> Moving : Target out of range<br/>need to close
    Attacking --> Dying : HP ≤ 0
    Attacking --> Idle : Attack cancelled<br/>new order

    state "Siege Mode" as SiegeMode {
        [*] --> Deploying
        Deploying --> SiegeActive : Deploy complete
        SiegeActive --> SiegeAttacking : Target in siege range
        SiegeAttacking --> SiegeActive : Target destroyed
        note right of SiegeActive
            Cannot move
            Increased attack range
            Increased damage
            Changed attack animation
        end note
    }

    SiegeMode --> Idle : Undeploy command<br/>(exit siege)
    SiegeMode --> Dying : HP ≤ 0

    state "Garrisoned" as Garrisoned {
        [*] --> InsideBuilding
        note right of InsideBuilding
            Unit HP hidden (set to 0)
            Protected from damage
            Enhanced fire rate
            Extended vision range
        end note
    }

    Garrisoned --> Idle : Exit building<br/>garrisonCooldown elapsed
    Garrisoned --> Dying : Building destroyed

    state "Producing" as Producing {
        [*] --> ConstructionProgress
        ConstructionProgress --> Ready : constructionHP ≥ maxHP
        note right of ConstructionProgress
            Progress per tick when powered
            Rate = (base * modifier) / (upgrade + 20) * 20
        end note
    }

    Producing --> Idle : Construction complete
    Producing --> Dying : Building destroyed<br/>during construction

    state "Dying" as Dying {
        [*] --> DeathAnimation
        DeathAnimation --> DeathEffects : Animation complete
        DeathEffects --> Cleanup : Effects spawned
        note right of DeathAnimation
            Infantry: bi[attacker] + random(bd[attacker]) + 10 - 231
            Machinery: Fixed frame 2
        end note
        note right of DeathEffects
            Machinery spawns:
            - Fire effect (random 11-14)
            - Smoke effect (random 27-31)
            - Debris effect (6)
        end note
    }

    Dying --> [*] : Unit removed<br/>from game

    %% Auto-transitions for siege-capable units
    Idle --> SiegeMode : Auto-siege<br/>bitmask 114688<br/>enemy nearby
```

## Unit State Details

### State Data (from `ca` array offsets)

| Offset | Field | Idle | Moving | Attacking | Dying | Siege | Garrisoned |
|--------|-------|------|--------|-----------|-------|-------|------------|
| +0/+101 | posX/posY | Current | Changing | Current | Frozen | Frozen | N/A |
| +202/+303 | offX/offY | 0 | Moving | 0 | Frozen | 0 | N/A |
| +404 | facing | Last dir | Move dir | Target dir | Frozen | Target dir | Saved |
| +1010 | pathStart | = pathEnd | < pathEnd | = pathEnd | - | = pathEnd | - |
| +1111 | pathEnd | = pathStart | > pathStart | = pathStart | - | = pathStart | - |
| +1313 | attackCooldown | 0 | 0 | Counting | - | Counting | Counting |
| +1414 | attackState | 0 | 1 | 3 | - | 3 | 0 |
| +1515 | stuckCounter | 0 | -5 to +5 | 0 | - | 0 | 0 |
| +1616 | hp | >0 | >0 | >0 | -1 | >0 | 0 (hidden) |
| +1919 | targetUnit | 0 | 0 or target | Target ref | - | Target ref | 0 |
| +2828 | flags | 0 | bit 4 set | bits 0-2 set | - | bit 7 set | 0 |
| +2929 | flags2 | 0 | 0 | 0 | - | bit 5 set | 0 |

### Attack State Values (offset +1414)

| Value | State | Description |
|-------|-------|-------------|
| 0 | Idle | No attack activity |
| 1 | Moving | Moving toward target |
| 3 | Attacking | Actively firing at target |

### Flag Bits (offset +2828)

| Bit | Meaning | Set In |
|-----|---------|--------|
| 0 | Weapon 1 active | Attacking, Siege |
| 1 | Weapon 2 active | Attacking (multi-weapon) |
| 2 | Weapon 3 active | Attacking (multi-weapon) |
| 3 | Linked to another unit | Transport/garrison |
| 4 | Moving between cells | Moving |
| 5 | Stuck/redirecting | Moving (stuck) |
| 6 | Building production active | Producing |
| 7 | Siege mode / special state | Siege Mode |

### Unit Type Bitmasks

| Bitmask | Value | Meaning |
|---------|-------|---------|
| 16447 (0x403F) | bits 0-3, 5, 14 | Infantry unit types |
| 16256 (0x3F80) | bits 7-13 | Machinery unit types |
| 114688 (0x1C000) | bits 14-16 | Producing buildings / siege-capable |
| 65536 (0x10000) | bit 16 | Large unit (2-cell collision) |

### Stuck Recovery Logic

```mermaid
flowchart TD
    MoveCheck["Unit tries to advance<br/>along path"] --> CellCheck{"Next cell<br/>passable?"}
    CellCheck -->|Yes| Advance["Advance to cell<br/>stuckCounter = 0"]
    CellCheck -->|No| Increment["stuckCounter++<br/>(or -- if blocked behind)"]

    Increment --> StuckCheck{"stuckCounter<br/>≥ 5 or ≤ -5?"}
    StuckCheck -->|No| Wait["Wait 1 tick<br/>Try again"]
    StuckCheck -->|Yes| HasRally{"Has rally point?"}

    HasRally -->|Yes| NewRally["Set new rally point<br/>Recalculate path"]
    HasRally -->|No| ClearPath["Clear current path<br/>Return to Idle"]

    NewRally --> RecalcPath["Recalculate path<br/>from current position"]
    RecalcPath --> PathFound{"Path found?"}
    PathFound -->|Yes| MoveCheck
    PathFound -->|No| ClearPath

    Wait --> MoveCheck
    Advance --> MoveCheck
```
