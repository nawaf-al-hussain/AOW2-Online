# Validation Methods

## How to Validate Different Types of Claims

### 1. Numeric Stat Validation

When checking if a unit stat is correct:

1. **Read the RE source**:
   ```bash
   # Find the unit in the JSON file
   cat /tmp/aow2-reference/project/gameplay_analysis/complete_unit_stats.json | \
     python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps([u for u in d if u.get('name')=='Zeus'], indent=2))"
   ```

2. **Read the implementation**:
   ```bash
   # Find the stat in the Java code
   rg "ZEUS" /home/z/my-project/AOW2-Online/aow2-common/src/ -A 10
   ```

3. **Compare**: Every numeric value must match exactly (or be within documented range)

### 2. Formula Validation

When checking if a combat formula is correct:

1. **Read the formula from RE docs**:
   ```bash
   cat /tmp/aow2-reference/project/gameplay_analysis/combat_formulas.md
   ```

2. **Read the implementation**:
   ```bash
   rg "calculateDamage|applyDamage" /home/z/my-project/AOW2-Online/aow2-core/src/ -A 15
   ```

3. **Write a verification test**:
   ```java
   // Create a test that uses documented values and expected results
   @Test
   void verifyDamageFormulaAgainstSpec() {
       // From combat_formulas.md: damage = cg[0][projectileType] * (10 - armour) / 10
       // For Zeus (projectileType=10) vs Infantry (armour=1):
       // cg[0][10] = 25 (from complete_unit_stats.json)
       // Expected: 25 * (10 - 1) / 10 = 22
       assertEquals(22, DamageCalculator.calculate(zeus, infantry));
   }
   ```

4. **Run the test**:
   ```bash
   cd /home/z/my-project/AOW2-Online && ./gradlew test --tests "*verifyDamageFormulaAgainstSpec*"
   ```

### 3. Completeness Validation

When checking if all features are implemented:

1. **Count from RE docs**:
   ```bash
   # Count unit types in RE data
   cat /tmp/aow2-reference/project/gameplay_analysis/complete_unit_stats.json | \
     python3 -c "import json,sys; d=json.load(sys.stdin); print(f'Total units: {len(d)}')"
   ```

2. **Count from implementation**:
   ```bash
   # Count unit type enums in code
   rg "enum.*UnitType" /home/z/my-project/AOW2-Online/ -A 30 | rg "^\s+[A-Z]"
   ```

3. **Compare counts**: They must match

### 4. Architecture Validation

When checking if the project structure is correct:

1. **Read the expected structure**:
   ```
   Read aow2-developer/references/project_structure.md
   ```

2. **Check actual structure**:
   ```bash
   find /home/z/my-project/AOW2-Online/ -type f -name "*.java" | head -50
   ```

3. **Verify**:
   - Module names match
   - Package names match
   - Key classes exist in expected locations

### 5. Test Coverage Validation

```bash
cd /home/z/my-project/AOW2-Online && ./gradlew jacocoTestReport
# Check the generated report in build/reports/jacoco/
```

Verify coverage meets targets:
- Core systems: 90%+
- Network: 85%+
- UI: 60%+
- Overall: 80%+

### 6. Regression Validation

After any fix:
1. Run full test suite: `./gradlew test`
2. Verify no previously passing tests broke
3. Run the game and verify affected system works
4. Check that the fix didn't introduce new issues

## Self-Validation Protocol

### Before Reporting Any Issue

1. **Primary check**: Read the RE documentation file you're citing
2. **Implementation check**: Read the actual code file
3. **Re-read check**: Read both again to confirm
4. **Confidence rating**:
   - **HIGH**: Both sources clearly support the claim, no ambiguity
   - **MEDIUM**: Sources support the claim but some interpretation needed
   - **LOW**: Sources are ambiguous or the claim requires inference

### When You Cannot Confirm

If you cannot fully verify a claim:
1. Mark it as `[UNVERIFIED]` in the report
2. Explain what you checked and what's uncertain
3. Suggest how it could be verified (specific test, specific file to check)
4. Never present an unverified claim as a confirmed issue

### Common Hallucination Traps

1. **"I think the unit has X HP"** → Read the JSON file, don't guess
2. **"The formula should be..."** → Read combat_formulas.md, don't derive
3. **"There are N missions"** → Count from campaign_guide.md, don't assume
4. **"The original game did X"** → Quote the specific RE doc section
5. **"This is a common RTS pattern"** → This project follows the RE spec, not genre conventions

## Running Automated Validation Scripts

When available, use the project's own tests for validation:

```bash
# Full test suite
cd /home/z/my-project/AOW2-Online && ./gradlew test

# Specific system tests
./gradlew :aow2-core:test --tests "com.aow2.core.combat.*"
./gradlew :aow2-core:test --tests "com.aow2.core.economy.*"
./gradlew :aow2-core:test --tests "com.aow2.core.movement.*"

# Compile check
./gradlew compileJava

# Code quality
./gradlew check
```

## Evidence Collection

When documenting an issue, always include:

1. **RE documentation quote**: Exact text from the reference file
2. **Code quote**: Exact code from the implementation
3. **Discrepancy**: Clear explanation of how they differ
4. **Location**: File path and line number (or search pattern)
5. **Confidence**: HIGH / MEDIUM / LOW

Example:
```
### C3: Zeus tank damage incorrect

**Confidence**: HIGH
**RE Reference**: gameplay_analysis/complete_unit_stats.json, Zeus entry: "attackDamage": 25
**Implementation**: aow2-common/src/.../UnitStats.java line 42: ZEUS(16, "T-22 Zeus", 25, 3, ...)
                                                    attackDamage=25 ✓
                  aow2-core/src/.../DamageCalculator.java line 28: damage = weaponDamage * (10 - armor) / 5
                                                                        divisor should be 10, not 5 ✗
**Expected**: damage = 25 * (10 - 1) / 10 = 22
**Actual**: damage = 25 * (10 - 1) / 5 = 45
**Fix**: Change divisor from 5 to 10 in DamageCalculator.java line 28
```
