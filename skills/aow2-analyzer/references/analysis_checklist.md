# Analysis Checklist — Per-System Verification

## Combat System Checklist

### Damage Formula
- [ ] Verify damage formula matches `combat_formulas.md` exactly:
  ```
  damage = weaponDamage * (10 - targetArmour) / 10
  damage = max(min(damage, weaponDamage - targetArmour), 1)
  ```
- [ ] Verify armor lookup matches `cf[2][unitType]` from RE data
- [ ] Verify armor research bonus is applied correctly
- [ ] Verify buildings have 0 base armor
- [ ] Verify damage is clamped to minimum of 1

### Projectile System
- [ ] Verify projectile types match documented types (bullet, rocket, artillery, flame)
- [ ] Verify splash damage for artillery matches spec
- [ ] Verify projectile speed and trajectory
- [ ] Verify hit detection against moving targets

### Death System
- [ ] Verify infantry death animation matches `bi[]` array: `{231, 249, 249, 259, 247}`
- [ ] Verify death random range matches `bd[]` array: `{16, 10, 10, 3, 2}`
- [ ] Verify machinery death animation is frame 2
- [ ] Verify explosion effects spawn for machinery deaths
- [ ] Verify HP is set to -1 on death (not 0)

### Attack Cooldowns
- [ ] Verify attack cooldowns per unit type match `cf[0][unitType]`
- [ ] Verify attack cycle counter behavior matches original
- [ ] Verify weapon cooldown timer logic

## Unit Stats Checklist

### For Each Unit Type (14 total)
- [ ] Verify max HP matches `complete_unit_stats.json`
- [ ] Verify armor value matches spec
- [ ] Verify movement speed matches spec
- [ ] Verify attack damage matches spec
- [ ] Verify attack range matches spec
- [ ] Verify cost (credits) matches spec
- [ ] Verify build time matches spec
- [ ] Verify population cost matches spec
- [ ] Verify infantry/machinery classification matches bitmask
- [ ] Verify large unit flag (2-cell collision) for Fortress, etc.

### Unit Type List (must all exist)
- [ ] Infantry (typeId=1)
- [ ] Grenadier (typeId=2)
- [ ] Sniper (typeId=3)
- [ ] Light Assault (typeId=4)
- [ ] Heavy Assault (typeId=7)
- [ ] Flame Assault (typeId=8)
- [ ] Mine Frog (typeId=9)
- [ ] Mine Lizard (typeId=10)
- [ ] Mine Scorpio (typeId=11)
- [ ] Coyote (typeId=15)
- [ ] T-22 Zeus (typeId=16)
- [ ] T-21 Hammer (typeId=17)
- [ ] Rhino (typeId=18)
- [ ] AV-40 Fortress (typeId=19)
- [ ] MLRS Torrent (typeId=20)
- [ ] Armadillo (typeId=21)
- [ ] Porcupine (typeId=22)

## Building Stats Checklist

### For Each Building Type (16 total)
- [ ] Verify building type ID matches spec
- [ ] Verify construction HP / build time matches spec
- [ ] Verify cost matches spec
- [ ] Verify power consumption matches spec
- [ ] Verify power production (for Generator) matches spec
- [ ] Verify production capabilities match spec
- [ ] Verify defensive stats (for Bunker, Rocket Tower)

### Building Type List
- [ ] Command Centre (typeId=1)
- [ ] Barracks/Infantry Centre (typeId=2)
- [ ] Machine Factory (typeId=3)
- [ ] Generator/Powerplant (typeId=4)
- [ ] Technology Centre (typeId=5)
- [ ] Headquarters (typeId=6)
- [ ] Bunker (typeId=7)
- [ ] Rocket Launcher/Tower (typeId=8)
- [ ] Wall/Barrier (typeId=9)
- [ ] Locator/Radar (typeId=10)

## Economy Checklist

