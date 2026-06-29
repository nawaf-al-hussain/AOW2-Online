package com.aow2.core.command;

import com.aow2.common.config.StatsRegistry;
import com.aow2.common.model.BuildingStats;
import com.aow2.common.model.BuildingType;
import com.aow2.common.model.CommandType;
import com.aow2.common.model.Faction;
import com.aow2.common.model.GridPosition;
import com.aow2.common.model.WeaponType;
import com.aow2.core.economy.EconomySystem;
import com.aow2.core.economy.PowerSystem;
import com.aow2.core.economy.ResourceGenerator;
import com.aow2.core.entity.Building;
import com.aow2.core.world.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the building upgrade payment flow.
 * Verifies that UpgradeCommandHandler correctly validates, deducts credits,
 * and applies upgrade effects (HP bonus, level increment, power grid update).
 * <p>
 * REF: complete_building_stats.json — upgradeCosts per building type
 * REF: GameConstants.BUILDING_POWER_RADIUS — power radius per Generator level
 */
class BuildingUpgradeTest {

    private EntityManager entities;
    private EconomySystem economy;
    private PowerSystem powerSystem;
    private UpgradeCommandHandler handler;

    @BeforeEach
    void setUp() {
        entities = new EntityManager();
        economy = new EconomySystem(new ResourceGenerator());
        powerSystem = new PowerSystem();
        handler = new UpgradeCommandHandler();
    }

    private Building createCompletedGenerator(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingStats stats = StatsRegistry.getInstance().getBuildingStats(BuildingType.CONFED_GENERATOR);
        Building gen = new Building(entities.allocateEntityId(), faction,
            new GridPosition(20, 20), BuildingType.CONFED_GENERATOR, stats);
        gen.setConstructionProgress(stats.buildTime());
        gen.setPowered(true);
        entities.addBuilding(gen);
        return gen;
    }

    private Building createCompletedCC(int playerId) {
        Faction faction = EconomySystem.playerFaction(playerId);
        BuildingStats stats = StatsRegistry.getInstance().getBuildingStats(BuildingType.CONFED_COMMAND_CENTRE);
        Building cc = new Building(entities.allocateEntityId(), faction,
            new GridPosition(10, 10), BuildingType.CONFED_COMMAND_CENTRE, stats);
        cc.setConstructionProgress(stats.buildTime());
        cc.setPowered(true);
        entities.addBuilding(cc);
        return cc;
    }

    @Nested
    @DisplayName("Upgrade Validation")
    class Validation {

        @Test
        @DisplayName("Upgrade succeeds when player can afford it")
        void upgradeSucceedsWhenAffordable() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 10000);

