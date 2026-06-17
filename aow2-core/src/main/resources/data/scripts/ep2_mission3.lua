-- Episode 2, Mission 3: Mountain Fortress
-- Capture the mountain outpost and turn its defenses against the enemy

local outpostRevealed = false
local counterAttack = false
local outpostCaptured = false
local holdTimer = 0
local holdDuration = 4800

function onStart()
    aow2.showMessage("The Confederation has fortified a mountain outpost with Rocket Launchers.")
    aow2.showMessage("Send Snipers to neutralize the garrison and capture the position!")
    aow2.onUnitKilled("onOutpostDefenderKilled")
    aow2.onBuildingDestroyed("onOutpostBuildingDestroyed")
    aow2.setObjective("capture_outpost", "active")
    aow2.setObjective("hold_outpost", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Reveal outpost garrison
    if tick == 300 and not outpostRevealed then
        outpostRevealed = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 26, 4)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 28, 5)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 25, 6)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 27, 3)
        aow2.spawnUnit("CONFEDERATION", "CONFED_SNIPER", 29, 7)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 24, 5)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 30, 4)
        aow2.showMessage("Mountain outpost garrison spotted! Use Snipers to clear them!")
    end

    -- Confederation counterattack to retake the outpost
    if tick == 2400 and not counterAttack then
        counterAttack = true
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 26, 15)
        aow2.spawnUnit("CONFEDERATION", "CONFED_HAMMER", 28, 16)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 25, 17)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 27, 18)
        aow2.spawnUnit("CONFEDERATION", "CONFED_GRENADIER", 29, 15)
        aow2.spawnUnit("CONFEDERATION", "CONFED_INFANTRY", 24, 16)
        aow2.showMessage("Confederation counterattack! Defend the outpost!")
    end

    -- Track holding timer once outpost area is secured
    if outpostCaptured then
        holdTimer = holdTimer + 1
        if holdTimer >= holdDuration then
            aow2.setObjective("hold_outpost", "complete")
            aow2.showMessage("Outpost held! The mountain fortress is ours!")
        end
    end

    -- Check if outpost area is clear of enemies
    if outpostRevealed and not outpostCaptured then
        if aow2.getUnitCount("CONFEDERATION") == 0 then
            outpostCaptured = true
            aow2.setObjective("capture_outpost", "complete")
            aow2.showMessage("Outpost captured! Hold it against the counterattack!")
        end
    end
end

function onOutpostDefenderKilled()
    local remaining = aow2.getUnitCount("CONFEDERATION")
    if remaining > 0 and remaining <= 3 then
        aow2.showMessage("Outpost nearly clear! " .. remaining .. " defenders remaining.")
    end
end

function onOutpostBuildingDestroyed()
    aow2.showMessage("Enemy fortification destroyed!")
end

function onTrigger(triggerId)
    if triggerId == 23 then
        aow2.showMessage("Our forces have reached the outpost perimeter!")
    elseif triggerId == 24 then
        aow2.showMessage("Confederation reinforcements detected on the mountain road!")
    end
end
