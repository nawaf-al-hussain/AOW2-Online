-- Custom Mission 11: Crossroads
-- Control the strategic crossroads against waves of attackers.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Crossroads")
    aow2.showMessage("Control the strategic crossroads against waves of attackers....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 600 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 5)
        aow2.showMessage("Enemy approaching the crossroads from two directions!")
    end

    if tick == 1000 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 16, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 21, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 11)
        aow2.showMessage("Enemy pushing harder on both roads!")
    end

    if tick == 1500 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 17, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 22, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 13, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 7)
        aow2.showMessage("Heavy units on the roads! Hold the crossroads!")
    end

    if tick > 1800 and wave3Spawned and not objectiveSet then
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
