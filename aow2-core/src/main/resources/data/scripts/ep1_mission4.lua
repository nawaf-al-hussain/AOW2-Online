-- Episode 1, Mission 4: Heavy Metal
-- Deploy vehicle forces and crush the Resistance armor division

local armorWave1 = false
local armorWave2 = false
local factoryDestroyed = false

function onStart()
    aow2.showMessage("Intelligence reports a Resistance vehicle depot to the east.")
    aow2.showMessage("Build a Machine Factory and deploy Hammer tanks!")
    aow2.onUnitKilled("onVehicleDestroyed")
    aow2.onBuildingDestroyed("onFactoryDestroyed")
    aow2.setObjective("destroy_factory", "active")
    aow2.setObjective("destroy_vehicles", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Enemy armor patrol
    if tick == 600 and not armorWave1 then
        armorWave1 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 22, 6)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 24, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 20, 4)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 23, 7)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 21, 10)
        aow2.showMessage("Enemy armor patrol detected! Use anti-vehicle tactics.")
    end

    -- Heavy armor reinforcement
    if tick == 3600 and not armorWave2 then
        armorWave2 = true
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 24, 5)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 23, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 25, 12)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 22, 3)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 20, 14)
        aow2.showMessage("Enemy heavy armor reserves deploying!")
    end

    -- Vehicle destruction objective
    if factoryDestroyed and aow2.getUnitCount("RESISTANCE") == 0 then
        aow2.setObjective("destroy_vehicles", "complete")
        aow2.showMessage("All enemy vehicles destroyed!")
    end
end

function onFactoryDestroyed()
    factoryDestroyed = true
    aow2.setObjective("destroy_factory", "complete")
    aow2.showMessage("Enemy Factory destroyed! No more vehicle production!")
end

function onVehicleDestroyed()
    local remaining = aow2.getUnitCount("RESISTANCE")
    aow2.showMessage("Enemy vehicle eliminated. " .. remaining .. " hostiles remaining.")
end

function onTrigger(triggerId)
    if triggerId == 6 then
        aow2.showMessage("Enemy Factory has been located!")
    elseif triggerId == 7 then
        aow2.showMessage("Enemy armor reinforcements incoming!")
    end
end
