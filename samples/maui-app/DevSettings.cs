namespace DynamicMauiDemo;

/// <summary>In-memory environment settings exposed by the demo's developer tools.</summary>
public static class DevSettings
{
    public const string DefaultEnvironmentId = DemoConfig.EnvironmentId;
    public const string DefaultApiBaseUrl = DemoConfig.ApiBaseUrl;

    public static string EnvironmentId { get; set; } = DefaultEnvironmentId;
    public static string ApiBaseUrl { get; set; } = DefaultApiBaseUrl;

    public static void Reset()
    {
        EnvironmentId = DefaultEnvironmentId;
        ApiBaseUrl = DefaultApiBaseUrl;
    }
}
