---
Task ID: 1
Agent: Main
Task: Campaign playtesting — audit all 29 missions, Lua scripts, map data, and Java wiring

Work Log:
- Step 1: Audited all 3 campaign JSONs (29 missions), verified all 58 file references (maps+scripts) exist, confirmed aow2-client↔aow2-core copies identical, verified trigger ID ranges non-overlapping
- Step 2: Audited MissionScriptEngine, CampaignManager, GameScene, AOW2App wiring — found 2 compile errors, onStart() never called, EventDispatcher never wired
- Step 3: Cross-referenced 8 Lua API calls against GameAPI.java — found broken event chain, uncaught exceptions, hardcoded map size
- Step 4: Applied 13 fixes across 39 files (6 Java, 30 map JSON, 3 Lua scripts)

Stage Summary:
- CRITICAL: MapLoader now supports 2D terrain array format (all 30 maps were loading as blank grass)
- CRITICAL: Lua onStart() now called from Java (mission init was dead code)
- CRITICAL: ModEventBridge + EventDispatcher wired (combat events now reach Lua callbacks)
- CRITICAL: 2 compile errors fixed in AOW2App.java
- HIGH: 1,061+ invalid terrain type replacements (DIRT→SAND, RUINS→FOREST) across 30 maps
- HIGH: 17 OOB spawnUnit coordinates fixed across 4 custom missions
- MEDIUM: ep2_mission4 victory condition bug fixed (fortressKilled never incremented)
- MEDIUM: try-catch added to getUnitCount/getBuildingCount for bad faction names
- MEDIUM: spawnUnit now uses dynamic map dimensions instead of hardcoded 128x128
- LOW: 2 dead variables cleaned up (ep1_mission3, ep2_mission6)
- 4 known design limitations documented (dual objectives, message display, area dispatch, tick narrowing)
