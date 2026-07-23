using Microsoft.Testing.Platform.Builder;

namespace TddGuard.Dotnet;

public static class TestingPlatformBuilderHook
{
    public static void AddExtensions(ITestApplicationBuilder builder, string[] _)
        => TddGuardBuilder.Register(builder);
}
