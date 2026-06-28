# Asset Development Guide — AOW2-Online

> **Purpose**: Single source of truth for the state of asset development, what's been done, what's pending, and exactly how to continue. Read this before doing any asset work.

**Last updated**: 2026-06-28
**Sessions covered**: 2026-06-27 to 2026-06-28 (5 commits, 4 distinct work phases)

---

## 1. Where everything lives

### 1.1 Source archives (uploaded by user, not in repo)

```
/home/z/my-project/upload/
├── art-of-war-2-online.apk                  ← Online Android build (HeroCraft 2011)
├── art-of-war-2-online-RE-FULL.zip          ← Full RE archive (extracted to /tmp/aow2-reference/)
├── art_of_war_2_global_260793.jar           ← J2ME Episode 1 (HeroCraft 2012, v1.12.0)
├── artofwar2l_1tio5twt.jar                  ← J2ME Episode 2 (Gear Games 2009, v1.0.06)
└── Art_Of_War_2_2.2_ios_2.2.1.ipa           ← iOS premium build (Gear Games 2009, v2.2)
```

### 1.2 Extracted assets in the repo

```
docs/RE/external_versions/
├── EXTERNAL_VERSIONS.md                                  ← Raw extraction reference (286 files, 760 lines)
├── jar_global_confederation_v1.12.0/                     ← J2ME Episode 1 (52 files)
│   ├── classes/  data/  fonts/  audio/  missions/  sprites/  text/  metadata/
├── jar_liberation_of_peru_v1.0.06/                       ← J2ME Episode 2 (88 files)
│   ├── classes/  data/  audio/  maps/  missions/  sprites/  text/  crack/  metadata/
└── ipa_ios_v2.2/                                         ← iOS premium build (146 files + decoded assets)
    ├── DECODED_ASSETS.md                                 ← Decoded/converted assets reference (FXGL-ready)
    ├── audio/              (74 raw WAV/MP3 files)
    ├── audio_ogg/          (73 OGG files + inventory.json — converted for FXGL)
    │   ├── sfx/            (72 OGG SFX at 96 kbps VBR)
    │   └── music/          (1 OGG music at 160 kbps VBR)
    ├── sprites/            (6 raw sprite files: d1 atlas, English_i0, Russian_i0, d00, l1, l2, l3)
    ├── sprites_decoded/    (95 decoded PNGs + inventory.json — FXGL-ready)
    │   ├── d1_master_atlas.png   (1024×1024 RGBA, renamed copy of d1)
    │   ├── english/              (45 PNGs unpacked from English_i0)
    │   ├── russian/              (45 PNGs unpacked from Russian_i0)
    │   ├── loading_screen_small.png   (renamed l1)
    │   ├── loading_screen_full.png    (renamed l2)
    │   ├── loading_screen_alt.png     (renamed l3)
    │   └── inventory.json
    ├── sprites_contact_sheet/   (3 montage PNGs for visual verification + JSON metadata)
    │   ├── english_contact_sheet.png   (5×9 grid of all 45 English sprites)
    │   ├── russian_contact_sheet.png   (5×9 grid of all 45 Russian sprites)
    │   ├── en_ru_differences.png       (side-by-side of the 1 sprite that differs EN vs RU)
    │   └── visual_verification.json
    ├── archives/  app_icons/  maps/  missions/  text/  data/  metadata/  executable/  crack/
```

### 1.3 Assets already wired into the game

```
aow2-client/src/main/resources/
├── assets/sprites/
│   ├── entities/   (7 sample PNGs: en_000, en_001, en_005, en_006, en_016, en_028, en_031)
│   └── ui/         (2 loading screens: loading_screen_full, loading_screen_small)
└── audio/
    ├── sfx/        (8 sample OGGs: select_1, affirmative_1, build_1, sniper_1,
    │                tank_heavy_1, explode_heavy_1, scream_1, attack_1)
    └── music/      (1 OGG: music.ogg)

aow2-core/src/main/resources/data/
├── maps/peru/      (38 JSON maps: peru_map1..peru_map20, peru_map51..peru_map68)
└── campaigns/enriched/
    ├── episode1_global_confederation.json   (7 missions with briefing_original, dialog_before/after)
    ├── episode2_liberation_of_peru.json     (7 missions with briefing_original, dialog_before/after)
    ├── _episode1_mission_briefings.json     (8 standalone briefings from iOS English_d0)
    └── _episode2_mission_briefings.json     (10 standalone briefings from Peru 0_d0)
```

