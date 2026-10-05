"""Test the RaspberryPi mcpi bridge."""
import socket
import time

HOST = '127.0.0.1'
PORT = 4711

def send(cmd, retries=3):
    for i in range(retries):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(5)
            s.connect((HOST, PORT))
            s.sendall((cmd + '\n').encode())
            data = b''
            while b'\n' not in data:
                chunk = s.recv(4096)
                if not chunk:
                    break
                data += chunk
            s.close()
            if data:
                return data.decode().strip()
        except (ConnectionRefusedError, socket.timeout) as e:
            print(f"retry {i+1}: {e}")
            time.sleep(1)
    return None

# Test world.getBlock
print("world.getBlock(0,100,0) =", send('world.getBlock(0,100,0)'))

# Test world.getHeight
print("world.getHeight(0,0) =", send('world.getHeight(0,0)'))

# Test chat.post (server will broadcast it)
print("chat.post(\"hello from python\") =", send('chat.post("hello from python")'))

# Test events.clearAll
print("events.clearAll() =", send('events.clearAll()'))

# Test setBlock
print("world.setBlock(0,100,0,1) =", send('world.setBlock(0,100,0,1)'))

# Test getBlock after setBlock
print("world.getBlock(0,100,0) =", send('world.getBlock(0,100,0)'))

# Test world.getBlocks with packed string
print("world.getBlocks(0,99,0,2,99,2) =", repr(send('world.getBlocks(0,99,0,2,99,2)')))