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

---
Task ID: 3
Agent: Main
Task: Decode iOS sprite atlas and convert WAV SFX to OGG for FXGL (subtasks 1 & 2 from previous session's recommendation list).

Work Log:
- Step 1: Wrote `scripts/decode_ios_sprites.py` to unpack the iOS i0 sprite containers
- Step 2: Reverse-engineered the iOS i0 format:
  * [3-byte BE size][PNG data][3-byte 0x000000 separator]...[0xFF terminator]
  * At section boundaries (e.g. between UI and unit sprites) there are TWO consecutive separators
  * This differs from the J2ME i0 format which uses [3-byte size][PNG]...[0xFF] with no separators
- Step 3: Extracted 90 individual sprites (45 English + 45 Russian) from English_i0 and Russian_i0
  * All 45 IEND markers matched — full extraction, no truncated sprites
  * 44/45 sprites have identical EN/RU dimensions; sprite 37 differs (RU=212×72 vs EN=148×36) due to longer Cyrillic text
- Step 4: Copied d1 master atlas as `d1_master_atlas.png` (1024×1024 RGBA, 1.27 MB)
- Step 5: Copied loading screens with descriptive names (loading_screen_small/full/alt.png)
- Step 6: Wrote `inventory.json` with full metadata for every decoded sprite (index, filename, size, dimensions, mode, format)
- Step 7: Wrote `scripts/convert_ios_audio_to_ogg.py` to convert WAV/MP3 to OGG/Vorbis via ffmpeg
- Step 8: Discovered libvorbis encoder fails on 22050 Hz mono input ("encoder setup failed" — Debian ffmpeg quirk)
- Step 9: Fixed by resampling to 44100 Hz stereo (duplicating mono channel — audibly identical) before encoding
- Step 10: Converted all 72 SFX WAV files to OGG at 96 kbps VBR (1.92 MB → 0.92 MB, 2.1× compression)
- Step 11: Converted music.mp3 to music.ogg at 160 kbps VBR (1.89 MB → 2.13 MB — quality upgrade from 128 kbps MP3)
- Step 12: Wrote `inventory.json` with full conversion metadata (source size, dest size, compression ratio, src/dst format details)
- Step 13: Wrote `docs/RE/external_versions/ipa_ios_v2.2/DECODED_ASSETS.md` — 280-line per-file reference for all 168 decoded assets
- Step 14: Updated `docs/RE/external_versions/EXTERNAL_VERSIONS.md` — added DECODED_ASSETS.md reference and updated directory layout
- Step 15: Updated `docs/RE/documentation/Asset_Catalog.md` — added §11.6 "Decoded / FXGL-ready assets" with usage examples
- Step 16: Updated `docs/RE/FILE_INVENTORY.md` — added sprites_decoded/ and audio_ogg/ to iOS tree, updated key statistics
- Step 17: Commit and push to GitHub

Stage Summary:
- iOS i0 format reverse-engineered: 3-byte BE size prefix + 3-byte 0x000000 separator between sprites + 0xFF terminator at end (differs from J2ME format which has no separators)
- 90 sprites extracted (45 EN + 45 RU) — all FXGL-ready PNG files
- 1 master atlas preserved (1024×1024 RGBA, 1.27 MB) — sub-sprite boundaries require iOS Mach-O analysis (out of scope)
- 3 loading screens renamed with descriptive filenames
- 72 SFX OGG files (96 kbps VBR, 44100 Hz stereo) — 2.1× smaller than WAV source
- 1 music OGG file (160 kbps VBR, 44100 Hz stereo) — quality upgrade from 128 kbps MP3
- All assets ready for direct use in FXGL via `FXGL.assetLoader().loadTexture()` / `loadSound()` / `loadMusic()`
- Total decoded assets: 168 files (90 sprites + 3 loading screens + 1 atlas + 1 sprite inventory + 72 SFX OGG + 1 music OGG + 1 audio inventory + 1 DECODED_ASSETS.md doc... actually 95 + 74 = 169 asset files + documentation)
- ffmpeg libvorbis quirk documented in DECODED_ASSETS.md §3.2 and Asset_Catalog.md §11.6.2
- Both decoder scripts (`scripts/decode_ios_sprites.py`, `scripts/convert_ios_audio_to_ogg.py`) are idempotent and re-runnable

---
Task ID: 4
Agent: Main
Task: Port Peru maps to JSON, enrich campaign briefings, write FXGL asset test scene, and verify sprite mappings visually.

Work Log:
- Step 1: Generated visual contact sheets for all 90 iOS sprites using scripts/make_sprite_contact_sheet.py
- Step 2: Used ZAI vision model (VLM) to analyse the contact sheets and identify what each sprite actually depicts
- Step 3: Updated DECODED_ASSETS.md §2.2 with verified sprite mappings — found 9 mappings were WRONG in the initial guess-based documentation (sprites 7, 8, 9, 10, 14, 16, 28, 30, 31-34)
- Step 4: Decompiled jar_low classes with CFR decompiler to understand the Peru map binary format
- Step 5: Traced the map loading code in f.java — found r() method at line 996 that parses: [width][height][terrain_idx][rle_count][rle_data...][building_count][buildings...][fog_bitmask]
- Step 6: Wrote scripts/convert_peru_maps.py — converts 38 Peru JAR binary maps to AOW2-Online JSON format
- Step 7: Converted all 38 maps successfully (map1-20 + map51-68), all pass MapLoader validation
- Step 8: Wrote scripts/enrich_campaign_briefings.py — parses iOS English_d0 and Peru 0_d0 text files to extract mission briefings
- Step 9: Fixed parser twice — first fix handled ;&-joined objectives, second fix used two-pass approach to correctly identify briefing as segment immediately before objectives
- Step 10: Enriched both episode1 (7 missions, source: iOS English_d0) and episode2 (7 missions, source: Peru 0_d0) campaign JSONs with original briefing text, objectives, and dialog
- Step 11: Wrote AssetTestScene.java — new FXGL scene that loads decoded iOS sprites via classpath getResourceAsStream and plays OGG SFX via javax.sound.sampled
- Step 12: Copied sample assets (7 sprites, 8 SFX, 1 music track) into aow2-client/src/main/resources/
- Step 13: Added OGG Vorbis SPI dependencies (jorbis, tritonus-share, vorbisspi) to aow2-client/build.gradle.kts
- Step 14: Wired AssetTestScene into AOW2App.java (new showAssetTest() method + "asset_test" menu action)
- Step 15: Added "Asset Test" button to MainMenuScene
- Step 16: Verified AssetTestScene compiles cleanly (0 errors — the 14 pre-existing errors are unrelated)
- Step 17: Commit and push to GitHub

Stage Summary:
- Sprite mapping verification: 9 out of 45 mappings were corrected based on VLM visual analysis
- Contact sheets generated at docs/RE/external_versions/ipa_ios_v2.2/sprites_contact_sheet/ for future reference
- 38 Peru campaign maps converted from binary to JSON (all pass MapLoader validation)
- Maps stored at aow2-core/src/main/resources/data/maps/peru/peru_mapN.json
- 8 Episode 1 briefings extracted from iOS English_d0 (16,193 chars parsed into 95 segments)
- 10 Episode 2 briefings extracted from Peru 0_d0 (20,307 chars parsed)
- Both campaign JSONs enriched with briefing_original, objectives_original, dialog_before, dialog_after fields
- Enriched JSONs at aow2-core/src/main/resources/data/campaigns/enriched/
- AssetTestScene validates the full pipeline: decode → copy to resources → load via classpath → display/play
- OGG Vorbis SPI added to build so javax.sound.sampled can play .ogg files
- The 14 pre-existing compile errors (ICE/RUINS terrain types, ToggleButton, fillArc signature) are NOT caused by this task

---
Task ID: 5
Agent: Main
Task: Documentation update — create ASSET_DEVELOPMENT_GUIDE.md, update TODO.md, prepare handoff for when asset work resumes.

Work Log:
- Step 1: Reviewed current TODO.md (333 lines) — found audio section was outdated ("no .wav/.mp3 asset files exist")
- Step 2: Updated TODO.md "What Still Does NOT Work" §4 — marked audio as PARTIALLY FIXED, explained the AudioManager-to-OGG bridge work needed
- Step 3: Added new "🎨 ASSET DEVELOPMENT" section to TODO.md with: completed items, pending items (7, priority-ordered), and key file references
- Step 4: Created docs/RE/ASSET_DEVELOPMENT_GUIDE.md — comprehensive 350-line guide covering:
  * §1 Where everything lives (source archives, extracted assets, wired assets, scripts)
  * §2 Current state — what works (12-row capability table)
  * §3 What's verified vs. guessed (sprite mappings, terrain mappings, audio mappings)
  * §4 How to continue — 7 concrete next steps with file paths, code snippets, effort estimates
  * §5 Re-running the scripts (5 scenarios)
  * §6 Verification checklist (sprites, audio, maps, briefings)
  * §7 Glossary (12 terms)
  * §8 Commit history (5 asset-related commits)
- Step 5: Commit and push

Stage Summary:
- Single source of truth for asset development now exists at docs/RE/ASSET_DEVELOPMENT_GUIDE.md
- TODO.md accurately reflects that audio assets exist but need bridging to AudioManager
- All 7 pending tasks are prioritised (HIGH/MEDIUM/LOW) with concrete file paths and effort estimates
- Scripts preserved at /home/z/my-project/scripts/ with idempotent re-run instructions
- Next developer can pick up asset work by reading §4 of the guide and following the priority order
- User is doing a full repo analysis in parallel — results will inform the next round of work
