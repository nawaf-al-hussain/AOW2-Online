# AOW2-Online — Master TODO

> Auto-generated from full codebase analysis against RE documentation.
> Date: 2026-06-20 | Scope: All 5 Java modules + web client

---

## 🔴 CRITICAL — Fix Immediately (8 issues)

### C-1: `AttackMove` command crashes multiplayer & replay
- **Files**: `CommandSerializer.java`, `ReplayRecorder.java`
- **Problem**: `CommandType.AttackMove` exists in the sealed interface and is handled in `LockstepEngine.applyCommand()`, but `CommandSerializer.serialize()/deserialize()` and `ReplayRecorder` have no case for it. Causes `MatchError` at runtime.
- **Fix**: Add `AttackMove` case (type `0x0C`) to serialize, deserialize, and replay recording paths.

### C-2: Infantry vs machinery 0.7x multiplier is dead code
- **Files**: `CombatSystem.java:443-483`, `DamageCalculator.java:224-228`
- **Problem**: `getTargetMultiplier()` correctly defines `0.7` for infantry attacking machinery, but it is **never called** during unit-vs-unit combat. Infantry deal full damage to tanks/vehicles instead of the intended 30% reduction. Breaks rock-paper-scissors balance.
- **Fix**: Call `getTargetMultiplier()` in `CombatSystem.executeAttack()` before applying damage, using the attacker's `UnitCategory` and target's `UnitCategory`.

### C-3: Infantry vs building 0.5x multiplier not applied for ranged attacks
- **Files**: `CombatSystem.java:510-513`, `ProjectileSystem.java` (applyDirectDamage/applySplashDamage)
- **Problem**: Melee BULLET attacks on buildings correctly apply the 0.5x multiplier, but ranged ROCKET/ARTILLERY attacks spawn a projectile and the projectile impact handlers never call `getTargetMultiplier()`. Ranged infantry (Grenadiers) deal full damage to buildings.
- **Fix**: Pass attacker type info through `Projectile` and apply `getTargetMultiplier()` in `applyDirectDamage()` and `applySplashDamage()`.

### C-4: Research system ID namespace collision (IDs 8–15)
- **Files**: `ResearchSystem.java:364-478`, `TechTree.java:134-271`
- **Problem**: `TechTree.java` maps IDs 8–15 as **Confederation** researches (Heavy Artillery Upgrade, Composite Armour II, etc.), but `ResearchSystem.applyResearchEffect()` maps IDs 8–15 as **Rebel** researches (Titanium Jacket, First-Aid Kit, etc.). A Confederation player researching ID 8 gets the Rebel "Titanium Jacket" armor bonus instead of the Confed "Heavy Artillery Upgrade".
- **Fix**: Rewrite `applyResearchEffect()` to use the 48-effect ID namespace consistently (Confed 0–23+43, Rebel 24–47).

### C-5: `buildings.json` (client) has ALL stats wrong
- **File**: `aow2-client/src/main/resources/data/buildings.json`
- **Problem**: Every single building (16 total) has 5–11 incorrect stat fields vs RE data. E.g., Command Centre: cost=100 (should be 22), armor=0 (should be 7), powerConsume=20 (should be 2). StatsRegistry.java is the authoritative source and matches RE, but this JSON is pre-RE-analysis placeholder data.
- **Fix**: Either sync `buildings.json` with StatsRegistry values or remove it if the client doesn't load from it directly.

### C-6: `tech_tree.json` (client) is entirely fabricated
- **File**: `aow2-client/src/main/resources/data/tech_tree.json`
- **Problem**: 48 research entries with wrong names, wrong effects, and fabricated categories (e.g., "Orbital Strike", "EMP Shielding", "Sabotage" — none exist in original). No entry matches the RE documentation.
- **Fix**: Replace with data derived from `TechTree.java` or remove if unused.

