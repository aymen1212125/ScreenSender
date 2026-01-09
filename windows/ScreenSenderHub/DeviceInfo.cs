using System;

namespace ScreenSenderHub;

public sealed class DeviceInfo
{
    public string Id { get; init; } = "";
    public string Name { get; init; } = "";
    public string Platform { get; init; } = "";
    public string Ip { get; init; } = "";
    public int ControlPort { get; init; }
    public DateTime ConnectedAt { get; init; } = DateTime.UtcNow;
}
