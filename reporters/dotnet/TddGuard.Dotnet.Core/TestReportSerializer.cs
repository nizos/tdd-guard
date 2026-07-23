using System.Text.Json;
using System.Text.Json.Serialization;

namespace TddGuard.Dotnet.Core;

/// <summary>
/// Serialises <see cref="TestRunOutput"/> to compact camelCase JSON,
/// omitting null properties to match the TDD Guard wire format.
/// </summary>
public static class TestReportSerializer
{
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false,
    };

    public static string Serialize(this TestRunOutput output)
    {
        return JsonSerializer.Serialize(output, Options);
    }
}
