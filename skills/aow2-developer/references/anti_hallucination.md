# Anti-Hallucination Protocol

## The Problem

LLMs can "hallucinate" — generate plausible-sounding but factually incorrect information.
In game development, this means inventing unit stats, combat formulas, or game mechanics
that don't exist in the original game. This is catastrophic for a spec-driven recreation
where faithfulness to the original is paramount.

## Core Rules

### Rule 1: Never Invent Game Data
All unit stats, building stats, weapon damage, armor values, costs, build times, and
research effects MUST come from the reverse-engineered documentation. If you can't find
a value in the docs, do NOT make one up.

**How to follow this rule:**
- Before implementing any stat, read the relevant JSON file:
  - `gameplay_analysis/complete_unit_stats.json`
  - `gameplay_analysis/complete_building_stats.json`
  - `gameplay_analysis/decrypted_data.json`
- If a value is missing, mark it with `// ASSUMPTION:` and the reasoning
- If multiple sources conflict, use the most authoritative source (decompiled code > wiki)

### Rule 2: Cross-Reference Before Implementing
Before writing any game logic, verify it against at least two sources:

1. **Primary source**: Decompiled code analysis (`MASTER_DOCUMENTATION.md`)
2. **Secondary source**: Machine-readable data (`decrypted_data.json`, `complete_unit_stats.json`)
3. **Tertiary source**: Wiki research (`wiki_research/research_results.md`)

If sources conflict, use the primary source and note the discrepancy.

### Rule 3: Validate Implementation Against Spec
After implementing any system, verify:
- Combat damage output matches the documented formula
- Unit stats match the JSON data exactly
- Building costs match the documented values
- Research effects match the tech tree documentation

**Validation example:**
```java
// After implementing DamageCalculator, run this test:
@Test
void shouldMatchDocumentedDamageFormula() {
    // Zeus (typeId=16) attacking Infantry (typeId=1)
    // From complete_unit_stats.json: Zeus damage = 25, Infantry armor = 1
    // From combat_formulas.md: damage = weaponDamage * (10 - armor) / 10
    // Expected: 25 * (10 - 1) / 10 = 22.5 → 22 (integer)
    int damage = DamageCalculator.calculate(zeusAttacker, infantryTarget);
    assertEquals(22, damage, "Zeus vs Infantry damage must match RE formula");
}
```

### Rule 4: Read Before Writing
Never assume you know the spec from memory. Always read the reference file before
implementing. The RE documentation is 3,300+ lines — no one can remember all of it.

**Workflow:**
1. User asks to implement feature X
2. You read the relevant reference file(s)
3. You quote the relevant section in your plan
4. You implement based on what you read, not what you think you know

### Rule 5: Mark All Assumptions
When the spec is incomplete or ambiguous:
1. Mark the assumption clearly: `// ASSUMPTION: artillery splash radius = 2 tiles (not in RE docs)`
2. Record the assumption in `ProjectProgress.md`
3. Note it for the analyzer skill to validate later

## Specific Anti-Hallucination Checks by System

### Combat System
- [ ] Damage formula exactly matches `combat_formulas.md`
- [ ] Armor calculation matches documented `cf[2][unitType]` lookup
- [ ] Death animations match `bi[]` and `bd[]` arrays
- [ ] Splash damage matches documented artillery behavior
- [ ] Attack cooldowns match `cf[0][unitType]` values

### Unit Stats
- [ ] All 14 unit types have correct stats from `complete_unit_stats.json`
- [ ] Unit costs match documented values
- [ ] Movement speeds match documented values
- [ ] Population costs match documented values
- [ ] Infantry vs machinery classification matches bitmask 16447/16256

### Building Stats
- [ ] All 16 building types have correct stats from `complete_building_stats.json`
- [ ] Building costs match documented values
- [ ] Power consumption/production matches spec
- [ ] Construction times match spec
- [ ] Production queue behavior matches documented "sequential processing"

### Economy
- [ ] Resource generation rate matches documented formula
- [ ] Command Centre diminishing returns: "30% less per additional CC"
- [ ] Generator power radius matches spec

### Pathfinding
- [ ] Algorithm matches documented A* approach in `pathfinding.md`
- [ ] Terrain passability matches documented tile types
- [ ] Collision handling matches documented behavior

### AI
- [ ] AI behavior matches documented patterns in `ai_analysis.md`
- [ ] Build orders match documented AI tendencies
- [ ] Attack/retreat thresholds match documented values

### Network
- [ ] Lockstep protocol matches documented session lifecycle
- [ ] Command serialization covers all 34 message types from `packet_formats.json`
- [ ] Desync detection matches documented approach

## When You're Not Sure

1. **Read the source code**: Check `source_readable/com/herocraft/game/artofwar2ol/`
2. **Check decrypted_data.json**: 3.76 MB of extracted game data
3. **Check text_strings.json**: 567 decoded strings with in-game descriptions
4. **Ask the user**: If you genuinely cannot find the answer, say so
5. **Mark as ASSUMPTION**: Never silently guess

## Red Flags (Signs of Hallucination)

- You find yourself writing numbers without checking the JSON files first
- You're "pretty sure" about a value but haven't verified it
- You're adapting a formula from another game (StarCraft, AoE, etc.)
- You're adding features that weren't in the original game (unless explicitly requested as new features)
- Your implementation "seems right" but you haven't cross-referenced the docs

## The Golden Rule

**When in doubt, read the docs. When still in doubt, mark it as an assumption. Never fabricate data.**
