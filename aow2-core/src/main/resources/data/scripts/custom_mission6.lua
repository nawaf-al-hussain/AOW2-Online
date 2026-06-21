-- Custom Mission 6: Scorched Earth
-- Advance through scorched territory, facing desperate enemy resistance.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Scorched Earth")
    aow2.showMessage("Advance through scorched territory, facing desperate enemy r...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 600 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 22, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 24, 8)
        aow2.showMessage("Remnants of the enemy garrison ahead.")
    end

    if tick == 1000 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 20, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 21, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 23, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 23, 10)
        aow2.showMessage("Enemy counter-attack from the flanks!")
    end

    if tick == 1600 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 18, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 19, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 22, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 3)
        aow2.showMessage("Desperate last stand! The enemy burns everything behind them!")
    end

    if tick > 1900 and wave3Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
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
