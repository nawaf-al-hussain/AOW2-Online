-- Custom Mission 8: Two Fronts
-- Fight a war on two fronts simultaneously - north and south.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Two Fronts")
    aow2.showMessage("Fight a war on two fronts simultaneously - north and south....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 500 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 18)
        aow2.showMessage("Enemy attacks from both the north and south!")
    end

    if tick == 800 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 12, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 12, 17)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 8, 1)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 8, 19)
        aow2.showMessage("Both fronts intensifying! Split your forces!")
    end

    if tick == 1200 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 14, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 14, 18)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 11, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 11, 15)
        aow2.showMessage("Heavy armor on both fronts! Prioritize your defenses!")
    end

    if tick == 1700 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 15, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 15, 17)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 13, 1)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 13, 19)
        aow2.showMessage("Final assault on both fronts! Victory or defeat!")
    end

    if tick > 2000 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 600 then
        aow2.showMessage("Divide your forces wisely. Both fronts must hold!")
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
