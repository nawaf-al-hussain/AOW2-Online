-- Episode 1, Mission 3: Infantry Advance
-- Push forward with upgraded infantry and capture the ridge

local ridgeDefenders = false
local reinforcementWave = false
local ridgeCaptured = false
-- enemiesOnRidge tracking handled by getUnitCount() in victory check

function onStart()
    aow2.showMessage("The Resistance holds a strategic ridge overlooking the valley.")
    aow2.showMessage("Research infantry upgrades and push forward!")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.onAreaEntered(12, 8, 5, "onRidgeApproach")
    aow2.setObjective("capture_ridge", "active")
    aow2.setObjective("eliminate_defenders", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Spawn ridge defenders
    if tick == 300 and not ridgeDefenders then
        ridgeDefenders = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 10, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 12, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 11, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 13, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 15, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 16, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 9, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 17, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 12, 10)
        aow2.showMessage("Ridge defenders spotted! They're well-entrenched.")
    end

    -- Enemy reinforcements when most defenders are killed
    if ridgeDefenders and not reinforcementWave then
        local enemyCount = aow2.getUnitCount("RESISTANCE")
        if enemyCount <= 4 then
            reinforcementWave = true
            aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 23, 5)
            aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 22, 8)
            aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 23, 10)
            aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 21, 3)
            aow2.showMessage("Enemy reinforcements arriving from the east!")
        end
    end

    -- Victory check
    if ridgeCaptured and aow2.getUnitCount("RESISTANCE") == 0 then
        aow2.setObjective("capture_ridge", "complete")
        aow2.setObjective("eliminate_defenders", "complete")
        aow2.showMessage("Ridge captured and secured! Outstanding, Commander.")
    end
end

function onRidgeApproach()
    if not ridgeCaptured then
        aow2.showMessage("Our forces are approaching the ridge!")
    end
end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 5 and remaining > 0 then
        aow2.showMessage("Almost there! " .. remaining .. " defenders remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 4 then
        aow2.showMessage("Ridge perimeter breach detected!")
    elseif triggerId == 5 then
        aow2.showMessage("Enemy infantry reserves are low.")
    end
end
