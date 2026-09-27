"""
StarStack Cyberpunk Cyan City Generator
Generates a Minecraft datapack with .mcfunction files
Run from StarStack root directory
"""
import os

# === Config ===
CX0 = 1000  # Center X
CY0 = 80    # Center Y (ground level)
CZ0 = 1000  # Center Z
HALF = 50   # City radius (total 100x100)

OUT_DIR = "mc/datapacks/cyber-city/data/city/functions"
os.makedirs(OUT_DIR, exist_ok=True)


def cx(x): return CX0 + x
def cy(y): return CY0 + y
def cz(z): return CZ0 + z


def fill(f, x1, y1, z1, x2, y2, z2, block):
    """Add a fill command"""
    f.write(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}\n")


def setblock(f, x, y, z, block):
    f.write(f"setblock {x} {y} {z} {block}\n")


def sign_cmd(f, x, y, z, face, color, line1, line2=""):
    """Place an oak_sign with custom color and text. face: 0-15 (compass directions)"""
    block_state = f'minecraft:oak_sign[rotation={face}]'
    text1 = '{"text":"' + line1 + '","color":"' + color + '"}'
    text2 = '{"text":"' + line2 + '","color":"' + color + '"}'
    f.write(f"setblock {x} {y} {z} {block_state}{{\"Color\":\"{color}\",\"Text1\":'{text1}','Text2':'{text2}'}}\n")


# ============================================================
# 1) Platform - 100x100 cyan foundation
# ============================================================
with open(f"{OUT_DIR}/platform.mcfunction", "w", encoding="utf-8") as f:
    # Underground base (black concrete, 8 layers)
    fill(f, cx(-HALF), cy(-9), cz(-HALF), cx(HALF), cy(-2), cz(HALF), "minecraft:black_concrete")
    # Ground surface - cyan concrete
    fill(f, cx(-HALF), cy(-1), cz(-HALF), cx(HALF), cy(-1), cz(HALF), "minecraft:cyan_concrete")
    # Edge lantern strips (sea lanterns every 4 blocks)
    for x in range(-HALF, HALF + 1, 4):
        setblock(f, cx(x), cy(-1), cz(-HALF), "minecraft:sea_lantern")
        setblock(f, cx(x), cy(-1), cz(HALF), "minecraft:sea_lantern")
    for z in range(-HALF, HALF + 1, 4):
        setblock(f, cx(-HALF), cy(-1), cz(z), "minecraft:sea_lantern")
        setblock(f, cx(HALF), cy(-1), cz(z), "minecraft:sea_lantern")
    # Outer rim - light blue concrete (prevents falling off)
    fill(f, cx(-HALF-1), cy(-1), cz(-HALF-1), cx(HALF+1), cy(-1), cz(HALF+1), "minecraft:light_blue_concrete")


# ============================================================
# 2) Roads - + shaped cross main roads
# ============================================================
with open(f"{OUT_DIR}/roads.mcfunction", "w", encoding="utf-8") as f:
    # North-South road at x=0
    fill(f, cx(-1), cy(-1), cz(-HALF), cx(1), cy(-1), cz(HALF), "minecraft:black_concrete")
    fill(f, cx(-1), cy(0), cz(-HALF), cx(1), cy(0), cz(HALF), "minecraft:cyan_concrete")
    # East-West road at z=0
    fill(f, cx(-HALF), cy(-1), cz(-1), cx(HALF), cy(-1), cz(1), "minecraft:black_concrete")
    fill(f, cx(-HALF), cy(0), cz(-1), cx(HALF), cy(0), cz(1), "minecraft:cyan_concrete")
    # Edge lanterns along roads (every 5 blocks)
    for z in range(-HALF, HALF + 1, 5):
        setblock(f, cx(-2), cy(0), cz(z), "minecraft:sea_lantern")
        setblock(f, cx(2), cy(0), cz(z), "minecraft:sea_lantern")
    for x in range(-HALF, HALF + 1, 5):
        setblock(f, cx(x), cy(0), cz(-2), "minecraft:sea_lantern")
        setblock(f, cx(x), cy(0), cz(2), "minecraft:sea_lantern")


# ============================================================
# 3) Central Plaza - 20x20 hologram tower
# ============================================================
with open(f"{OUT_DIR}/plaza.mcfunction", "w", encoding="utf-8") as f:
    # Plaza ground
    fill(f, cx(-10), cy(-1), cz(-10), cx(10), cy(-1), cz(10), "minecraft:light_blue_concrete")
    fill(f, cx(-10), cy(0), cz(-10), cx(10), cy(0), cz(10), "minecraft:cyan_concrete")
    # Four corner glowing pillars
    for x, z in [(-10, -10), (10, -10), (10, 10), (-10, 10)]:
        fill(f, cx(x), cy(0), cz(z), cx(x), cy(8), cz(z), "minecraft:cyan_concrete")
        setblock(f, cx(x), cy(9), cz(z), "minecraft:sea_lantern")
        setblock(f, cx(x), cy(10), cz(z), "minecraft:cyan_stained_glass")

    # Central hologram tower (5x5 base, 50 blocks tall glass tower)
    fill(f, cx(-2), cy(0), cz(-2), cx(2), cy(0), cz(2), "minecraft:cyan_concrete")
    fill(f, cx(-2), cy(1), cz(-2), cx(2), cy(50), cz(2), "minecraft:light_blue_stained_glass")
    fill(f, cx(-2), cy(51), cz(-2), cx(2), cy(51), cz(2), "minecraft:cyan_concrete")
    setblock(f, cx(0), cy(52), cz(0), "minecraft:beacon")
    # Tower mid-section lantern rings
    for y in [10, 20, 30, 40]:
        fill(f, cx(-2), cy(y), cz(-2), cx(2), cy(y), cz(2), "minecraft:sea_lantern")


