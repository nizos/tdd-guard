using System.Text.Json;

namespace TddGuard.Dotnet.Tests;

internal static class TestJsonAssert
{
    internal static JsonElement Parse(string json)
        => JsonDocument.Parse(json).RootElement;

    internal static string Reason(this JsonElement root)
        => root.GetProperty("reason").GetString()!;

    internal static JsonElement Module(this JsonElement root, int index = 0)
        => root.GetProperty("testModules")[index];

    internal static string ModuleId(this JsonElement module)
        => module.GetProperty("moduleId").GetString()!;

    internal static JsonElement Tests(this JsonElement module)
        => module.GetProperty("tests");

    internal static JsonElement Test(this JsonElement module, int index = 0)
        => module.GetProperty("tests")[index];

    internal static string State(this JsonElement test)
        => test.GetProperty("state").GetString()!;

    internal static string Name(this JsonElement test)
        => test.GetProperty("name").GetString()!;

    internal static string FullName(this JsonElement test)
        => test.GetProperty("fullName").GetString()!;

    internal static bool HasErrors(this JsonElement test)
        => test.TryGetProperty("errors", out _);

    internal static string ErrorMessage(this JsonElement test, int index = 0)
        => test.GetProperty("errors")[index].GetProperty("message").GetString()!;
}
