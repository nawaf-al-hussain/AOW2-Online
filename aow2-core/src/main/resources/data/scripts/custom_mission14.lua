-- Custom Mission 14: Total War
-- All-out war with massive forces on both sides.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local wave5Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Total War")
    aow2.showMessage("All-out war with massive forces on both sides....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 400 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 15)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 26, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 26, 12)
        aow2.showMessage("Enemy army advancing! All units to battle stations!")
    end

    if tick == 800 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 23, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 23, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 23, 16)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 27, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 22, 9)
        aow2.showMessage("Enemy armor joining the assault!")
    end

    if tick == 1200 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 21, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 21, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 24, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 20, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 20, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 22, 16)
        aow2.showMessage("Heavy armor pushing the center! Reinforce now!")
    end

    if tick == 1700 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 19, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 19, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 20, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 18, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 18, 15)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 12)
        aow2.showMessage("Enemy committing everything! This is total war!")
    end

    if tick == 2200 and not wave5Spawned then
        wave5Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 16, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 16, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 15, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 13)
        aow2.showMessage("Final wave! Destroy them all!")
    end

    if tick > 2500 and wave5Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 1500 then
        aow2.showMessage("No retreat! No surrender! Fight to the last!")
    end

end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave5Spawned then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
end
