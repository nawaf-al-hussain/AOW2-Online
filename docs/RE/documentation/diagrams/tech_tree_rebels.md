# Tech Tree - Rebels (Resistance)

## Resistance Research Dependencies

The Resistance represents the Liberation Army with speed, stealth and surprise tactics. Their research tree is based on research IDs 24-47 and 44 from the `g(int i)` method analysis. The Resistance collects resources faster and produces units more cheaply.

```mermaid
graph TD
    subgraph Legend
        L1["🟢 Infantry Research"]
        L2["🔵 Machinery Research"]
        L3["🟡 Building/Economy Research"]
        L4["🔴 Weapon Research"]
        L5["🟣 Strategic Research"]
    end

    %% Tier 1 - Foundation (Resistance)
    R24["R24: Infantry Armour +1<br/>types 0,2,4,14"]
    R25["R25: Attack Range<br/>Reduction /3<br/>(Player 1)"]
    R29["R29: Building Radius +1<br/>Unlocks chain 30"]
    R39["R39: Supply Cap = 8<br/>(Player 1)"]
    R40["R40: Building Armour = 9<br/>(Player 1)"]
    R45["R45: Credit Limit = 120<br/>(Player 1)"]

    %% Tier 2 - Infantry Path
    R24 --> R26["R26: Attack Speed +1<br/>types 0,2,3<br/>Production +1 types 0,4"]
    R24 --> R27["R27: Attack Range -1<br/>types 0,2,4,14<br/>Unlocks chain"]

    %% Tier 3 - Coyote/Light Vehicle Chain
    R26 --> R28["R28: Attack Range +1 (type 15)<br/>Production +1 types 2,2<br/>Coyote range upgrade"]

    %% Tier 4 - Tank/Medium Chain
    R27 --> R29
    R28 --> R29

    %% Tier 5 - Advanced Tank Research
    R29 --> R30["R30: Attack Speed +2 (type 3)<br/>Range +2 (type 3)<br/>Production +2 (type 14)<br/>+2 (type 4)<br/>Sniper upgrade"]
    R29 --> R31["R31: Attack Speed +1 (type 4)<br/>+1 (type 5)<br/>Production +1 (type 6)<br/>+1 (type 8)"]

    %% Tier 6 - Heavy Machinery Chain
    R30 --> R32["R32: Attack Range -1<br/>types 6,8,10,15,12<br/>Unlocks chain 33-37<br/>Building Radius +1"]
    R31 --> R32

    %% Tier 7 - Machinery Upgrades
    R32 --> R33["R33: Infantry Armour +1<br/>types 6,8,10,15,12"]
    R32 --> R34["R34: Attack Range<br/>Reduction /3<br/>(Player 1)"]
    R33 --> R35["R35: Attack Speed -2<br/>(faster) types 12, 14"]
    R33 --> R36["R36: Unit Siege Upgrade<br/>Type 10 siege = 15<br/>Mine Lizard siege mode"]

    %% Tier 8 - Advanced Weapons
    R35 --> R37["R37: Building Radius +1<br/>Unlocks chain 38"]
    R36 --> R37

    %% Tier 9 - Final Weapon
    R37 --> R38["R38: Attack Damage +2 (type 20)<br/>Range +2 (type 20)<br/>Production +2 (type 12)<br/>MLRS Torrent upgrade"]

    %% Economy Path
    R39 --> R41["R41: Building Radius +1<br/>Unlocks chain 42"]
    R41 --> R42["R42: Building Radius +1<br/>(cumulative)"]
    R39 --> R44["R44: Production P[5] = 7<br/>(Player 1)"]
    R45 --> R46["R46: Score Bonus = 30<br/>(Player 1)"]
    R46 --> R47["R47: Display Bonus = 25<br/>(Player 1)"]

    %% Building Defence Path
    R40 --> R41

    %% Styling by category
    style R24 fill:#4caf50,stroke:#333,color:#fff
    style R26 fill:#4caf50,stroke:#333,color:#fff
    style R27 fill:#4caf50,stroke:#333,color:#fff
    style R33 fill:#4caf50,stroke:#333,color:#fff

    style R28 fill:#2196f3,stroke:#333,color:#fff
    style R30 fill:#2196f3,stroke:#333,color:#fff
    style R31 fill:#2196f3,stroke:#333,color:#fff
    style R32 fill:#2196f3,stroke:#333,color:#fff
    style R35 fill:#2196f3,stroke:#333,color:#fff
    style R36 fill:#2196f3,stroke:#333,color:#fff

    style R29 fill:#ff9800,stroke:#333,color:#fff
    style R37 fill:#ff9800,stroke:#333,color:#fff
    style R39 fill:#ff9800,stroke:#333,color:#fff
    style R40 fill:#ff9800,stroke:#333,color:#fff
    style R41 fill:#ff9800,stroke:#333,color:#fff
    style R42 fill:#ff9800,stroke:#333,color:#fff
    style R45 fill:#ff9800,stroke:#333,color:#fff

    style R25 fill:#f44336,stroke:#333,color:#fff
    style R34 fill:#f44336,stroke:#333,color:#fff
    style R38 fill:#f44336,stroke:#333,color:#fff

    style R44 fill:#9c27b0,stroke:#333,color:#fff
    style R46 fill:#9c27b0,stroke:#333,color:#fff
    style R47 fill:#9c27b0,stroke:#333,color:#fff
```

## Resistance Research Chain Summary

