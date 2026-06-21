-- Custom Mission 7: Jungle Warfare
-- Navigate dense jungle terrain while fighting guerrilla forces.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local wave4Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Jungle Warfare")
    aow2.showMessage("Navigate dense jungle terrain while fighting guerrilla force...")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 400 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 28, 15)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 18)
        aow2.showMessage("Sniper fire from the treeline! Take cover!")
    end

    if tick == 700 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 27, 16)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 29, 20)
        aow2.showMessage("Guerrillas emerging from multiple directions!")
    end

    if tick == 1100 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 25, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 27, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 27, 19)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 28, 17)
        aow2.showMessage("Enemy vehicles pushing through the jungle!")
    end

    if tick == 1600 and not wave4Spawned then
        wave4Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_ARMADILLO", 24, 16)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 28, 13)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 28, 21)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 28, 10)
        aow2.showMessage("Main guerrilla force engaged! Watch for ambushes!")
    end

    if tick > 1900 and wave4Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 300 then
        aow2.showMessage("Visibility is limited in the jungle. Watch for ambushes!")
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
