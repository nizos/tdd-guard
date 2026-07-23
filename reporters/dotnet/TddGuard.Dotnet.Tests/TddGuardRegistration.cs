using Microsoft.Testing.Platform.Builder;

#pragma warning disable CA1050 // Declare types in namespaces
#pragma warning disable CA1515 // MTP codegen requires this class to be public

/// <summary>
/// MTP builder hook required by the <c>TestingPlatformBuilderHook</c> MSBuild item
/// in the test csproj. MTP codegen expects this class in the global namespace with
/// exactly this name and signature. Left empty so the extension tests can construct
/// <see cref="TddGuard.Dotnet.TddGuardListener"/> directly with a spy writer.
/// </summary>
public static class TestingPlatformBuilderHook
{
    public static void AddExtensions(ITestApplicationBuilder builder, string[] _)
    {
        // Don't register TDD Guard when running the test project's own tests.
        // The extension tests use a spy writer via direct construction.
    }
}
