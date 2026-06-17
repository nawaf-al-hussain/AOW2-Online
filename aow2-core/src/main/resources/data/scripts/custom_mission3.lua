-- Custom Mission 3: Bunker Bust
-- Destroy fortified enemy bunkers protecting a strategic position.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Bunker Bust")
    aow2.showMessage("Destroy fortified enemy bunkers protecting a strategic posit...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 400 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 8)
        aow2.showMessage("Enemy bunkers detected. Prepare siege equipment!")
    end

    if tick == 800 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 14, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 16, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 9)
        aow2.showMessage("Reinforcements arriving at the bunkers!")
    end

    if tick == 1400 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 13, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 12, 8)
        aow2.showMessage("Heavy armor moving to reinforce! Take out those bunkers!")
    end

    if tick > 1700 and wave3Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 1000 then
        aow2.showMessage("Focus fire on the bunkers! They must be destroyed!")
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
