package com.aow2.core.combat;

import com.aow2.common.event.DamageAppliedEvent;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.UnitStats;
import com.aow2.common.model.UnitType;
import com.aow2.common.model.WeaponType;
import com.aow2.core.engine.GameState;
import com.aow2.core.entity.Unit;
import com.aow2.core.research.ResearchSystem;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for B-6 from FULL_ANALYSIS.md: CombatSystem ignores modded
 * armor research.
 * <p>
 * BUG (before fix): CombatSystem called the hardcoded
 * {@code ArmorCalculator.calculateEffectiveArmor(Unit, Set&lt;Integer&gt;)}
 * overload, which only knows about hardcoded armor research IDs (0, 9, 24, 33
 * for infantry; none for vehicles). Any modded research effect added via
 * {@code tech_tree.json} would be recorded in the {@code ResearchBonusTracker}
 * but silently ignored during damage calculation.
 * <p>
 * FIX: CombatSystem now uses the data-driven
 * {@code ArmorCalculator.calculateEffectiveArmor(Unit, ResearchBonusTracker)}
 * overload via the new {@code calculateEffectiveUnitArmor} helper. The tracker
 * accumulates ALL armor research effects from tech_tree.json, so modders who
 * add new armor research effects see them actually applied.
 * <p>
 * This test proves the tracker path is being used by applying the same research
 * TWICE — the hardcoded map would only return +2 (lookup), but the tracker
 * accumulates additively to +4. If damage reflects +4 armor (not +2), the
 * tracker path is being consulted.
 */
@DisplayName("B-6: CombatSystem uses ResearchBonusTracker for armor")
class CombatSystemArmorPathTest {

    /**
     * Proves the ResearchBonusTracker is consulted by applying research ID 0
     * twice. The hardcoded INFANTRY_ARMOR_RESEARCH map returns a fixed +2
     * regardless of how many times the research is "completed", but the tracker
     * accumulates to +4. If the damage reflects +4 armor, the tracker is used.
     */
    @Test
    @DisplayName("Tracker accumulates armor bonus additively (hardcoded map would not)")
    void trackerAccumulatesArmorBonus() {
        // Given: a ResearchSystem with research ID 0 applied TWICE for player 0
        EntityManager entityManager = new EntityManager();
        ResearchSystem researchSystem = new ResearchSystem();
        // applyResearchEffect reads from ResearchRegistry (tech_tree.json) and
        // calls tracker.addInfantryArmorBonus(2). Calling it twice accumulates to +4.
        researchSystem.applyResearchEffect(0, 0, entityManager);
        researchSystem.applyResearchEffect(0, 0, entityManager);

        // Verify tracker has accumulated +4 (not +2 from a lookup)
        int trackerBonus = researchSystem.getBonusTracker(0).getInfantryArmorBonus();
        assertEquals(4, trackerBonus,
            "Tracker should accumulate +2 + +2 = +4 (proves it's additive, not a lookup)");

        // Given: a CombatSystem wired with this research system
        GameState gameState = new GameState();
        CombatSystem combatSystem = new CombatSystem(gameState, entityManager);
        combatSystem.setResearchSystem(researchSystem);

        // Given: an attacker (RESISTANCE, player 1) with weaponDamage=20, BULLET
        UnitStats attackerStats = new UnitStats(UnitType.CONFED_INFANTRY, "Attacker", 40, 20, 5, 5,
            0, 4, 6, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit attacker = new Unit(1, Faction.RESISTANCE, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, attackerStats);

        // Given: a target (CONFEDERATION, player 0) infantry with base armor=5
        UnitStats targetStats = new UnitStats(UnitType.CONFED_INFANTRY, "Target", 40, 5, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit target = new Unit(2, Faction.CONFEDERATION, new GridPosition(11, 10),
            UnitType.CONFED_INFANTRY, targetStats);

        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        // When: performing an attack
        combatSystem.performAttack(attacker, target);

        // Then: damage should reflect +4 armor (base 5 + tracker 4 = 9 effective armor)
        // Damage formula: 20 * (10 - 9) / 10 = 2, min(2, 20-9)=2, max(2, 1)=2
        // So target HP should be 40 - 2 = 38
        // If the OLD hardcoded-map path were used, it would return +2 (lookup, not additive)
        // → effective armor 7 → damage 20*(10-7)/10 = 6 → HP 34
        // The fact that HP is 38 (not 34) proves the tracker path is being used.
        assertEquals(38, target.getHp(),
            "Damage should reflect tracker bonus (+4 armor → 2 damage → HP 38). "
            + "If HP is 34, the hardcoded map path was used (only +2 armor → 6 damage).");

        // Verify a DamageAppliedEvent was enqueued with the expected damage
        var events = gameState.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof DamageAppliedEvent),
            "DamageAppliedEvent should be enqueued");
    }

