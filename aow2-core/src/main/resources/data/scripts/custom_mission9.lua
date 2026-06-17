-- Custom Mission 9: Sabotage
-- Infiltrate enemy territory and destroy key installations.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Sabotage")
    aow2.showMessage("Infiltrate enemy territory and destroy key installations....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 400 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 22, 10)
        aow2.showMessage("Enemy patrol spotted. Stay undetected as long as possible.")
    end

    if tick == 800 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 18, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 24, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 21, 9)
        aow2.showMessage("Enemy alerted! Move fast to destroy their installations!")
    end

    if tick == 1200 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 16, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 25, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 19, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 23, 13)
        aow2.showMessage("Enemy reinforcement en route! Destroy the targets now!")
    end

    if tick == 1600 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 15, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 26, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 17, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 27, 12)
        aow2.showMessage("Heavy units arriving! Extract if possible!")
    end

    if tick > 1900 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 1000 then
        aow2.showMessage("Destroy the enemy Technology Centre and Generator!")
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
