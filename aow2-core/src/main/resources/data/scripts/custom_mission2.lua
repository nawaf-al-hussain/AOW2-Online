-- Custom Mission 2: Valley of Death
-- Navigate a treacherous valley while under constant ambush.

local wave1Spawned = false
local wave2Spawned = false
local wave3Spawned = false
local objectiveSet = false

function onStart()
    aow2.showMessage("Mission: Valley of Death")
    aow2.showMessage("Navigate a treacherous valley while under constant ambush....")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.setObjective("main_objective", "active")
end

function onTick()
    local tick = aow2.getTick()

    if tick == 500 and not wave1Spawned then
        wave1Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 25, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 23, 12)
        aow2.showMessage("Snipers on the ridge! Watch your flanks!")
    end

    if tick == 900 and not wave2Spawned then
        wave2Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 22, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 14)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 6)
        aow2.showMessage("Enemy vehicles moving through the valley!")
    end

    if tick == 1500 and not wave3Spawned then
        wave3Spawned = true
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 20, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 21, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 27, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 25, 15)
        aow2.showMessage("Final ambush! Destroy all enemy forces!")
    end

    if tick > 1800 and wave3Spawned and not objectiveSet then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("main_objective", "complete")
            aow2.showMessage("Mission accomplished! All enemies eliminated!")
            objectiveSet = true
        end
    end

    if tick == 800 then
        aow2.showMessage("The valley narrows ahead. Prepare for close combat!")
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
