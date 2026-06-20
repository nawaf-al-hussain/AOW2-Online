-- Episode 1, Mission 1: First Contact
-- Tutorial mission: learn basic controls, repel initial Resistance incursion

local wave1Spawned = false
local wave2Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Commander, Resistance forces have been detected in the northern sector.")
    aow2.showMessage("Build a Command Centre and train infantry to secure the area.")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("repel_attack", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Wave 1: at tick 600, spawn enemy scouts
    if tick == 600 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 18, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 19, 4)
        aow2.showMessage("Enemy scouts approaching from the east!")
    end

    -- Wave 2: at tick 1200, spawn main attack
    if tick == 1200 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 18, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 19, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 9)
        aow2.showMessage("Enemy main force incoming! Defend your position!")
    end

    -- Victory check: all enemy units destroyed after wave 2
    if tick > 1500 and wave2Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("repel_attack", "complete")
            aow2.showMessage("Area secured! Well done, Commander.")
            objectiveSet = true
        end
    end
end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave2Spawned then
        aow2.showMessage("Enemy forces nearly eliminated! " .. remaining .. " units remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 1 then
        aow2.showMessage("Reinforcement timer triggered. Prepare for contact.")
    end
end
