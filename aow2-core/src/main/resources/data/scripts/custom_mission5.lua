-- Custom Mission 5: Last Bastion
-- Defend your last stronghold against overwhelming enemy forces.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Last Bastion")
    aow2.showMessage("Defend your last stronghold against overwhelming enemy force...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 500 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 5, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 5, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 6, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 4, 10)
        aow2.showMessage("Enemy assault beginning from the west!")
    end

    if tick == 900 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 3, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 4, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 6, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 5, 9)
        aow2.showMessage("Enemy armor pushing through! Reinforce the perimeter!")
    end

    if tick == 1400 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 2, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 3, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 7, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 8, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 4, 12)
        aow2.showMessage("Full enemy assault! This is our last stand!")
    end

    if tick == 1900 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 1, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 2, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 5, 10)
        aow2.showMessage("Enemy heavy artillery approaching! Hold at all costs!")
    end

    if tick > 2200 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 1500 and aow2.getUnitCount("CONFEDERATION") > 5 then
        aow2.showMessage("Our defenses hold! The enemy falters!")
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