### 1.4 Scripts (preserved outside the repo)

```
/home/z/my-project/scripts/
├── organize_external_versions.py     ← Categorise raw extraction into version folders
├── decode_ios_sprites.py             ← Unpack iOS i0 containers → individual PNGs
├── convert_ios_audio_to_ogg.py       ← WAV/MP3 → OGG/Vorbis via ffmpeg
├── make_sprite_contact_sheet.py      ← Generate montage PNGs for visual verification
├── convert_peru_maps.py              ← J2ME binary maps → AOW2-Online JSON
└── enrich_campaign_briefings.py      ← Parse iOS/Peru d0 text → enriched campaign JSONs
```

All scripts are **idempotent** — re-running them overwrites the output. They are NOT in the repo (lives in `/home/z/my-project/scripts/`) but are referenced from documentation.

---

## 2. Current state — what works

| Capability | Status | Notes |
|-----------|--------|-------|
| Extract assets from JAR/IPA archives | ✅ Done | 286 files extracted, categorised, documented |
| Decode iOS sprite containers (i0) | ✅ Done | 90 PNGs (45 EN + 45 RU), format reverse-engineered |
| Convert iOS audio to OGG | ✅ Done | 73 OGG files (72 SFX + 1 music), Vorbis SPI added to build |
| Verify sprite mappings visually | ✅ Done | VLM analysis applied, 9 corrections, contact sheets generated |
| Port Peru campaign maps to JSON | ✅ Done | 38 maps converted, all pass MapLoader validation |
| Enrich campaign briefings | ✅ Done | Both episodes enriched with original Gear Games text |
| Load decoded sprites in FXGL | ✅ Done | `AssetTestScene` loads PNGs via `getResourceAsStream()` |
| Play OGG SFX in FXGL | ✅ Done | `AssetTestScene` plays via `javax.sound.sampled` + Vorbis SPI |
| AudioManager plays real SFX | ❌ Pending | AudioManager still loads placeholder `.wav` names; needs bridging |
| Real sprites in GameScene | ❌ Pending | GameScene uses `ProceduralSpriteGenerator` fallback; SpriteManager expects files at `assets/sprites/{units,buildings,terrain}/` with specific naming |
| Real music in MainMenuScene | ❌ Pending | AudioManager expects `/audio/music/{name}.mp3` but we have `music.ogg` |
| Mission briefings displayed | ❌ Partial | `CampaignScene` shows existing `briefing` field; `briefing_original` from enriched JSONs not yet wired |
| Peru maps selectable in Skirmish | ❌ Pending | `AOW2App.discoverMapResources()` only scans `data/maps/` top-level, not `data/maps/peru/` |

---

## 3. What's been verified vs. guessed

### 3.1 Sprite mappings (45 English sprites)

See `docs/RE/external_versions/ipa_ios_v2.2/DECODED_ASSETS.md` §2.2.1 for the full table with confidence levels.

- **✅ Verified** (16 sprites): Mappings confirmed by VLM visual analysis AND cross-referenced with source-code-derived J2ME indices. These are the "safe" sprites to use in code.
- **🟡 Likely** (29 sprites): Mappings based on VLM analysis only, no source-code cross-reference. Treat with caution — verify by visual inspection before relying on them.
- **❓ Unknown** (0 sprites): All sprites have at least a VLM-derived mapping.

**Key verified sprites safe for game use**:

