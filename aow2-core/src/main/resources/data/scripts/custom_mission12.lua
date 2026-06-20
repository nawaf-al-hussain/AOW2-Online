-- Custom Mission 12: Relay Station
-- Capture and hold the communications relay station.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Relay Station")
    aow2.showMessage("Capture and hold the communications relay station....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 500 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 12, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 12, 12)
        aow2.showMessage("Enemy garrison at the relay station. Clear them out!")
    end

    if tick == 900 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 10, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 14, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 13)
        aow2.showMessage("Enemy attempting to retake the station!")
    end

    if tick == 1400 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 8, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 16, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 9, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 7, 11)
        aow2.showMessage("Major counter-attack! Hold the relay station!")
    end

    if tick > 1700 and wave3Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 1100 then
        aow2.showMessage("The relay station must remain operational! Protect it!")
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
