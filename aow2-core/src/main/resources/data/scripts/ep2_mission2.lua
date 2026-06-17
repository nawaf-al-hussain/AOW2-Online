-- Episode 2, Mission 2: Guerrilla Tactics
-- Use stealth and speed to ambush a Confederation supply convoy

local convoyAppeared = false
local convoyDestroyed = 0
local targetVehicles = 5
local timeLimit = 3600
local escapeWarning = false

function onStart()
    aow2.showMessage("A Confederation supply convoy is moving through the mountain pass.")
    aow2.showMessage("Set up an ambush — strike fast and fade before reinforcements arrive!")
    aow2.onUnitKilled("onConvoyUnitKilled")
    aow2.setObjective("destroy_vehicles", "active")
    aow2.setObjective("escape_timer", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Convoy appears
    if tick == 300 and not convoyAppeared then
        convoyAppeared = true
        -- Supply vehicles (using Hammer as proxy for supply trucks)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 3, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 5, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 7, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 9, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 11, 11)
        -- Escort
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 4, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 6, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 8, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 10, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 12, 10)
        aow2.showMessage("Supply convoy entering the pass! Ambush positions!")
    end

    -- Time warning
    if tick > timeLimit - 900 and not escapeWarning then
        escapeWarning = true
        aow2.showMessage("WARNING: Confederation reinforcements approaching! Escape soon!")
    end

    -- Time expired
    if tick > timeLimit then
        aow2.setObjective("escape_timer", "failed")
        aow2.showMessage("Reinforcements have arrived! The ambush has failed!")
    end

    -- Victory check
    if convoyDestroyed >= targetVehicles then
        aow2.setObjective("destroy_vehicles", "complete")
        aow2.setObjective("escape_timer", "complete")
        aow2.showMessage("Convoy destroyed! Fade into the mountains, comrades!")
    end
end

function onConvoyUnitKilled()
    convoyDestroyed = convoyDestroyed + 1
    aow2.showMessage("Supply vehicle destroyed! (" .. convoyDestroyed .. "/" .. targetVehicles .. ")")
    if convoyDestroyed >= targetVehicles - 1 then
        aow2.showMessage("One more vehicle to go!")
    end
end

function onTrigger(triggerId)
    if triggerId == 21 then
        aow2.showMessage("Enemy armor reinforcements detected!")
    elseif triggerId == 22 then
        aow2.showMessage("Confederation Hammer tanks approaching! Fall back!")
    end
end