```mermaid
graph LR
    subgraph "Infantry Path"
        I1["R24: Infantry Armour +1"] --> I2["R26: Attack Speed +1"]
        I1 --> I3["R27: Attack Range -1"]
        I2 --> I4["R28: Coyote Range +1"]
    end

    subgraph "Machinery Path"
        I3 --> M1["R29: Building Radius +1"]
        I4 --> M1
        M1 --> M2["R30: Sniper Speed+2 Range+2"]
        M1 --> M3["R31: Light Vehicle Speed+1"]
        M2 --> M4["R32: Heavy Range -1<br/>+ Building Radius"]
        M3 --> M4
        M4 --> M5["R33: Machinery Armour +1"]
        M5 --> M6["R35: Attack Speed -2"]
        M5 --> M7["R36: Mine Lizard Siege"]
        M6 --> M8["R37: Building Radius +1"]
        M7 --> M8
        M8 --> M9["R38: MLRS Damage +2"]
    end

    subgraph "Economy Path"
        E1["R39: Supply Cap = 8"] --> E2["R41: Building Radius +1"]
        E2 --> E3["R42: Building Radius +1"]
        E1 --> E4["R44: Production P[5]=7"]
        E1 --> E5["R45: Credit Limit = 120"]
        E5 --> E6["R46: Score Bonus = 30"]
        E6 --> E7["R47: Display Bonus = 25"]
    end

    subgraph "Building Defence"
        B1["R40: Building Armour = 9"] --> B2["R41: Building Radius"]
    end
```

### Resistance vs Confederation Research Comparison

```mermaid
graph TB
    subgraph Confederation["CONFEDERATION (Player 0)"]
        direction TB
        C_R0["R0: Infantry Armour +2"]
        C_R6["R6: Rhino → Heavy Assault"]
        C_R12["R12: Hammer → Mine Scorpio"]
        C_R14["R14: Artillery +10 dmg"]
        C_R15["R15: Supply Cap = 8"]
        C_R16["R16: Building Armour = 9"]
        C_R21["R21: Credit Limit = 120"]
    end

    subgraph Resistance["RESISTANCE (Player 1)"]
        direction TB
        R_R24["R24: Infantry Armour +1"]
        R_R30["R30: Sniper Speed+2 Range+2"]
        R_R36["R36: Mine Lizard Siege"]
        R_R38["R38: MLRS +2 dmg +2 range"]
        R_R39["R39: Supply Cap = 8"]
        R_R40["R40: Building Armour = 9"]
        R_R45["R45: Credit Limit = 120"]
    end

    C_R0 -.->|"Confed +1 armour<br/>advantage"| R_R24
    C_R14 -.->|"Confed +8 more dmg<br/>advantage"| R_R38
    C_R15 -.->|"Same effect<br/>different player"| R_R39
```

### Resistance Research Effects Detail

| ID | Category | Effect | Target |
|----|----------|--------|--------|
| 24 | Infantry | Armour +1 | Types 0, 2, 4, 14 |
| 25 | Strategic | Attack range reduction /3 | Player 1 |
| 26 | Infantry | Attack speed +1 | Types 0, 2, 3; Production types 0, 4 |
| 27 | Infantry | Attack range -1 | Types 0, 2, 4, 14 |
| 28 | Light Vehicle | Range +1 (Coyote) | Type 15; Production types 2 |
| 29 | Building | Radius +1 | Building placement range |
| 30 | Sniper | Speed +2, Range +2 | Type 3; Production types 14, 4 |
| 31 | Light Vehicle | Speed +1 | Types 4, 5; Production types 6, 8 |
| 32 | Heavy Machinery | Range -1, Radius +1 | Types 6, 8, 10, 15, 12 |
| 33 | Infantry | Armour +1 | Types 6, 8, 10, 15, 12 |
| 34 | Strategic | Range reduction /3 | Player 1 |
| 35 | Machinery | Attack speed -2 (faster) | Types 12, 14 |
| 36 | Unit Upgrade | Siege mode upgrade | Type 10 Mine Lizard = 15 |
| 37 | Building | Radius +1 | Building placement range |
| 38 | Artillery | Damage +2, Range +2 | Type 20 MLRS; Production type 12 |
| 39 | Economy | Supply cap = 8 | Player 1 |
| 40 | Building | Armour = 9 | Player 1 buildings |
| 41 | Building | Radius +1 | Building placement range |
| 42 | Building | Radius +1 (cumulative) | Building placement range |
| 43 | Production | P[4] = 7 | Player 0 (shared) |
| 44 | Production | P[5] = 7 | Player 1 |
| 45 | Economy | Credit limit = 120 | Player 1 |
| 46 | Scoring | Score bonus = 30 | Player 1 |
| 47 | Scoring | Display bonus = 25 | Player 1 |

### Faction Asymmetry Summary

| Aspect | Confederation | Resistance |
|--------|--------------|------------|
| **Armour** | +2 base infantry armour | +1 base infantry armour |
| **Speed** | Slower units, slower economy | Faster units, faster resource collection |
| **Firepower** | Higher per-unit damage (+10 artillery) | Moderate damage (+2 MLRS) |
| **Range** | Longer base ranges | Shorter ranges, compensated with upgrades |
| **Economy** | Standard income | Faster resource generation |
| **Cost** | More expensive units | Cheaper units, more cost-effective |
| **Production** | Standard build times | Faster production |
| **Tactics** | Defensive, head-on engagements | Hit-and-run, flanking, speed advantage |
| **Building Radius** | Starts with R5 (+1) | Starts with R29 (+1), cumulative |
| **Unique Units** | T-22 Zeus (heavy tank) | Coyote (rotating turret) |
