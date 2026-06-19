# Art of War 2 Online - Complete Reverse Engineering Project
## File Inventory

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
├── documentation/              - ⭐ PRIMARY OUTPUT
│   ├── MASTER_DOCUMENTATION.md           - Complete knowledge base (3,330 lines)
│   ├── ArtOfWar3_Recreation_Blueprint.md - Recreation guide (2,283 lines)
│   ├── source_analysis.md                - Source code analysis
│   ├── Asset_Catalog.md                  - Asset inventory
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

### Key Statistics
- **Total Java classes decompiled**: 185 (87 main + 28×3 resolution variants)
- **Total assets cataloged**: 256 raw → 405 processed
- **Unit types**: 14 (7 Confederation + 7 Rebels) + 3 mines
- **Building types**: 16 (8 per faction)
- **Technologies**: 16 (8 per faction) + 32 asymmetric research effects
- **Network message types**: 34 identified
- **Decoded text strings**: 567
- **Map records**: 193
- **Master documentation**: 3,330 lines
- **Recreation blueprint**: 2,283 lines