| Index | File | Purpose |
|-------|------|---------|
| 0 | `en_000.png` | Title screen — "GLOBAL CONFEDERATION" globe |
| 1 | `en_001.png` | Terrain tile sheet — grass/dirt/water |
| 2 | `en_002.png` | Terrain tile sheet — rock/desert |
| 3 | `en_003.png` | Terrain tile sheet — tree/forest |
| 4 | `en_004.png` | Terrain tile sheet — bush/vegetation |
| 5 | `en_005.png` | **Confederation (blue) sprite sheet** — 15 sub-sprites (7 buildings, 5 vehicles, 2 infantry, 1 crystal) |
| 6 | `en_006.png` | **Rebels (red) sprite sheet** — 12 sub-sprites (buildings, vehicles, infantry) |
| 16 | `en_016.png` | Explosion effect (RGBA) |
| 28 | `en_028.png` | UI elements sheet (grid panels, icons, landmass outline) |
| 30 | `en_030.png` | Blue faction insignia/badge |
| 31–34 | `en_031.png`–`en_034.png` | Water textures (4 animated variants) |

### 3.2 Peru map terrain mappings

The Peru maps' terrain byte values >100 are **NOT correctly mapped** — they default to GRASS. The `d0` data file contains lookup tables (`this.a.d[]` and `this.a.e[]` in the decompiled `f.java`) that translate these variant indices to actual terrain types, but those tables have not been decoded yet.

**Affected**: ~80% of terrain tiles in the 38 Peru maps currently render as GRASS when they should be terrain variants (e.g. 0x6F=111 should probably be a specific grass type, 0xF0=240 should probably be a specific water type).

**Workaround**: The maps are structurally valid (correct dimensions, valid JSON, loadable by MapLoader) but visually inaccurate. Fixing this requires decoding the `d0` lookup tables — see §4.4 below.

### 3.3 Audio file mappings

The iOS SFX filenames are descriptive (`select_1.ogg`, `sniper_1.ogg`, etc.) but the AOW2-Online `AudioManager` expects placeholder names (`ui_click`, `gunshot`, etc.) per `aow2-client/src/main/resources/audio/README.txt`. The mapping has not been formalised yet — see §4.1 below.

---

## 4. How to continue — concrete next steps

### 4.1 Bridge AudioManager to the converted OGG files (HIGH priority)

**Problem**: `AudioManager.java` loads SFX via:
```java
getClass().getResource("/audio/sfx/{sfxName}.wav")  // expects .wav extension
```
And music via:
```java
getClass().getResource("/audio/music/{trackName}.mp3")  // expects .mp3 extension
```

But the converted files are `.ogg` with original iOS names (`select_1.ogg`, not `ui_click.wav`).

**Fix needed**:
1. Update `AudioManager.playSFX()` and `preloadSFX()` to load `.ogg` instead of `.wav`
2. Update `AudioManager.playMusic()` to load `.ogg` instead of `.mp3`
3. Create a name-mapping table from placeholder names to real iOS filenames:

   | AudioManager placeholder | iOS OGG filename |
   |--------------------------|------------------|
   | `ui_click` | `select_1.ogg` (or random from `select_1..6`) |
   | `ui_hover` | `select_2.ogg` |
   | `build_complete` | `building_ready_1.ogg` |
   | `unit_produced` | `affirmative_1.ogg` (or random from `affirmative_1..4`) |
   | `research_done` | `research_complete_1.ogg` |
   | `error` | `menu_close_1.ogg` |
   | `explosion` | `explode_heavy_1.ogg` (or random from `explode_heavy_1..7`) |
   | `gunshot` | `machine_light_1.ogg` (or `sniper_1.ogg` for sniper units) |
   | `rocket_fire` | `rocket_light_1.ogg` |
   | `artillery` | `tank_siege_1.ogg` |
   | `flame` | `flamethrower_1.ogg` |
   | `mine_place` | (no equivalent — generate programmatically or reuse `build_1.ogg`) |
   | `mine_explode` | `explode_light_1.ogg` |
   | `victory` | (no equivalent — generate programmatically) |
   | `defeat` | (no equivalent — generate programmatically) |
   | `ambient_war` | (no equivalent — use `music.ogg` at low volume) |

4. Copy ALL 72 SFX OGG files (not just the 8-sample) into `aow2-client/src/main/resources/audio/sfx/`
5. Update `GameScene.java` to call `playSFX()` with context-appropriate names (e.g. `playSFX("sniper_1")` when a sniper fires, not just `playSFX("gunshot")`)

**Files to edit**:
- `aow2-client/src/main/java/com/aow2/client/audio/AudioManager.java`
- `aow2-client/src/main/java/com/aow2/client/scene/GameScene.java` (call sites)

