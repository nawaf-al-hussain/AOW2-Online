-- Episode 2, Mission 1: Resistance Rising
-- Establish a hidden base and begin the fight for liberation

local patrolSpawned = false
local ambushReady = false

function onStart()
    aow2.showMessage("Comandante, the time has come. The Confederation occupies our homeland.")
    aow2.showMessage("Build a Headquarters in the jungle and train our first fighters.")
    aow2.onUnitKilled("onPatrolKilled")
    aow2.setObjective("destroy_patrol", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Confederation patrol
    if tick == 300 and not patrolSpawned then
        patrolSpawned = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 20, 5)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 22, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 21, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 19, 3)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 23, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 18, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 20, 15)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 22, 16)
        aow2.showMessage("Confederation patrol spotted! Set up the ambush!")
    end

    -- Reinforcement warning
    if tick == 900 and not ambushReady then
        ambushReady = true
        aow2.showMessage("Strike now before they call for reinforcements!")
    end

    -- Victory check
    if patrolSpawned and aow2.getUnitCount("CONFEDERATION") == 0 then
        aow2.setObjective("destroy_patrol", "complete")
        aow2.showMessage("Patrol eliminated! The liberation of Peru has begun!")
    end
end

function onPatrolKilled()
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 then
        aow2.showMessage("Enemy down! " .. remaining .. " Confederation soldiers remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 20 then
        aow2.showMessage("A patrol is approaching our position!")
    end
end
