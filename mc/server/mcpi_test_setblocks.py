import socket, time
HOST = '127.0.0.1'
PORT = 4711
def send(cmd):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    s.connect((HOST, PORT))
    s.sendall((cmd + '\n').encode())
    data = b''
    while b'\n' not in data:
        chunk = s.recv(4096)
        if not chunk: break
        data += chunk
    s.close()
    return data.decode().strip()
# Fill a 3x3x3 region of stone
print("world.setBlocks(0,50,0,2,52,2,1) =", send('world.setBlocks(0,50,0,2,52,2,1)'))
# Verify
print("world.getBlock(0,50,0) =", send('world.getBlock(0,50,0)'))
print("world.getBlock(1,51,1) =", send('world.getBlock(1,51,1)'))
print("world.getBlock(2,52,2) =", send('world.getBlock(2,52,2)'))
