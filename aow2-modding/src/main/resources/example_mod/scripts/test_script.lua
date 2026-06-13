-- Example Lua mod script for Art of War 2: Online
-- Demonstrates the available game API functions

-- Log a message when the script loads
aow2.showMessage("Example Mod loaded!")

-- Set up some campaign objectives
aow2.setObjective("destroy_enemy", "active")
aow2.setObjective("build_base", "active")

-- Register event hooks
aow2.onUnitKilled("onUnitDeath")

-- Set a 30-second timer
aow2.setTimer(30, "onTimerExpired")

-- Query some game state
local tick = aow2.getTick()
local unitCount = aow2.getUnitCount("confederation")

-- Define callback functions
function onUnitDeath(unitId)
    aow2.showMessage("Unit destroyed: " .. tostring(unitId))
    
    -- Check if all enemy units are destroyed
    local enemyCount = aow2.getUnitCount("resistance")
    if enemyCount == 0 then
        aow2.setObjective("destroy_enemy", "completed")
        aow2.showMessage("Objective completed: Destroy all enemy units!")
    end
end

function onTimerExpired()
    aow2.showMessage("30 seconds have passed!")
    
    -- Spawn reinforcement units
    aow2.spawnUnit("confederation", "CONFED_INFANTRY", 5, 5)
    aow2.spawnUnit("confederation", "CONFED_INFANTRY", 6, 5)
end

-- Area trigger example
aow2.onAreaEntered(20, 15, 3, "onBaseApproached")

function onBaseApproached(unitId)
    aow2.showMessage("Enemy approaching the base area!")
end
