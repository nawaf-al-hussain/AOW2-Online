# Art of War 2 Online - Complete Reverse Engineering Project
## File Inventory

> **NEW (2026-06-28):** Asset development guide available at
> [`ASSET_DEVELOPMENT_GUIDE.md`](ASSET_DEVELOPMENT_GUIDE.md) — single source
> of truth for asset work status, next steps, and re-running scripts.

### Project Structure
```
/project
├── FILE_INVENTORY.md          - This file
├── original_apk/
│   └── art-of-war-2-online.apk - Original APK (2.3 MB)
├── decompiled/
│   ├── raw/                    - Raw APK extraction
│   ├── apktool_decoded/        - apktool decoded resources
│   └── jadx_output/            - jadx decompiled Java source
├── source_original/            - Original obfuscated source (preserved)
├── assets_raw/                 - Raw extracted assets (256 files)
├── assets_processed/           - Categorized and identified assets (405 files)
├── external_versions/          - ⭐ NEW: Non-APK distributions (286 files, 16.5 MB)
│   ├── EXTERNAL_VERSIONS.md                - Per-file reference for all 286 files
│   ├── jar_global_confederation_v1.12.0/   - J2ME Episode 1 (HeroCraft 2012, 52 files)
│   │   ├── classes/      (18 .class files — pre-Android Java source)
│   │   ├── data/         (11 binary data tables: a, d, d0, dmt, m0-m5, ml)
│   │   ├── fonts/        (8 font files: f0_0/f0_0p, f1_0/f1_0p, f2_0/f2_0p, f3_0/f3_0p)
│   │   ├── audio/        (1 MIDI: s0m)
│   │   ├── missions/     (7 mission scripts: mi0-mi6)
│   │   ├── sprites/      (5 PNG: i0 sprite pack, hc HeroCraft logo, gg minimap, l2 loading, icon.png)
│   │   ├── text/         (1 sn8p encoded text/string blob)
│   │   └── metadata/     (1 MANIFEST.MF)
│   ├── jar_liberation_of_peru_v1.0.06/    - J2ME Episode 2 (Gear Games 2009, 88 files)
│   │   ├── classes/      (11 .class files — Episode 2 entry point aow22.class)
│   │   ├── data/         (14 binary data tables: a, d, d0, f, m0, m26, m35, m37, m42, m43, m44, ml, n, u)
│   │   ├── audio/        (1 MIDI file: s0 — Episode 2 background music, format 1 / 9 tracks)
│   │   ├── maps/         (38 map files: map1-20 + map51-68 — ONLY source for Episode 2)
│   │   ├── missions/     (8 mission scripts: mi0-mi7)
│   │   ├── sprites/      (10 PNG: i0 sprite pack, r00-r05 rank insignia, l1, l2, icon.png)
│   │   ├── text/         (3 plain-ASCII string files: 0_t0, 0_s0, 0_d0 — readable game strings!)
│   │   ├── crack/        (2 cracker-added .class files in crk/ subfolder of original JAR)
│   │   └── metadata/     (1 MANIFEST.MF with MIDlet-1 + MIDlet-2 Crack AOW2 entries)
│   └── ipa_ios_v2.2/                       - iOS premium build (Gear Games 2009, 146 files)
│       ├── audio/        (74 files: 72 SFX .wav + music.mp3 + music.wav — RAW, ONLY source for SFX)
│       ├── audio_ogg/    (74 files: 72 SFX OGG + music.ogg + inventory.json — DECODED for FXGL)
│       │   ├── sfx/      (72 OGG files at 96 kbps VBR, 44100 Hz stereo — 2.1× compression vs WAV)
│       │   ├── music/    (1 OGG: music.ogg at 160 kbps VBR from music.mp3)
│       │   └── inventory.json
│       ├── archives/     (1 file: add = ZIP archive containing duplicate music.mp3)
│       ├── app_icons/    (2 files: Default.png + Icon.png)
│       ├── sprites/      (6 files: d1 1024×1024 master atlas + 2 localized i0 packs + d00 + l1, l2, l3 — RAW)
│       ├── sprites_decoded/ (95 files: 90 PNGs + 3 loading screens + 1 inventory.json + 1 atlas — DECODED for FXGL)
│       │   ├── d1_master_atlas.png   (1024×1024 RGBA — renamed copy of d1)
│       │   ├── english/              (45 PNGs: en_000.png – en_044.png, unpacked from English_i0)
│       │   ├── russian/              (45 PNGs: ru_000.png – ru_044.png, unpacked from Russian_i0)
│       │   ├── loading_screen_small.png  (renamed l1)
│       │   ├── loading_screen_full.png   (renamed l2)
│       │   ├── loading_screen_alt.png    (renamed l3)
│       │   └── inventory.json
│       ├── maps/         (6 files: m0-m5)
│       ├── missions/     (37 files: mi0-mi6 generic + mi7_en–mi21_en + mi7_ru–mi21_ru)
│       ├── text/         (6 files: English_t0/s0/d0 + Russian_t0/s0/d0 — ONLY source for Russian)
│       ├── data/         (6 files: a, d, n, u, ml, d00)
│       ├── metadata/     (6 files: Info.plist, Art_of_War_2-Info.plist, PkgInfo, 2 .nib, Art Of War 2.CutCut)
│       ├── executable/   (1 file: Art Of War 2 — Mach-O armv6 binary, 523 KB)
│       └── crack/        (1 file: Spid3r signature from idwaneo.com)
├── documentation/              - ⭐ PRIMARY OUTPUT
│   ├── MASTER_DOCUMENTATION.md           - Complete knowledge base (3,330 lines)
│   ├── ArtOfWar3_Recreation_Blueprint.md - Recreation guide (2,283 lines)
│   ├── source_analysis.md                - Source code analysis
│   ├── Asset_Catalog.md                  - Asset inventory (now includes §11 External Versions)
│   ├── Knowledge_Graph_Documentation.md  - Knowledge graph docs
│   ├── reverse_engineering_roadmap.md    - Investigation roadmap
│   ├── class_mapping.json                - Obfuscated→deobfuscated mapping
│   ├── knowledge_graph.json              - Machine-readable knowledge graph
│   └── diagrams/                         - All visualizations
│       ├── system_architecture.png       - System architecture diagram
│       ├── unit_comparison.png           - Unit stats comparison chart
│       ├── tech_tree.png                 - Tech tree visualization
│       ├── combat_flow.png               - Combat system flowchart
│       ├── network_protocol.png          - Network protocol diagram
│       ├── dashboard.png                 - RE dashboard overview
│       ├── data_flow.png                 - Data flow diagram
│       ├── class_dependency.dot          - Graphviz class dependency graph
│       ├── architecture_dependency_graph.md  - Mermaid dependency graph
│       ├── class_relationship_map.md     - Mermaid class relationships
│       ├── game_runtime_flow.md          - Mermaid runtime flow
│       ├── unit_state_machine.md         - Mermaid unit states
│       ├── network_session_lifecycle.md  - Mermaid network lifecycle
│       ├── data_flow.md                  - Mermaid data flow
│       ├── combat_system_flow.md         - Mermaid combat flow
│       ├── tech_tree_confederation.md    - Mermaid confed tech tree
│       ├── tech_tree_rebels.md           - Mermaid rebels tech tree
│       └── system_map.md                 - Mermaid system map
├── gameplay_analysis/          - Game mechanics analysis
│   ├── unit_stats.md           - Unit encyclopedia
│   ├── building_stats.md       - Building encyclopedia
│   ├── combat_formulas.md      - ALL combat formulas
│   ├── ai_analysis.md          - AI behavior documentation
│   ├── pathfinding.md          - Pathfinding algorithm docs
│   ├── campaign_guide.md       - Campaign documentation
│   ├── map_system.md           - Map format documentation
│   ├── decryption_algorithm.md - Data encryption docs
│   ├── complete_unit_stats.json - Full unit data (machine-readable)
│   ├── complete_building_stats.json - Full building data
│   ├── decrypted_data.json     - All decrypted game data (3.76 MB)
│   ├── game_data.json          - Extracted numeric constants
│   └── text_strings.json       - 567 decoded text strings
├── network_analysis/           - Multiplayer protocol analysis
│   ├── protocol_specification.md   - Complete protocol spec (34 msg types)
│   ├── multiplayer_architecture.md - MP system architecture
│   ├── session_lifecycle.md        - Session flow documentation
│   └── packet_formats.json         - Machine-readable packet formats
├── database_analysis/          - Save/persistence system
│   └── save_system.md          - Save format documentation
└── wiki_research/              - Community knowledge
    ├── research_results.md     - Web research findings
    └── game_data.json          - Community-sourced game data
```

