-- Episode 2, Mission 5: Coastal Assault
-- Launch an amphibious assault on the Confederation port

local portDefenders = false
local navalReinforcements = false
local commandDestroyed = false

function onStart()
    aow2.showMessage("The Confederation uses the coastal port to bring in reinforcements.")
    aow2.showMessage("Armadillo transports will carry our forces ashore!")
    aow2.onUnitKilled("onPortDefenderKilled")
    aow2.onBuildingDestroyed("onPortBuildingDestroyed")
    aow2.setObjective("destroy_command", "active")
    aow2.setObjective("secure_docks", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Port garrison
    if tick == 300 and not portDefenders then
        portDefenders = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 26, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 25, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 27, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 29, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 30, 13)
        aow2.spawnUnit("CONFEDERATION", "CONFED_SNIPER", 26, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 24, 16)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 15)
        aow2.showMessage("Port defenses spotted! Land on the beach and push inland!")
    end

    -- Naval reinforcements
    if tick == 4800 and not navalReinforcements then
        navalReinforcements = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 32, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 33, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 31, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 30, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 34, 11)
        aow2.showMessage("Confederation naval reinforcements arriving! Secure the docks NOW!")
    end

    -- Victory check
    if commandDestroyed then
        aow2.setObjective("destroy_command", "complete")
        if aow2.getUnitCount("CONFEDERATION") == 0 then
            aow2.setObjective("secure_docks", "complete")
            aow2.showMessage("Port captured! No more supplies for the Confederation!")
        end
    end
end

function onPortBuildingDestroyed()
    commandDestroyed = true
    aow2.showMessage("Command Centre destroyed! The port is leaderless!")
end

function onPortDefenderKilled()
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 and remaining <= 4 then
        aow2.showMessage("Port nearly cleared! " .. remaining .. " defenders remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 27 then
        aow2.showMessage("Our forces have landed on the docks!")
    elseif triggerId == 28 then
        aow2.showMessage("Port Command Centre located!")
    elseif triggerId == 29 then
        aow2.showMessage("Confederation naval reinforcements detected offshore!")
    end
end
