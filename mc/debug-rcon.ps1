Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Net.Sockets;
using System.Text;
public class Rcon {
    public static string Send(string host, int port, string password, string cmd) {
        try {
            TcpClient client = new TcpClient(host, port);
            client.ReceiveTimeout = 5000;
            client.SendTimeout = 3000;
            NetworkStream stream = client.GetStream();
            byte[] loginPayload = MakePacket(3, password);
            stream.Write(loginPayload, 0, loginPayload.Length);
            byte[] loginResp = ReadPacket(stream);
            if (loginResp == null) { client.Close(); return "no response (login)"; }
            byte[] cmdPayload = MakePacket(2, cmd);
            stream.Write(cmdPayload, 0, cmdPayload.Length);
            byte[] resp = ReadPacket(stream);
            client.Close();
            if (resp == null) return "no response (cmd)";
            string hex = BitConverter.ToString(resp);
            string text = Encoding.UTF8.GetString(resp, 8, resp.Length - 10).TrimEnd('\0');
            return "LEN=" + resp.Length + "\nHEX=" + hex + "\nTEXT=" + text;
        } catch (Exception e) {
            return "ERR: " + e.Message;
        }
    }
    static byte[] MakePacket(int type, string payload) {
        byte[] data = Encoding.UTF8.GetBytes(payload + "\0\0");
        byte[] len = BitConverter.GetBytes(data.Length + 8);
        byte[] id = BitConverter.GetBytes(0x12345678);
        byte[] t = BitConverter.GetBytes(type);
        byte[] packet = new byte[12 + data.Length];
        Array.Copy(len, 0, packet, 0, 4);
        Array.Copy(id, 0, packet, 4, 4);
        Array.Copy(t, 0, packet, 8, 4);
        Array.Copy(data, 0, packet, 12, data.Length);
        return packet;
    }
    static byte[] ReadPacket(NetworkStream stream) {
        byte[] lenBuf = new byte[4];
        int totalRead = 0;
        while (totalRead < 4) {
            int n = stream.Read(lenBuf, totalRead, 4 - totalRead);
            if (n <= 0) return null;
            totalRead += n;
        }
        int len = BitConverter.ToInt32(lenBuf, 0);
        byte[] body = new byte[len];
        int got = 0;
        while (got < len) {
            int n = stream.Read(body, got, len - got);
            if (n <= 0) break;
            got += n;
        }
        return body;
    }
}
"@
$r = [Rcon]::Send("127.0.0.1", 25575, "starstack", $args[0])
Write-Output $r