    /**
     * Control test: without any research, the base armor is used.
     * This confirms the test setup is correct and the previous test's difference
     * is due to the tracker, not some other factor.
     */
    @Test
    @DisplayName("Without research, base armor applies (control)")
    void noResearchUsesBaseArmor() {
        EntityManager entityManager = new EntityManager();
        ResearchSystem researchSystem = new ResearchSystem();  // No research applied

        GameState gameState = new GameState();
        CombatSystem combatSystem = new CombatSystem(gameState, entityManager);
        combatSystem.setResearchSystem(researchSystem);

        UnitStats attackerStats = new UnitStats(UnitType.CONFED_INFANTRY, "Attacker", 40, 20, 5, 5,
            0, 4, 6, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit attacker = new Unit(1, Faction.RESISTANCE, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, attackerStats);

        UnitStats targetStats = new UnitStats(UnitType.CONFED_INFANTRY, "Target", 40, 5, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit target = new Unit(2, Faction.CONFEDERATION, new GridPosition(11, 10),
            UnitType.CONFED_INFANTRY, targetStats);

        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        combatSystem.performAttack(attacker, target);

        // Base armor=5 → damage = 20*(10-5)/10 = 10, min(10, 20-5)=10 → damage=10
        // HP = 40 - 10 = 30
        assertEquals(30, target.getHp(),
            "Without research, base armor (5) should apply → damage 10 → HP 30");
    }

    /**
     * Verifies the fix also works when no ResearchSystem is wired (fallback path).
     * In this case, the legacy Set<Integer> overload is used. This test confirms
     * the fallback doesn't crash and still applies armor correctly.
     */
    @Test
    @DisplayName("Without ResearchSystem wired, legacy armor path still works (fallback)")
    void noResearchSystemUsesLegacyFallback() {
        EntityManager entityManager = new EntityManager();
        GameState gameState = new GameState();
        CombatSystem combatSystem = new CombatSystem(gameState, entityManager);
        // Note: setResearchSystem NOT called — researchSystem is null

        UnitStats attackerStats = new UnitStats(UnitType.CONFED_INFANTRY, "Attacker", 40, 20, 5, 5,
            0, 4, 6, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit attacker = new Unit(1, Faction.RESISTANCE, new GridPosition(10, 10),
            UnitType.CONFED_INFANTRY, attackerStats);

        UnitStats targetStats = new UnitStats(UnitType.CONFED_INFANTRY, "Target", 40, 5, 5, 5,
            0, 4, 4, WeaponType.BULLET, 5, 4, 10, 650, 6, 255, 0, -1);
        Unit target = new Unit(2, Faction.CONFEDERATION, new GridPosition(11, 10),
            UnitType.CONFED_INFANTRY, targetStats);

        entityManager.addUnit(attacker);
        entityManager.addUnit(target);

        combatSystem.performAttack(attacker, target);

        // No research system → legacy path → base armor (5) → damage 10 → HP 30
        assertEquals(30, target.getHp(),
            "Without ResearchSystem, legacy path should use base armor → damage 10 → HP 30");
    }
}