### Resource Generation
- [ ] Verify auto-generation rate matches spec
- [ ] Verify Command Centre diminishing returns (30% less per additional)
- [ ] Verify Resistance collects faster than Confederation (if documented)

### Building Placement
- [ ] Verify Command Centre radius enforcement
- [ ] Verify collision checking with terrain
- [ ] Verify collision checking with other buildings
- [ ] Verify power connection to Generator

### Power System
- [ ] Verify Generator power radius matches spec
- [ ] Verify buildings without power stop functioning
- [ ] Verify power upgrade increases production by 30%

## Research/Tech Tree Checklist

### Confederation Techs (8 base)
- [ ] All 8 techs present and match spec
- [ ] Research times match spec
- [ ] Research costs match spec
- [ ] Research effects (16 asymmetric) match spec
- [ ] Only one research at a time

### Resistance Techs (8 base)
- [ ] All 8 techs present and match spec
- [ ] Asymmetric effects differ from Confederation where documented
- [ ] Unit unlock dependencies correct (Snipers, Flame Assault, etc.)

## Pathfinding Checklist

### Algorithm
- [ ] A* algorithm implemented (reference `pathfinding.md`)
- [ ] Terrain passability matches tile types
- [ ] Water tiles are impassable
- [ ] Mountain tiles are impassable
- [ ] Building tiles block movement
- [ ] Friendly units can be navigated around

### Movement
- [ ] 8-directional movement (matching facing directions)
- [ ] Sub-tile pixel offsets for smooth movement
- [ ] Stuck detection and re-routing (stuckCounter from original)
- [ ] Group formation movement with spacing

## AI Checklist

### Behavior Patterns (reference `ai_analysis.md`)
- [ ] AI builds base in logical order
- [ ] AI produces mixed unit compositions
- [ ] AI attacks when military advantage detected
- [ ] AI retreats when outnumbered
- [ ] AI researches technologies
- [ ] AI captures mines for resources
- [ ] AI uses terrain (defensive positions, chokepoints)

### Difficulty Levels
- [ ] Easy: Slower decisions, simpler strategies
- [ ] Normal: Standard behavior
- [ ] Hard: Faster decisions, optimized strategies

## Campaign Checklist

### Episode 1: Global Confederation (7 missions)
- [ ] Mission 1-7 load with correct maps
- [ ] Mission objectives match `campaign_guide.md`
- [ ] Victory conditions work
- [ ] Defeat conditions work
- [ ] Mission briefings display correctly
- [ ] Save/load works mid-mission

### Episode 2: Liberation of Peru (7 missions)
- [ ] Mission 1-7 load with correct maps
- [ ] Player controls Resistance faction
- [ ] Story/lore matches documentation

### Custom Missions (15)
- [ ] All 15 missions present and playable

## Multiplayer Checklist

### Lockstep P2P
- [ ] Command serialization covers all player actions
- [ ] Both clients produce identical game state from same command stream
- [ ] Desync detection mechanism works
- [ ] Frame synchronization protocol is correct
- [ ] Latency compensation is implemented

### Spring Boot Server
- [ ] Player registration works
- [ ] Authentication (JWT) works
- [ ] Matchmaking queue functions
- [ ] Game session management works
- [ ] Match results are persisted
- [ ] Leaderboard is updated

## Map Builder Checklist
- [ ] All terrain types paintable
- [ ] All building types placeable
- [ ] All unit types placeable
- [ ] Map validation catches errors
- [ ] Map testing works (play immediately)
- [ ] Map sharing (upload/download) works

## Modding Checklist
- [ ] Mod loader finds and loads mods
- [ ] JSON stat overrides work
- [ ] Lua scripts execute
- [ ] Mod conflicts are detected
- [ ] Invalid mods are rejected gracefully
- [ ] Hot-reload works during development

## Replay Checklist
- [ ] All commands recorded
- [ ] Replay file is compact
- [ ] Playback produces identical game state
- [ ] Seeking works (jump to any tick)
- [ ] Replay sharing works
- [ ] Format versioning for compatibility
