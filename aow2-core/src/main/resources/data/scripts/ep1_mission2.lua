-- Episode 1, Mission 2: Power Grid
-- Establish power infrastructure and defend against a counterattack

local counterAttack1 = false
local counterAttack2 = false
local defendTimer = 0
local defendObjective = "active"

function onStart()
    aow2.showMessage("Our foothold is tenuous. We need Generators to power our buildings.")
    aow2.showMessage("The Resistance will not sit idle — fortify your position!")
    aow2.onUnitKilled("onEnemyKilled")
    aow2.onBuildingDestroyed("onBuildingLost")
    aow2.setObjective("defend_command", "active")
    aow2.setObjective("destroy_attackers", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- First counterattack wave
    if tick == 1200 and not counterAttack1 then
        counterAttack1 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 21, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 19, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 21, 3)
        aow2.showMessage("Counterattack! Enemy forces are approaching from the east!")
    end

    -- Second counterattack wave
    if tick == 3000 and not counterAttack2 then
        counterAttack2 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 20, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 20, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 21, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 19, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 18, 2)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 21, 11)
        aow2.showMessage("Second wave incoming! Hold the line!")
    end

    -- Track defend timer
    if counterAttack1 then
        defendTimer = defendTimer + 1
        if defendTimer >= 6000 then
            defendObjective = "complete"
            aow2.setObjective("defend_command", "complete")
            aow2.showMessage("Command Centre has survived the assault!")
        end
    end

    -- Check destroy objective
    if counterAttack2 and aow2.getUnitCount("RESISTANCE") == 0 then
        aow2.setObjective("destroy_attackers", "complete")
        aow2.showMessage("All enemy attackers eliminated!")
    end
end

function onEnemyKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining > 0 then
        aow2.showMessage("Enemy unit destroyed. " .. remaining .. " remaining.")
    end
end

function onBuildingLost()
    aow2.showMessage("We've lost a building! Protect the Command Centre!")
end

function onTrigger(triggerId)
    if triggerId == 2 then
        aow2.showMessage("Intelligence reports: enemy mobilizing nearby.")
    elseif triggerId == 3 then
        aow2.showMessage("Final enemy assault imminent!")
    end
end