# ============================================================
# 4) North Tower (z=-35, tallest)
# ============================================================
with open(f"{OUT_DIR}/tower_north.mcfunction", "w", encoding="utf-8") as f:
    bx1, bz1 = -8, -44
    bx2, bz2 = 8, -28
    HEIGHT = 45
    # Floor
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(0), cz(bz2), "minecraft:black_concrete")
    # 4 walls (black concrete frame)
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx1), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx2), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz1), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz2), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    # Glass facades (front/back)
    fill(f, cx(bx1+1), cy(1), cz(bz1), cx(bx2-1), cy(HEIGHT-1), cz(bz1), "minecraft:cyan_stained_glass")
    fill(f, cx(bx1+1), cy(1), cz(bz2), cx(bx2-1), cy(HEIGHT-1), cz(bz2), "minecraft:cyan_stained_glass")
    # Floor dividers every 8 levels
    for y in range(2, HEIGHT - 1, 8):
        fill(f, cx(bx1+1), cy(y), cz(bz1+1), cx(bx2-1), cy(y), cz(bz2-1), "minecraft:cyan_concrete")
    # Rooftop - sea lantern cap
    fill(f, cx(bx1), cy(HEIGHT), cz(bz1), cx(bx2), cy(HEIGHT+1), cz(bz2), "minecraft:sea_lantern")
    # Antenna
    fill(f, cx(0), cy(HEIGHT+2), cz(-36), cx(0), cy(HEIGHT+5), cz(-36), "minecraft:cyan_stained_glass")
    setblock(f, cx(0), cy(HEIGHT+6), cz(-36), "minecraft:sea_lantern")
    # Front sign
    sign_cmd(f, cx(0), cy(20), cz(bz1-1), 0, "aqua", "CYBER", "NETWORK")


# ============================================================
# 5) East Tower (x=35)
# ============================================================
with open(f"{OUT_DIR}/tower_east.mcfunction", "w", encoding="utf-8") as f:
    bx1, bz1 = 28, -8
    bx2, bz2 = 44, 8
    HEIGHT = 35
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(0), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx1), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx2), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz1), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz2), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    # Glass facade (interior)
    fill(f, cx(bx1+1), cy(1), cz(bz1+1), cx(bx2-1), cy(HEIGHT-1), cz(bz2-1), "minecraft:light_blue_stained_glass")
    # Floor dividers
    for y in range(2, HEIGHT - 1, 6):
        fill(f, cx(bx1+1), cy(y), cz(bz1+1), cx(bx2-1), cy(y), cz(bz2-1), "minecraft:cyan_concrete")
    # Rooftop
    fill(f, cx(bx1), cy(HEIGHT), cz(bz1), cx(bx2), cy(HEIGHT+1), cz(bz2), "minecraft:sea_lantern")
    # East-facing sign
    sign_cmd(f, cx(bx2+1), cy(15), cz(0), 4, "aqua", "DATA", "HUB")


# ============================================================
# 6) South Tower (z=35, medium height)
# ============================================================
with open(f"{OUT_DIR}/tower_south.mcfunction", "w", encoding="utf-8") as f:
    bx1, bz1 = -8, 28
    bx2, bz2 = 8, 44
    HEIGHT = 30
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(0), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx1), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx2), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz1), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz2), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1+1), cy(1), cz(bz1+1), cx(bx2-1), cy(HEIGHT-1), cz(bz2-1), "minecraft:cyan_stained_glass")
    for y in range(2, HEIGHT - 1, 6):
        fill(f, cx(bx1+1), cy(y), cz(bz1+1), cx(bx2-1), cy(y), cz(bz2-1), "minecraft:cyan_concrete")
    fill(f, cx(bx1), cy(HEIGHT), cz(bz1), cx(bx2), cy(HEIGHT+1), cz(bz2), "minecraft:sea_lantern")
    sign_cmd(f, cx(0), cy(15), cz(bz2+1), 8, "aqua", "Future Tech", "LAB")