### C-7: `tech_tree.json` (common) — 9 of 16 globalEffectId mappings incorrect
- **File**: `aow2-common/src/main/resources/data/tech_tree.json` lines 573–592
- **Problem**: Simplified 8-tech-to-48-effect mappings are wrong for 9 entries. E.g., Rebel "Snipers" (localId 4) maps to globalEffectId 28 (Coyote Range Upgrade) instead of the correct sniper-unlock effect. Rebel "Doping" (localId 3) maps to ID 27 (Infantry Range Upgrade) which has the **opposite** effect.
- **Fix**: Audit and correct all 16 mappings against `TechTree.java` and RE documentation.

### C-8: Death animation `attackerCategory` always hardcoded to 0
- **Files**: `CombatSystem.java:317,358,476`, `ProjectileSystem.java:295,367`, `MineDetonationSystem.java:176,237`
- **Problem**: Every call to `calculateDeathAnimationFrame(target, 0)` passes `0` as the attacker category. Only death animation base 231 and range 16 are ever used. The other 4 categories (bases 249, 259, 247) are dead code. All infantry deaths produce the same animation.
- **Fix**: Pass the correct attacker weapon category (from the attacker's weapon type or unit type) to `calculateDeathAnimationFrame()`.

---

## 🟠 HIGH — Fix Before Release (18 issues)

### H-1: BULLET attacks on buildings use wrong armor source
- **File**: `CombatSystem.java:516-517`
- **Problem**: Melee attacks on buildings use `target.getStats().armor()` (e.g., Bunker=8, Wall=15), while projectile attacks correctly use `0`. The RE spec says building armor comes from a per-player research-only array `N[]` (default 0). Neither path correctly implements the N[] array.
- **Fix**: Implement the RE `N[]` armor lookup for buildings and use it consistently for both melee and ranged attacks.

### H-2: Bunker garrison attacks ignore research-adjusted armor
- **File**: `CombatSystem.java:306`
- **Problem**: Uses `nearestEnemy.getStats().armor()` (base only) instead of `armorCalculator.calculateEffectiveArmor(target, getCompletedResearch(target))`. Energy Suit +2 infantry armor is ignored for bunker attacks.
- **Fix**: Use `armorCalculator.calculateEffectiveArmor()` like unit-vs-unit combat does.

### H-3: Mine area damage ignores research-adjusted armor
- **Files**: `MineDetonationSystem.java:168,229`
- **Problem**: Uses `enemy.getStats().armor()` (base only) for damage calculation. Research armor bonuses are ignored for all mine damage.
- **Fix**: Use `armorCalculator.calculateEffectiveArmor()` for mine damage targets.

### H-4: Mine trigger uses Euclidean distance instead of Chebyshev
- **Files**: `Mine.java:98-99`, `MineDetonationSystem.java:221`
- **Problem**: Mine trigger radius uses `getPosition().distanceTo()` (Euclidean) instead of `GridPosition.distanceClass()` (Chebyshev). Units at diagonal distance 1.41 are outside Euclidean radius 1 but inside Chebyshev radius 1.
- **Fix**: Replace `distanceTo()` with `distanceClass()` for mine proximity checks.

### H-5: ROCKET projectile uses fixed flight time (artillery-only in RE)
- **File**: `ProjectileSystem.java:191-193`
- **Problem**: Both ROCKET and ARTILLERY get fixed 15-tick flight time. RE spec says only projectile type 10 (artillery) gets fixed flight time; rockets should use distance-based flight time.
- **Fix**: Remove `WeaponType.ROCKET` from the fixed-flight-time condition.

### H-6: No faction income differential (Resistance should collect faster)
- **File**: `ResourceGenerator.java:84-102`
- **Problem**: RE doc confirms "Resistance collects resources faster than Confederation (confirmed by Gear Games)" but both factions use identical income logic.
- **Fix**: Add a faction-based income multiplier for Resistance.

### H-7: Full RE income formula not implemented
- **File**: `ResourceGenerator.java:84-102`
- **Problem**: RE formula `(baseIncome * playerModifier * 100 / 100) * 20 / (upgradeBonus + 20)` includes `playerModifier` (difficulty scaling) and `upgradeBonus` (building upgrade reduces cycle time). Neither is implemented.
- **Fix**: Implement the full formula with difficulty-based income modifier and upgrade-level time reduction.

### H-8: Pathfinding doesn't support per-unit-type terrain passability
- **File**: `PathfindingSystem.java:166`
- **Problem**: Uses `terrain.isPassable()` (default, no unit type parameter). Infantry SHOULD be able to cross SHALLOW_WATER but pathfinding treats it as impassable for all units.
- **Fix**: Accept a `UnitCategory` parameter in `findPath()` and use `terrain.isPassableBy(category)`.

### H-9: Attack range check uses Euclidean distance instead of Chebyshev
- **File**: `MovementSystem.java:177`
- **Problem**: `unit.getPosition().distanceTo(target.getPosition())` is Euclidean. RE game uses `distanceClass` (Chebyshev) for ALL range checks. A unit with range 6 can attack 6 tiles diagonally in Chebyshev but only ~4.2 in Euclidean.
- **Fix**: Replace `distanceTo()` with `GridPosition.distanceClass()` for all attack range checks.

### H-10: All AI distance checks use Euclidean instead of Chebyshev
- **Files**: `MilitaryAI.java:269,406,425`, `EconomyAI.java:263`
- **Problem**: AI target priority, base defense triggers, and defender sorting all use Euclidean distance. Should use Chebyshev `distanceClass()`.
- **Fix**: Replace all `distanceTo()` calls in AI with `GridPosition.distanceClass()`.

### H-11: AI has no per-unit combat targeting preferences
- **File**: `MilitaryAI.java`
- **Problem**: RE doc specifies infantry targets infantry, light machinery raids, heavy machinery sieges, artillery bombards at range. Implementation uses generic strength-sorted attack groups with no unit-type targeting.
- **Fix**: Add unit-type-based target filtering in `MilitaryAI.decideAction()`.

### H-12: AI has no siege mode auto-activation
- **File**: `AISystem.java` (absent)
- **Problem**: RE doc says units with siege capability auto-enter siege mode when enemies are nearby. No siege mode handling in AI code.
- **Fix**: Add siege mode activation/deactivation logic to `MilitaryAI` or `AISystem`.

### H-13: AI has no garrison behavior
- **File**: `AISystem.java` (absent)
- **Problem**: RE doc describes AI garrisoning units in bunkers/towers for protection, fire rate bonus, and extended vision. Not implemented.
- **Fix**: Add garrison decision logic to `MilitaryAI`.

### H-14: Custom missions have empty `scriptFiles: []` in JSON
- **File**: `custom_missions.json`
- **Problem**: All 15 custom missions have `scriptFiles: []` despite Lua scripts existing on disk. Custom missions run without any scripted waves/objectives/victory conditions.
- **Fix**: Wire each custom mission to its corresponding Lua script file.

### H-15: CampaignScene UI hardcodes wrong episode/mission structure
- **File**: `CampaignScene.java:54-78`
- **Problem**: UI shows 3 episodes × 5 missions (15 total). Actual data has 2 episodes × 7 missions + 15 custom (29 total). Episode 3 "Arctic Operations" is fabricated. UI doesn't use CampaignManager data.
- **Fix**: Read episode/mission data from CampaignManager and remove fabricated Episode 3.

### H-16: REBEL_WALL stats entirely fabricated
- **File**: `StatsRegistry.java:461-469`
- **Problem**: RE only provides upgrade_costs for Rebel Wall. HP=200, armor=15, cost=10, buildTime=10, etc. are all ASSUMPTIONs with no RE basis.
- **Fix**: Extract actual stats from RE binary or document as unverified placeholder.

### H-17: ResearchSystem effect targets wrong units (IDs 2 and 3)
- **File**: `ResearchSystem.java:377-390`
- **Problem**: ID 2 says "Fortress attack speed reduced by 50%" but RE says "Assault's fire rate increases by 50%". ID 3 says "Fortress damage +40%" but RE says "Assault's damage increases by 40%". The term "Assault" is ambiguous — may refer to Fortress (AV-40 "assault machine") or Heavy Assault unit.
- **Fix**: Clarify RE "Assault" unit reference and correct the target unit.

### H-18: `SyncChecker.computeStateHash()` overflow risk in long sessions
- **File**: `SyncChecker.java:82-141`
- **Problem**: `hash * 31` repeated for ~100 entities over 100,000+ ticks can cause 64-bit overflow patterns that increase false-positive desync reports.
- **Fix**: Use proper hash mixing (e.g., `Long.rotateLeft()`, `Objects.hash()`, or CRC32).

---

## 🟡 MEDIUM — Fix Soon (23 issues)

### M-1: Building armor inconsistency — melee vs ranged on buildings
- **Files**: `CombatSystem.java:516-517` vs `ProjectileSystem.java:378`
- **Problem**: Melee BULLET attacks use `target.getStats().armor()` (non-zero), ranged attacks use `0`. Same building, different armor depending on attack type.
- **Fix**: Unify building armor treatment — implement RE `N[]` array for both paths.

### M-2: Projectile flight time formula differs from RE spec
- **File**: `ProjectileSystem.java:184-187`
- **Problem**: Uses `ChebyshevDist / speed`. RE uses `distanceTable[abs(dy)*21 + abs(dx)] / speedTable[projectileType]` — a different indexing scheme with absolute-value table.
- **Fix**: Implement the RE distance table indexing if exact flight time parity is needed.

### M-3: FLAME weapon splash radius defined but unreachable
- **Files**: `ProjectileSystem.java:68`, `WeaponType.java:19`, `CombatSystem.java:458,511`
- **Problem**: FLAME has `splashRadius=1` defined but splash is only enabled for ROCKET and ARTILLERY. Flame Assault's area damage is dead code.
- **Fix**: Either enable splash for FLAME in combat/porjectile code or remove the dead data.

### M-4: Buildings don't block LOS despite comment claiming FIX M-30
- **File**: `FogOfWarSystem.java:283-291`
- **Problem**: FIX comment says "Buildings also block LOS" but `blocksLineOfSight()` only checks for `TerrainType.MOUNTAIN`. RE spec is ambiguous on whether buildings block LOS.
- **Fix**: Clarify RE spec and either add building LOS blocking or correct the comment.

### M-5: Campaign fog-disable condition not implemented
- **File**: `FogOfWarSystem.java`
- **Problem**: RE has conditions to disable fog entirely (timed condition, campaign flag `y.Z[side][16] == 0`). Not implemented. Only matters for campaign mode.
- **Fix**: Add fog override/bypass conditions for campaign-specific scenarios.

### M-6: Campaign UI missing save slot selection and mission completion indicators
- **File**: `CampaignScene.java`
- **Problem**: SaveManager supports 3 save slots but UI has no slot selection/load/delete. CampaignManager tracks completed missions but UI doesn't show completed/available/locked states.
- **Fix**: Add save slot management UI and mission completion visual indicators.

### M-7: Campaign score tracking not implemented
- **File**: `CampaignManager.java:180-200`
- **Problem**: RE specifies scoring (+200 building destroy, -100 own loss). `completeCurrentMission()` doesn't calculate or store scores.
- **Fix**: Implement score calculation in mission completion.

### M-8: No map/veto selection in matchmaking
- **File**: `MatchmakingService.java`
- **Problem**: RE protocol includes map selection before match (Type 41 MAP_LIST). Current matchmaking only pairs by ELO.
- **Fix**: Add map preference/veto system to matchmaking flow.

### M-9: No disconnect/reconnect handling during match
- **File**: `LockstepEngine.java`
- **Problem**: RE specifies 14-second disconnect timeout and reconnection logic. No reconnection protocol exists.
- **Fix**: Implement pause-on-disconnect and reconnection flow.

### M-10: Replay seeking is O(n) — no state snapshotting
- **File**: `ReplayPlayer.java:143-168`
- **Problem**: Backward seek rebuilds entire game state from tick 0. 30-minute replay = 18,000+ ticks of replay. No keyframe/snapshot system.
- **Fix**: Add periodic state snapshots (e.g., every 1000 ticks) for efficient seeking.

### M-11: No replay viewer UI scene
- **File**: `ReplayPlayer.java` (logic only)
- **Problem**: ReplayPlayer has play/pause/seek logic but no `ReplayScene.java` or UI for browsing/watching replays.
- **Fix**: Create ReplayScene with playback controls, timeline scrubber, and speed options.

### M-12: Map editor capped at 127×127 instead of 128×128
- **File**: `MapEditor.java:102-103`
- **Problem**: RE spec uses 128×128 grid. Editor caps at 127×127 (off-by-one).
- **Fix**: Change max dimension to 128.

### M-13: Map editor missing resource deposit placement and AI config metadata
- **File**: `MapEditor.java`
- **Problem**: RE defines resource deposits as special terrain types and 30 metadata shorts per map (AI config, victory conditions, reinforcement schedules). Editor only has basic terrain/building/unit tools.
- **Fix**: Add resource deposit tool and map metadata editor (AI config, victory conditions).

### M-14: Siege range bonus (+3) and deploy time (5 ticks) are unverified ASSUMPTIONs
- **File**: `CombatSystem.java:50,64`
- **Problem**: RE confirms siege mode exists but not exact range bonus or deploy tick count.
- **Fix**: Extract from RE binary or tune via playtesting.

### M-15: Artillery fixed flight time (15 ticks) is an unverified ASSUMPTION
- **File**: `ProjectileSystem.java:41`
- **Problem**: RE formula `at[6][59] - at[5][59] + 1` uses game data arrays not fully extracted.
- **Fix**: Extract exact value from RE binary.

### M-16: Nuclear damage distance factor formula is unverified ASSUMPTION
- **File**: `DamageCalculator.java:99-104`
- **Problem**: Assumes `bS[distanceTable[dy][dx]]` = `12 - max(|dx|,|dy|)`. The actual `bS[]` table values may differ from linear falloff.
- **Fix**: Extract `bS[]` table from RE binary and verify.

### M-17: Ranged attack wind-up state has no RE basis
- **File**: `CombatSystem.java:413-431`
- **Problem**: Implements WIND_UP state (state 2) for ranged units with duration `attackSpeed/2`. RE only documents states 0 (idle), 1 (moving), 3 (attacking). This adds ~50% attack delay for all ranged units.
- **Fix**: Verify whether the original has a wind-up phase. If not, remove it to match original DPS.

### M-18: ResearchSystem research costs/durations are hardcoded ASSUMPTIONs
- **File**: `TechTree.java:134-270`
- **Problem**: RE provides a cost formula but not all parameter values. Costs and durations are hardcoded assumptions.
- **Fix**: Extract research costs/durations from RE binary or derive from the formula.

### M-19: `tech_tree.json` (common) has 3 duplicate globalEffectId mappings
- **File**: `aow2-common/src/main/resources/data/tech_tree.json`
- **Problem**: "Lava flame fuel" and "Volcano flame gun" both map to globalEffectId 7. "Heavy machine gun" and "Reinforced engine" both map to globalEffectId 31. Last-write-wins means one tech always overrides the other.
- **Fix**: Assign unique globalEffectIds or implement proper mapping.

### M-20: Flame Assault unit classification discrepancy
- **File**: `UnitType.java:23` vs `complete_unit_stats.json:53`
- **Problem**: RE says `type: "infantry"` but impl says `UnitCategory.SPECIAL_MACHINERY`. Affects combat calculations (infantry vs machinery multipliers).
- **Fix**: Verify against multiple RE sources (game_data.json says "machinery") and document the resolution.

### M-21: `GameState.drainEvents()` uses CopyOnWriteArrayList.remove(0) — O(n²)
- **File**: `GameState.java:50`
- **Problem**: `processedEvents.remove(0)` copies entire array on each removal. With MAX_PROCESSED_EVENTS=10,000 and many events per tick, causes heavy GC pressure.
- **Fix**: Replace with `ArrayDeque` or `ArrayList` with index-based eviction.

### M-22: `PathfindingSystem` A* is O(n² log n) due to linear PriorityQueue scan
- **File**: `PathfindingSystem.java:248-255`
- **Problem**: `isBetterPathInSet()` iterates the entire PriorityQueue. On 128×128 maps with many entities, causes lag spikes.
- **Fix**: Add a `Map<GridPosition, PathNode>` alongside the priority queue for O(1) lookups.

### M-23: `EntityManager.mines` is a plain ArrayList — not thread-safe
- **File**: `EntityManager.java:42`
- **Problem**: `units`, `buildings`, `projectiles` use ConcurrentHashMap but `mines` uses plain ArrayList. Concurrent mod script access could cause CME.
- **Fix**: Change to `CopyOnWriteArrayList<Mine>` or `ConcurrentHashMap<Integer, Mine>`.

---

## 🟢 LOW — Fix When Convenient (16 issues)

### L-1: Infantry vs building 0.5x and siege 1.5x exact multipliers are ASSUMPTIONs
- **File**: `DamageCalculator.java:217,220`
- **Problem**: RE confirms direction (reduced/bonus) but not exact values (0.5/1.5).
- **Fix**: Extract exact multipliers from RE binary.

### L-2: Siege mode range bonus (+3) and deploy time (5 ticks) are ASSUMPTIONs
- **File**: `CombatSystem.java:50,64`
- **Already tracked as M-14.**

### L-3: Mine arm delay (10 ticks) is an ASSUMPTION
- **File**: `MineDetonationSystem.java:41`
- **Problem**: RE confirms arm delay exists but not the exact tick count.
- **Fix**: Extract from RE binary.

### L-4: Movement speed formula `10 - speed + 1` is unverified
- **File**: `MovementSystem.java:116`
- **Problem**: RE provides speed ratings but not the ticks-per-cell formula. The assumption `MAX_SPEED_RATING = 10` is unverified.
- **Fix**: Extract exact formula from RE binary.

### L-5: Diagonal movement cost uses 1.41 multiplier vs RE lookup table
- **File**: `PathfindingSystem.java:197-198`
- **Problem**: RE uses a terrain-cost lookup table with direction+terrain combined cost. The 1.41 approximation may differ.
- **Fix**: Implement the RE distance/cost lookup table if exact parity needed.

### L-6: AI difficulty tick intervals (60/30/15) are unverified
- **File**: `AIDifficulty.java:19-31`
- **Problem**: RE doesn't specify exact tick intervals per difficulty. Plausible but unconfirmed.
- **Fix**: Extract from RE binary.

### L-7: AI attack advantage (1.5x) and retreat (0.5x) thresholds are ASSUMPTIONs
- **File**: `MilitaryAI.java:52,55`
- **Problem**: Not documented in RE.
- **Fix**: Tune via playtesting against original AI behavior.

### L-8: AI `strategyQuality` probabilistic skip is fabricated
- **File**: `AISystem.java:154`
- **Problem**: No RE basis for random chance to skip optimal decision.
- **Fix**: Consider removing and using deterministic difficulty scaling only.

### L-9: CC placement radius (20 tiles) is an ASSUMPTION
- **File**: `BuildingPlacementSystem.java:37`
- **Problem**: RE says CC constrains placement but no exact radius.
- **Fix**: Extract from RE binary or measure in original game.

### L-10: Production cancel refund (50%) is an ASSUMPTION
- **File**: `ProductionSystem.java:38`
- **Problem**: No RE documentation of refund rate.
- **Fix**: Test in original game.

### L-11: Production time upgrade bonus (+5 per level) is an ASSUMPTION
- **File**: `ProductionSystem.java:211-212`
- **Problem**: RE formula has `(upgradeBonus + 20)` but no per-level increment spec.
- **Fix**: Extract from RE binary.

### L-12: Bunker garrison capacity (5) is an ASSUMPTION
- **File**: `StatsRegistry.java:357-364`
- **Problem**: Not present in RE data.
- **Fix**: Test in original game.

### L-13: All Rebel building stats (except upgrade_costs) assumed = Confed equivalent
- **File**: `StatsRegistry.java:389-469`
- **Problem**: RE only provides upgrade_costs for Rebel buildings. All other stats copied from Confed.
- **Fix**: Extract Rebel building stats from RE binary.

### L-14: REBEL_TOWER weapon type (MACHINE_GUN) is an ASSUMPTION
- **File**: `StatsRegistry.java:452-458`
- **Problem**: RE has no weapon data for Rebel buildings.
- **Fix**: Extract from RE binary.

### L-15: Web client — monolithic `page.tsx` (~1300 lines) and stub API route
- **Files**: `aow2-web/src/app/page.tsx`, `aow2-web/src/app/api/route.ts`
- **Problem**: Entire web UI in one file; stub API route unused.
- **Fix**: Decompose into Next.js route pages. Remove stub or implement server-side API.

### L-16: Web client — Prisma query logging enabled in production
- **File**: `aow2-web/src/lib/db.ts`
- **Problem**: `log: ['query']` logs all DB queries in all environments.
- **Fix**: Gate with `process.env.NODE_ENV === 'development' ? ['query'] : []`.

---

## 📋 UNVERIFIED ASSUMPTIONS — Require RE Binary Extraction (10 items)

These are documented ASSUMPTIONs in the codebase that cannot be verified without further RE binary analysis:

| # | Assumption | File | Value | Impact |
|---|-----------|------|-------|--------|
| A-1 | Nuclear distance factor formula | `DamageCalculator.java:99` | `weaponDamage * (12-d) / 12` | Splash damage accuracy |
| A-2 | Artillery fixed flight time | `ProjectileSystem.java:41` | 15 ticks | Projectile timing |
| A-3 | Mine arm delay | `MineDetonationSystem.java:41` | 10 ticks | Mine behavior timing |
| A-4 | Movement speed formula | `MovementSystem.java:116` | `10 - speed + 1` ticks/cell | Unit movement speed |
| A-5 | CC placement radius | `BuildingPlacementSystem.java:37` | 20 tiles | Base building range |
| A-6 | Cancel refund percent | `ProductionSystem.java:38` | 50% | Economy balance |
| A-7 | Building attack cooldown | `GameConstants` | 5 ticks | Defensive building DPS |
| A-8 | Infantry base recovery rate | `HPRegenerationSystem.java` | 1 HP/cycle | Healing speed |
| A-9 | Machinery base repair rate | `HPRegenerationSystem.java` | 2 HP/cycle | Repair speed |
| A-10 | Rebel building stats | `StatsRegistry.java` | Copied from Confed | Faction balance |

---

## 🧪 MISSING TESTS (2 subsystems)

| Subsystem | File | Notes |
|-----------|------|-------|
| `BuildingUpgradeSystem` | — | Handles credit deduction + HP scaling. 0 tests. |
| `ResourceGenerator` | — | Credit income calculation with diminishing returns. 0 tests. |

---

## 📊 SUMMARY

| Severity | Count | Key Themes |
|----------|-------|-----------|
| 🔴 CRITICAL | 8 | Research ID collision, dead code multipliers, fabricated JSON data, AttackMove serialization crash |
| 🟠 HIGH | 18 | Wrong distance metric (Euclidean vs Chebyshev), missing AI behaviors, campaign wiring bugs, armor inconsistencies |
| 🟡 MEDIUM | 23 | Unverified assumptions, missing features (reconnect, replay UI, map metadata), performance issues |
| 🟢 LOW | 16 | Minor assumptions, code style, web client cleanup |
| **Total** | **65** | |

### Recommended Fix Order

1. **C-1** (AttackMove crash) — 5-minute fix, blocks all multiplayer
2. **C-2 + C-3** (damage multipliers) — Core gameplay balance
3. **C-4** (research ID collision) — Research system completely broken for 8 techs
4. **C-5 + C-6 + C-7** (fabricated JSON data) — Data integrity
5. **C-8** (death animation categories) — Visual correctness
6. **H-4 + H-9 + H-10** (Euclidean→Chebyshev) — Distance metric correctness across all systems
7. **H-6 + H-7** (faction economy) — Gameplay balance
8. **H-8** (per-unit pathfinding) — Movement correctness
9. **H-11 + H-12 + H-13** (AI behaviors) — AI quality
10. **H-14 + H-15** (campaign wiring) — Campaign playability