-- Episode 1, Mission 7: Final Stand
-- The Resistance makes a desperate last stand. Crush them utterly.

local wave1 = false
local wave2 = false
local wave3 = false
local finalWave = false
local buildingsDestroyed = 0
local targetBuildings = 8
local unitsKilled = 0
local targetUnits = 30

function onStart()
    aow2.showMessage("The remnants of the Resistance have consolidated for a final stand.")
    aow2.showMessage("Show no mercy — end this war. The Confederation will prevail.")
    aow2.onUnitKilled("onFinalEnemyKilled")
    aow2.onBuildingDestroyed("onFinalBuildingDestroyed")
    aow2.setObjective("destroy_buildings", "active")
    aow2.setObjective("annihilate_forces", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Wave 1: Initial defenders
    if tick == 600 and not wave1 then
        wave1 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 30, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 29, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 27, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 31, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 25, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 24, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 14)
        aow2.showMessage("First defensive line encountered!")
    end

    -- Wave 2: Counterattack
    if tick == 2400 and not wave2 then
        wave2 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 29, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 27, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 30, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 26, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 31, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 16)
        aow2.showMessage("Enemy counterattack! Reinforce your front lines!")
    end

    -- Wave 3: Desperate defenders
    if tick == 4800 and not wave3 then
        wave3 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 30, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 27, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 29, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 24, 15)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 31, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 23, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 17)
        aow2.showMessage("Desperate defenders emerge! Push them back!")
    end

    -- Final wave
    if tick == 7200 and not finalWave then
        finalWave = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 28, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 26, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 30, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 27, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 29, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 31, 16)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 24, 18)
        aow2.showMessage("FINAL WAVE! The Resistance gives everything they have!")
    end

    -- Victory check
    if buildingsDestroyed >= targetBuildings and unitsKilled >= targetUnits then
        aow2.setObjective("destroy_buildings", "complete")
        aow2.setObjective("annihilate_forces", "complete")
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.showMessage("The Resistance is no more! The Confederation is VICTORIOUS!")
        end
    end
end

function onFinalBuildingDestroyed()
    buildingsDestroyed = buildingsDestroyed + 1
    aow2.showMessage("Enemy building destroyed! (" .. buildingsDestroyed .. "/" .. targetBuildings .. ")")
end

function onFinalEnemyKilled()
    unitsKilled = unitsKilled + 1
    if unitsKilled % 10 == 0 then
        aow2.showMessage("Enemy casualties: " .. unitsKilled .. "/" .. targetUnits)
    end
end

function onTrigger(triggerId)
    if triggerId == 14 then
        aow2.showMessage("Enemy positions identified. Begin the assault!")
    elseif triggerId == 15 then
        aow2.showMessage("Enemy reserves mobilizing!")
    elseif triggerId == 16 then
        aow2.showMessage("They're making their last stand!")
    elseif triggerId == 17 then
        aow2.showMessage("The enemy command structure has collapsed!")
    end
end
