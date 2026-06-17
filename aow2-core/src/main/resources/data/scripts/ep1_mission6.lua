-- Episode 1, Mission 6: Operation Thunderstrike
-- Launch a combined arms assault on the Resistance headquarters

local phase1 = false
local phase2 = false
local phase3 = false
local hqDestroyed = false
local timeLimit = 7200
local timeWarning = false

function onStart()
    aow2.showMessage("We have located the Resistance headquarters.")
    aow2.showMessage("Use all available forces — there will be no second chance!")
    aow2.onUnitKilled("onHQUnitKilled")
    aow2.onBuildingDestroyed("onHQBuildingDestroyed")
    aow2.setObjective("destroy_hq", "active")
    aow2.setObjective("time_limit", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Phase 1: Outer defense
    if tick == 600 and not phase1 then
        phase1 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 22, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 27, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 8)
        aow2.showMessage("Phase 1: Breach the outer defenses!")
    end

    -- Phase 2: Inner defense
    if tick == 1800 and not phase2 then
        phase2 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 25, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 23, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 27, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 22, 6)
        aow2.showMessage("Phase 2: Enemy armor deployed! Anti-vehicle support needed!")
    end

    -- Phase 3: Last stand defenders
    if tick == 5400 and not phase3 then
        phase3 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 25, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 26, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 24, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 27, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 23, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 28, 8)
        aow2.showMessage("Phase 3: Enemy last stand! Push through!")
    end

    -- Time warning
    if tick > timeLimit - 1800 and not timeWarning then
        timeWarning = true
        aow2.showMessage("WARNING: 60 seconds remaining! Destroy the HQ!")
    end

    -- Time expired check
    if tick > timeLimit and not hqDestroyed then
        aow2.setObjective("time_limit", "failed")
        aow2.showMessage("Time has run out! The operation has failed.")
    end

    -- Victory check
    if hqDestroyed then
        aow2.setObjective("destroy_hq", "complete")
        aow2.setObjective("time_limit", "complete")
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.showMessage("Resistance HQ destroyed! Operation Thunderstrike: SUCCESS!")
        end
    end
end

function onHQBuildingDestroyed()
    hqDestroyed = true
    aow2.showMessage("Resistance HQ destroyed! The heart of the enemy is broken!")
end

function onHQUnitKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining > 0 and remaining <= 5 then
        aow2.showMessage("Almost there! " .. remaining .. " enemies remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 11 then
        aow2.showMessage("HQ outer wall breached!")
    elseif triggerId == 12 then
        aow2.showMessage("Enemy reserves are deploying!")
    elseif triggerId == 13 then
        aow2.showMessage("This is our last chance! Destroy the HQ now!")
    end
end
