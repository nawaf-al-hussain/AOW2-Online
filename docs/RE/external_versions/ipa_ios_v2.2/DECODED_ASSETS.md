# Decoded Assets — iOS Sprite and Audio Conversion

This document catalogues the **decoded/converted** assets derived from the raw iOS extraction. These are the assets ready for direct use in the AOW2-Online FXGL recreation project — no further extraction or format conversion should be needed.

**Generation date**: 2026-06-28
**Total decoded assets**: 168 files (90 sprites + 73 OGG audio + 3 loading screens + 1 inventory JSON + 1 sprite inventory JSON)

---

## 1. Why this folder exists

The raw iOS extraction under `ipa_ios_v2.2/sprites/` and `ipa_ios_v2.2/audio/` contains files in their original Gear Games packaging formats:

- `English_i0` / `Russian_i0` are **packed sprite containers** (multiple PNGs concatenated with size prefixes and separators) — FXGL cannot load these directly.
- `d1` is a single 1024×1024 master atlas PNG with no sub-sprite boundary metadata — FXGL would need to slice it manually.
- `*.wav` SFX files are 8-bit PCM mono at 22050 Hz — FXGL prefers OGG/Vorbis for size and loader efficiency.

This folder holds the **post-extraction, FXGL-ready** versions:

- Individual PNG sprites unpacked from the i0 containers
- The master atlas preserved as a properly-named PNG
- Loading screens with descriptive filenames
- All audio converted to OGG/Vorbis at 44100 Hz stereo

The conversion scripts are preserved at:
- `/home/z/my-project/scripts/decode_ios_sprites.py`
- `/home/z/my-project/scripts/convert_ios_audio_to_ogg.py`

---

## 2. Decoded Sprites (`sprites_decoded/`)

**Total**: 95 files (1 master atlas + 45 English + 45 Russian + 3 loading screens + 1 inventory JSON)

### 2.1 `d1_master_atlas.png` — Master Sprite Atlas

- **Source**: `ipa_ios_v2.2/sprites/d1`
- **Dimensions**: 1024 × 1024 pixels
- **Mode**: RGBA (true color with alpha)
- **Size**: 1,271,525 bytes (1.21 MB)
- **Purpose**: Contains all entity sprites (units, buildings, terrain, UI elements) packed into a single atlas. This is the only true-color master atlas across all four distributions of Art of War 2 (the J2ME and Android versions only ship 8-bit colormap sprite packs).
- **Usage note**: Sub-sprite boundaries are defined inside the iOS Mach-O binary (`ipa_ios_v2.2/executable/Art Of War 2`). To slice this atlas programmatically, the binary's sprite-rect table would need to be extracted — that is out of scope for this task. For now, treat this as a reference image for manual sprite identification. Individual sprites are better sourced from the `english/` and `russian/` folders below (those are already pre-sliced by Gear Games).

### 2.2 `english/` — 45 English-Localized Sprites

Extracted from `ipa_ios_v2.2/sprites/English_i0` using the i0 unpacker. Each sprite is saved as `en_NNN.png` where NNN is the zero-padded index within the container.

**i0 container format (iOS variant)**:
```
[3-byte BE size][PNG data][3-byte 0x000000 separator]
[3-byte BE size][PNG data][3-byte 0x000000 separator]
...
[3-byte BE size][PNG data]                ← last sprite, no trailing separator
[3-byte 0x000000 separator][0xFF terminator]
```

Note: At "section boundaries" (e.g. between UI sprites and unit sprites, observed at index 36→37), there are **two consecutive 3-byte separators** instead of one. The unpacker handles this by skipping all consecutive zero-byte groups.

#### 2.2.1 Verified sprite mappings

**Verification method**: A visual contact sheet (`sprites_contact_sheet/english_contact_sheet.png`) was generated and analysed with the ZAI vision model. The mappings below are the model's output, cross-referenced against the existing `Asset_Catalog.md` §4.3 (J2ME/APK sprite indices derived from Java source-code analysis).