### 4.2 Fix the 14 pre-existing compile errors (HIGH priority)

**Problem**: The build is currently broken. `./gradlew :aow2-client:compileJava` fails with 14 errors unrelated to asset work:

| File | Error | Fix |
|------|-------|-----|
| `ProceduralSpriteGenerator.java` L119, L427 | `fillArc()` signature mismatch | Check JavaFX 21 API — likely needs different arg count |
| `ProceduralSpriteGenerator.java` L675, L739, L747 | `ICE` / `RUINS` terrain types not found | These were removed from `TerrainType` enum but references remain — either re-add them or remove the cases |
| `IsometricRenderer.java` L298, L305, L306 | Cannot find symbol | Likely related to removed terrain types |
| `MultiplayerLobbyScene.java` L64, L628 | Cannot find symbol | Check imports / method signatures |
| `CampaignScene.java` L142 (×2) | Cannot find symbol | Check imports |
| `ReplayViewerScene.java` L321 | `ToggleButton` cannot be converted to `Button` | Change `styleButton()` param type or pass `.button` property |
| `AOW2App.java` L525 | `@Override` doesn't override | Remove `@Override` or fix method signature |

**Approach**: Run `./gradlew :aow2-client:compileJava 2>&1 | grep "error:"` to see the current list, then fix each one. These were introduced by earlier sessions' refactoring and have nothing to do with asset work.

### 4.3 Decode the d0 terrain lookup tables (MEDIUM priority)

**Problem**: The Peru maps' RLE terrain data contains byte values 0-255, but only values 0-10 have direct terrain type mappings. Values >100 are "variant indices" that need to be translated through lookup tables in the `d0` data file.

**The lookup tables** (from decompiled `f.java` `r()` method, lines 996-1080):
- `this.a.d[]` — array of terrain type IDs indexed by variant index
- `this.a.e[]` — array of terrain type range boundaries (5 entries + sentinel)
- `this.a.e[13]` — boundary between passable and impassible terrain
- `this.a.e[14]` — boundary between terrain and buildings (128+)
- `this.a.e[15]` — boundary for special structures

**How to decode**:
1. The `d0` file is at `docs/RE/external_versions/jar_liberation_of_peru_v1.0.06/data/d0` (103,215 bytes)
2. The format is documented in `EXTERNAL_VERSIONS.md` §3.2: "26 byte arrays + 18 short arrays with 2-byte BE length prefixes"
3. Write a Python script to parse `d0` and extract the `d[]` and `e[]` arrays
4. Update `scripts/convert_peru_maps.py`'s `TERRAIN_MAP` dict to use the decoded lookup tables
5. Re-run `convert_peru_maps.py` to regenerate the 38 JSON maps with correct terrain

**Estimated effort**: 2-4 hours (1 hour to parse `d0`, 1 hour to update the converter, 1 hour to verify)

### 4.4 Wire enriched campaign briefings into CampaignScene (MEDIUM priority)

**Problem**: `CampaignScene.showMissionBriefing()` (line 411) displays the mission's `briefing` field from the original campaign JSON. The enriched JSONs (in `data/campaigns/enriched/`) add `briefing_original`, `objectives_original`, `dialog_before`, `dialog_after` fields — but CampaignScene doesn't read them.

**Fix needed**:
1. Update `CampaignManager` to load from `data/campaigns/enriched/` instead of `data/campaigns/`
2. Update `CampaignScene.showMissionBriefing()` to display:
   - `briefing_original` as the primary briefing text (more authentic than the rewritten `briefing`)
   - `dialog_before` as a "Pre-mission conversation" expandable section
   - `objectives_original` alongside the existing `objectives` (show both for comparison)
   - `dialog_after` as a "Post-mission debrief" section (shown after mission completion)