### Why the `external_versions/` folder exists

The Online APK (HeroCraft 2011, 2.3 MB) is a downstream Android port of a 2009–2010 Gear Games J2ME title. Android's resource pipeline stripped several asset classes that the AOW2-Online recreation project needs:

- **Pre-recorded SFX** (gunfire, screams, UI clicks, explosions) — only the iOS build ships these (72 WAV files, also converted to 72 OGG files under `audio_ogg/`)
- **Full-quality music** — the APK only had two 10 KB MIDI files; iOS ships `music.mp3` (1.9 MB) and `music.wav` (2.6 MB), also converted to `music.ogg` under `audio_ogg/`
- **Episode 2 — Liberation of Peru** campaign (38 maps) — only the Peru JAR ships these
- **Plain-text game strings** — the APK encodes them in `sn8p` binary; the Peru JAR and iOS build both ship readable ASCII
- **1024×1024 master sprite atlas** — only the iOS build ships a true-color atlas (`d1`)
- **Pre-sliced individual sprites** — only the iOS build ships pre-sliced sprites in its `i0` containers (90 sprites unpacked under `sprites_decoded/`)
- **Russian localization** — only the iOS build ships Russian text and localized sprites
- **Episode 1 pre-Android form** — the J2ME Global Confederation JAR is the canonical pre-port source

