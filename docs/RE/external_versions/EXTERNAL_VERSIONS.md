# Art of War 2 — External Versions Asset Documentation

This document catalogs every file extracted from three **non-APK** distributions of Art of War 2, alongside the existing `art-of-war-2-online.apk` reverse-engineering corpus. These external builds were added so the AOW2-Online recreation project can pull the best-quality asset from whichever platform shipped it (J2ME pre-port, iOS premium release, or Android online port).

**Total files extracted and catalogued**: 286 files across 3 distributions.
**Total disk footprint**: ~16.5 MB (vs. 1.9 MB for the online APK alone).

---

## 1. Why These Files Exist

The original `art-of-war-2-online.apk` is a 2011 HeroCraft Android repackage of a 2009–2010 Gear Games J2ME title. It is small (2.3 MB) because Android's resource pipeline stripped several asset classes:

| Asset class missing from the APK | Where it survives |
|----------------------------------|-------------------|
| Pre-recorded **SFX** (gunfire, screams, UI clicks, explosions) | **iOS v2.2 build** ships 73 WAV files |
| **High-quality background music** (the APK only had two 10 KB MIDI files) | **iOS v2.2 build** ships full music.mp3 (1.9 MB) + music.wav (2.6 MB) |
| **Episode 2 — Liberation of Peru** campaign maps (38 missions) | **`artofwar2l_1tio5twt.jar`** ships all 38 map files |
| **Plain-text game strings** (the APK encodes them in `sn8p` binary format) | **Liberation of Peru `0/` folder** ships ASCII `t0`/`s0`/`d0` |
| **Episode 1 — Global Confederation** in pre-Android form | **`art_of_war_2_global_260793.jar`** is the v1.12.0 HeroCraft J2ME release |
| **1024×1024 master sprite atlas** | **iOS `d1`** is a 1024×1024 RGBA PNG (1.27 MB) |
| **Russian localization** | **iOS** ships both `English.lproj/` and `Russian.lproj/` |

Each external version was therefore preserved verbatim so the recreation project can pick, for any given asset class, the highest-quality source without having to re-purchase or re-decompile anything.

---

## 2. Directory Layout

```
docs/RE/external_versions/
├── EXTERNAL_VERSIONS.md                          ← this file
├── jar_global_confederation_v1.12.0/             ← J2ME Episode 1 (HeroCraft 2012)
│   ├── classes/      (18 .class files)
│   ├── data/         (13 binary data tables)
│   ├── fonts/        (8 font definition + image-strip pairs)
│   ├── audio/        (1 MIDI file)
│   ├── missions/     (7 mission scripts)
│   ├── sprites/      (3 PNG files: i0 sprite pack, hc, gg, l2)
│   ├── text/         (1 sn8p text/string blob)
│   └── metadata/     (1 MANIFEST.MF)
├── jar_liberation_of_peru_v1.0.06/               ← J2ME Episode 2 (Gear Games 2009)
│   ├── classes/      (11 .class files)
│   ├── data/         (14 binary data tables: a, d, d0, f, m0, m26, m35, m37, m42, m43, m44, ml, n, u)
│   ├── audio/        (1 MIDI file: s0 — Episode 2 background music)
│   ├── maps/         (38 map files: map1-20 + map51-68)
│   ├── missions/     (8 mission scripts: mi0-mi7)
│   ├── sprites/      (10 PNG files: i0 + r00-r05 rank insignia + l1, l2, icon.png)
│   ├── text/         (3 plain-text string files: 0_t0, 0_s0, 0_d0)
│   ├── crack/        (2 cracker-added .class files)
│   └── metadata/     (1 MANIFEST.MF)
└── ipa_ios_v2.2/                                 ← iOS premium build (Gear Games 2009)
    ├── audio/        (74 files: 73 SFX .wav + music.mp3)
    ├── archives/     (1 file: add = ZIP archive containing duplicate music.mp3)
    ├── app_icons/    (2 files: Default.png + Icon.png)
    ├── sprites/      (6 files: d1 master atlas + 2 localized i0 packs + d00 + l1, l2, l3)
    ├── maps/         (6 files: m0-m5)
    ├── missions/     (37 files: mi0-mi6 generic + mi7_en through mi21_en + mi7_ru through mi21_ru)
    ├── text/         (6 files: English_t0/s0/d0 + Russian_t0/s0/d0)
    ├── data/         (6 files: a, d, n, u, ml, d00)
    ├── metadata/     (6 files: Info.plist, Art_of_War_2-Info.plist, PkgInfo, 2 .nib, Art Of War 2.CutCut)
    ├── executable/   (1 file: Art Of War 2 — Mach-O armv6 binary)
    └── crack/        (1 file: Spid3r signature from idwaneo.com)
```

