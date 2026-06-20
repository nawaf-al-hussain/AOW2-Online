# Tech Tree - Confederation

## Confederation Research Dependencies

The Confederation represents the Global Confederation with advanced technology, superior armor and firepower. Their research tree is based on research IDs 0-23 and 43 from the `g(int i)` method analysis.

```mermaid
graph TD
    subgraph Legend
        L1["🟢 Infantry Research"]
        L2["🔵 Machinery Research"]
        L3["🟡 Building/Economy Research"]
        L4["🔴 Weapon Research"]
        L5["🟣 Strategic Research"]
    end

    %% Tier 1 - Foundation
    R0["R0: Infantry Armour +2<br/>Sniper armour +2<br/>Light armour +2<br/>Unlocks chain"]
    R4["R4: Building Armour +4<br/>Production armour +4"]
    R5["R5: Building Radius +1<br/>Unlocks chain"]
    R15["R15: Supply Cap = 8<br/>(Player 0)"]
    R16["R16: Building Armour = 9<br/>(Player 0)"]
    R21["R21: Credit Limit = 120<br/>(Player 0)"]

    %% Tier 2 - Infantry Path
    R0 --> R1["R1: Attack Range<br/>Reduction /3<br/>(Player 0)"]
    R0 --> R2["R2: Attack Speed -2<br/>(faster)"]
    R0 --> R3["R3: Attack Damage +2<br/>Production Damage +2"]

    %% Tier 3 - Sniper/Infantry Chain
    R2 --> R6["R6: Unit Upgrade<br/>Type 18 Rhino → Type 7<br/>Heavy Assault unlock"]
    R3 --> R7["R7: Attack Speed +5 (type 11)<br/>+8 (type 13)<br/>Production bonuses"]

    %% Tier 4 - Heavy Machinery Chain
    R6 --> R8["R8: Attack Range -1<br/>types 7,18,9,11,17,13,16<br/>Building Radius +1<br/>Unlocks chain 9-13"]
    R7 --> R8

    %% Tier 5 - Advanced Machinery
    R8 --> R9["R9: Infantry Armour +2<br/>types 7,18,9,11,17,13,16"]
    R8 --> R10["R10: Attack Range<br/>Reduction /3<br/>(Player 1)"]
    R9 --> R11["R11: Attack Speed -2<br/>(faster) types 11, 13"]
    R9 --> R12["R12: Unit Upgrade<br/>Type 17 Hammer → Type 11<br/>Mine Scorpio unlock"]

    %% Tier 6 - Advanced Weapons
    R11 --> R13["R13: Building Radius +1<br/>Unlocks chain 14"]
    R12 --> R13

    %% Tier 7 - Artillery/Siege
    R13 --> R14["R14: Attack Damage +10 (type 21)<br/>Range +2 (type 21)<br/>Production +2 (type 16)<br/>+5 (type 13)"]

    %% Economy Path
    R5 --> R17["R17: Unit Limit +2<br/>(Player 0)<br/>Production +1 (type 15)<br/>Speed = 20"]
    R15 --> R17
    R17 --> R22["R22: Score Bonus = 30<br/>(Player 0)"]
    R21 --> R22
    R22 --> R23["R23: Display Bonus = 25<br/>(Player 0)"]

    %% Building Defence Path
    R4 --> R16
    R5 --> R18["R18: Building Radius +1"]
    R16 --> R18

    %% Production Path
    R15 --> R19["R19: Production P[1] = 7<br/>(Player 1)"]
    R15 --> R20["R20: Production P[2] = 7<br/>(Player 1)"]

    %% Credit Path
    R21 --> R43["R43: Production P[4] = 7<br/>(Player 0)"]

    %% Styling by category
    style R0 fill:#4caf50,stroke:#333,color:#fff
    style R1 fill:#4caf50,stroke:#333,color:#fff
    style R2 fill:#4caf50,stroke:#333,color:#fff
    style R3 fill:#4caf50,stroke:#333,color:#fff
    style R9 fill:#4caf50,stroke:#333,color:#fff
    style R24 fill:#4caf50,stroke:#333,color:#fff

    style R6 fill:#2196f3,stroke:#333,color:#fff
    style R7 fill:#2196f3,stroke:#333,color:#fff
    style R8 fill:#2196f3,stroke:#333,color:#fff
    style R11 fill:#2196f3,stroke:#333,color:#fff
    style R12 fill:#2196f3,stroke:#333,color:#fff

    style R4 fill:#ff9800,stroke:#333,color:#fff
    style R5 fill:#ff9800,stroke:#333,color:#fff
    style R13 fill:#ff9800,stroke:#333,color:#fff
    style R15 fill:#ff9800,stroke:#333,color:#fff
    style R16 fill:#ff9800,stroke:#333,color:#fff
    style R17 fill:#ff9800,stroke:#333,color:#fff
    style R18 fill:#ff9800,stroke:#333,color:#fff
    style R21 fill:#ff9800,stroke:#333,color:#fff

    style R10 fill:#f44336,stroke:#333,color:#fff
    style R14 fill:#f44336,stroke:#333,color:#fff

    style R19 fill:#9c27b0,stroke:#333,color:#fff
    style R20 fill:#9c27b0,stroke:#333,color:#fff
    style R22 fill:#9c27b0,stroke:#333,color:#fff
    style R23 fill:#9c27b0,stroke:#333,color:#fff
    style R43 fill:#9c27b0,stroke:#333,color:#fff
```