See:
- `external_versions/EXTERNAL_VERSIONS.md` for the raw per-file reference
- `external_versions/ipa_ios_v2.2/DECODED_ASSETS.md` for the decoded/converted assets reference (FXGL-ready)
- `documentation/Asset_Catalog.md` §11 (especially §11.6) for the cross-version asset selection guide

### Key Statistics
- **Total Java classes decompiled**: 185 (87 main + 28×3 resolution variants) — Online APK only
- **Total assets cataloged**: 256 raw → 405 processed — Online APK only
- **External version files cataloged**: 286 files (52 + 88 + 146) across 3 non-APK distributions
- **External version total size**: 16.5 MB (988 KB + 1.5 MB + 14 MB)
- **Decoded iOS sprites**: 90 PNGs (45 English + 45 Russian) + 3 loading screens + 1 master atlas = 95 files
- **Converted iOS audio**: 73 OGG files (72 SFX + 1 music) + 1 inventory JSON = 74 files
- **Unit types**: 14 (7 Confederation + 7 Rebels) + 3 mines
- **Building types**: 16 (8 per faction)
- **Technologies**: 16 (8 per faction) + 32 asymmetric research effects
- **Network message types**: 34 identified
- **Decoded text strings**: 567 (Online APK) + plain-ASCII strings from Peru JAR and iOS build
- **Map records**: 193 (Online APK) + 38 Peru Episode 2 maps + 6 iOS Episode 1 maps
- **SFX files**: 0 (Online APK) → 72 WAV files (iOS raw) → 72 OGG files (iOS decoded for FXGL)
- **Master documentation**: 3,330 lines
- **Recreation blueprint**: 2,283 lines
