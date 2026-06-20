-- Episode 2, Mission 7: Liberation Day
-- The final battle for Peru. Drive the Confederation from our homeland.

local assault1 = false
local assault2 = false
local assault3 = false
local buildingsDown = 0
local targetBuildings = 8
local timeLimit = 9000

function onStart()
    aow2.showMessage("Comandante, the day of liberation is at hand!")
    aow2.showMessage("All Resistance cells converge on the capital. Victory or death!")
    aow2.onUnitKilled("onLiberationUnitKilled")
    aow2.onBuildingDestroyed("onLiberationBuildingDestroyed")
    aow2.setObjective("destroy_buildings", "active")
    aow2.setObjective("liberate_timer", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Assault wave 1: Outer city defenses
    if tick == 600 and not assault1 then
        assault1 = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 30, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 32, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 34, 6)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 31, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 29, 4)
        aow2.spawnUnit("CONFEDERATION", "CONFED_SNIPER", 33, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 35, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 27, 16)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 36, 7)
        aow2.showMessage("Assault Wave 1: Breach the outer city defenses!")
    end

    -- Assault wave 2: Inner garrison
    if tick == 2400 and not assault2 then
        assault2 = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 33, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 30, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 35, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 31, 15)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 34, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 29, 6)
        aow2.spawnUnit("CONFEDERATION", "CONFED_SNIPER", 36, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 32, 17)
        aow2.showMessage("Assault Wave 2: Inner garrison deploying heavy armor!")
    end

    -- Assault wave 3: Last stand
    if tick == 5400 and not assault3 then
        assault3 = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 30, 5)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 32, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 34, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 31, 15)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 29, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 33, 13)
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 35, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 27, 16)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 36, 18)
        aow2.showMessage("Assault Wave 3: The enemy fights with the desperation of the doomed!")
    end

    -- Time check
    if tick > timeLimit then
        aow2.setObjective("liberate_timer", "failed")
        aow2.showMessage("Reinforcements have arrived! We must retreat!")
    end

    -- Victory check
    if buildingsDown >= targetBuildings then
        aow2.setObjective("destroy_buildings", "complete")
        aow2.setObjective("liberate_timer", "complete")
        if aow2.getUnitCount("CONFEDERATION") == 0 then
            aow2.showMessage("LIBERATION DAY! Peru is free! Viva la Resistance!")
        end
    end
end

function onLiberationBuildingDestroyed()
    buildingsDown = buildingsDown + 1
    aow2.showMessage("Confederation building destroyed! (" .. buildingsDown .. "/" .. targetBuildings .. ")")
    if buildingsDown >= targetBuildings then
        aow2.showMessage("All enemy buildings destroyed! The capital is ours!")
    end
end

function onLiberationUnitKilled()
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 and remaining % 5 == 0 then
        aow2.showMessage("Enemy casualties mounting! " .. remaining .. " soldiers remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 32 then
        aow2.showMessage("The liberation assault has begun! For Peru!")
    elseif triggerId == 33 then
        aow2.showMessage("Confederation reserves are deploying!")
    elseif triggerId == 34 then
        aow2.showMessage("Their last line of defense! Push through!")
    elseif triggerId == 35 then
        aow2.showMessage("The enemy command has fallen! Press the advantage!")
    end
end
