-- Episode 2, Mission 4: Desert Storm
-- Break through the desert blockade with armor superiority

local blockadeSpawned = false
local reserveArmor = false
local fortressTanks = 3
local fortressKilled = 0

function onStart()
    aow2.showMessage("The Confederation has blockaded the desert road with Fortress tanks.")
    aow2.showMessage("Our Rhino heavy tanks can break their line!")
    aow2.onUnitKilled("onBlockadeUnitKilled")
    aow2.onBuildingDestroyed("onBlockadeEmplacementDestroyed")
    aow2.setObjective("destroy_blockade", "active")
    aow2.setObjective("eliminate_fortress", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Blockade forces
    if tick == 300 and not blockadeSpawned then
        blockadeSpawned = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 20, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 22, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_ZEUS", 24, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 19, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 21, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 23, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 25, 13)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 20, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 22, 6)
        aow2.showMessage("Blockade sighted! Zeus heavy tanks ahead — use anti-armor tactics!")
    end

    -- Reserve armor
    if tick == 3000 and not reserveArmor then
        reserveArmor = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 30, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 28, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 26, 12)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 29, 14)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 31, 9)
        aow2.showMessage("Confederation reserve armor incoming! Break through before they mass!")
    end

    -- Victory check
    if fortressKilled >= fortressTanks then
        aow2.setObjective("eliminate_fortress", "complete")
        aow2.showMessage("All Zeus tanks destroyed!")
        if aow2.getUnitCount("CONFEDERATION") == 0 then
            aow2.setObjective("destroy_blockade", "complete")
            aow2.showMessage("Blockade broken! The road to liberation is open!")
        end
    end
end

function onBlockadeUnitKilled()
    fortressKilled = fortressKilled + 1
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 then
        aow2.showMessage("Enemy unit destroyed! " .. remaining .. " remaining.")
    end
end

function onBlockadeEmplacementDestroyed()
    aow2.showMessage("Enemy emplacement destroyed!")
end

function onTrigger(triggerId)
    if triggerId == 25 then
        aow2.showMessage("Enemy Rocket Launcher position destroyed!")
    elseif triggerId == 26 then
        aow2.showMessage("Confederation reserves are mobilizing!")
    end
end