# ============================================================
# 7) West Tower (x=-35)
# ============================================================
with open(f"{OUT_DIR}/tower_west.mcfunction", "w", encoding="utf-8") as f:
    bx1, bz1 = -44, -8
    bx2, bz2 = -28, 8
    HEIGHT = 25
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(0), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx1), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx2), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz1), cx(bx2), cy(HEIGHT), cz(bz1), "minecraft:black_concrete")
    fill(f, cx(bx1), cy(0), cz(bz2), cx(bx2), cy(HEIGHT), cz(bz2), "minecraft:black_concrete")
    fill(f, cx(bx1+1), cy(1), cz(bz1+1), cx(bx2-1), cy(HEIGHT-1), cz(bz2-1), "minecraft:light_blue_stained_glass")
    for y in range(2, HEIGHT - 1, 6):
        fill(f, cx(bx1+1), cy(y), cz(bz1+1), cx(bx2-1), cy(y), cz(bz2-1), "minecraft:cyan_concrete")
    fill(f, cx(bx1), cy(HEIGHT), cz(bz1), cx(bx2), cy(HEIGHT+1), cz(bz2), "minecraft:sea_lantern")
    sign_cmd(f, cx(bx1-1), cy(15), cz(0), 12, "aqua", "MARKET", "ZONE")


# ============================================================
# 8) Holographic billboards & neon decor
# ============================================================
with open(f"{OUT_DIR}/neon.mcfunction", "w", encoding="utf-8") as f:
    # 4 intersection landmarks
    for x, z in [(0, -45), (0, 45), (-45, 0), (45, 0)]:
        # Glass pillar
        for y in range(1, 7):
            setblock(f, cx(x), cy(y), cz(z), "minecraft:cyan_stained_glass")
        setblock(f, cx(x), cy(7), cz(z), "minecraft:sea_lantern")
        setblock(f, cx(x), cy(8), cz(z), "minecraft:cyan_concrete")
        # Sign on ground
        sign_cmd(f, cx(x), cy(1), cz(z), 0, "aqua", "STAR 2077", "STARSTACK")

    # Roadside neon poles (every 8 blocks along x=±3 and z=±3)
    for x in range(-HALF + 5, HALF - 4, 8):
        for z in [-3, 3]:
            fill(f, cx(x), cy(1), cz(z), cx(x), cy(3), cz(z), "minecraft:cyan_concrete")
            setblock(f, cx(x), cy(4), cz(z), "minecraft:sea_lantern")
    for z in range(-HALF + 5, HALF - 4, 8):
        for x in [-3, 3]:
            fill(f, cx(x), cy(1), cz(z), cx(x), cy(3), cz(z), "minecraft:cyan_concrete")
            setblock(f, cx(x), cy(4), cz(z), "minecraft:sea_lantern")

    # Welcome sign at plaza center
    sign_cmd(f, cx(0), cy(1), cz(0), 0, "dark_aqua", "SPAWN", "Cyber City")


# ============================================================
# 9) Clear city (reset)
# ============================================================
with open(f"{OUT_DIR}/clear.mcfunction", "w", encoding="utf-8") as f:
    fill(f, cx(-HALF), cy(-9), cz(-HALF), cx(HALF), cy(80), cz(HALF), "minecraft:air")


# ============================================================
# 10) Main entry build.mcfunction
# ============================================================
with open(f"{OUT_DIR}/build.mcfunction", "w", encoding="utf-8") as f:
    f.write("# Cyberpunk Cyan City - one click build\n")
    f.write("tellraw @a [{\"text\":\"[StarStack] \",\"color\":\"aqua\"},{\"text\":\"Building Cyberpunk City...\",\"color\":\"white\"}]\n")
    f.write("function city:platform\n")
    f.write("function city:roads\n")
    f.write("function city:plaza\n")
    f.write("function city:tower_north\n")
    f.write("function city:tower_east\n")
    f.write("function city:tower_south\n")
    f.write("function city:tower_west\n")
    f.write("function city:neon\n")
    tp_cmd = f"/tp {CX0} {CY0 + 1} {CZ0}"
    f.write(f"tellraw @a [{{\"text\":\"[StarStack] \",\"color\":\"aqua\"}},{{\"text\":\"Done! \",\"color\":\"white\"}},{{\"text\":\"Click to TP\",\"color\":\"yellow\",\"clickEvent\":{{\"action\":\"run_command\",\"value\":\"{tp_cmd}\"}}}}]\n")
    f.write(f"setworldspawn {CX0} {CY0} {CZ0}\n")


# ============================================================
# pack.mcmeta
# ============================================================
META_DIR = "mc/datapacks/cyber-city"
os.makedirs(META_DIR, exist_ok=True)
with open(f"{META_DIR}/pack.mcmeta", "w", encoding="utf-8") as f:
    f.write('{\n')
    f.write('  "pack": {\n')
    f.write('    "pack_format": 48,\n')
    f.write('    "description": "StarStack Cyberpunk Cyan City"\n')
    f.write('  }\n')
    f.write('}\n')

print(f"Generated cyber-city datapack at {META_DIR}")
print("Functions:")
for fn in sorted(os.listdir(OUT_DIR)):
    print(f"  - {fn}")