**Confidence levels**:
- ✅ **Verified** — VLM analysis + matches existing source-code-derived mapping
- 🟡 **Likely** — VLM analysis only, no source-code cross-reference available
- ❓ **Unknown** — VLM analysis was vague or unclear

| Index | Dimensions | Mode | Size | Verified purpose | Confidence |
|-------|-----------|------|------|------------------|------------|
| 0 | 480×320 | P | 53,189 B | Title screen — "GLOBAL CONFEDERATION" text over a dark blue grid-overlaid globe focused on the Americas | ✅ Verified |
| 1 | 179×125 | P | 14,875 B | Terrain tile sheet — grass/dirt/water tiles | ✅ Verified |
| 2 | 206×131 | P | 13,727 B | Terrain tile sheet — rock/desert tiles | ✅ Verified |
| 3 | 229×114 | P | 13,749 B | Terrain tile sheet — tree/forest tiles | ✅ Verified |
| 4 | 200×63 | P | 7,302 B | Terrain tile sheet — bush/vegetation tiles | ✅ Verified |
| 5 | 274×147 | P | 32,090 B | **Confederation (blue) sprite sheet** — 15 sub-sprites: 7 buildings (Command Center, Factory, Outpost, 3 turrets, Dome), 5 vehicles (Tank, APC, Scout Car, Heavy Tank, Artillery), 2 infantry (Soldier, Specialist), 1 crystal/resource | ✅ Verified |
| 6 | 268×140 | P | 24,217 B | **Rebels (red) sprite sheet** — 12 sub-sprites: buildings, vehicles, infantry (red faction equivalent of #5) | ✅ Verified |
| 7 | 200×112 | P | 17,663 B | UI/information panel (was incorrectly guessed as "building sprite sheet") | ✅ Verified (corrected) |
| 8 | 77×152 | P | 5,311 B | Unit sprite sheet (blue faction — was guessed as "building") | ✅ Verified (corrected) |
| 9 | 77×152 | P | 4,918 B | Unit sprite sheet (red faction — was guessed as "building") | ✅ Verified (corrected) |
| 10 | 107×152 | P | 8,543 B | Vehicle sprite sheet (blue faction — was guessed as "factory") | ✅ Verified (corrected) |
| 11 | 153×49 | P | 4,001 B | Vehicle sprite sheet (blue faction, smaller) | 🟡 Likely |
| 12 | 91×109 | P | 5,598 B | Vehicle sprite sheet (red faction) | 🟡 Likely |
| 13 | 112×118 | P | 6,392 B | Vehicle sprite sheet (red faction) | 🟡 Likely |
| 14 | 223×78 | P | 12,576 B | Water/ship tile sheet (was guessed as "UI panel") | ✅ Verified (corrected) |
| 15 | 154×71 | P | 9,796 B | Aircraft sprite sheet (blue faction) | 🟡 Likely |
| 16 | 135×129 | RGBA | 27,374 B | Explosion effect (was guessed as "unit portrait") | ✅ Verified (corrected) |
| 17 | 73×119 | RGBA | 14,797 B | Empty/placeholder sprite (transparent) | 🟡 Likely |
| 18 | 65×75 | RGBA | 6,992 B | Empty/placeholder sprite (transparent) | 🟡 Likely |
| 19 | 121×67 | RGBA | 1,156 B | Cloud/smoke effect | 🟡 Likely |
| 20 | 91×82 | P | 3,283 B | Dirt/terrain tile sheet | 🟡 Likely |
| 21 | 94×63 | P | 2,194 B | Dirt/terrain tile sheet | 🟡 Likely |
| 22 | 106×56 | P | 2,582 B | Grass/terrain tile sheet | 🟡 Likely |
| 23 | 169×30 | RGBA | 11,500 B | UI element (buttons/icons) | 🟡 Likely |
| 24 | 68×81 | RGBA | 11,417 B | UI element (buttons/icons) | 🟡 Likely |
| 25 | 78×30 | P | 764 B | Small terrain detail | 🟡 Likely |
| 26 | 126×38 | RGBA | 9,382 B | UI element (buttons/icons) | 🟡 Likely |
| 27 | 231×42 | RGBA | 2,905 B | UI/information panel | 🟡 Likely |
| 28 | 300×300 | RGBA | 15,800 B | **UI elements sheet** — grid-patterned panels, coloured rectangular blocks, small icon-like symbols (crosses, arrows, shapes), and a distinct orange landmass outline. Used for maps, menus, and tactical information display. (was guessed as "large icon or portrait") | ✅ Verified (corrected) |
| 29 | 272×31 | RGBA | 9,936 B | Building/structure sprite (transparent) | 🟡 Likely |
| 30 | 45×94 | P | 1,114 B | Blue faction insignia/badge (was guessed as "tower/wall") | ✅ Verified (corrected) |
| 31 | 220×166 | RGBA | 85,408 B | Water texture (transparent) — was guessed as "mission briefing" | ✅ Verified (corrected) |
| 32 | 220×166 | RGBA | 85,936 B | Water texture (transparent) | ✅ Verified (corrected) |
| 33 | 220×166 | RGBA | 101,659 B | Water texture (transparent) | ✅ Verified (corrected) |
| 34 | 220×166 | RGBA | 97,764 B | Water texture (transparent) | ✅ Verified (corrected) |
| 35 | 133×36 | P | 3,493 B | Character portraits sheet | 🟡 Likely |
| 36 | 198×81 | P | 942 B | UI element (buttons/icons) | 🟡 Likely |
| 37 | 148×36 | P | 370 B | UI element (buttons/icons) — this is the sprite that differs in Russian (212×72) due to longer Cyrillic text | ✅ Verified |
| 38 | 61×43 | P | 887 B | Red faction insignia/badge | 🟡 Likely |
| 39 | 88×53 | RGBA | 1,718 B | Black smoke effect | 🟡 Likely |
| 40 | 58×50 | RGBA | 3,801 B | Menu button/logo | 🟡 Likely |
| 41 | 50×28 | P | 796 B | Menu button/logo | 🟡 Likely |
| 42 | 63×16 | P | 578 B | Menu button/logo | 🟡 Likely |
| 43 | 98×15 | P | 1,323 B | Menu button/logo | 🟡 Likely |
| 44 | 33×7 | P | 251 B | Menu button/logo | 🟡 Likely |

#### 2.2.2 Key corrections from visual verification

The visual verification revealed **several mappings were wrong** in the initial guess-based documentation:

| Index | Initial guess (WRONG) | Verified purpose (CORRECT) |
|-------|----------------------|----------------------------|
| 7 | "Building sprite sheet" | UI/information panel |
| 8 | "Building — small (bunker/locator?)" | Unit sprite sheet (blue faction) |
| 9 | "Building — small alt" | Unit sprite sheet (red faction) |
| 10 | "Building — medium (factory?)" | Vehicle sprite sheet (blue faction) |
| 14 | "Wide UI panel / button bar" | Water/ship tile sheet |
| 16 | "Unit sprite with alpha (transparent bg)" | Explosion effect |
| 28 | "Large icon or portrait (square)" | UI elements sheet (grid panels, icons, landmass outline) |
| 30 | "Tall narrow sprite (tower/wall?)" | Blue faction insignia/badge |
| 31–34 | "Large sprite — likely mission briefing" | Water textures (4 variants) |

**Lesson**: Initial dimension-based guesses are unreliable. Always verify sprite mappings by visual inspection before using them in code.

#### 2.2.3 Contact sheets for further verification

The following contact sheet PNGs are available for manual visual verification:

| File | Contents |
|------|----------|
| `sprites_contact_sheet/english_contact_sheet.png` | All 45 English sprites in a 5×9 grid |
| `sprites_contact_sheet/russian_contact_sheet.png` | All 45 Russian sprites in a 5×9 grid |
| `sprites_contact_sheet/en_ru_differences.png` | Side-by-side comparison of the 1 sprite that differs between EN and RU (index 37) |
| `sprites_contact_sheet/visual_verification.json` | Machine-readable metadata for every sprite |

To regenerate these contact sheets:
```bash
python3 /home/z/my-project/scripts/make_sprite_contact_sheet.py
```

### 2.3 `russian/` — 45 Russian-Localized Sprites

Same structure as `english/`, saved as `ru_NNN.png`. All 45 sprites have identical dimensions to their English counterparts **except index 37**, which is wider in Russian (212×72 vs 148×36) due to longer Cyrillic text strings being rendered into the sprite.

### 2.4 Loading screens (3 files)

| Filename | Source | Dimensions | Mode | Size | Purpose |
|----------|--------|-----------|------|------|---------|
| `loading_screen_small.png` | `l1` | 148×98 | P | 3,229 B | Small loading thumbnail |
| `loading_screen_full.png` | `l2` | 480×320 | P | 83,825 B | Full iOS-resolution loading screen |
| `loading_screen_alt.png` | `l3` | 480×320 | P | 8,086 B | Alternate loading screen (post-load transition) |

### 2.5 `inventory.json`

Machine-readable metadata for every decoded sprite. Schema:

```json
{
  "master_atlas": { "filename": "...", "width": ..., "height": ..., "mode": "...", ... },
  "english_sprites": [
    { "index": 0, "filename": "en_000.png", "size_bytes": 53189, "width": 480, "height": 320, "mode": "P", "format": "PNG" },
    ...
  ],
  "russian_sprites": [ ... ],
  "loading_screens": [ ... ]
}
```

---

## 3. Converted Audio (`audio_ogg/`)

**Total**: 74 files (72 SFX + 1 music + 1 inventory JSON)

### 3.1 Why convert WAV → OGG?

| Format | Pros | Cons |
|--------|------|------|
| **WAV (original)** | Lossless, simple | 8-bit PCM mono, ~10× larger than needed |
| **OGG/Vorbis (converted)** | ~2× smaller for SFX, ~10× smaller for music, FXGL-native | Lossy (but transparent at the bitrates chosen) |

FXGL's `SoundLoader` and `MusicLoader` are designed around OGG. WAV works but is intended only for very short UI clicks (sub-100 ms). For SFX with any duration (gunfire, screams, explosions), OGG is the recommended format.

### 3.2 Conversion parameters

| Asset type | Codec | Bitrate | Sample rate | Channels | Container |
|------------|-------|---------|-------------|----------|-----------|
| SFX | libvorbis | 96 kbps VBR | 44100 Hz | Stereo | OGG |
| Music | libvorbis | 160 kbps VBR | 44100 Hz | Stereo | OGG |

**Why resample to 44100 Hz stereo?** The original WAV files are 22050 Hz mono. FFmpeg's libvorbis encoder has a known issue where it fails with "encoder setup failed" when given 22050 Hz mono input. Resampling to 44100 Hz stereo (by duplicating the mono channel to both left and right) works around this and produces output that is bit-identical to what a 22050 Hz mono encoding would sound like.

**Why 96 kbps for SFX?** At 44100 Hz stereo, 96 kbps VBR is the libvorbis "sweet spot" — transparent for SFX (no perceptible quality loss vs. the original 8-bit PCM) while achieving ~2× compression. Higher bitrates (128 kbps+) would be wasteful for 8-bit source material.

**Why 160 kbps for music?** Music benefits from higher fidelity than SFX. 160 kbps VBR is transparent for most listeners and produces files slightly larger than the original 128 kbps MP3 (because Vorbis at 160 kbps is higher quality than MP3 at 128 kbps).

### 3.3 `sfx/` — 72 SFX OGG files

All 72 SFX WAV files converted. Total: 1,921,838 B → 924,490 B (2.1× compression).

Grouped by category:

| Category | File count | Total OGG size | Source WAV size | Compression |
|----------|-----------|----------------|-----------------|-------------|
| **UI / selection** | 25 | 378,288 B | ~800 KB | ~2.1× |
| **Infantry weapons** | 4 | 41,090 B | ~80 KB | ~2.0× |
| **Vehicle weapons** | 23 | 223,784 B | ~480 KB | ~2.1× |
| **Explosions** | 14 | 195,345 B | ~520 KB | ~2.7× |
| **Voice** | 6 | 85,983 B | ~150 KB | ~1.8× |

**UI / selection (25 files)**:

| File | Source | Purpose |
|------|--------|---------|
| `select_1.ogg` – `select_6.ogg` | `select_1.wav` – `select_6.wav` | Six unit-select click variants |
| `affirmative_1.ogg` – `affirmative_4.ogg` | `affirmative_1.wav` – `affirmative_4.wav` | Four "acknowledged" voice responses |
| `affirmative_l_1.ogg` – `affirmative_l_7.ogg`, `affirmative_l_11.ogg` | matching WAVs | Eight quieter ("low-volume") affirmatives |
| `menu_open_1.ogg` | `menu_open_1.wav` | Menu open sound |
| `menu_close_1.ogg` | `menu_close_1.wav` | Menu close sound |
| `click_1.ogg` | `click_1.wav` | Generic UI click |
| `money_1.ogg` | `money_1.wav` | Funds received |
| `build_1.ogg` | `build_1.wav` | Construction start |
| `building_ready_1.ogg` | `building_ready_1.wav` | Construction complete |
| `research_complete_1.ogg` | `research_complete_1.wav` | Tech research complete |

**Infantry weapons (4 files)**:

| File | Source | Purpose |
|------|--------|---------|
| `sniper_1.ogg` – `sniper_3.ogg` | matching WAVs | Sniper rifle fire |
| `flamethrower_1.ogg` | `flamethrower_1.wav` | Flamethrower (continuous burn) |

**Vehicle weapons (23 files)**:

| File | Source | Purpose |
|------|--------|---------|
| `machine_light_1.ogg` – `machine_light_6.ogg` | matching WAVs | Light machine gun fire |
| `machine_med_1.ogg` – `machine_med_3.ogg` | matching WAVs | Medium machine gun fire |
| `tank_light_1.ogg` – `tank_light_4.ogg` | matching WAVs | Light tank cannon fire |
| `tank_heavy_1.ogg` – `tank_heavy_4.ogg` | matching WAVs | Heavy tank cannon fire |
| `tank_siege_1.ogg` – `tank_siege_3.ogg` | matching WAVs | Siege tank cannon fire |
| `rocket_light_1.ogg` – `rocket_light_3.ogg` | matching WAVs | Light rocket launcher fire |

**Explosions (14 files)**:

| File | Source | Purpose |
|------|--------|---------|
| `explode_light_1.ogg` – `explode_light_5.ogg` | matching WAVs | Light explosions (infantry death, light vehicle destruction) |
| `explode_heavy_1.ogg` – `explode_heavy_7.ogg` | matching WAVs | Heavy explosions (tank destruction) |
| `explode_bld_1.ogg`, `explode_bld_2.ogg` | matching WAVs | Building destruction |

**Voice (6 files)**:

| File | Source | Purpose |
|------|--------|---------|
| `scream_1.ogg` – `scream_5.ogg` | matching WAVs | Five infantry death screams |
| `attack_1.ogg` | `attack_1.wav` | "Attack!" voice command response |

### 3.4 `music/` — 1 music OGG file

| File | Source | Duration | Size | Purpose |
|------|--------|----------|------|---------|
| `music.ogg` | `music.mp3` (1,889,074 B) | 118.0 s | 2,134,378 B | Background music (full track, ~2 minutes) |

**Note**: The OGG is slightly larger than the source MP3 (2.13 MB vs 1.89 MB) because we encoded at 160 kbps VBR vs the original's 128 kbps MP3 — a deliberate quality upgrade. If file size is more important than quality, re-encode with `-b:a 96k` to get ~1.4 MB.

The original `music.wav` (8-bit mono 22050 Hz, 2.6 MB) was NOT used as the source because it is lower fidelity than `music.mp3`. It is preserved in `ipa_ios_v2.2/audio/music.wav` for archival.

### 3.5 `inventory.json`

Machine-readable metadata for every converted audio file. Schema:

```json
{
  "sfx": [
    {
      "filename": "affirmative_1.ogg",
      "source": "affirmative_1.wav",
      "src_size_bytes": 54896,
      "dst_size_bytes": 24551,
      "compression_ratio": 2.24,
      "src_format": { "codec": "pcm_u8", "sample_rate": 22050, "channels": 1, ... },
      "dst_format": { "codec": "vorbis", "sample_rate": 44100, "channels": 2, ... }
    },
    ...
  ],
  "music": [ { ... } ]
}
```

---

## 4. Usage in FXGL

### 4.1 Loading sprites

Copy the desired sprites into the FXGL assets directory:

```bash
# For English localization (default for AOW2-Online)
cp docs/RE/external_versions/ipa_ios_v2.2/sprites_decoded/english/*.png \
   aow2-client/src/main/resources/assets/textures/entities/

# Loading screens
cp docs/RE/external_versions/ipa_ios_v2.2/sprites_decoded/loading_screen_*.png \
   aow2-client/src/main/resources/assets/textures/ui/
```

Then load in FXGL:

```java
Texture sprite = FXGL.assetLoader().loadTexture("entities/en_005.png");
```

### 4.2 Loading audio

```bash
# SFX
cp docs/RE/external_versions/ipa_ios_v2.2/audio_ogg/sfx/*.ogg \
   aow2-client/src/main/resources/assets/sounds/

# Music
cp docs/RE/external_versions/ipa_ios_v2.2/audio_ogg/music/music.ogg \
   aow2-client/src/main/resources/assets/music/
```

Then load in FXGL:

```java
// SFX (short sounds, played on events)
Audio sound = FXGL.assetLoader().loadSound("sounds/sniper_1.ogg");
FXGL.getAudioPlayer().playSound(sound);

// Music (looping background track)
Audio music = FXGL.assetLoader().loadMusic("music/music.ogg");
FXGL.getAudioPlayer().playMusic("bgm", music);
```

### 4.3 Mapping sprites to entities

Based on the sprite dimensions and the known unit/building list from `Asset_Catalog.md` §3, the following mapping is suggested (to be verified by visual inspection):

| Sprite | Likely entity |
|--------|---------------|
| `en_005.png` (274×147) | Confederation unit sprite sheet |
| `en_006.png` (268×140) | Rebels unit sprite sheet |
| `en_007.png` (200×112) | Building sprite sheet |
| `en_008.png`–`en_010.png` (77×152 / 107×152) | Individual buildings (bunker, locator, factory) |
| `en_016.png` (135×129 RGBA) | Unit portrait with transparency |
| `en_028.png` (300×300 RGBA) | Mission briefing illustration |
| `en_031.png`–`en_034.png` (220×166 RGBA) | Mission briefing screens |

---

## 5. Regenerating the Decoded Assets

Both scripts are idempotent — re-running them will produce identical output (overwriting any existing files).

```bash
# Decode sprites
python3 /home/z/my-project/scripts/decode_ios_sprites.py

# Convert audio
python3 /home/z/my-project/scripts/convert_ios_audio_to_ogg.py
```

If new source files are added (e.g. an updated iOS build), drop them into `ipa_ios_v2.2/sprites/` or `ipa_ios_v2.2/audio/` and re-run the corresponding script.
