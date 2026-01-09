using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ScreenSenderHub;

public sealed class ControlServer
{
    private readonly Action<DeviceInfo> onDeviceConnected;
    private readonly Action<string> onDeviceDisconnected;
    private TcpListener? listener;
    private CancellationTokenSource? cts;
    private readonly ConcurrentDictionary<string, TcpClient> clients = new();

    public ControlServer(Action<DeviceInfo> onDeviceConnected, Action<string> onDeviceDisconnected)
    {
        this.onDeviceConnected = onDeviceConnected;
        this.onDeviceDisconnected = onDeviceDisconnected;
    }

    public void Start(int port)
    {
        Stop();
        cts = new CancellationTokenSource();
        listener = new TcpListener(IPAddress.Any, port);
        listener.Start();
        _ = AcceptLoopAsync(listener, cts.Token);
    }

    public void Stop()
    {
        try { cts?.Cancel(); } catch { }
        try { listener?.Stop(); } catch { }
        foreach (var client in clients.Values)
        {
            try { client.Close(); } catch { }
        }
        clients.Clear();
        cts = null;
        listener = null;
    }

    private async Task AcceptLoopAsync(TcpListener tcpListener, CancellationToken token)
    {
        while (!token.IsCancellationRequested)
        {
            TcpClient? client = null;
            try
            {
                client = await tcpListener.AcceptTcpClientAsync(token);
                _ = HandleClientAsync(client, token);
            }
            catch when (token.IsCancellationRequested)
            {
                return;
            }
            catch
            {
                try { client?.Close(); } catch { }
            }
        }
    }

    private async Task HandleClientAsync(TcpClient client, CancellationToken token)
    {
        string? deviceId = null;
        try
        {
            using var stream = client.GetStream();
            using var reader = new StreamReader(stream, Encoding.UTF8, false, 1024, leaveOpen: true);
            using var writer = new StreamWriter(stream, Encoding.UTF8, 1024, leaveOpen: true) { AutoFlush = true };

            var line = await reader.ReadLineAsync(token);
            if (line == null) return;

            var device = ParseHello(line, client);
            deviceId = device.Id;
            clients[deviceId] = client;
            onDeviceConnected(device);

            while (!token.IsCancellationRequested)
            {
                var msg = await reader.ReadLineAsync(token);
                if (msg == null) break;
                if (msg.StartsWith("PONG", StringComparison.OrdinalIgnoreCase)) continue;
                if (msg.StartsWith("PING", StringComparison.OrdinalIgnoreCase))
                {
                    await writer.WriteLineAsync("PONG");
                }
            }
        }
        catch
        {
            // Best-effort: disconnection will clean up.
        }
        finally
        {
            try { client.Close(); } catch { }
            if (deviceId != null)
            {
                clients.TryRemove(deviceId, out _);
                onDeviceDisconnected(deviceId);
            }
        }
    }

    private static DeviceInfo ParseHello(string line, TcpClient client)
    {
        var parts = line.Split('|');
        var id = parts.Length > 1 ? parts[1] : Guid.NewGuid().ToString("N");
        var name = parts.Length > 2 ? parts[2] : "Device";
        var platform = parts.Length > 3 ? parts[3] : "unknown";
        var endPoint = (IPEndPoint)client.Client.RemoteEndPoint!;
        var ip = endPoint.Address.ToString();
        var port = endPoint.Port;

        return new DeviceInfo
        {
            Id = id,
            Name = name,
            Platform = platform,
            Ip = ip,
            ControlPort = port,
            ConnectedAt = DateTime.UtcNow
        };
    }
}