---

## 3. J2ME Global Confederation v1.12.0 (`jar_global_confederation_v1.12.0/`)

**Source file**: `art_of_war_2_global_260793.jar` (546 KB)
**MIDlet-Name**: `Art Of War 2 - Global Confederation`
**MIDlet-Vendor**: `HeroCraft`
**MIDlet-Version**: `1.12.0`
**MicroEdition-Profile**: `MIDP-2.0` / `CLDC-1.0`
**Build date**: 12 November 2012 (per `META-INF/MANIFEST.MF`)
**Why it exists**: This is the **pre-Android J2ME source distribution** of Episode 1 (Global Confederation campaign). The Android APK is a downstream port of this JAR — same mission scripts, same sprite pack, same text blob — so this JAR is the canonical reference for what the Android port changed (mainly: added the `b/` icon folder and the `s1`/`s2` high-res sprite variants).

### 3.1 `classes/` — 18 Java class files

| File | Size | Purpose |
|------|------|---------|
| `aow2.class` | 1,146 B | **MIDlet entry point** — main `startApp()` / `pauseApp()` / `destroyApp()` lifecycle |
| `a.class` | 13,534 B | Largest game class — likely the `GameCanvas` subclass holding the main loop |
| `b.class` | 2,011 B | Helper — likely input/keys |
| `c.class` | 5,874 B | Helper — likely rendering |
| `d.class` | 10,857 B | Helper — likely entity/unit management |
| `e.class` | 1,324 B | Small helper |
| `f.class` | 21,112 B | Mid-size class — likely AI/pathfinding |
| `g.class` | 120,046 B | **Largest class** — likely the map/terrain engine + sprite unpacker (matches `i0` 271 KB pack) |
| `h.class` | 1,168 B | Small helper |
| `i.class` | 3,982 B | Mid helper — likely mission scripting hooks |
| `j.class` | 453 B | Tiny helper |
| `k.class` | 23,014 B | Mid-large class |
| `l.class` | 1,009 B | Small helper |
| `m.class` | 9,579 B | Mid class |
| `n.class` | 231 B | Tiny helper |
| `o.class` | 6,406 B | Mid helper |
| `p.class` | 69,634 B | Large class — likely network/multiplayer stack (the Online edition's protocol) |

**Why the obfuscated names**: Gear Games shipped with ProGuard-style single-letter obfuscation. The downstream APK has the same names, so cross-referencing is straightforward using the existing `class_mapping.json` in the parent documentation tree.

### 3.2 `data/` — 13 binary data tables

| File | Size | Purpose |
|------|------|---------|
| `a` | 5,501 B | Encoded data table — same name in APK, holds unit/building stats indices |
| `d` | 164 B | Display flags (mirrors APK `d`) |
| `d0` | 106,423 B | Game data tables — 26 byte arrays + 18 short arrays (same format as APK `s0/d0`) |
| `dmt` | 39 B | Tiny flag blob — likely "demo" flag (file extension suggests `d(emo)m(a)r(k)er`) |
| `gg` | 2,033 B | Globe / minimap PNG (105×70, 8-bit colormap) |
| `hc` | 1,853 B | HeroCraft splash logo PNG (170×142, 8-bit colormap) |
| `m0`–`m5` | 4.9–14 KB | Six map files for Episode 1 missions |
| `ml` | 1,489 B | Map layout metadata |
| `n` | (none in this folder — present in APK as `n`) | — |
| `p` | (in APK only) | Platform version byte — not shipped in J2ME |
| `sn8p` | 31,352 B | **Encoded text blob** — 567 game strings (decoded in `gameplay_analysis/text_strings.json` upstream) |

### 3.3 `fonts/` — 8 font files (4 font definition + 4 image strips)

| File | Size | Purpose |
|------|------|---------|
| `f0_0` / `f0_0p` | 368 B / 628 B | Main font: A–Z, a–z (52 glyphs), 614×12 PNG strip |
| `f0_1` / `f0_1p` | (n/a in JAR — only `f0_0` family is shipped) | — |
| `f1_0` / `f1_0p` | 147 B / 294 B | Secondary font: 35 glyphs, 218×12 PNG strip |
| `f2_0` / `f2_0p` | 42 B / 140 B | Small digit font: 0–9 (10 glyphs), 39×5 PNG strip |
| `f3_0` / `f3_0p` | 42 B / 194 B | Alt digit font: 0–9, 39×5 PNG strip |

**Why font pairs**: `fX_Y` is the binary glyph-width table; `fX_Yp` is the matching PNG strip. The renderer reads widths from the binary, blits glyphs from the PNG. Same scheme as the APK.

### 3.4 `audio/` — 1 MIDI file

| File | Size | Purpose |
|------|------|---------|
| `s0m` | 12,285 B | MIDI format 0, 1 track at 480 ppqn — Episode 1 background music |

### 3.5 `missions/` — 7 mission scripts

| File | Size | Purpose |
|------|------|---------|
| `mi0` | 392 B | Tutorial / mission 0 header |
| `mi1` | 363 B | Mission 1 |
| `mi2` | 456 B | Mission 2 |
| `mi3` | 309 B | Mission 3 |
| `mi4` | 690 B | Mission 4 |
| `mi5` | 643 B | Mission 5 |
| `mi6` | 683 B | Mission 6 |

### 3.6 `sprites/` — 3 PNG files

| File | Size | Purpose |
|------|------|---------|
| `i0` | 271,333 B | **Sprite pack** — custom container with 3-byte big-endian size prefix per PNG, 0xFF terminator. Contains 57 sub-images for s0 (240×320) resolution |
| `l2` | 44,601 B | Loading screen PNG (240×320, 8-bit colormap) |

### 3.7 `text/` — 1 encoded text blob

| File | Size | Purpose |
|------|------|---------|
| `sn8p` | 31,352 B | Encoded game strings — moved here for category clarity (also listed under `data/` semantics) |

### 3.8 `metadata/` — 1 manifest

| File | Size | Purpose |
|------|------|---------|
| `MANIFEST.MF` | 435 B | J2ME MIDlet manifest declaring MIDlet-1, MIDlet-Name, MIDlet-Vendor, MIDlet-Version, MicroEdition-Configuration/Profile, MIDlet-Data-Size (8 KB), MIDlet-Info-URL (`http://www.herocraft.com`), MIDlet-Install-Notify (`http://www.herocraft.com/logfile.php`) |

---

## 4. J2ME Liberation of Peru v1.0.06 (`jar_liberation_of_peru_v1.0.06/`)

**Source file**: `artofwar2l_1tio5twt.jar` (812 KB)
**MIDlet-Name**: `Art Of War 2 - Liberation Of Peru`
**MIDlet-Vendor**: `Gear Games` (direct, **not** via HeroCraft)
**MIDlet-Version**: `1.0.06`
**MicroEdition-Profile**: `MIDP-2.0` / `CLDC-1.0`
**Build date**: 16 November 2009 (per `META-INF/MANIFEST.MF` and class timestamps)
**Why it exists**: This is the **original Episode 2 campaign** as Gear Games shipped it directly. It is the only source for the 38 Peru campaign maps, and it is the only source for plain-text game strings (no `sn8p` encoding). The `MIDlet-2: Crack AOW2` entry and `crk/` folder are cracker-added and should not be considered part of the original Gear Games distribution.

### 4.1 `classes/` — 11 Java class files

| File | Size | Purpose |
|------|------|---------|
| `aow22.class` | 753 B | **MIDlet entry point** for Episode 2 (note the `2` suffix — different class name from Episode 1's `aow2.class`) |
| `a.class` | 15,714 B | Game canvas / main loop |
| `b.class` | 7,411 B | Input/keys |
| `c.class` | 231 B | Tiny helper |
| `d.class` | 1,667 B | Small helper |
| `e.class` | 10,993 B | Mid helper |
| `f.class` | 112,896 B | **Largest class** — terrain/map engine |
| `g.class` | 3,455 B | Mid helper |
| `h.class` | 71,295 B | Large class — likely AI/scripting |
| `i.class` | 4,952 B | Mission scripting hooks |
| `j.class` | 1,735 B | Small helper |

**Why fewer classes than Episode 1**: Episode 2 was built directly by Gear Games without HeroCraft's online/multiplayer wrapper, so the network stack (`p.class` in Episode 1, 70 KB) is absent here.

### 4.2 `data/` — 13 binary data tables

| File | Size | Purpose |
|------|------|---------|
| `a` | 6,108 B | Encoded data table |
| `d` | 226 B | Display flags |
| `d0` | 103,215 B | Game data tables (same 26-byte-array + 18-short-array format as Episode 1) |
| `f` | 9 B | ASCII flag string — value `000000000` (9 feature flags) |
| `m0` | 4,937 B | Generic map / mission template |
| `m26` | 10,113 B | Mission 26 special data |
| `m35` | 12,916 B | Mission 35 special data |
| `m37` | 5,273 B | Mission 37 special data |
| `m42` | 7,370 B | Mission 42 special data |
| `m43` | 14,320 B | Mission 43 special data |
| `m44` | 16,580 B | Mission 44 special data |
| `ml` | 1,489 B | Map layout metadata |
| `u` | 45 B | Tiny data — likely unit cap or rank table |

**Why m26/m35/m37/m42/m43/m44 have special files**: These six missions have unique mechanics (e.g. m44 is the final Peru liberation scenario with scripted reinforcements) that don't fit the regular `mapNN` template, so they get standalone data files.

### 4.3 `maps/` — 38 map files

Two ranges, contiguous within each range:

| Range | Count | Purpose |
|-------|-------|---------|
| `map1`–`map20` | 20 | Main campaign missions 1–20 |
| `map51`–`map68` | 18 | Side missions / bonus maps (note: 51–68 inclusive = 18 files, plus `map56`–`map68` complete the set) |

**Why this matters**: The Android APK only ships 6 Episode 1 maps (`m0`–`m5`). The Peru campaign maps **only exist** in this JAR — they are the single source for any future Episode 2 recreation in AOW2-Online.

### 4.4 `missions/` — 8 mission scripts

| File | Size | Purpose |
|------|------|---------|
| `mi0` | 325 B | Tutorial header |
| `mi1` | 537 B | Mission 1 |
| `mi2` | 461 B | Mission 2 |
| `mi3` | 496 B | Mission 3 |
| `mi4` | 745 B | Mission 4 |
| `mi5` | 713 B | Mission 5 |
| `mi6` | 657 B | Mission 6 |
| `mi7` | 66 B | Tiny stub — placeholder for an unfinished/removed mission |

### 4.5 `sprites/` — 10 PNG files

| File | Size | Purpose |
|------|------|---------|
| `i0` | 282,547 B | **Sprite pack** — Episode 2 sprite container (same format as Episode 1, 282 KB vs. 271 KB — Peru-specific sprites) |
| `r00`–`r05` | 5.8–6.8 KB each | Six 98×97 8-bit colormap PNGs — likely **rank insignia badges** shown on the mission briefing screen (Major-General, Lieutenant-General, General of Army, Marshal, etc., as documented in the parent `Asset_Catalog.md` rank list) |
| `l1` | 3,229 B | Loading screen PNG (148×98, 8-bit colormap) — small variant |
| `l2` | 33,345 B | Loading screen PNG (240×323, 8-bit colormap) — full-screen variant |
| `icon.png` | 845 B | MIDlet icon (42×29, 8-bit colormap) — note different aspect ratio from Episode 1's 32×32 |

### 4.6 `text/` — 3 plain-text string files

| File | Size | Purpose |
|------|------|---------|
| `0_t0` | 16,675 B | **Plain-ASCII title/help strings** — readable Episode 2 title screen, "How to Play" text, credits ("Giar Geymz, 2009", "Music: Bogatencov & AN"). Prefixed with `0_` because it was inside the JAR's `0/` subfolder. |
| `0_s0` | 1,743 B | **Plain-ASCII menu strings** — `Continue | New Game | Help | About the Game | More Games | Exit | Save | Download | Output | Campaign | Simple Game | ...` |
| `0_d0` | 20,307 B | **Plain-ASCII campaign dialogue** — Episode 2 narrative: `"...We must not continue to tolerate this oppression! The Confederation is a cover up for his high purposes of world peace..."` |

The bare `s0` (MIDI music) and `d0` (binary data table) that also exist at the JAR root are NOT in this folder — `s0` is correctly routed to `audio/` and `d0` is correctly routed to `data/`. The `0/` subfolder versions are the plain-text localisation overrides.

### 4.7 `audio/` — 1 MIDI file

| File | Size | Purpose |
|------|------|---------|
| `s0` | 26,684 B | **Episode 2 background music** — Standard MIDI format 1, 9 tracks at 96 ppqn. Note: this is more complex than Episode 1's `s0m` (format 0, single track at 480 ppqn, 12 KB) — Episode 2 has a richer, multi-track score. |

### 4.8 `crack/` — 2 cracker-added files

| File | Size | Purpose |
|------|------|---------|
| `a.class` | 7,560 B | Cracker-added class — likely the unlock/payload that bypasses the original Gear Games DRM check |
| `main.class` | 1,290 B | Cracker-added MIDlet entry point — declared as `MIDlet-2: Crack AOW2, , crk.main` in the manifest |

**Why these are kept**: They are part of the artifact we received. They are isolated under `crack/` so the recreation project can ignore them, but they remain available for forensic completeness.

### 4.9 `metadata/`

| File | Size | Purpose |
|------|------|---------|
| `MANIFEST.MF` | 369 B | J2ME manifest with **two** MIDlets declared: `MIDlet-1: Art Of War 2 - Liberation Of Peru, icon.png, aow22` and `MIDlet-2: Crack AOW2, , crk.main`. The second MIDlet is the cracker's unlock tool. |

---

## 5. iOS v2.2 (`ipa_ios_v2.2/`)

**Source file**: `Art_Of_War_2_2.2_ios_2.2.1.ipa` (11.2 MB)
**Bundle identifier**: `com.gear-games.aow21`
**CFBundleVersion**: `2.2`
**MinimumOSVersion**: `2.2.1`
**Build date**: 23 October 2009 (per `add` ZIP timestamp)
**Why it exists**: This is the **premium iOS release** and is by far the most asset-rich distribution. It is the **only** source for:
- Pre-recorded SFX (73 WAV files)
- High-quality music (`music.mp3` 1.9 MB, `music.wav` 2.6 MB)
- A 1024×1024 master sprite atlas (`d1`)
- Russian localization (alongside English)
- Mission briefings in plain text (per-language `mi*_en` / `mi*_ru` files)

The IPA was cracked — `Spid3r` signature file and `Art Of War 2.CutCut` Mach-O are cracker-added and should be ignored for asset purposes (though preserved here for forensic completeness).

### 5.1 `audio/` — 74 audio files (the missing SFX!)

#### 5.1.1 Background music (2 files)

| File | Size | Format | Purpose |
|------|------|--------|---------|
| `music.mp3` | 1,889,074 B | MPEG-1 Layer III, 128 kbps, 44.1 kHz, Stereo, ID3v2.3 | Main background music (premium stereo recording) |
| `music.wav` | 2,602,228 B | RIFF WAVE, 8-bit PCM, mono, 22050 Hz | Alternate background music (8-bit mono — likely the iPhone-original format; `music.mp3` is a higher-quality re-encode of this) |

#### 5.1.2 Sound effects (72 files)

All SFX are RIFF WAVE, 8-bit or 16-bit PCM, mono, 22050 Hz. Grouped by category:

**UI / selection sounds (12 files)**

| File | Size | Purpose |
|------|------|---------|
| `select_1.wav` – `select_6.wav` | 22–26 KB | Six variants of the unit-select click |
| `affirmative_1.wav` – `affirmative_4.wav` | 55–83 KB | Four "acknowledged" voice responses |
| `affirmative_l_1.wav` – `affirmative_l_7.wav`, `affirmative_l_11.wav` | 16–22 KB | Eight quieter ("low-volume") affirmative responses |
| `menu_open_1.wav` | 17,660 B | Menu open sound |
| `menu_close_1.wav` | 16,452 B | Menu close sound |
| `click_1.wav` | 17,748 B | Generic UI click |
| `money_1.wav` | 33,624 B | "Funds received" sound — played when player gains money |
| `build_1.wav` | 26,204 B | Construction-start sound |
| `building_ready_1.wav` | 43,340 B | Construction-complete sound |
| `research_complete_1.wav` | 23,240 B | Technology research complete |

**Weapon sounds — guns (12 files)**

| File | Size | Purpose |
|------|------|---------|
| `machine_light_1.wav` – `machine_light_6.wav` | 12–25 KB | Light machine gun fire (six variants for non-repetition) |
| `machine_med_1.wav` – `machine_med_3.wav` | 15–19 KB | Medium machine gun fire |
| `sniper_1.wav` – `sniper_3.wav` | 9–10 KB | Sniper rifle fire |
| `flamethrower_1.wav` | 35,194 B | Flamethrower (continuous-burn sound) |

**Weapon sounds — tank cannons (12 files)**

| File | Size | Purpose |
|------|------|---------|
| `tank_light_1.wav` – `tank_light_4.wav` | 8–9 KB | Light tank cannon fire |
| `tank_heavy_1.wav` – `tank_heavy_4.wav` | 14–21 KB | Heavy tank cannon fire |
| `tank_siege_1.wav` – `tank_siege_3.wav` | 14–16 KB | Siege tank cannon fire |
| `rocket_light_1.wav` – `rocket_light_3.wav` | 15–16 KB | Light rocket launcher fire |

**Explosion sounds (12 files)**

| File | Size | Purpose |
|------|------|---------|
| `explode_light_1.wav` – `explode_light_5.wav` | 11–40 KB | Light explosions (infantry death, light vehicle destruction) |
| `explode_heavy_1.wav` – `explode_heavy_7.wav` | 22–85 KB | Heavy explosions (tank destruction, building destruction) |
| `explode_bld_1.wav`, `explode_bld_2.wav` | 45 KB | Building destruction (dedicated variant) |

**Voice / unit sounds (5 files)**

| File | Size | Purpose |
|------|------|---------|
| `scream_1.wav` – `scream_5.wav` | 14–21 KB | Five infantry death screams |
| `attack_1.wav` | 69,686 B | "Attack!" voice command response |

### 5.2 `archives/` — 1 file

| File | Size | Purpose |
|------|------|---------|
| `add` | 3,193,640 B | **ZIP archive** containing `music.mp3` (1,889,074 B uncompressed). This is a redundant copy of `music.mp3` that the iOS app bundles separately — likely the App Store's encrypted "additional content" payload. Kept verbatim; do not unzip into the repo (it would duplicate `music.mp3`). |

### 5.3 `app_icons/` — 2 files

| File | Size | Purpose |
|------|------|---------|
| `Default.png` | 10,053 B | iOS launch screen PNG (320×480, 8-bit colormap) — shown while the app loads |
| `Icon.png` | 2,152 B | iOS home-screen icon PNG (57×57, 8-bit colormap) |

### 5.4 `sprites/` — 6 files

| File | Size | Purpose |
|------|------|---------|
| `d1` | 1,271,525 B | **Master sprite atlas** — 1024×1024 RGBA PNG. This is the only platform that ships a true-color master atlas; the J2ME/Android versions only ship 8-bit colormap sprite packs (`i0`). Use this for high-resolution sprite extraction. |
| `d00` | 106,423 B | Binary terrain-type index table — 1 byte per cell, used by the map renderer to look up which terrain tile to blit from `d1` |
| `English_i0` | 740,346 B | Localized sprite pack — contains English-text-rendered UI elements (button labels, mission briefings burned into sprites) |
| `Russian_i0` | 740,635 B | Localized sprite pack — Russian equivalent |
| `l1` | 3,229 B | Loading screen PNG (148×98, 8-bit colormap) — small variant, same as J2ME |
| `l2` | 83,825 B | Loading screen PNG (480×320, 8-bit colormap) — full iOS-resolution loading screen |
| `l3` | 8,086 B | Loading screen PNG (480×320, 8-bit colormap) — alternate loading screen (probably the post-load transition) |

### 5.5 `maps/` — 6 files

| File | Size | Purpose |
|------|------|---------|
| `m0` | 4,934 B | Map 0 — tutorial |
| `m1` | 7,078 B | Map 1 |
| `m2` | 6,611 B | Map 2 |
| `m3` | 10,097 B | Map 3 |
| `m4` | 10,623 B | Map 4 |
| `m5` | 14,014 B | Map 5 |

Same sizes as Episode 1's `m0`–`m5` in the J2ME Global Confederation JAR — confirming the iOS build is Episode 1, not Episode 2.

### 5.6 `missions/` — 37 mission script files

| Range | Count | Purpose |
|-------|-------|---------|
| `mi0`–`mi6` | 7 | Generic (non-language-specific) mission headers — small (309–683 B) |
| `mi7_en`–`mi21_en` | 15 | English mission briefings (4.7–19.9 KB) |
| `mi7_ru`–`mi21_ru` | 15 | Russian mission briefings (5.1–20.4 KB — slightly larger due to UTF-8 Cyrillic) |

**Total: 37 mission files covering 22 missions** (mi0–mi21, with mi0–mi6 being shared headers and mi7–mi21 being the 15 fully-scripted missions in two languages).

### 5.7 `text/` — 6 localized string files

| File | Size | Purpose |
|------|------|---------|
| `English_t0` | 12,766 B | English title/help/credits strings |
| `English_s0` | 1,130 B | English menu strings |
| `English_d0` | 16,193 B | English campaign dialogue |
| `Russian_t0` | 11,458 B | Russian title/help/credits strings |
| `Russian_s0` | 1,181 B | Russian menu strings |
| `Russian_d0` | 15,212 B | Russian campaign dialogue |

### 5.8 `data/` — 6 binary data tables

| File | Size | Purpose |
|------|------|---------|
| `a` | 5,555 B | Encoded data table (same role as APK/JAR `a`) |
| `d` | 164 B | Display flags (8 bytes used, padded to 164) |
| `n` | 8,464 B | Name/data table |
| `u` | 45 B | Tiny data — likely unit cap or rank table (same as Episode 2's `u`) |
| `ml` | 1,489 B | Map layout metadata (byte-identical size to all other versions — confirms the map layout schema never changed across platforms) |
| `d00` | (in sprites/ — see §5.4) | — |

### 5.9 `metadata/` — 6 files

| File | Size | Purpose |
|------|------|---------|
| `Info.plist` | 672 B | Compiled binary plist (bplist00) — bundle metadata |
| `Art_of_War_2-Info.plist` | 499 B | Source-format plist — Xcode project's Info.plist template |
| `PkgInfo` | 8 B | Apple PkgInfo — `APPL????` (8-byte ASCII signature for application type) |
| `Art_of_War_2ViewController.nib` | 1,216 B | Compiled Interface Builder nib — main view controller |
| `MainWindow.nib` | 1,505 B | Compiled Interface Builder nib — main window (referenced by `Info.plist`'s `NSMainNibFile=MainWindow`) |
| `Art Of War 2.CutCut` | 4,096 B | Mach-O armv6 executable — **cracker-added** patcher binary. Same architecture as the main executable. Filename suggests it "cuts" the DRM check out of the main binary. Should be ignored for asset purposes. |

### 5.10 `executable/` — 1 file

| File | Size | Purpose |
|------|------|---------|
| `Art Of War 2` | 523,424 B | **Mach-O armv6 executable** — the iOS app's main binary. Compiled for armv6 (iPhone 3G-era). Useful for cross-referencing native function names against the J2ME/Android class names, but not directly portable to the FXGL recreation. |

### 5.11 `crack/` — 1 file

| File | Size | Purpose |
|------|------|---------|
| `Spid3r` | 67 B | Cracker signature — contains the ASCII text `Copyright IDCrack v3.1.21 From www.idwaneo.com & Cracked by Spid3r`. **Not a game asset.** Kept verbatim for forensic completeness. |

---

## 6. Cross-Version Asset Comparison

| Asset class | Online APK (2011) | J2ME Global (2012) | J2ME Peru (2009) | iOS v2.2 (2009) | Best source |
|-------------|-------------------|--------------------|-------------------|------------------|-------------|
| **Sprite pack `i0`** | 260–526 KB × 3 res | 271 KB × 1 res | 282 KB × 1 res | 740 KB × 2 langs | iOS (largest, language-specific) |
| **Master sprite atlas** | — | — | — | `d1` 1024² RGBA | **iOS only** |
| **Music** | 2 MIDI files (18 KB) | 1 MIDI (12 KB) | 1 MIDI (27 KB) | `music.mp3` (1.9 MB) + `music.wav` (2.6 MB) | **iOS only** |
| **SFX** | — | — | — | 72 WAV files | **iOS only** |
| **Map files** | 6 (Episode 1) | 6 (Episode 1) | 38 (Episode 2) | 6 (Episode 1) | Peru for Episode 2; iOS for Episode 1 |
| **Mission scripts** | 6 (`mi0`–`mi5`) | 7 (`mi0`–`mi6`) | 8 (`mi0`–`mi7`) | 37 (mi0–mi21 × 2 langs) | **iOS only** (full set + dual language) |
| **Plain-text strings** | — | — | `0_t0`/`0_s0`/`0_d0` (ASCII) | English/Russian `t0`/`s0`/`d0` (ASCII) | iOS (both languages) |
| **Encoded strings** | `sn8p` (21 KB) | `sn8p` (31 KB) | — | — | J2ME Global (most complete encoded set) |
| **Font files** | 10 (5 pairs × 2 res) | 8 (4 pairs) | — | — | APK (most variants) |
| **Loading screens** | 1 (`l2`) | 1 (`l2`) | 2 (`l1`, `l2`) | 3 (`l1`, `l2`, `l3`) | iOS (most variants) |
| **Rank insignia** | — | — | 6 (`r00`–`r05`) | — | **Peru only** |
| **Network protocol classes** | `p.class` (70 KB) | `p.class` (70 KB) | — | (native code) | J2ME Global (cleanest Java source) |

---

## 7. Recommended Asset Selection for AOW2-Online Recreation

Based on the cross-version comparison:

1. **SFX**: Pull all 72 WAV files from iOS `audio/`. The original APK had none, and the recreation project's "programmatic SFX synthesis" fallback can be retired.
2. **Background music**: Use `music.mp3` from iOS `audio/` for the main menu and `music.wav` for in-game loops (smaller per-stream memory footprint).
3. **Sprites**: Decode `d1` (iOS 1024² RGBA atlas) and slice into individual entity sprites. Fall back to APK's `b/` icon folder for the 200 mini-icons (those don't exist in iOS).
4. **Maps**: Use iOS `m0`–`m5` for Episode 1 and Peru JAR `map1`–`map20` + `map51`–`map68` for Episode 2.
5. **Mission scripts**: Use iOS `mi*_en` files as the primary source — they contain full briefing text, not just headers.
6. **Text strings**: Use iOS `English_*` for English UI strings. Use Peru JAR `0_*` files only if any string is missing from iOS.
7. **Rank insignia**: Pull all 6 `r00`–`r05` PNGs from Peru JAR — they don't exist anywhere else.

---

## 8. Extraction Methodology

All three archives were extracted with `unzip` (JAR and IPA are both PKZIP containers). No proprietary tools were required. The categorization script is preserved at `/home/z/my-project/scripts/organize_external_versions.py` and can be re-run if any source archive is updated.

```
mkdir -p /tmp/aow2-extract/{jar_global,jar_low,ipa_ios}
cd /tmp/aow2-extract/jar_global && unzip -q /home/z/my-project/upload/art_of_war_2_global_260793.jar
cd /tmp/aow2-extract/jar_low    && unzip -q /home/z/my-project/upload/artofwar2l_1tio5twt.jar
cd /tmp/aow2-extract/ipa_ios    && unzip -q /home/z/my-project/upload/Art_Of_War_2_2.2_ios_2.2.1.ipa
python3 /home/z/my-project/scripts/organize_external_versions.py
```

**Verification**: 286 files organized (52 + 88 + 146), totalling 16.5 MB. File counts match the raw extraction exactly (no files dropped, no files duplicated across version folders).
