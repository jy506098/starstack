import socket, sys

def send(cmd):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(10)
    s.connect(('127.0.0.1', 25575))
    def build_packet(req_id, pkt_type, payload):
        # payload should be null-terminated already
        length = 4 + 4 + len(payload) + 2
        pkt = length.to_bytes(4, 'little', signed=True) + req_id.to_bytes(4, 'little', signed=True) + pkt_type.to_bytes(4, 'little', signed=True) + payload + b'\x00\x00'
        return pkt
    # Auth
    pkt = build_packet(0, 3, b'starstack\x00')
    s.sendall(pkt)
    # Read response
    hdr = b''
    while len(hdr) < 4:
        chunk = s.recv(4 - len(hdr))
        if not chunk: break
        hdr += chunk
    if len(hdr) < 4:
        s.close()
        return "[no auth hdr]"
    length = int.from_bytes(hdr, 'little', signed=True)
    if length < 0:
        s.close()
        return "[bad auth length]"
    body = b''
    while len(body) < length:
        chunk = s.recv(length - len(body))
        if not chunk: break
        body += chunk
    req_id = int.from_bytes(body[:4], 'little', signed=True)
    if req_id == -1:
        s.close()
        return "[auth failed]"
    # Now command
    pkt = build_packet(1, 2, cmd.encode('utf-8') + b'\x00')
    s.sendall(pkt)
    hdr = b''
    while len(hdr) < 4:
        chunk = s.recv(4 - len(hdr))
        if not chunk: break
        hdr += chunk
    if len(hdr) < 4:
        s.close()
        return "[no cmd hdr]"
    length = int.from_bytes(hdr, 'little', signed=True)
    body = b''
    while len(body) < length:
        chunk = s.recv(length - len(body))
        if not chunk: break
        body += chunk
    s.close()
    # Strip req_id (4) + type (4) + null terminator at end + 2 padding bytes
    text = body[8:]
    if text.endswith(b'\x00\x00'):
        text = text[:-2]
    elif text.endswith(b'\x00'):
        text = text[:-1]
    return text.decode('utf-8', errors='replace')

if __name__ == '__main__':
    cmd = ' '.join(sys.argv[1:]) if len(sys.argv) > 1 else 'help'
    print(send(cmd))