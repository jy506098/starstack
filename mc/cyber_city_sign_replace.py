"""
Cyber City - Manual sign replacement
"""
import re

OUT_DIR = "mc/world/datapacks/cyber-city/data/city/functions"

# Modern sign NBT format for MC 1.20+
def build_sign(x, y, z, face, color, line1, line2=""):
    text1 = '{"text":"' + line1 + '","color":"' + color + '"}'
    text2 = '{"text":"' + line2 + '","color":"' + color + '"}'
    return f"setblock {x} {y} {z} minecraft:oak_sign[rotation={face}]{{front_text:{{messages:[{text1},{text2}],color:\"{color}\",has_glowing_text:0b}},back_text:{{messages:[{text1},{text2}],color:\"{color}\",has_glowing_text:0b}},is_waxed:0b}}\n"

# Sign list - (file, command_to_replace, new_command)
# Just write all the sign commands fresh, append to tower files

# 1) North tower
signs = {
    "tower_north.mcfunction": [
        # (x, y, z, face, color, line1, line2)
        (1000, 100, 955, 0, "aqua", "CYBER", "NETWORK"),
    ],
    "tower_east.mcfunction": [
        (1045, 95, 1000, 4, "aqua", "DATA", "HUB"),
    ],
    "tower_south.mcfunction": [
        (1000, 95, 1037, 8, "aqua", "Future Tech", "LAB"),
    ],
    "tower_west.mcfunction": [
        (955, 95, 1000, 12, "aqua", "MARKET", "ZONE"),
    ],
    "neon.mcfunction": [
        (1000, 81, 955, 0, "aqua", "STAR 2077", "STARSTACK"),
        (1000, 81, 1045, 0, "aqua", "STAR 2077", "STARSTACK"),
        (955, 81, 1000, 0, "aqua", "STAR 2077", "STARSTACK"),
        (1045, 81, 1000, 0, "aqua", "STAR 2077", "STARSTACK"),
        (1000, 81, 1000, 0, "dark_aqua", "SPAWN", "Cyber City"),
    ],
}

for filename, sign_list in signs.items():
    path = f"{OUT_DIR}/{filename}"
    with open(path, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()

    # Remove old broken sign commands (any line containing oak_sign)
    new_lines = [l for l in lines if "oak_sign" not in l]

    # Append new properly formatted sign commands
    for x, y, z, face, color, l1, l2 in sign_list:
        new_lines.append(build_sign(x, y, z, face, color, l1, l2).strip())

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(new_lines) + "\n")
    print(f"Updated {filename} with {len(sign_list)} signs")

# Verify
print("\nVerification:")
for filename in ["tower_north.mcfunction", "tower_east.mcfunction", "tower_south.mcfunction", "tower_west.mcfunction", "neon.mcfunction"]:
    path = f"{OUT_DIR}/{filename}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    sign_count = content.count("oak_sign")
    print(f"  {filename}: {sign_count} sign(s)")