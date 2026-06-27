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

---
Task ID: 2
Agent: Main
Task: Extract and catalogue all assets from the three non-APK distributions of Art of War 2 (J2ME Global Confederation, J2ME Liberation of Peru, iOS v2.2), organize them into separate categorized folders, document each file's purpose, and push to GitHub.

Work Log:
- Step 1: Extracted `art_of_war_2_global_260793.jar` (J2ME Global Confederation v1.12.0, HeroCraft 2012) → 52 files
- Step 2: Extracted `artofwar2l_1tio5twt.jar` (J2ME Liberation of Peru v1.0.06, Gear Games 2009) → 88 files
- Step 3: Extracted `Art_Of_War_2_2.2_ios_2.2.1.ipa` (iOS v2.2, Gear Games 2009) → 146 files
- Step 4: Wrote `scripts/organize_external_versions.py` to categorize each file by purpose (classes / data / fonts / audio / missions / maps / sprites / text / metadata / crack / executable / app_icons / archives)
- Step 5: Ran the categorization script — 286 files organized into `docs/RE/external_versions/{jar_global_confederation_v1.12.0, jar_liberation_of_peru_v1.0.06, ipa_ios_v2.2}/`
- Step 6: Inspected special files (Mach-O binary, ZIP archive `add`, PNG atlas `d1`, plain-text strings in Peru `0/` folder, cracker signature `Spid3r`) to confirm categorization
- Step 7: Wrote `docs/RE/external_versions/EXTERNAL_VERSIONS.md` — 760-line per-file reference explaining the purpose of every one of the 286 files
- Step 8: Updated `docs/RE/documentation/Asset_Catalog.md` — added §11 "External Versions (Non-APK Distributions)" with cross-version asset selection guide
- Step 9: Updated `docs/RE/FILE_INVENTORY.md` — added `external_versions/` tree, "Why the external_versions/ folder exists" section, and updated key statistics (286 external files, 16.5 MB)
- Step 10: Commit and push to GitHub

Stage Summary:
- BIG WIN: iOS build is the ONLY source for 72 SFX WAV files (screams, explosions, tank fire, UI clicks) — the Online APK had zero SFX
- BIG WIN: iOS build ships `music.mp3` (1.9 MB) and `music.wav` (2.6 MB) — APK only had 18 KB of MIDI
- BIG WIN: iOS build ships `d1` — a 1024×1024 RGBA master sprite atlas (1.27 MB) — only true-color atlas across all versions
- BIG WIN: iOS build ships Russian localization (`Russian.lproj/`) — only Russian source
- BIG WIN: Peru JAR ships 38 Episode 2 maps (map1-20 + map51-68) — only source for Episode 2
- BIG WIN: Peru JAR ships `0_t0/0_s0/0_d0` as plain ASCII — only readable Episode 2 game strings
- BIG WIN: Peru JAR ships `r00-r05` rank insignia PNGs — only source for rank badges
- BIG WIN: iOS build ships 37 mission scripts (mi0-mi21 × 2 languages) — only full mission briefing set
- Cracker artifacts isolated under `crack/` subfolders (Peru: 2 .class files; iOS: Spid3r signature + CutCut Mach-O) — preserved for forensic completeness but flagged as non-game-assets
- Categorization script preserved at `scripts/organize_external_versions.py` for re-runnability
