using System;
using System.Collections.Generic;
using System.Collections.ObjectModel;
using System.Linq;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Windows;

namespace ScreenSenderHub;

public partial class MainWindow : Window
{
    private readonly ObservableCollection<DeviceInfo> devices = new();
    private readonly Dictionary<string, DeviceInfo> deviceIndex = new();
    private readonly ControlServer controlServer;
    private readonly UdpBroadcaster broadcaster;
    private readonly string deviceId = Guid.NewGuid().ToString("N");

    public MainWindow()
    {
        InitializeComponent();
        DevicesList.ItemsSource = devices;

        controlServer = new ControlServer(OnDeviceConnected, OnDeviceDisconnected);
        broadcaster = new UdpBroadcaster();

        ControlPortBox.Text = NetworkConfig.DefaultControlPort.ToString();
        LocalIpText.Text = string.Join(", ", GetLocalIPv4());
        RestartServerBtn.Click += (_, _) => RestartServer();

        Loaded += (_, _) => RestartServer();
        Closed += (_, _) => ShutdownNetworking();
    }

    private void RestartServer()
    {
        if (!int.TryParse(ControlPortBox.Text.Trim(), out var port) || port is < 1 or > 65535)
        {
            ServerStatusText.Text = "Server: invalid port";
            return;
        }

        try
        {
            controlServer.Start(port);
            broadcaster.Start(deviceId, Environment.MachineName, port, NetworkConfig.DiscoveryPort);
            ServerStatusText.Text = $"Server: listening on {port} (UDP {NetworkConfig.DiscoveryPort})";
        }
        catch (Exception ex)
        {
            ServerStatusText.Text = $"Server error: {ex.Message}";
        }
    }

    private void ShutdownNetworking()
    {
        broadcaster.Stop();
        controlServer.Stop();
    }

    private void OnDeviceConnected(DeviceInfo device)
    {
        Dispatcher.Invoke(() =>
        {
            if (deviceIndex.TryGetValue(device.Id, out var existing))
            {
                devices.Remove(existing);
                deviceIndex.Remove(device.Id);
            }

            devices.Add(device);
            deviceIndex[device.Id] = device;
        });
    }

    private void OnDeviceDisconnected(string deviceIdToRemove)
    {
        Dispatcher.Invoke(() =>
        {
            if (!deviceIndex.TryGetValue(deviceIdToRemove, out var existing)) return;
            devices.Remove(existing);
            deviceIndex.Remove(deviceIdToRemove);
        });
    }

    private static IEnumerable<string> GetLocalIPv4()
    {
        return NetworkInterface.GetAllNetworkInterfaces()
            .Where(nic => nic.OperationalStatus == OperationalStatus.Up)
            .SelectMany(nic => nic.GetIPProperties().UnicastAddresses)
            .Where(addr => addr.Address.AddressFamily == AddressFamily.InterNetwork)
            .Select(addr => addr.Address.ToString())
            .Distinct()
            .ToList();
    }
}