            int cost = gen.getStats().upgradeCosts().get(0);
            int creditsBefore = economy.getCredits(0);

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(1, gen.getUpgradeLevel());
            assertEquals(creditsBefore - cost, economy.getCredits(0));
            assertTrue(gen.getUpgradeMaxHpBonus() > 0, "HP bonus should be applied");
        }

        @Test
        @DisplayName("Upgrade fails when player cannot afford it")
        void upgradeFailsWhenNotAffordable() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 10); // Not enough

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(0, gen.getUpgradeLevel(), "Upgrade level should not change");
            assertEquals(10, economy.getCredits(0), "Credits should not be deducted");
        }

        @Test
        @DisplayName("Upgrade fails when building does not exist")
        void upgradeFailsForNonexistentBuilding() {
            economy.setCredits(0, 10000);
            handler.handle(new CommandType.Upgrade(0, 0, 999), entities, economy, powerSystem);
            assertEquals(10000, economy.getCredits(0), "No credits should be deducted");
        }

        @Test
        @DisplayName("Upgrade fails when building is under construction")
        void upgradeFailsWhenUnderConstruction() {
            Building gen = createCompletedGenerator(0);
            gen.setConstructionProgress(0); // Reset to under construction
            economy.setCredits(0, 10000);

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(0, gen.getUpgradeLevel(), "Should not upgrade under-construction building");
        }

        @Test
        @DisplayName("Upgrade fails when player does not own the building")
        void upgradeFailsForWrongOwner() {
            Building gen = createCompletedGenerator(0); // Owned by player 0
            economy.setCredits(1, 10000);

            handler.handle(new CommandType.Upgrade(0, 1, gen.getId()), entities, economy, powerSystem);

            assertEquals(0, gen.getUpgradeLevel(), "Player 1 should not upgrade player 0's building");
        }

        @Test
        @DisplayName("Upgrade fails at max level (3)")
        void upgradeFailsAtMaxLevel() {
            Building gen = createCompletedGenerator(0);
            gen.setUpgradeLevel(3);
            economy.setCredits(0, 10000);

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(3, gen.getUpgradeLevel(), "Should not exceed max level");
        }
    }

    @Nested
    @DisplayName("Upgrade Effects")
    class Effects {

        @Test
        @DisplayName("Upgrade increments level and applies HP bonus")
        void upgradeIncrementsLevelAndHp() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 10000);
            int baseHp = gen.getMaxHp();

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(1, gen.getUpgradeLevel());
            int expectedBonus = (int) (baseHp * 0.20);
            assertEquals(expectedBonus, gen.getUpgradeMaxHpBonus());
            assertEquals(baseHp + expectedBonus, gen.getEffectiveMaxHp());
        }

        @Test
        @DisplayName("Multiple upgrades stack HP bonus")
        void multipleUpgradesStackHp() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 100000);
            int baseHp = gen.getMaxHp();

            // Upgrade to level 1
            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);
            assertEquals(1, gen.getUpgradeLevel());

            // Upgrade to level 2
            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);
            assertEquals(2, gen.getUpgradeLevel());

            // Upgrade to level 3
            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);
            assertEquals(3, gen.getUpgradeLevel());

            int expectedBonus = (int) (baseHp * 0.20) * 3;
            assertEquals(expectedBonus, gen.getUpgradeMaxHpBonus());
        }

        @Test
        @DisplayName("Generator upgrade updates power grid radius")
        void generatorUpgradeUpdatesPowerGrid() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 10000);

            // Before upgrade: level 0, radius 10
            assertEquals(0, gen.getUpgradeLevel());
            assertEquals(10, powerSystem.getPowerRadius(gen));

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            // After upgrade: level 1, radius 20
            assertEquals(1, gen.getUpgradeLevel());
            assertEquals(20, powerSystem.getPowerRadius(gen));
        }

        @Test
        @DisplayName("Upgrade deducts correct cost from player credits")
        void upgradeDeductsCorrectCost() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 5000);

            int expectedCost = gen.getStats().upgradeCosts().get(0);
            int creditsBefore = economy.getCredits(0);

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            assertEquals(creditsBefore - expectedCost, economy.getCredits(0));
        }

        @Test
        @DisplayName("Upgrade heals the HP bonus immediately")
        void upgradeHealsHpBonus() {
            Building gen = createCompletedGenerator(0);
            economy.setCredits(0, 10000);

            // Damage the building
            gen.takeDamage(50);
            int hpBeforeUpgrade = gen.getHp();

            handler.handle(new CommandType.Upgrade(0, 0, gen.getId()), entities, economy, powerSystem);

            int hpBonus = (int) (gen.getMaxHp() * 0.20);
            assertEquals(hpBeforeUpgrade + hpBonus, gen.getHp(),
                "Building should be healed by the HP bonus amount");
        }
    }

    @Nested
    @DisplayName("Command Serialization")
    class Serialization {

        @Test
        @DisplayName("Upgrade command serializes and deserializes correctly")
        void upgradeCommandRoundTrips() {
            CommandType.Upgrade original = new CommandType.Upgrade(42, 0, 123);

            byte[] serialized = com.aow2.core.network.CommandSerializer.serialize(original);
            CommandType.Upgrade deserialized = (CommandType.Upgrade) com.aow2.core.network.CommandSerializer.deserialize(serialized);

            assertEquals(original.tick(), deserialized.tick());
            assertEquals(original.playerId(), deserialized.playerId());
            assertEquals(original.buildingId(), deserialized.buildingId());
        }
    }

    @Nested
    @DisplayName("Replay Recording")
    class ReplayRecording {

        @Test
        @DisplayName("Upgrade command is recorded in replay")
        void upgradeCommandRecorded() {
            var recorder = new com.aow2.core.replay.ReplayRecorder();
            recorder.startRecording("test_map", new Faction[]{Faction.CONFEDERATION, Faction.RESISTANCE});

            recorder.recordCommand(new CommandType.Upgrade(0, 0, 1));
            recorder.recordCommand(new CommandType.Upgrade(5, 1, 2));

            var replay = recorder.stopRecording();
            assertEquals(2, replay.commandCount(), "Replay should contain 2 upgrade commands");
        }
    }
}