## Confederation Research Chain Summary

```mermaid
graph LR
    subgraph "Infantry Path"
        I1["R0: Infantry Armour"] --> I2["R1: Range Reduction"]
        I1 --> I3["R2: Attack Speed"]
        I1 --> I4["R3: Attack Damage"]
        I3 --> I5["R6: Rhino→Heavy Assault"]
    end

    subgraph "Machinery Path"
        I5 --> M1["R8: Range Adjustment<br/>+ Building Radius"]
        M1 --> M2["R9: Infantry Armour +2"]
        M2 --> M3["R11: Attack Speed +2"]
        M2 --> M4["R12: Hammer→Scorpio"]
        M3 --> M5["R13: Building Radius"]
        M4 --> M5
        M5 --> M6["R14: Artillery Damage +10"]
    end

    subgraph "Economy Path"
        E1["R5: Building Radius"] --> E2["R17: Unit Limit +2"]
        E1 --> E3["R18: Building Radius +1"]
        E2 --> E4["R22: Score Bonus"]
        E4 --> E5["R23: Display Bonus"]
    end

    subgraph "Building Defence Path"
        B1["R4: Building Armour +4"] --> B2["R16: Building Armour = 9"]
    end

    subgraph "Production Path"
        P1["R15: Supply Cap = 8"] --> P2["R19: Production P[1]=7"]
        P1 --> P3["R20: Production P[2]=7"]
        P1 --> P4["R21: Credit Limit = 120"]
        P4 --> P5["R43: Production P[4]=7"]
    end
```

### Confederation Unit Unlock Sequence

| Research | Unlocks | Effect |
|----------|---------|--------|
| R0 (base) | Infantry, Grenadier, Light Assault | Infantry armour +2 |
| R6 | Heavy Assault (from Rhino) | Unit type upgrade |
| R8 | Sniper, Siege-capable units | Range adjustment |
| R12 | Mine Scorpio (from Hammer) | Unit type upgrade |
| R14 | Advanced Artillery | +10 damage, +2 range for type 21 |

### Confederation Research Effects Detail

| ID | Category | Effect | Target |
|----|----------|--------|--------|
| 0 | Infantry | Armour +2 | Infantry, Sniper, Light Assault |
| 1 | Infantry | Attack range reduction /3 | Player 0 |
| 2 | Infantry | Attack speed -2 (faster) | Specific unit types |
| 3 | Infantry | Attack damage +2 | All combat units |
| 4 | Building | Armour +4 | All buildings, production |
| 5 | Building | Radius +1 | Building placement range |
| 6 | Unit Upgrade | Type 18→Type 7 | Rhino → Heavy Assault |
| 7 | Machinery | Speed +5/+8, Production +8/+5 | Types 11, 13, 17, 9 |
| 8 | Machinery | Range -1, Radius +1 | Heavy units, building radius |
| 9 | Infantry | Armour +2 | Heavy unit types |
| 10 | Strategic | Range reduction /3 | Player 1 |
| 11 | Machinery | Speed -2 (faster) | Types 11, 13 |
| 12 | Unit Upgrade | Type 17→Type 11 | Hammer → Mine Scorpio |
| 13 | Building | Radius +1 | Building placement range |
| 14 | Artillery | Damage +10, Range +2 | Type 21, production bonuses |
| 15 | Economy | Supply cap = 8 | Player 0 |
| 16 | Building | Armour = 9 | Player 0 buildings |
| 17 | Economy | Unit limit +2, Production +1 | Player 0, type 15 |
| 18 | Building | Radius +1 | All buildings |
| 19 | Production | P[1] = 7 | Player 1 |
| 20 | Production | P[2] = 7 | Player 1 |
| 21 | Economy | Credit limit = 120 | Player 0 |
| 22 | Scoring | Score bonus = 30 | Player 0 |
| 23 | Scoring | Display bonus = 25 | Player 0 |
| 43 | Production | P[4] = 7 | Player 0 |
