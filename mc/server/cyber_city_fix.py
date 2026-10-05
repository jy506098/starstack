"""
Cyber City Fix - Update sign syntax to modern format and add load.json auto-trigger
"""
import os

OUT_DIR = "mc/datapacks/cyber-city/data/city/functions"
META_DIR = "mc/datapacks/cyber-city"

# 1) Add load.json to auto-build when world loads
load_json = """{
    "values": ["city:build"]
}
"""
with open(f"{META_DIR}/data/city/load.json", "w", encoding="utf-8") as f:
    f.write(load_json)

# 2) Fix sign syntax in all tower/neon functions
# Modern MC 1.20+ uses front_text.messages instead of Text1/Text2
def sign_modern(f, x, y, z, face, color, line1, line2=""):
    block_state = f'minecraft:oak_sign[rotation={face}]'
    text1 = '{"text":"' + line1 + '","color":"' + color + '"}'
    text2 = '{"text":"' + line2 + '","color":"' + color + '"}'
    f.write(f"setblock {x} {y} {z} {block_state}{{front_text:{{messages:['{text1}','{text2}'],color:\"{color}\",has_glowing_text:0b}},back_text:{{messages:['{text1}','{text2}'],color:\"{color}\",has_glowing_text:0b}},is_waxed:0b}}\n")


# Just update build.mcfunction to use a clearer message and add autorun hint
with open(f"{OUT_DIR}/build.mcfunction", "w", encoding="utf-8") as f:
    f.write("# Cyberpunk Cyan City - one click build (also runs on world load via load.json)\n")
    f.write("tellraw @a [{\"text\":\"[StarStack] \",\"color\":\"aqua\"},{\"text\":\"Building Cyberpunk City...\",\"color\":\"white\"}]\n")
    f.write("function city:platform\n")
    f.write("function city:roads\n")
    f.write("function city:plaza\n")
    f.write("function city:tower_north\n")
    f.write("function city:tower_east\n")
    f.write("function city:tower_south\n")
    f.write("function city:tower_west\n")
    f.write("function city:neon\n")
    f.write("setworldspawn 1000 81 1000\n")
    f.write("tellraw @a [{\"text\":\"[StarStack] \",\"color\":\"aqua\"},{\"text\":\"Done! TP to \",\"color\":\"white\"},{\"text\":\"/tp 1000 81 1000\",\"color\":\"yellow\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/tp 1000 81 1000\"}}]\n")

# 3) Replace sign commands in all files using modern syntax
import re

for fn in ["tower_north.mcfunction", "tower_east.mcfunction", "tower_south.mcfunction", "tower_west.mcfunction", "neon.mcfunction"]:
    path = f"{OUT_DIR}/{fn}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Replace old sign syntax with new
    # Old: minecraft:oak_sign[rotation=N]{"Color":"X","Text1":'{...}','Text2':'{...}'}
    # Find these patterns and rewrite them
    lines = content.split("\n")
    new_lines = []
    for line in lines:
        if "oak_sign" in line and line.strip():
            # Parse the old format
            import re as re_mod
            m = re_mod.match(r'setblock (\S+) (\S+) (\S+) minecraft:oak_sign\[rotation=(\d+)\]\{"Color":"(\w+)","Text1":\'(.*?)\',"Text2":\'(.*?)\'\}', line)
            if m:
                x, y, z, face, color, text1, text2 = m.groups()
                new_line = f'setblock {x} {y} {z} minecraft:oak_sign[rotation={face}]{{front_text:{{messages:[{text1},{text2}],color:"{color}",has_glowing_text:0b}},back_text:{{messages:[{text1},{text2}],color:"{color}",has_glowing_text:0b}},is_waxed:0b}}\n'
                new_lines.append(new_line)
            else:
                new_lines.append(line)
        else:
            new_lines.append(line)

    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(new_lines))

print("Updated load.json and sign format")
print("Files modified:")
for fn in ["tower_north.mcfunction", "tower_east.mcfunction", "tower_south.mcfunction", "tower_west.mcfunction", "neon.mcfunction", "build.mcfunction"]:
    print(f"  - {fn}")