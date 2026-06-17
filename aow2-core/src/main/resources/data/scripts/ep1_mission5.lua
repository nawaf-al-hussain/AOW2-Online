-- Episode 1, Mission 5: Siege of Fort Bravo
-- Besiege the fortified Resistance position using artillery

local bunkerCount = 3
local bunkersDestroyed = 0
local fortAssault = false
local garrisonRevealed = false

function onStart()
    aow2.showMessage("Fort Bravo is heavily defended with bunkers and towers.")
    aow2.showMessage("Deploy Torrent MLRS units in siege mode to bombard their defenses!")
    aow2.onUnitKilled("onGarrisonKilled")
    aow2.onBuildingDestroyed("onBunkerDestroyed")
    aow2.setObjective("destroy_bunkers", "active")
    aow2.setObjective("capture_fort", "active")
end

function onTick()
    local tick = aow2.getTick()

    -- Reveal fort garrison
    if tick == 300 and not garrisonRevealed then
        garrisonRevealed = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 13, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 11)
        aow2.spawnUnit("RESISTANCE", "REBEL_SNIPER", 14, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 12, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_GRENADIER", 16, 10)
        aow2.showMessage("Fort garrison spotted! Break through their defenses.")
    end

    -- Fort assault reinforcements
    if tick == 5400 and not fortAssault then
        fortAssault = true
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 14, 8)
        aow2.spawnUnit("RESISTANCE", "REBEL_INFANTRY", 15, 9)
        aow2.spawnUnit("RESISTANCE", "REBEL_RHINO", 20, 10)
        aow2.spawnUnit("RESISTANCE", "REBEL_COYOTE", 22, 12)
        aow2.showMessage("Enemy relief force approaching! Take the fort quickly!")
    end

    -- Victory check: all bunkers destroyed and area secured
    if bunkersDestroyed >= bunkerCount then
        if aow2.getUnitCount("RESISTANCE") == 0 then
            aow2.setObjective("capture_fort", "complete")
            aow2.showMessage("Fort Bravo has fallen! The Confederation prevails!")
        end
    end
end

function onBunkerDestroyed()
    bunkersDestroyed = bunkersDestroyed + 1
    aow2.showMessage("Bunker destroyed! (" .. bunkersDestroyed .. "/" .. bunkerCount .. ")")
    if bunkersDestroyed >= bunkerCount then
        aow2.setObjective("destroy_bunkers", "complete")
        aow2.showMessage("All bunkers destroyed! Move in to capture the fort!")
    end
end

function onGarrisonKilled()
    local remaining = aow2.getUnitCount("RESISTANCE")
    if remaining <= 3 and remaining > 0 then
        aow2.showMessage("Fort garrison nearly eliminated! " .. remaining .. " remaining.")
    end
end

function onTrigger(triggerId)
    if triggerId == 8 then
        aow2.showMessage("Bunker breach! Press the attack!")
    elseif triggerId == 9 then
        aow2.showMessage("Our forces are inside the fort perimeter!")
    elseif triggerId == 10 then
        aow2.showMessage("Enemy relief force detected on approach!")
    end
end
