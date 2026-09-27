"""
Cyber City Fix 2 - CRLF to LF, try multiple pack_formats
"""
import os

DP = "mc/world/datapacks/cyber-city"


def to_lf(path):
    """Convert file to LF line endings"""
    with open(path, "rb") as f:
        data = f.read()
    # Strip UTF-8 BOM
    if data.startswith(b'\xef\xbb\xbf'):
        data = data[3:]
    # Convert CRLF to LF
    data = data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')
    with open(path, "wb") as f:
        f.write(data)


# Walk all files and convert
for root, dirs, files in os.walk(DP):
    for fn in files:
        path = os.path.join(root, fn)
        to_lf(path)
        print(f"LF: {path}")

# Try multiple pack_formats (try the latest known: 26.x might map to high pack_format)
# MC 1.21.x uses 34, 1.21.2-3 uses 42, 1.21.4 uses 46
# For MC 26.3 we need to guess - try high values
for pf in [48, 57, 64, 71]:
    meta_path = f"{DP}/pack.mcmeta"
    with open(meta_path, "w", encoding="utf-8") as f:
        f.write("{\n")
        f.write('  "pack": {\n')
        f.write(f'    "pack_format": {pf},\n')
        f.write('    "description": "StarStack Cyberpunk Cyan City"\n')
        f.write("  }\n")
        f.write("}\n")
    to_lf(meta_path)

# Final: write the pack.mcmeta with pack_format: 48 (most likely)
with open(f"{DP}/pack.mcmeta", "w", encoding="utf-8") as f:
    f.write('{\n')
    f.write('  "pack": {\n')
    f.write('    "pack_format": 48,\n')
    f.write('    "description": "StarStack Cyberpunk Cyan City"\n')
    f.write('  }\n')
    f.write('}\n')

to_lf(f"{DP}/pack.mcmeta")
to_lf(f"{DP}/data/city/load.json")

# Verify
print("\n--- pack.mcmeta ---")
with open(f"{DP}/pack.mcmeta", "rb") as f:
    print(f.read()[:200])
print("\n--- load.json ---")
with open(f"{DP}/data/city/load.json", "rb") as f:
    print(f.read())

# Also verify all function files are clean
print("\n--- build.mcfunction (first 5 lines) ---")
with open(f"{DP}/data/city/functions/build.mcfunction", "rb") as f:
    for i, line in enumerate(f):
        if i >= 5: break
        print(line)