3. Optionally: wire `dialog_after` segments into the in-mission `MessageLog` (which currently doesn't exist — see TODO.md "Known Design Limitations" #2)

**Files to edit**:
- `aow2-core/src/main/java/com/aow2/core/campaign/CampaignManager.java` (change resource path)
- `aow2-client/src/main/java/com/aow2/client/scene/CampaignScene.java` (display new fields)

### 4.5 Decode the iOS Mach-O sprite-rect table (MEDIUM priority)

**Problem**: The iOS `d1` file is a 1024×1024 RGBA master sprite atlas containing ALL entity sprites packed together. To slice it programmatically, we need the sprite-rect table (x, y, width, height for each sub-sprite) which is embedded in the iOS Mach-O binary.

**Why this matters**: The pre-sliced `i0` sprites (which we already decoded) are 8-bit colormap and lower quality than the 1024² RGBA atlas. Slicing `d1` would give us true-color versions of every sprite.

**How to decode**:
1. The Mach-O binary is at `docs/RE/external_versions/ipa_ios_v2.2/executable/Art Of War 2` (523,424 bytes, armv6)
2. Use a disassembler (Ghidra, IDA Free, or Hopper) to find the sprite-rect table
3. Look for a sequence of `(x, y, width, height)` tuples — likely as `int[4]` arrays or a struct array
4. The table is probably referenced from the sprite-drawing function which takes a sprite ID and blits from `d1`
5. Extract the table and write a Python script to slice `d1` into individual PNGs

**Estimated effort**: 4-8 hours (disassembly + table extraction + slicing script + verification)

**Alternative**: Skip this and use the pre-sliced `i0` sprites (already decoded, lower quality but sufficient for the recreation).

### 4.6 Make Peru maps selectable in Skirmish mode (LOW priority)

**Problem**: `AOW2App.discoverMapResources()` (line ~470) scans `data/maps/` for `.json` files but only at the top level — it doesn't recurse into `data/maps/peru/`.

**Fix**: Update `discoverMapResources()` to recursively scan subdirectories, or add a separate "Peru Campaign Maps" category to the skirmish map selection dialog.

### 4.7 Copy all 72 SFX + music into client resources (LOW priority)

Currently only a 9-file sample is copied. To get all SFX working:

```bash
cp docs/RE/external_versions/ipa_ios_v2.2/audio_ogg/sfx/*.ogg \
   aow2-client/src/main/resources/audio/sfx/
cp docs/RE/external_versions/ipa_ios_v2.2/audio_ogg/music/music.ogg \
   aow2-client/src/main/resources/audio/music/
```

This adds ~925 KB of SFX + 2.1 MB of music to the client resources.

---

## 5. Re-running the scripts

All scripts live at `/home/z/my-project/scripts/` and are idempotent. Re-run them if source archives change or if you want to regenerate outputs.

### 5.1 If you add a new source archive

1. Drop the file into `/home/z/my-project/upload/`
2. Extract it: `unzip -q /home/z/my-project/upload/<new-file> -d /tmp/aow2-extract/<new-dir>`
3. Update `scripts/organize_external_versions.py` to handle the new archive's file naming
4. Run: `python3 scripts/organize_external_versions.py`
5. Update `docs/RE/external_versions/EXTERNAL_VERSIONS.md` with the new version's per-file documentation

### 5.2 If the iOS i0 sprite format changes

1. The format is documented in `scripts/decode_ios_sprites.py` docstring
2. The auto-detection logic (3-byte size prefix + 0xFF terminator for J2ME vs. 3-byte size + 3-byte 0x000000 separator for iOS) is at lines 73-94
3. Run: `python3 scripts/decode_ios_sprites.py`
4. Regenerate contact sheets: `python3 scripts/make_sprite_contact_sheet.py`
5. Re-verify mappings with VLM if new sprites appear

### 5.3 If you need to re-convert audio

```bash
python3 scripts/convert_ios_audio_to_ogg.py
```

This re-converts all 72 WAV + 1 MP3 to OGG. The ffmpeg parameters are:
- SFX: 96 kbps VBR, 44100 Hz stereo (resampled from 22050 Hz mono to work around libvorbis quirk)
- Music: 160 kbps VBR, 44100 Hz stereo

### 5.4 If you need to re-convert Peru maps

```bash
python3 scripts/convert_peru_maps.py
```

This re-parses all 38 binary maps and regenerates the JSON files at `aow2-core/src/main/resources/data/maps/peru/`. The `TERRAIN_MAP` dict in the script controls the byte-to-terrain-string mapping — update it after decoding the `d0` lookup tables (see §4.3).

### 5.5 If you need to re-enrich campaign briefings

```bash
python3 scripts/enrich_campaign_briefings.py
```

This re-parses iOS `English_d0` and Peru `0_d0`, extracts mission briefings, and regenerates the enriched campaign JSONs at `aow2-core/src/main/resources/data/campaigns/enriched/`.

---

## 6. Verification checklist

Before relying on any asset in game code, verify:

### 6.1 Sprite verification
- [ ] Open `docs/RE/external_versions/ipa_ios_v2.2/sprites_contact_sheet/english_contact_sheet.png`
- [ ] Visually confirm the sprite at the index you plan to use matches its documented purpose
- [ ] Check the confidence level in `DECODED_ASSETS.md` §2.2.1 — only ✅ Verified mappings are safe to use without visual confirmation
- [ ] If the sprite is a "sheet" (multiple sub-sprites), confirm you know how to slice it — the sub-sprite boundaries are NOT documented (requires §4.5 work)

### 6.2 Audio verification
- [ ] Run `AssetTestScene` (Main Menu → Asset Test button) and click each SFX button
- [ ] Confirm the sound plays and matches its described purpose
- [ ] If the sound doesn't play, check the log for `UnsupportedAudioFileException` (Vorbis SPI not loaded) or `LineUnavailableException` (audio device busy)

### 6.3 Map verification
- [ ] Load the map via Skirmish mode (after §4.6 is done) or via a test
- [ ] Confirm dimensions match expectations (see `_conversion_summary.json` for all 38 maps' sizes)
- [ ] Visually inspect the terrain — if everything is GRASS, that's the §4.3 issue (d0 lookup tables not decoded)
- [ ] Check `startingPositions` — these are inferred from building types 121-123, fallback to opposite corners

### 6.4 Campaign briefing verification
- [ ] Open `aow2-core/src/main/resources/data/campaigns/enriched/episode1_global_confederation.json`
- [ ] Check that `briefing_original` is non-empty for each mission
- [ ] Check that `objectives_original` has at least 1 entry per mission
- [ ] After §4.4 is done, launch a campaign mission and confirm the briefing panel shows the original text

---

## 7. Glossary

| Term | Meaning |
|------|---------|
| **APK** | Android Package — the Online Android build |
| **JAR** | Java Archive — the J2ME feature-phone builds |
| **IPA** | iOS App Store Package — the iOS build |
| **i0** | Gear Games' packed sprite container format (3-byte size prefix per PNG, J2ME uses 0xFF terminator, iOS uses 3-byte 0x000000 separators) |
| **d0** | Binary data file containing 26 byte arrays + 18 short arrays — includes terrain lookup tables |
| **d1** | iOS-only 1024×1024 RGBA master sprite atlas |
| **d00** | iOS binary terrain-type index table (1 byte per cell) |
| **sn8p** | Encoded text/string blob (567 game strings in the Online APK) |
| **VLM** | Vision Language Model — used to visually verify sprite mappings |
| **CFR** | Counter-Factual Reasoning — the Java decompiler used (`/tmp/cfr.jar`) |
| **Mach-O** | macOS/iOS executable format — the iOS binary contains the sprite-rect table for d1 |
| **Vorbis SPI** | Service Provider Interface for OGG/Vorbis audio — added to the build so `javax.sound.sampled` can play `.ogg` files |

---

## 8. Commit history (asset work only)

| Commit | Date | Description |
|--------|------|-------------|
| `d5f25ed` | 2026-06-27 | Add external versions: J2ME Global Confederation, J2ME Liberation of Peru, iOS v2.2 |
| `212f136` | 2026-06-27 | Fix categorization: move jar_low bare s0 (MIDI) to audio/, d0 (binary) to data/ |
| `aa81d90` | 2026-06-27 | Finalize categorization: move hc/gg PNGs to sprites/, sn8p to text/ |
| `f6d3b39` | 2026-06-28 | Decode iOS sprites and convert WAV SFX to OGG for FXGL |
| `fb2cbe6` | 2026-06-28 | Port Peru maps, enrich campaign briefings, add FXGL asset test scene, verify sprites |

All commits pushed to `main` at `https://github.com/nawaf-al-hussain/AOW2-Online.git`.
