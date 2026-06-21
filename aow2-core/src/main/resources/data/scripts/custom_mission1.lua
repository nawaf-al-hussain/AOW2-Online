-- Custom Mission 1: Island Skirmish
-- Secure a small island outpost against light Resistance forces.

local wave1Spawned = false
local wave2Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Island Skirmish")
    aow2.showMessage("Secure a small island outpost against light Resistance force...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 600 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 7)
        aow2.showMessage("Enemy landing craft spotted on the eastern shore!")
    end

    if tick == 1200 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 17, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 4)
        aow2.showMessage("Second wave approaching! Hold the line!")
    end

    if tick > 1500 and wave2Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave2Spawned then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
end
