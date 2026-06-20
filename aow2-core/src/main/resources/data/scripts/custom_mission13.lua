-- Custom Mission 13: Pincer Movement
-- Execute a pincer movement to encircle and destroy enemy forces.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Pincer Movement")
    aow2.showMessage("Execute a pincer movement to encircle and destroy enemy forc...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 500 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 18, 12)
        aow2.showMessage("Enemy force in the center. Flank them from both sides!")
    end

    if tick == 800 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 22, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 24, 6)
        aow2.showMessage("Close the pincer! Don't let them escape!")
    end

    if tick == 1200 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 25, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 14, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 23, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 7)
        aow2.showMessage("Enemy trying to break out! Maintain the encirclement!")
    end

    if tick == 1700 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 26, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 13, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 12, 14)
        aow2.showMessage("Final enemy push! Complete the destruction!")
    end

    if tick > 2000 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 600 then
        aow2.showMessage("Coordinate your flanking forces. Close the trap!")
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
