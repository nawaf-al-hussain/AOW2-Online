-- Custom Mission 4: Blitzkrieg
-- Execute a lightning-fast assault before the enemy can reinforce.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Blitzkrieg")
    aow2.showMessage("Execute a lightning-fast assault before the enemy can reinfo...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 300 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 11, 7)
        aow2.showMessage("Blitz assault begins! Strike fast and hard!")
    end

    if tick == 600 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 12, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 13, 6)
        aow2.showMessage("Enemy scrambling to respond! Keep up the pressure!")
    end

    if tick == 1000 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 14, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 15, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 11, 10)
        aow2.showMessage("Enemy heavy units deploying! Time is running out!")
    end

    if tick > 1300 and wave3Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 500 then
        aow2.showMessage("Speed is key! Finish them before reinforcements arrive!")
    end

end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave3Spawned then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
end
