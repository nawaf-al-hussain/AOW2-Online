-- Custom Mission 10: Iron Gauntlet
-- Run the gauntlet of enemy fortifications with limited resources.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Iron Gauntlet")
    aow2.showMessage("Run the gauntlet of enemy fortifications with limited resour...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 300 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 17, 7)
        aow2.showMessage("First defensive line! Push through!")
    end

    if tick == 600 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 14, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 15, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 16, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 13, 2)
        aow2.showMessage("Second defensive line! Don't stop!")
    end

    if tick == 1000 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 12, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 13, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 14, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 11, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 10, 6)
        aow2.showMessage("Third line - heavy armor! This is the iron gauntlet!")
    end

    if tick == 1500 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 10, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 11, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 12, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 9, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 8, 8)
        aow2.showMessage("Final line! Break through or die trying!")
    end

    if tick > 1800 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 700 then
        aow2.showMessage("Resources are limited. Make every unit count!")
    end

end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave4Spawned then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
end
