-- Episode 2, Mission 6: Night Raid
-- Infiltrate the Confederation research facility under cover of darkness

local facilityRevealed = false
local alarmRaised = false
local techDestroyed = false
local extractionPoint = {x = 10, y = 10}
local infiltratorAlive = true

function onStart()
    aow2.showMessage("The Confederation is developing a new superweapon at their Tech Centre.")
    aow2.showMessage("Infiltrate the facility, destroy their research, and extract the infiltrator.")
    aow2.onUnitKilled("onFacilityUnitKilled")
    aow2.onBuildingDestroyed("onFacilityBuildingDestroyed")
    aow2.setObjective("destroy_tech", "active")
    aow2.setObjective("escort_infiltrator", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Facility guards
    if tick == 200 and not facilityRevealed then
        facilityRevealed = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 16, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 18, 8)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 15, 10)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 20, 9)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 17, 6)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 19, 11)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 22, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 14, 12)
        aow2.showMessage("Research facility guards spotted. Use stealth and precision.")
    end

    -- Alarm triggers reinforcements
    if not alarmRaised and facilityRevealed then
        local enemyCount = aow2.getUnitCount("CONFEDERATION")
        if enemyCount <= 4 then
            alarmRaised = true
            aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 25, 10)
            aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 27, 12)
            aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 26, 8)
            aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 14)
            aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 24, 11)
            aow2.showMessage("ALARM! Facility security reinforcements incoming!")
            aow2.showMessage("Get the infiltrator to the extraction point!")
        end
    end

    -- Time limit warning
    if tick == 6000 then
        aow2.showMessage("Dawn is approaching! Extract before the alarm brings more troops!")
    end

    -- Victory check
    if techDestroyed and infiltratorAlive then
        aow2.setObjective("destroy_tech", "complete")
        aow2.setObjective("escort_infiltrator", "complete")
        aow2.showMessage("Tech Centre destroyed and infiltrator extracted! Mission success!")
    end
end

function onFacilityBuildingDestroyed()
    techDestroyed = true
    aow2.showMessage("Tech Centre destroyed! Their superweapon research is gone!")
    aow2.showMessage("Now extract the infiltrator to the safe zone!")
end

function onFacilityUnitKilled()
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 and remaining <= 2 then
        aow2.showMessage("Facility nearly clear! " .. remaining .. " guards remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 30 then
        aow2.showMessage("The Tech Centre has been located inside the compound!")
    elseif triggerId == 31 then
        aow2.showMessage("Extraction window closing! Move the infiltrator now!")
    end
end
