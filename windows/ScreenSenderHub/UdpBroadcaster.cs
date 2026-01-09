using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace ScreenSenderHub;

public sealed class UdpBroadcaster
{
    private UdpClient? client;
    private CancellationTokenSource? cts;

    public void Start(string deviceId, string deviceName, int controlPort, int discoveryPort)
    {
        Stop();
        cts = new CancellationTokenSource();
        client = new UdpClient
        {
            EnableBroadcast = true
        };
        _ = BroadcastLoopAsync(deviceId, deviceName, controlPort, discoveryPort, cts.Token);
    }

    public void Stop()
    {
        try { cts?.Cancel(); } catch { }
        try { client?.Close(); } catch { }
        cts = null;
        client = null;
    }

    private async Task BroadcastLoopAsync(
        string deviceId,
        string deviceName,
        int controlPort,
        int discoveryPort,
        CancellationToken token)
    {
        var endpoint = new IPEndPoint(IPAddress.Broadcast, discoveryPort);
        while (!token.IsCancellationRequested)
        {
            try
            {
                var payload = $"SCREENSENDER|DISCOVERY|{deviceId}|{deviceName}|{controlPort}";
                var bytes = Encoding.UTF8.GetBytes(payload);
                await client!.SendAsync(bytes, bytes.Length, endpoint);
            }
            catch
            {
                // Ignore broadcast failures, keep trying.
            }

            try
            {
                await Task.Delay(1000, token);
            }
            catch
            {
                return;
            }
        }
    }
}
