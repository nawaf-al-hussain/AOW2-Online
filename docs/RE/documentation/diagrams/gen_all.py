#!/usr/bin/env python3
"""Generate all visualization diagrams for Art of War 2 Online reverse-engineering project."""

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np
import os

OUT_DIR = "/home/z/my-project/project/documentation/diagrams"
DPI = 150

# ─── Global Style ───
plt.rcParams.update({
    'figure.facecolor': '#1a1a2e',
    'axes.facecolor': '#16213e',
    'text.color': '#e0e0e0',
    'axes.labelcolor': '#e0e0e0',
    'xtick.color': '#b0b0b0',
    'ytick.color': '#b0b0b0',
    'font.family': 'sans-serif',
    'font.size': 9,
})

# ═══════════════════════════════════════════════════════════════
# 1. SYSTEM ARCHITECTURE DIAGRAM
# ═══════════════════════════════════════════════════════════════
def system_architecture():
    fig, ax = plt.subplots(figsize=(20, 14))
    ax.set_xlim(0, 20)
    ax.set_ylim(0, 14)
    ax.set_aspect('equal')
    ax.axis('off')
    ax.set_title('Art of War 2 Online — System Architecture', fontsize=18, fontweight='bold', color='#00d4ff', pad=20)

    # Color map: Core=red, Gameplay=blue, Content=green, Multiplayer=purple
    COLORS = {
        'core':   ('#e74c3c', '#c0392b'),
        'gameplay': ('#3498db', '#2980b9'),
        'content':  ('#2ecc71', '#27ae60'),
        'multi':    ('#9b59b6', '#8e44ad'),
        'platform': ('#7f8c8d', '#6c7a7a'),
        'support':  ('#f39c12', '#d68910'),
    }

    def draw_box(ax, x, y, w, h, label, sublabel, cat):
        fc, ec = COLORS[cat]
        box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.15", facecolor=fc, edgecolor=ec, linewidth=2, alpha=0.85)
        ax.add_patch(box)
        ax.text(x+w/2, y+h/2+0.12, label, ha='center', va='center', fontsize=8, fontweight='bold', color='white')
        if sublabel:
            ax.text(x+w/2, y+h/2-0.2, sublabel, ha='center', va='center', fontsize=6, color='#ffffffcc', style='italic')

    def draw_arrow(ax, x1, y1, x2, y2, color='#aaaaaa', style='->', lw=1.2):
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                     arrowprops=dict(arrowstyle=style, color=color, lw=lw, connectionstyle="arc3,rad=0.05"))

    # ── Platform Layer (bottom) ──
    draw_box(ax, 0.5, 0.3, 3, 1.2, 'Android Activity', 'Application.java', 'platform')
    draw_box(ax, 4, 0.3, 3, 1.2, 'SurfaceView', 'an → h', 'platform')
    draw_box(ax, 7.5, 0.3, 3, 1.2, 'Canvas API', 'android.graphics', 'platform')
    draw_box(ax, 11, 0.3, 3, 1.2, 'Audio / Vibrator', 'MediaPlayer + vib', 'platform')
    draw_box(ax, 14.5, 0.3, 3, 1.2, 'SharedPreferences', 'Config storage', 'platform')

    # ── Core Engine ──
    draw_box(ax, 0.5, 2.2, 4.5, 1.4, 'AppCtrl (Main Thread)', 'Priority 10, Runnable loop', 'core')
    draw_box(ax, 5.5, 2.2, 4.5, 1.4, 'MIDlet Loader', 'bh → s0.aow2ol reflection', 'core')
    draw_box(ax, 10.5, 2.2, 4.5, 1.4, 'Screen Selector', 'bb.java: s0/s1/s2 by width', 'core')
    draw_box(ax, 15.5, 2.2, 3.5, 1.4, 'Save Manager', 'U()/V() RMS aow2olhc', 'core')

    # ── Game Engine ──
    draw_box(ax, 0.5, 4.5, 3.8, 1.4, 'Game Loop D()', '30 FPS, tick intervals', 'gameplay')
    draw_box(ax, 4.8, 4.5, 3.8, 1.4, 'Screen State Machine', 'aO: 0-125 states', 'gameplay')
    draw_box(ax, 9.1, 4.5, 3.8, 1.4, 'Input Handler', 'Touch/Key dispatch', 'gameplay')
    draw_box(ax, 13.4, 4.5, 3.2, 1.4, 'Game Mode', 'c:0=connect 1=pre 3=game', 'gameplay')

    # ── World System ──
    draw_box(ax, 0.3, 6.7, 3.0, 1.3, 'World State', '128×128 tile grid', 'content')
    draw_box(ax, 3.6, 6.7, 3.0, 1.3, 'Unit Manager', '100 units, ca[] array', 'content')
    draw_box(ax, 6.9, 6.7, 3.0, 1.3, 'Building Manager', 'constructionHP system', 'content')
    draw_box(ax, 10.2, 6.7, 3.0, 1.3, 'Projectile Manager', '400 max, velocity flight', 'content')
    draw_box(ax, 13.5, 6.7, 2.6, 1.3, 'Fog of War', 'Q[p][2][x32][y]', 'content')
    draw_box(ax, 16.5, 6.7, 2.6, 1.3, 'Spatial Hash', 'bk[p][8][8] buckets', 'content')

    # ── Combat System ──
    draw_box(ax, 0.3, 8.8, 2.8, 1.3, 'Range Check', '31×31 lookup table', 'core')
    draw_box(ax, 3.4, 8.8, 2.8, 1.3, 'Damage Calc', 'dmg=base×(10-arm)/10', 'core')
    draw_box(ax, 6.5, 8.8, 2.8, 1.3, 'Armour Calc', 'cf[2][type] + research', 'core')
    draw_box(ax, 9.6, 8.8, 2.8, 1.3, 'Weapon System', '3 weapons, cooldown', 'core')
    draw_box(ax, 12.7, 8.8, 3.0, 1.3, 'Splash System', 'Artillery type 10, radius', 'core')
    draw_box(ax, 16.0, 8.8, 3.2, 1.3, 'Death System', 'HP=-1, kill rewards', 'core')

    # ── AI System ──
    draw_box(ax, 0.3, 10.8, 3.2, 1.1, 'AI Decision Loop', 'control phase ac==0', 'gameplay')
    draw_box(ax, 3.8, 10.8, 3.2, 1.1, 'Target Search', 'spatial hash scan', 'gameplay')
    draw_box(ax, 7.3, 10.8, 3.2, 1.1, 'Path Planner', 'Bresenham 3-candidate', 'gameplay')
    draw_box(ax, 10.8, 10.8, 3.2, 1.1, 'Build Planner', 'Timed events, credits', 'gameplay')
    draw_box(ax, 14.3, 10.8, 3.2, 1.1, 'Siege / Garrison', 'Auto-siege, bunker', 'gameplay')

    # ── Economy System ──
    draw_box(ax, 0.3, 12.3, 2.8, 1.0, 'Credit System', 'Cap: 30,000', 'support')
    draw_box(ax, 3.4, 12.3, 2.8, 1.0, 'Income System', '127-tick cycle', 'support')
    draw_box(ax, 6.5, 12.3, 2.8, 1.0, 'Production Queue', 'K/L/M 60-slot', 'support')
    draw_box(ax, 9.6, 12.3, 2.8, 1.0, 'Research System', '48 research IDs', 'support')
    draw_box(ax, 12.7, 12.3, 2.5, 1.0, 'Power System', 'Generator radius', 'support')
    draw_box(ax, 15.5, 12.3, 3.5, 1.0, 'Build Cost System', '(base×mod)/10 × 20/(up+20)', 'support')

    # ── Multiplayer (right side) ──
    draw_box(ax, 17.0, 10.8, 2.5, 1.1, 'Network Manager', 'e.java, TCP socket', 'multi')
    draw_box(ax, 17.0, 8.8, 2.5, 1.3, 'Protocol Manager', 'XOR cipher + Base64', 'multi')
    draw_box(ax, 17.0, 4.5, 2.5, 1.4, 'HTTP Handler', 'aa.java, 3-phase', 'multi')
    draw_box(ax, 17.0, 6.7, 2.5, 1.3, 'Request Queue', '3-slot circular buffer', 'multi')

    # ── Render System ──
    draw_box(ax, 14.0, 4.5, 2.8, 1.4, 'Render Pipeline', 'k→x→bu→z→bv', 'content')

    # ── Arrows: Platform → Core ──
    draw_arrow(ax, 2.0, 1.5, 2.75, 2.2, '#e74c3c')
    draw_arrow(ax, 5.5, 1.5, 7.75, 2.2, '#e74c3c')
    draw_arrow(ax, 9.0, 1.5, 12.75, 2.2, '#e74c3c')

    # ── Arrows: Core → Game Engine ──
    draw_arrow(ax, 2.75, 3.6, 2.4, 4.5, '#3498db')
    draw_arrow(ax, 7.75, 3.6, 6.7, 4.5, '#3498db')
    draw_arrow(ax, 12.75, 3.6, 11.0, 4.5, '#3498db')

    # ── Arrows: Game Engine → World ──
    draw_arrow(ax, 2.4, 5.9, 1.8, 6.7, '#2ecc71')
    draw_arrow(ax, 6.7, 5.9, 5.1, 6.7, '#2ecc71')
    draw_arrow(ax, 11.0, 5.9, 8.4, 6.7, '#2ecc71')

    # ── Arrows: World → Combat ──
    draw_arrow(ax, 1.8, 8.0, 1.7, 8.8, '#e74c3c')
    draw_arrow(ax, 5.1, 8.0, 4.8, 8.8, '#e74c3c')
    draw_arrow(ax, 8.4, 8.0, 7.9, 8.8, '#e74c3c')

    # ── Arrows: Combat → AI ──
    draw_arrow(ax, 1.7, 10.1, 1.9, 10.8, '#3498db')
    draw_arrow(ax, 4.8, 10.1, 5.4, 10.8, '#3498db')

    # ── Arrows: AI → Economy ──
    draw_arrow(ax, 1.9, 11.9, 1.7, 12.3, '#f39c12')
    draw_arrow(ax, 5.4, 11.9, 4.8, 12.3, '#f39c12')

    # ── Arrows: Game Engine ↔ Network ──
    draw_arrow(ax, 16.6, 5.2, 17.0, 5.2, '#9b59b6')
    draw_arrow(ax, 17.0, 6.0, 16.6, 6.0, '#9b59b6')

    # ── Arrows: Network → Protocol ──
    draw_arrow(ax, 18.25, 10.8, 18.25, 10.1, '#9b59b6')

    # ── Arrows: Network ↔ Request Queue ──
    draw_arrow(ax, 18.25, 8.0, 18.25, 8.0, '#9b59b6')

    # Legend
    legend_items = [
        ('Core / Combat', COLORS['core'][0]),
        ('Gameplay / AI', COLORS['gameplay'][0]),
        ('Content / World', COLORS['content'][0]),
        ('Multiplayer', COLORS['multi'][0]),
        ('Support / Economy', COLORS['support'][0]),
        ('Platform', COLORS['platform'][0]),
    ]
    for i, (label, color) in enumerate(legend_items):
        ax.add_patch(FancyBboxPatch((0.5 + i*3.1, 13.2), 0.3, 0.3, boxstyle="round,pad=0.02", facecolor=color, edgecolor='white', linewidth=0.5))
        ax.text(0.9 + i*3.1, 13.35, label, va='center', fontsize=7, color='#e0e0e0')

    plt.tight_layout()
    fig.savefig(os.path.join(OUT_DIR, 'system_architecture.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ system_architecture.png")

# ═══════════════════════════════════════════════════════════════
# 2. UNIT COMPARISON CHART
# ═══════════════════════════════════════════════════════════════
def unit_comparison():
    # Confederation units (from master docs)
    confed_units = {
        'Infantry':    {'HP': 40, 'Damage': 2, 'Armor': 5, 'Speed': 5},
        'Grenadier':   {'HP': 40, 'Damage': 2, 'Armor': 5, 'Speed': 6},
        'Flame Asslt': {'HP': 50, 'Damage': 4, 'Armor': 5, 'Speed': 6},
        'AV-40 Fort':  {'HP': 50, 'Damage': 4, 'Armor': 5, 'Speed': 7},
        'T-21 Hammer': {'HP': 50, 'Damage': 8, 'Armor': 9, 'Speed': 7},
        'T-22 Zeus':   {'HP': 70, 'Damage': 6, 'Armor': 5, 'Speed': 7},
        'MLRS Torrent':{'HP': 80, 'Damage': 15,'Armor': 7, 'Speed': 4},
    }
    rebel_units = {
        'Infantry':    {'HP': 40, 'Damage': 2, 'Armor': 4, 'Speed': 6},
        'Grenadier':   {'HP': 40, 'Damage': 2, 'Armor': 4, 'Speed': 6},
        'Sniper':      {'HP': 40, 'Damage': 3, 'Armor': 6, 'Speed': 5},
        'Coyote':      {'HP': 45, 'Damage': 3, 'Armor': 6, 'Speed': 8},
        'Armadillo':   {'HP': 55, 'Damage': 4, 'Armor': 6, 'Speed': 7},
        'Rhino':       {'HP': 50, 'Damage': 6, 'Armor': 4, 'Speed': 7},
        'Porcupine':   {'HP': 60, 'Damage': 10,'Armor': 6, 'Speed': 5},
    }

    stats = ['HP', 'Damage', 'Armor', 'Speed']
    fig, axes = plt.subplots(2, 2, figsize=(16, 12))
    fig.suptitle('Unit Comparison: Confederation vs Resistance', fontsize=16, fontweight='bold', color='#00d4ff', y=0.98)

    for idx, stat in enumerate(stats):
        ax = axes[idx // 2][idx % 2]
        c_names = list(confed_units.keys())
        r_names = list(rebel_units.keys())
        c_vals = [confed_units[u][stat] for u in c_names]
        r_vals = [rebel_units[u][stat] for u in r_names]

        x = np.arange(max(len(c_names), len(r_names)))
        width = 0.35
        ax.bar(x - width/2, c_vals, width, label='Confederation', color='#e74c3c', alpha=0.85, edgecolor='#c0392b', linewidth=0.8)
        ax.bar(x + width/2, r_vals, width, label='Resistance', color='#3498db', alpha=0.85, edgecolor='#2980b9', linewidth=0.8)

        ax.set_title(stat, fontsize=13, fontweight='bold', color='#ffffff')
        ax.set_xticks(x)
        # Use union of names for labels
        all_names = c_names if len(c_names) >= len(r_names) else r_names
        ax.set_xticklabels(all_names, rotation=30, ha='right', fontsize=8)
        ax.legend(fontsize=8, loc='upper left', facecolor='#16213e', edgecolor='#555', labelcolor='#e0e0e0')
        ax.grid(axis='y', alpha=0.2, color='#555')

        # Add value labels on bars
        for i, v in enumerate(c_vals):
            ax.text(i - width/2, v + 0.5, str(v), ha='center', va='bottom', fontsize=7, color='#e0e0e0')
        for i, v in enumerate(r_vals):
            ax.text(i + width/2, v + 0.5, str(v), ha='center', va='bottom', fontsize=7, color='#e0e0e0')

    plt.tight_layout(rect=[0, 0, 1, 0.96])
    fig.savefig(os.path.join(OUT_DIR, 'unit_comparison.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ unit_comparison.png")

# ═══════════════════════════════════════════════════════════════
# 3. TECH TREE VISUALIZATION
# ═══════════════════════════════════════════════════════════════
def tech_tree():
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(24, 14))
    fig.suptitle('Tech Tree: Confederation (Left) vs Resistance (Right)', fontsize=16, fontweight='bold', color='#00d4ff', y=0.98)

    def draw_tech(ax, nodes, edges, title, faction_color):
        ax.set_xlim(-0.5, 6.5)
        ax.set_ylim(-1, 12)
        ax.axis('off')
        ax.set_title(title, fontsize=14, fontweight='bold', color=faction_color, pad=10)

        # Draw edges first
        for (n1, n2) in edges:
            x1, y1 = nodes[n1]['pos']
            x2, y2 = nodes[n2]['pos']
            ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                        arrowprops=dict(arrowstyle='->', color='#888888', lw=1.0, connectionstyle="arc3,rad=0.0"))

        # Draw nodes
        for name, info in nodes.items():
            x, y = info['pos']
            color = info.get('color', faction_color)
            box = FancyBboxPatch((x-0.55, y-0.3), 1.1, 0.6, boxstyle="round,pad=0.08",
                                  facecolor=color, edgecolor='white', linewidth=0.8, alpha=0.9)
            ax.add_patch(box)
            ax.text(x, y+0.05, name, ha='center', va='center', fontsize=6, fontweight='bold', color='white')
            if 'sub' in info:
                ax.text(x, y-0.15, info['sub'], ha='center', va='center', fontsize=4.5, color='#ffffffcc')

    # ── Confederation Tech Tree ──
    confed_nodes = {
        'R0':  {'pos': (0.5, 11), 'color': '#4caf50', 'sub': 'Infantry Arm+2'},
        'R1':  {'pos': (0, 9.5), 'color': '#4caf50', 'sub': 'Range Reduct /3'},
        'R2':  {'pos': (1, 9.5), 'color': '#4caf50', 'sub': 'Attack Speed -2'},
        'R3':  {'pos': (2, 9.5), 'color': '#4caf50', 'sub': 'Attack Dmg +2'},
        'R4':  {'pos': (5, 11), 'color': '#ff9800', 'sub': 'Build Arm +4'},
        'R5':  {'pos': (3.5, 11), 'color': '#ff9800', 'sub': 'Build Radius +1'},
        'R6':  {'pos': (0.5, 8), 'color': '#2196f3', 'sub': 'Rhino→Heavy Asslt'},
        'R7':  {'pos': (2, 8), 'color': '#2196f3', 'sub': 'Speed +5/+8'},
        'R8':  {'pos': (1.2, 6.5), 'color': '#2196f3', 'sub': 'Range -1, Rad+1'},
        'R9':  {'pos': (0, 5), 'color': '#4caf50', 'sub': 'Infantry Arm +2'},
        'R10': {'pos': (2.5, 5), 'color': '#f44336', 'sub': 'Range Reduct /3 P1'},
        'R11': {'pos': (0, 3.5), 'color': '#2196f3', 'sub': 'Atk Speed -2'},
        'R12': {'pos': (2, 3.5), 'color': '#2196f3', 'sub': 'Hammer→Scorpio'},
        'R13': {'pos': (1, 2), 'color': '#ff9800', 'sub': 'Build Radius +1'},
        'R14': {'pos': (1, 0.5), 'color': '#f44336', 'sub': 'Artillery Dmg +10'},
        'R15': {'pos': (4.5, 9.5), 'color': '#ff9800', 'sub': 'Supply Cap =8'},
        'R16': {'pos': (5.5, 9.5), 'color': '#ff9800', 'sub': 'Build Arm =9'},
        'R17': {'pos': (4, 8), 'color': '#9c27b0', 'sub': 'Unit Limit +2'},
        'R18': {'pos': (5.5, 8), 'color': '#ff9800', 'sub': 'Build Rad +1'},
        'R21': {'pos': (3.5, 6.5), 'color': '#ff9800', 'sub': 'Credit Limit=120'},
        'R22': {'pos': (4, 5), 'color': '#9c27b0', 'sub': 'Score Bonus=30'},
        'R23': {'pos': (4, 3.5), 'color': '#9c27b0', 'sub': 'Display Bonus=25'},
        'R43': {'pos': (3, 5), 'color': '#9c27b0', 'sub': 'Prod P[4]=7'},
        'R19': {'pos': (5, 6.5), 'color': '#9c27b0', 'sub': 'Prod P[1]=7'},
        'R20': {'pos': (6, 6.5), 'color': '#9c27b0', 'sub': 'Prod P[2]=7'},
    }
    confed_edges = [
        ('R0','R1'),('R0','R2'),('R0','R3'),('R2','R6'),('R3','R7'),
        ('R6','R8'),('R7','R8'),('R8','R9'),('R8','R10'),
        ('R9','R11'),('R9','R12'),('R11','R13'),('R12','R13'),
        ('R13','R14'),('R4','R16'),('R5','R17'),('R5','R18'),
        ('R15','R17'),('R15','R19'),('R15','R20'),
        ('R21','R22'),('R22','R23'),('R21','R43'),('R17','R22'),('R16','R18'),
    ]
    draw_tech(ax1, confed_nodes, confed_edges, 'Confederation (Player 0)', '#e74c3c')

    # ── Resistance Tech Tree ──
    rebel_nodes = {
        'R24': {'pos': (0.5, 11), 'color': '#4caf50', 'sub': 'Infantry Arm +1'},
        'R25': {'pos': (2, 11), 'color': '#f44336', 'sub': 'Range Reduct /3'},
        'R26': {'pos': (0, 9.5), 'color': '#4caf50', 'sub': 'Atk Speed +1'},
        'R27': {'pos': (1.5, 9.5), 'color': '#4caf50', 'sub': 'Atk Range -1'},
        'R28': {'pos': (0, 8), 'color': '#2196f3', 'sub': 'Coyote Range +1'},
        'R29': {'pos': (1.5, 7), 'color': '#ff9800', 'sub': 'Build Radius +1'},
        'R30': {'pos': (0.5, 5.5), 'color': '#2196f3', 'sub': 'Sniper Spd+2 Rng+2'},
        'R31': {'pos': (2.5, 5.5), 'color': '#2196f3', 'sub': 'Light Veh Speed+1'},
        'R32': {'pos': (1.5, 4), 'color': '#2196f3', 'sub': 'Heavy Range -1'},
        'R33': {'pos': (0.5, 2.5), 'color': '#4caf50', 'sub': 'Machinery Arm +1'},
        'R34': {'pos': (2.5, 2.5), 'color': '#f44336', 'sub': 'Range Reduct /3'},
        'R35': {'pos': (0, 1), 'color': '#2196f3', 'sub': 'Atk Speed -2'},
        'R36': {'pos': (1.5, 1), 'color': '#2196f3', 'sub': 'Mine Lizard Siege'},
        'R37': {'pos': (1, -0.5), 'color': '#ff9800', 'sub': 'Build Radius +1'},
        'R38': {'pos': (1, -1.5), 'color': '#f44336', 'sub': 'MLRS Dmg +2'},
        'R39': {'pos': (4.5, 11), 'color': '#ff9800', 'sub': 'Supply Cap =8'},
        'R40': {'pos': (5.5, 11), 'color': '#ff9800', 'sub': 'Build Arm =9'},
        'R41': {'pos': (5, 9.5), 'color': '#ff9800', 'sub': 'Build Radius +1'},
        'R42': {'pos': (5, 8), 'color': '#ff9800', 'sub': 'Build Rad +1 cum'},
        'R44': {'pos': (4, 9.5), 'color': '#9c27b0', 'sub': 'Prod P[5]=7'},
        'R45': {'pos': (4, 6.5), 'color': '#ff9800', 'sub': 'Credit Limit=120'},
        'R46': {'pos': (4, 5), 'color': '#9c27b0', 'sub': 'Score Bonus=30'},
        'R47': {'pos': (4, 3.5), 'color': '#9c27b0', 'sub': 'Display Bonus=25'},
    }
    rebel_edges = [
        ('R24','R26'),('R24','R27'),('R26','R28'),('R27','R29'),('R28','R29'),
        ('R29','R30'),('R29','R31'),('R30','R32'),('R31','R32'),
        ('R32','R33'),('R32','R34'),('R33','R35'),('R33','R36'),
        ('R35','R37'),('R36','R37'),('R37','R38'),
        ('R39','R41'),('R39','R44'),('R40','R41'),('R41','R42'),
        ('R45','R46'),('R46','R47'),
    ]
    draw_tech(ax2, rebel_nodes, rebel_edges, 'Resistance (Player 1)', '#3498db')

    # Legend
    legend_data = [
        ('Infantry', '#4caf50'), ('Machinery', '#2196f3'),
        ('Building/Economy', '#ff9800'), ('Weapon', '#f44336'),
        ('Strategic', '#9c27b0'),
    ]
    for i, (label, color) in enumerate(legend_data):
        fig.patches.append(FancyBboxPatch((0.05 + i*0.08, 0.01), 0.015, 0.015, 
            boxstyle="round,pad=0.002", facecolor=color, edgecolor='white', linewidth=0.5,
            transform=fig.transFigure))
        fig.text(0.07 + i*0.08, 0.017, label, fontsize=8, color='#e0e0e0', transform=fig.transFigure)

    fig.savefig(os.path.join(OUT_DIR, 'tech_tree.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ tech_tree.png")

# ═══════════════════════════════════════════════════════════════
# 4. COMBAT FLOW DIAGRAM
# ═══════════════════════════════════════════════════════════════
def combat_flow():
    fig, ax = plt.subplots(figsize=(20, 16))
    ax.set_xlim(0, 20)
    ax.set_ylim(0, 16)
    ax.axis('off')
    ax.set_title('Combat System Flow', fontsize=18, fontweight='bold', color='#00d4ff', pad=20)

    def draw_step(ax, x, y, w, h, label, color='#3498db'):
        box = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.12", facecolor=color, edgecolor='white', linewidth=1, alpha=0.9)
        ax.add_patch(box)
        ax.text(x+w/2, y+h/2, label, ha='center', va='center', fontsize=7, fontweight='bold', color='white', wrap=True)

    def draw_diamond(ax, cx, cy, size, label, color='#f39c12'):
        diamond = plt.Polygon([(cx, cy+size), (cx+size*1.5, cy), (cx, cy-size), (cx-size*1.5, cy)],
                               facecolor=color, edgecolor='white', linewidth=1, alpha=0.9)
        ax.add_patch(diamond)
        ax.text(cx, cy, label, ha='center', va='center', fontsize=6, fontweight='bold', color='white')

    def arrow(ax, x1, y1, x2, y2, color='#888', lw=1.5):
        ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                     arrowprops=dict(arrowstyle='->', color=color, lw=lw))

    # ── Row 1: Attack Initiation ──
    draw_step(ax, 0.3, 14.8, 2.5, 0.8, 'Combat Initiated', '#27ae60')
    draw_diamond(ax, 5, 15.2, 0.5, 'Trigger\nType?')
    draw_step(ax, 7.5, 15.0, 2.2, 0.7, 'Auto-engage\n(spatial hash)', '#3498db')
    draw_step(ax, 7.5, 14.0, 2.2, 0.7, 'Player Attack\nOrder', '#3498db')
    draw_step(ax, 7.5, 13.0, 2.2, 0.7, 'Siege Mode\nAuto-fire', '#3498db')

    arrow(ax, 2.8, 15.2, 3.5, 15.2)
    arrow(ax, 6.5, 15.2, 7.5, 15.35)
    arrow(ax, 5, 14.7, 5, 14.35); arrow(ax, 5, 14.35, 7.5, 14.35)
    arrow(ax, 5, 14.2, 5, 13.35); arrow(ax, 5, 13.35, 7.5, 13.35)

    draw_step(ax, 10.5, 14.2, 2.5, 0.7, 'Target Priority\nEvaluation', '#9b59b6')
    arrow(ax, 9.7, 15.35, 10.5, 14.55)
    arrow(ax, 9.7, 14.35, 10.5, 14.55)
    arrow(ax, 9.7, 13.35, 10.5, 14.55)

    # ── Row 2: Range Check ──
    draw_step(ax, 0.3, 12.2, 2.5, 0.8, 'Range Check\no() method', '#e74c3c')
    arrow(ax, 11.75, 14.2, 1.55, 12.6)

    draw_step(ax, 3.5, 12.2, 2.5, 0.8, 'Get Distance\ndx/dy → lookup', '#c0392b')
    arrow(ax, 2.8, 12.6, 3.5, 12.6)

    draw_diamond(ax, 8, 12.6, 0.5, '|dx|>15\n|dy|>15?')
    arrow(ax, 6.0, 12.6, 6.5, 12.6)

    draw_step(ax, 10.5, 12.9, 2.2, 0.7, 'OutOfRange\nDist=127', '#e74c3c')
    draw_step(ax, 10.5, 11.9, 2.2, 0.7, 'Lookup Dist\n31×31 table', '#c0392b')
    arrow(ax, 8.75, 13.1, 10.5, 13.25)
    arrow(ax, 8, 12.1, 8, 12.25); arrow(ax, 8, 12.25, 10.5, 12.25)

    draw_diamond(ax, 14, 12.6, 0.5, 'Dist ≤\nRange?')
    arrow(ax, 12.7, 12.25, 12.5, 12.6)

    draw_step(ax, 16, 13.2, 2.5, 0.6, 'Move Closer\nPath Calc', '#f39c12')
    draw_step(ax, 16, 12.2, 2.5, 0.6, 'In Range →\nProceed', '#27ae60')
    arrow(ax, 14.75, 13.1, 16, 13.5)
    arrow(ax, 14.75, 12.1, 16, 12.5)

    # ── Row 3: Weapon & Projectile ──
    draw_step(ax, 0.3, 10.2, 2.5, 0.8, 'Select Weapon\n3 slots, cooldown', '#3498db')
    arrow(ax, 1.55, 12.2, 1.55, 11.0)

    draw_diamond(ax, 5, 10.6, 0.5, 'Cooldown\nDone?')
    arrow(ax, 2.8, 10.6, 3.5, 10.6)

    draw_step(ax, 7.5, 10.7, 2.2, 0.7, 'Wait\nattackCycle++', '#f39c12')
    arrow(ax, 5.75, 11.1, 7.5, 11.05)

    draw_step(ax, 7.5, 9.7, 2.2, 0.7, 'Face Target\n8 compass', '#2ecc71')
    arrow(ax, 5.75, 10.1, 7.5, 10.05)

    # ── Row 4: Projectile Spawn & Flight ──
    draw_step(ax, 0.3, 8.4, 2.5, 0.8, 'Projectile Spawn\nvelocity calc', '#9b59b6')
    arrow(ax, 9.7, 10.05, 1.55, 9.2)

    draw_step(ax, 3.5, 8.4, 2.5, 0.8, 'Flight Loop\nv+=velX, w+=velY', '#8e44ad')
    arrow(ax, 2.8, 8.8, 3.5, 8.8)

    draw_diamond(ax, 8, 8.8, 0.5, 'Time\nExpired?')
    arrow(ax, 6.0, 8.8, 6.5, 8.8)

    draw_step(ax, 10.5, 9.1, 2.2, 0.7, 'Continue\nFlight', '#2ecc71')
    draw_step(ax, 10.5, 8.1, 2.2, 0.7, 'IMPACT\nPHASE', '#e74c3c')
    arrow(ax, 8.75, 9.3, 10.5, 9.45)
    arrow(ax, 8, 8.3, 8, 8.45); arrow(ax, 8, 8.45, 10.5, 8.45)

    # ── Row 5: Impact & Damage ──
    draw_diamond(ax, 2, 7.0, 0.5, 'Projectile\nType?')
    arrow(ax, 1.55, 8.4, 1.55, 7.5); arrow(ax, 1.55, 7.5, 2, 7.5)

    draw_step(ax, 4.5, 7.3, 2.5, 0.7, 'SPLASH DAMAGE\nArea scan, radius', '#e74c3c')
    draw_step(ax, 4.5, 6.3, 2.5, 0.7, 'DIRECT DAMAGE\nSingle target', '#c0392b')
    arrow(ax, 2.75, 7.5, 4.5, 7.65)
    arrow(ax, 2, 6.5, 2, 6.65); arrow(ax, 2, 6.65, 4.5, 6.65)

    draw_step(ax, 8, 6.8, 2.5, 0.7, 'Armour Calc\nl() method', '#3498db')
    arrow(ax, 7.0, 7.65, 8, 7.15)
    arrow(ax, 7.0, 6.65, 8, 7.15)

    draw_step(ax, 11.5, 6.8, 2.8, 0.7, 'Damage = base×\n(10-arm)/10', '#f39c12')
    arrow(ax, 10.5, 7.15, 11.5, 7.15)

    draw_step(ax, 15, 6.8, 2.8, 0.7, 'Apply Damage\nHP -= damage', '#e74c3c')
    arrow(ax, 14.3, 7.15, 15, 7.15)

    # ── Row 6: Death Check ──
    draw_diamond(ax, 2, 5.0, 0.5, 'HP\n≤ 0?')
    arrow(ax, 16.4, 6.8, 16.4, 5.5); arrow(ax, 16.4, 5.5, 2, 5.5)

    draw_step(ax, 4.5, 5.3, 2.5, 0.7, 'Unit Survives\nReturn combat', '#27ae60')
    draw_step(ax, 4.5, 4.3, 2.5, 0.7, 'UNIT DIES\nHP = -1', '#e74c3c')
    arrow(ax, 2.75, 5.5, 4.5, 5.65)
    arrow(ax, 2, 4.5, 2, 4.65); arrow(ax, 2, 4.65, 4.5, 4.65)

    # ── Row 7: Death Effects ──
    draw_step(ax, 8, 5.3, 2.5, 0.7, 'Death Animation\nInfantry/Machinery', '#9b59b6')
    draw_step(ax, 11, 5.3, 2.5, 0.7, 'Effects: Fire\nSmoke, Debris', '#8e44ad')
    draw_step(ax, 14, 5.3, 2.5, 0.7, 'Kill Reward\ncredits + score', '#f39c12')
    arrow(ax, 7.0, 4.65, 8, 5.65)
    arrow(ax, 10.5, 5.65, 11, 5.65)
    arrow(ax, 13.5, 5.65, 14, 5.65)

    draw_step(ax, 8, 3.8, 2.5, 0.7, 'Remove Unit\nClear occupancy', '#c0392b')
    draw_diamond(ax, 14, 4.1, 0.5, 'Win\nCheck?')
    arrow(ax, 9.25, 5.3, 9.25, 4.5)
    arrow(ax, 10.5, 4.15, 12.5, 4.15)

    draw_step(ax, 16.5, 4.5, 2.5, 0.7, 'Game End\nab=1 or 2', '#e74c3c')
    draw_step(ax, 16.5, 3.5, 2.5, 0.7, 'Continue\nGame Loop', '#27ae60')
    arrow(ax, 14.75, 4.6, 16.5, 4.85)
    arrow(ax, 14.75, 3.6, 16.5, 3.85)

    fig.savefig(os.path.join(OUT_DIR, 'combat_flow.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ combat_flow.png")

# ═══════════════════════════════════════════════════════════════
# 5. NETWORK PROTOCOL DIAGRAM
# ═══════════════════════════════════════════════════════════════
def network_protocol():
    fig, ax = plt.subplots(figsize=(18, 14))
    ax.set_xlim(0, 18)
    ax.set_ylim(0, 14)
    ax.axis('off')
    ax.set_title('Network Protocol: Client-Server Communication', fontsize=16, fontweight='bold', color='#00d4ff', pad=20)

    # Client column
    ax.add_patch(FancyBboxPatch((0.5, 1), 4, 12, boxstyle="round,pad=0.2", facecolor='#1a3a5c', edgecolor='#3498db', linewidth=2, alpha=0.5))
    ax.text(2.5, 12.8, 'CLIENT\n(Game)', ha='center', fontsize=12, fontweight='bold', color='#3498db')

    # Server column
    ax.add_patch(FancyBboxPatch((13.5, 1), 4, 12, boxstyle="round,pad=0.2", facecolor='#3a1a1a', edgecolor='#e74c3c', linewidth=2, alpha=0.5))
    ax.text(15.5, 12.8, 'SERVER\n(Game Host)', ha='center', fontsize=12, fontweight='bold', color='#e74c3c')

    # Phase labels
    phases = [
        ('LOGIN', 11.5, '#27ae60'),
        ('LOBBY', 9.5, '#f39c12'),
        ('MATCHMAKING', 7.5, '#9b59b6'),
        ('GAME SYNC', 5.0, '#3498db'),
        ('GAME END', 2.5, '#e74c3c'),
    ]

    for label, y, color in phases:
        ax.text(9, y, label, ha='center', fontsize=9, fontweight='bold', color=color,
                bbox=dict(boxstyle='round,pad=0.3', facecolor='#0d1b2a', edgecolor=color, linewidth=1))

    # Message arrows (client → server and server → client)
    msg_data = [
        # (y, direction, msg_type, label, color)
        (11.8, 'right', 'type=1', 'Login Message\n[player_id, license]', '#27ae60'),
        (11.2, 'left', 'type=4', 'SESSION_INIT\n[game_id, players, income]', '#27ae60'),
        (9.8, 'right', 'type=20', 'Request Lobby List\n[room_filter]', '#f39c12'),
        (9.2, 'left', 'type=20', 'LOBBY_LIST\n[rooms, player_counts]', '#f39c12'),
        (7.8, 'right', 'type=38', 'Match Search\n[mode, preferences]', '#9b59b6'),
        (7.2, 'left', 'type=12', 'MATCH_START\n[mode, game_id, map_data]', '#9b59b6'),
        (5.8, 'right', 'type=30', 'Game State Update\n[XOR cipher + Base64]', '#3498db'),
        (5.2, 'left', 'type=30', 'GAME_STATE\n[turn, units, buildings]', '#3498db'),
        (4.6, 'right', 'type=30', 'Sync Update\n(every 15s default)', '#3498db'),
        (4.0, 'left', 'type=30', 'State Response\n[verified turn #]', '#3498db'),
        (2.8, 'left', 'type=33', 'GAME_RESULT\n[score_p0, score_p1]', '#e74c3c'),
    ]

    for y, direction, msg_type, label, color in msg_data:
        if direction == 'right':
            ax.annotate('', xy=(13.5, y), xytext=(4.5, y),
                        arrowprops=dict(arrowstyle='->', color=color, lw=1.5))
            ax.text(9, y+0.15, f'{msg_type}: {label}', ha='center', fontsize=6.5, color=color)
        else:
            ax.annotate('', xy=(4.5, y), xytext=(13.5, y),
                        arrowprops=dict(arrowstyle='->', color=color, lw=1.5, linestyle='--'))
            ax.text(9, y+0.15, f'{msg_type}: {label}', ha='center', fontsize=6.5, color=color)

    # Connection details box
    details = [
        'TCP Port: 47584-47588',
        'Encryption: 15-byte XOR stream cipher',
        'Encoding: Custom Base64 (y.aK alphabet)',
        'Session: Key rotation + checksum (cP/cQ)',
        'Timeouts: 10s/5s/30s (3-phase HTTP)',
        'Retry: Up to 10 reconnect attempts',
        'Buffer: 3-slot circular request queue',
    ]
    ax.text(9, 1.2, 'Connection Details', ha='center', fontsize=10, fontweight='bold', color='#f39c12')
    for i, d in enumerate(details):
        ax.text(9, 0.8 - i*0.25, d, ha='center', fontsize=7, color='#b0b0b0')

    fig.savefig(os.path.join(OUT_DIR, 'network_protocol.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ network_protocol.png")

# ═══════════════════════════════════════════════════════════════
# 6. REVERSE ENGINEERING DASHBOARD
# ═══════════════════════════════════════════════════════════════
def dashboard():
    fig = plt.figure(figsize=(20, 14))
    fig.suptitle('Reverse Engineering Dashboard — Art of War 2 Online', fontsize=18, fontweight='bold', color='#00d4ff', y=0.98)

    # ── Subplot 1: Entity Counts ──
    ax1 = fig.add_subplot(2, 3, 1)
    categories = ['Classes', 'Assets', 'Units', 'Buildings', 'Campaigns', 'Maps', 'Scripts', 'MP Systems']
    values = [82, 256, 14, 18, 14, 15, 48, 6]
    colors = ['#e74c3c', '#e67e22', '#f1c40f', '#2ecc71', '#1abc9c', '#3498db', '#9b59b6', '#8e44ad']
    bars = ax1.barh(categories, values, color=colors, alpha=0.85, edgecolor='white', linewidth=0.5)
    ax1.set_title('Entity Counts', fontsize=11, fontweight='bold', color='#ffffff')
    for bar, val in zip(bars, values):
        ax1.text(bar.get_width() + 3, bar.get_y() + bar.get_height()/2, str(val), va='center', fontsize=8, color='#e0e0e0')
    ax1.set_xlim(0, max(values)*1.2)
    ax1.grid(axis='x', alpha=0.2, color='#555')

    # ── Subplot 2: Architecture Complexity ──
    ax2 = fig.add_subplot(2, 3, 2)
    systems = ['Core\nEngine', 'Combat', 'AI', 'Pathfinding', 'Economy', 'Network', 'Rendering', 'World']
    complexity = [9, 8, 7, 6, 5, 8, 7, 9]
    radar_angles = np.linspace(0, 2*np.pi, len(systems), endpoint=False).tolist()
    complexity_r = complexity + complexity[:1]
    radar_angles_r = radar_angles + radar_angles[:1]
    ax2 = fig.add_subplot(2, 3, 2, polar=True)
    ax2.fill(radar_angles_r, complexity_r, alpha=0.25, color='#3498db')
    ax2.plot(radar_angles_r, complexity_r, color='#3498db', linewidth=2)
    ax2.set_xticks(radar_angles)
    ax2.set_xticklabels(systems, fontsize=6, color='#e0e0e0')
    ax2.set_ylim(0, 10)
    ax2.set_yticks([2, 4, 6, 8, 10])
    ax2.set_yticklabels(['2', '4', '6', '8', '10'], fontsize=6, color='#888')
    ax2.set_title('Architecture Complexity', fontsize=11, fontweight='bold', color='#ffffff', pad=20)
    ax2.set_facecolor('#16213e')
    ax2.grid(color='#444', alpha=0.3)

    # ── Subplot 3: Confidence Scores ──
    ax3 = fig.add_subplot(2, 3, 3)
    conf_cats = ['s0/s1/s2\nScreen Var', 'XOR Cipher', 'Network\nFormat', 'Game Loop', 'Unit Stats', 'Combat\nFormulas', 'Pathfinding', 'AI System', 'Research\nEffects', 'Terrain\nTypes']
    conf_vals = [95, 95, 90, 90, 90, 85, 85, 80, 75, 65]
    bar_colors = ['#27ae60' if v >= 85 else '#f39c12' if v >= 70 else '#e74c3c' for v in conf_vals]
    ax3.barh(conf_cats, conf_vals, color=bar_colors, alpha=0.85, edgecolor='white', linewidth=0.5)
    ax3.set_title('Confidence Scores (%)', fontsize=11, fontweight='bold', color='#ffffff')
    ax3.set_xlim(0, 100)
    for i, v in enumerate(conf_vals):
        ax3.text(v + 1, i, f'{v}%', va='center', fontsize=7, color='#e0e0e0')
    ax3.axvline(x=85, color='#27ae60', linestyle='--', alpha=0.5, linewidth=0.8)
    ax3.axvline(x=70, color='#f39c12', linestyle='--', alpha=0.5, linewidth=0.8)
    ax3.grid(axis='x', alpha=0.2, color='#555')

    # ── Subplot 4: Code Distribution ──
    ax4 = fig.add_subplot(2, 3, 4)
    code_cats = ['s0 Package', 's1 Package', 's2 Package', 'Core Platform', 'Billing/Ads']
    code_vals = [25, 25, 25, 18, 7]
    code_colors = ['#e74c3c', '#3498db', '#2ecc71', '#9b59b6', '#f39c12']
    wedges, texts, autotexts = ax4.pie(code_vals, labels=code_cats, autopct='%1.0f%%', 
                                        colors=code_colors, startangle=90, textprops={'fontsize': 8, 'color': '#e0e0e0'})
    for at in autotexts:
        at.set_color('white')
        at.set_fontsize(7)
    ax4.set_title('Code Distribution by Package', fontsize=11, fontweight='bold', color='#ffffff')

    # ── Subplot 5: Data Structure Sizes ──
    ax5 = fig.add_subplot(2, 3, 5)
    data_names = ['ca[] Entity\n(7272 B)', 'bW[][] Grid\n(128×128)', 'bk[][][] Hash\n(2×8×8)', 'Q[][][][] Fog\n(2×2×128×128)', 'al[][][] Path\n(2×100×50)', 'Projectile\n(400×12 arrays)']
    data_sizes = [7272, 16384, 128, 65536, 10000, 4800]
    ax5.bar(range(len(data_names)), data_sizes, color='#9b59b6', alpha=0.85, edgecolor='white', linewidth=0.5)
    ax5.set_xticks(range(len(data_names)))
    ax5.set_xticklabels(data_names, fontsize=6, ha='center')
    ax5.set_title('Key Data Structure Sizes (bytes)', fontsize=11, fontweight='bold', color='#ffffff')
    ax5.set_ylabel('Bytes', fontsize=8)
    ax5.grid(axis='y', alpha=0.2, color='#555')
    for i, v in enumerate(data_sizes):
        ax5.text(i, v + 500, f'{v:,}', ha='center', fontsize=7, color='#e0e0e0')

    # ── Subplot 6: Timeline ──
    ax6 = fig.add_subplot(2, 3, 6)
    events = ['R0 Base', 'R6 Unit\nUpgrade', 'R8 Range\nAdjust', 'R12 Hammer\n→Scorpio', 'R14 Artillery\n+10dmg']
    tiers = [1, 3, 4, 6, 7]
    ax6.step(tiers, range(len(events)), where='mid', color='#3498db', linewidth=2)
    ax6.scatter(tiers, range(len(events)), color='#e74c3c', s=60, zorder=5)
    for i, (evt, t) in enumerate(zip(events, tiers)):
        ax6.text(t + 0.2, i, evt, va='center', fontsize=7, color='#e0e0e0')
    ax6.set_xlabel('Research Tier', fontsize=8)
    ax6.set_title('Confederation Research Progression', fontsize=11, fontweight='bold', color='#ffffff')
    ax6.set_yticks([])
    ax6.grid(axis='x', alpha=0.2, color='#555')

    plt.tight_layout(rect=[0, 0, 1, 0.95])
    fig.savefig(os.path.join(OUT_DIR, 'dashboard.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ dashboard.png")

# ═══════════════════════════════════════════════════════════════
# 7. CLASS DEPENDENCY GRAPH (DOT)
# ═══════════════════════════════════════════════════════════════
def class_dependency_dot():
    dot = """digraph ClassDependency {
    // Graph settings
    rankdir=TB;
    bgcolor="#1a1a2e";
    fontcolor="#e0e0e0";
    fontname="sans-serif";
    node [shape=box, style="rounded,filled", fontname="sans-serif", fontsize=10, fontcolor="white"];
    edge [color="#888888", arrowsize=0.8];

    // Platform Layer
    subgraph cluster_platform {
        label="Platform Layer";
        style=filled;
        color="#7f8c8d";
        fillcolor="#2c3e50";
        Application [fillcolor="#7f8c8d", label="Application\\n(Activity)"];
        AppCtrl [fillcolor="#7f8c8d", label="AppCtrl\\n(Main Thread)"];
        SurfaceView [fillcolor="#7f8c8d", label="an (SurfaceView)"];
        Renderer [fillcolor="#7f8c8d", label="h (Renderer)"];
    }

    // Core Engine
    subgraph cluster_core {
        label="Core Engine";
        style=filled;
        color="#e74c3c";
        fillcolor="#3a1a1a";
        MIDlet [fillcolor="#e74c3c", label="bh (MIDlet)"];
        ScreenSelect [fillcolor="#e74c3c", label="bb (Screen Select)"];
        GlobalData [fillcolor="#e74c3c", label="y (Global Data)"];
    }

    // Game Canvas
    subgraph cluster_canvas {
        label="Game Canvas Hierarchy";
        style=filled;
        color="#3498db";
        fillcolor="#1a2a3a";
        Displayable [fillcolor="#3498db", label="bv (Displayable)"];
        Canvas [fillcolor="#3498db", label="z (Abstract Canvas)"];
        ResourceLoader [fillcolor="#3498db", label="bu (Resource Loader)"];
        BaseCanvas [fillcolor="#3498db", label="x (Base Game Canvas)"];
        MainGame [fillcolor="#3498db", label="k (Main Game Class)"];
        Graphics [fillcolor="#3498db", label="aq (Android Graphics)"];
    }

    // Game Systems
    subgraph cluster_systems {
        label="Game Systems";
        style=filled;
        color="#2ecc71";
        fillcolor="#1a3a1a";
        GameLogic [fillcolor="#2ecc71", label="w (Game Logic/Map)"];
        NetworkMgr [fillcolor="#9b59b6", label="e (Network Manager)"];
        NetSender [fillcolor="#9b59b6", label="m (Sender Thread)"];
        NetReceiver [fillcolor="#9b59b6", label="o (Receiver Thread)"];
        RequestQueue [fillcolor="#9b59b6", label="z (Request Queue)"];
        HTTPHandler [fillcolor="#9b59b6", label="aa (HTTP Handler)"];
        AudioMgr [fillcolor="#f39c12", label="i (Audio Manager)"];
        FontMgr [fillcolor="#f39c12", label="f (Font Manager)"];
        BitmapFont [fillcolor="#f39c12", label="d (Bitmap Font)"];
        ConfigMgr [fillcolor="#f39c12", label="c (Config Manager)"];
        GameState [fillcolor="#2ecc71", label="a (Game State Mgr)"];
    }

    // UI
    subgraph cluster_ui {
        label="UI System";
        style=filled;
        color="#f39c12";
        fillcolor="#3a2a0a";
        Dialog [fillcolor="#f39c12", label="bz (Dialog Base)"];
        DialogForm [fillcolor="#f39c12", label="af (Dialog Form)"];
        UIItem [fillcolor="#f39c12", label="t (UI Item)"];
    }

    // Persistence
    subgraph cluster_persist {
        label="Persistence";
        style=filled;
        color="#1abc9c";
        fillcolor="#0a2a2a";
        RecordStore [fillcolor="#1abc9c", label="i (RMS Storage)"];
        Billing [fillcolor="#e74c3c", label="ae (Billing Manager)"];
    }

    // Edges - Platform
    Application -> AppCtrl;
    AppCtrl -> MIDlet;
    AppCtrl -> ScreenSelect;
    AppCtrl -> SurfaceView;
    SurfaceView -> Renderer;

    // Edges - Core
    MIDlet -> MainGame;
    ScreenSelect -> MainGame;
    GlobalData -> GameLogic;
    GlobalData -> MainGame;

    // Edges - Canvas hierarchy
    Displayable -> Canvas;
    Canvas -> ResourceLoader;
    ResourceLoader -> BaseCanvas;
    BaseCanvas -> MainGame;
    MainGame -> Graphics;

    // Edges - Game Systems
    MainGame -> GameLogic;
    MainGame -> NetworkMgr;
    MainGame -> AudioMgr;
    MainGame -> FontMgr;
    MainGame -> ConfigMgr;
    MainGame -> GameState;

    // Edges - Network
    NetworkMgr -> NetSender;
    NetworkMgr -> NetReceiver;
    NetworkMgr -> RequestQueue;
    RequestQueue -> HTTPHandler;

    // Edges - Fonts
    FontMgr -> BitmapFont;

    // Edges - UI
    MainGame -> Dialog;
    Dialog -> DialogForm;
    DialogForm -> UIItem;

    // Edges - Persistence
    ConfigMgr -> RecordStore;
    MainGame -> Billing;

    // Edges - Cross-system
    GameLogic -> NetworkMgr [style=dashed, color="#9b59b6"];
    GameLogic -> Graphics [style=dashed, color="#3498db"];
    GameLogic -> GlobalData [style=dashed, color="#e74c3c"];
    GameState -> GameLogic [style=dashed, color="#2ecc71"];
    NetworkMgr -> GameLogic [style=dashed, color="#9b59b6"];
}
"""
    with open(os.path.join(OUT_DIR, 'class_dependency.dot'), 'w') as f:
        f.write(dot)
    print("✓ class_dependency.dot")

# ═══════════════════════════════════════════════════════════════
# 8. DATA FLOW DIAGRAM
# ═══════════════════════════════════════════════════════════════
def data_flow():
    fig, ax = plt.subplots(figsize=(22, 12))
    ax.set_xlim(0, 22)
    ax.set_ylim(0, 12)
    ax.axis('off')
    ax.set_title('Data Flow: Input → Commands → Simulation → Rendering', fontsize=16, fontweight='bold', color='#00d4ff', pad=20)

    # Layer positions
    layers = [
        ('INPUT', 0.3, '#27ae60', [
            'Touch Events', 'Key Events', 'Network Input', 'Timer/Tick'
        ]),
        ('COMMANDS', 4.5, '#3498db', [
            'Move', 'Attack', 'Build', 'Produce', 'Research', 'Garrison', 'Siege'
        ]),
        ('SIMULATION', 7.0, '#f39c12', [
            'Path Calc', 'Movement', 'Combat', 'AI', 'Economy', 'Fog of War', 'Projectile'
        ]),
        ('STATE', 9.5, '#9b59b6', [
            'Unit State', 'Building State', 'Player State', 'Map State', 'Spatial Hash'
        ]),
        ('RENDER', 11.0, '#e74c3c', [
            'Fog', 'Terrain', 'Buildings', 'Units', 'Projectiles', 'UI', 'Effects'
        ]),
    ]

    for layer_name, y_base, color, items in layers:
        # Layer background
        ax.add_patch(FancyBboxPatch((0.5, y_base - 0.3), 21, 2.2, boxstyle="round,pad=0.15",
                                     facecolor=color, edgecolor='white', linewidth=1, alpha=0.15))
        ax.text(0.7, y_base + 1.6, layer_name, fontsize=11, fontweight='bold', color=color)

        # Items
        n = len(items)
        spacing = 20 / n
        for i, item in enumerate(items):
            x = 1.2 + i * spacing
            box = FancyBboxPatch((x, y_base), spacing*0.85, 1.2, boxstyle="round,pad=0.08",
                                  facecolor=color, edgecolor='white', linewidth=0.6, alpha=0.75)
            ax.add_patch(box)
            ax.text(x + spacing*0.42, y_base + 0.6, item, ha='center', va='center', fontsize=7, color='white', fontweight='bold')

    # Flow arrows between layers
    arrow_specs = [
        # (from_layer_y, to_layer_y, x_pos, color)
        (2.2, 4.5, 3, '#27ae60'),
        (2.2, 4.5, 7, '#27ae60'),
        (2.2, 4.5, 11, '#3498db'),
        (2.2, 4.5, 15, '#3498db'),
        (5.7, 7.0, 3, '#3498db'),
        (5.7, 7.0, 7, '#3498db'),
        (5.7, 7.0, 11, '#f39c12'),
        (5.7, 7.0, 15, '#f39c12'),
        (8.2, 9.5, 5, '#f39c12'),
        (8.2, 9.5, 10, '#9b59b6'),
        (8.2, 9.5, 15, '#9b59b6'),
        (10.7, 11.0, 5, '#9b59b6'),
        (10.7, 11.0, 10, '#e74c3c'),
        (10.7, 11.0, 15, '#e74c3c'),
    ]
    for y1, y2, x, color in arrow_specs:
        ax.annotate('', xy=(x, y2), xytext=(x, y1),
                     arrowprops=dict(arrowstyle='->', color=color, lw=1.5, alpha=0.6))

    # Output box at bottom
    ax.add_patch(FancyBboxPatch((0.5, 0.2), 21, 1.0, boxstyle="round,pad=0.15",
                                 facecolor='#1abc9c', edgecolor='white', linewidth=1, alpha=0.2))
    output_items = ['Android Display', 'Audio Output', 'Network Output', 'Vibrator']
    for i, item in enumerate(output_items):
        x = 2 + i * 5
        box = FancyBboxPatch((x, 0.3), 3.5, 0.8, boxstyle="round,pad=0.08",
                              facecolor='#1abc9c', edgecolor='white', linewidth=0.6, alpha=0.75)
        ax.add_patch(box)
        ax.text(x + 1.75, 0.7, item, ha='center', va='center', fontsize=7, color='white', fontweight='bold')

    # Render → Output arrows
    for x in [3, 8, 13, 18]:
        ax.annotate('', xy=(x, 1.1), xytext=(x, 2.0),
                     arrowprops=dict(arrowstyle='->', color='#1abc9c', lw=1.2, alpha=0.5))

    fig.savefig(os.path.join(OUT_DIR, 'data_flow.png'), dpi=DPI, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close(fig)
    print("✓ data_flow.png")

# ═══════════════════════════════════════════════════════════════
# MAIN
# ═══════════════════════════════════════════════════════════════
if __name__ == '__main__':
    print("Generating diagrams for Art of War 2 Online RE project...")
    system_architecture()
    unit_comparison()
    tech_tree()
    combat_flow()
    network_protocol()
    dashboard()
    class_dependency_dot()
    data_flow()
    print("\n✅ All 8 diagrams generated successfully!")
