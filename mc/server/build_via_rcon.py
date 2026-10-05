"""
Build Cyber City via RCON - bypass datapack function loading
Reads the .mcfunction files and sends commands one by one via RCON
"""
import os
import sys
import time

# Import RCON
sys.path.insert(0, 'mc')
from rcon import rcon

RCON_HOST = '10.0.0.124'
RCON_PORT = 25575
RCON_PASS = 'StarStack2026'

# Functions to execute in order
FUNCTIONS = [
    'platform',
    'roads',
    'plaza',
    'tower_north',
    'tower_east',
    'tower_south',
    'tower_west',
    'neon',
]

FUNC_DIR = 'mc/world/datapacks/cyber-city/data/city/functions'


def send_rcon(cmd, retries=2):
    """Send RCON with retries"""
    for i in range(retries):
        try:
            result = rcon(RCON_HOST, RCON_PORT, RCON_PASS, cmd)
            return result
        except Exception as e:
            print(f"RCON error (attempt {i+1}): {e}")
            time.sleep(0.5)
    return None


def execute_file(filename):
    """Execute a .mcfunction file via RCON"""
    path = f"{FUNC_DIR}/{filename}.mcfunction"
    if not os.path.exists(path):
        print(f"  Missing: {path}")
        return False

    with open(path, 'r', encoding='utf-8') as f:
        lines = [l.strip() for l in f if l.strip() and not l.strip().startswith('#')]

    print(f"  {filename}.mcfunction: {len(lines)} commands")

    success = 0
    failed = 0
    for i, line in enumerate(lines):
        result = send_rcon(line)
        # Skip tells (info messages) - they don't matter
        if result is None or 'Unknown' in str(result) or 'error' in str(result).lower() or 'Error' in str(result):
            if 'tellraw' in line:
                # tellraw returning empty means no players, that's fine
                success += 1
            else:
                failed += 1
                if failed <= 3:
                    print(f"    Line {i+1} failed: {line[:60]}... -> {str(result)[:80]}")
        else:
            success += 1

    print(f"  -> Success: {success}, Failed: {failed}")
    return failed == 0


def main():
    print(f"Building Cyber City via RCON @ {RCON_HOST}:{RCON_PORT}")
    print(f"Functions: {FUNCTIONS}\n")

    # Set spawn
    send_rcon('setworldspawn 1000 81 1000')
    send_rcon('tp @a 1000 81 1000')

    for func in FUNCTIONS:
        print(f"\n[Function: {func}]")
        execute_file(func)
        time.sleep(0.1)

    print("\n=== Done! ===")
    print("Server is ready at (1000, 81, 1000)")


if __name__ == '__main__':
    main()