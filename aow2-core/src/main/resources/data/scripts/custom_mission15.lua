-- Custom Mission 15: Endurance
-- Survive as long as possible against endless enemy waves.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local wave5Spawned = false
local wave6Spawned = false
local wave7Spawned = false
local wave8Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Endurance")
    aow2.showMessage("Survive as long as possible against endless enemy waves....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 400 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 12)
        aow2.showMessage("Endurance challenge begins. Survive as long as you can!")
    end

    if tick == 700 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 19, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 21, 14)
        aow2.showMessage("Wave 2 incoming!")
    end

    if tick == 1000 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 18, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 22, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 13)
        aow2.showMessage("Wave 3! The enemy grows stronger!")
    end

    if tick == 1300 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 16, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 23, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 14)
        aow2.showMessage("Wave 4! Heavy armor on the field!")
    end

    if tick == 1600 and not wave5Spawned then
        wave5Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 14, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 25, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 13, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 12, 10)
        aow2.showMessage("Wave 5! How long can you endure?")
    end

    if tick == 1900 and not wave6Spawned then
        wave6Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 11, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 27, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 10, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 28, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 12, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 14)
        aow2.showMessage("Wave 6! The onslaught continues!")
    end

    if tick == 2200 and not wave7Spawned then
        wave7Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 9, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 29, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 8, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 30, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 28, 13)
        aow2.showMessage("Wave 7! Can anything stop this?")
    end

    if tick == 2500 and not wave8Spawned then
        wave8Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 7, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 31, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 6, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_PORCUPINE", 32, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 8, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 30, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 5, 10)
        aow2.showMessage("Final wave! Survive this to prove your worth!")
    end

    if tick > 2800 and wave8Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 2000 and aow2.getUnitCount("CONFEDERATION") > 3 then
        aow2.showMessage("Impressive endurance! You've survived longer than expected!")
    end

end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 and wave8Spawned then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
end
