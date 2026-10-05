$pass = "test"
$port = 25575
function RconSend($cmd) {
    $sock = New-Object System.Net.Sockets.TcpClient("127.0.0.1", $port)
    $stream = $sock.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream)
    $reader = New-Object System.IO.StreamReader($stream)
    $payload = [System.Text.Encoding]::UTF8.GetBytes($cmd)
    $len = 4 + 4 + $payload.Length + 2
    $lenBytes = [System.BitConverter]::GetBytes([int32]$len)
    $id = [System.BitConverter]::GetBytes([int32]1)
    $reqBytes = New-Object byte[] ($len)
    [Array]::Copy($lenBytes, 0, $reqBytes, 0, 4)
    [Array]::Copy($id, 0, $reqBytes, 4, 4)
    [Array]::Copy($payload, 0, $reqBytes, 8, $payload.Length)
    $reqBytes[$reqBytes.Length-2] = 0
    $reqBytes[$reqBytes.Length-1] = 0
    $stream.Write($reqBytes, 0, $reqBytes.Length)
    $stream.Flush()
    Start-Sleep -Milliseconds 200
    $buf = New-Object byte[] 8192
    $resp = $stream.Read($buf, 0, 8192)
    $sock.Close()
    return [System.Text.Encoding]::UTF8.GetString($buf, 0, $resp)
}
Write-Host "--- /help ---"
RconSend "help"
Write-Host "--- /list ---"
RconSend "list"
Write-Host "--- /time day ---"
RconSend "time day"
Write-Host "--- /weather sun ---"
RconSend "weather sun"
Write-Host "--- /gamemode ---"
RconSend "gamemode survival"
