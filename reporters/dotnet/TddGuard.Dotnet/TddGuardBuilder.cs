using Microsoft.Testing.Platform.Builder;
using Microsoft.Testing.Platform.Extensions;
using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet;

/// <summary>
/// Public entry point for MTP V2 auto-registration.
/// Called by the generated <c>TestingPlatformBuilderHook</c> via the
/// <c>buildTransitive/*.props</c> MSBuild item shipped in the NuGet package.
/// </summary>
public static class TddGuardBuilder
{
    public static void Register(
        ITestApplicationBuilder builder,
        GetEnvironmentVariable? getEnv = null,
        GetCurrentWorkingDirectory? getCwd = null,
        LogDiagnostic? log = null)
    {
        ArgumentNullException.ThrowIfNull(builder);
        var diagnose = log ?? (msg => Console.Error.WriteLine($"[tdd-guard-dotnet] {msg}"));

        ProjectRootResolver.Resolve(
            getEnv ?? Environment.GetEnvironmentVariable,
            getCwd ?? Directory.GetCurrentDirectory)
        .LogOnError(diagnose)
        .Switch(
            root =>
            {
                var write = ReportFileWriter.Create(root.Path)
                    .WithDiagnostics(diagnose);
                var compositeFactory = new CompositeExtensionFactory<TddGuardListener>(
                    () => new TddGuardListener(write));
                builder.TestHost.AddTestSessionLifetimeHandle(compositeFactory);
                builder.TestHost.AddDataConsumer(compositeFactory);
            },
            error => { } // Disabled — already logged by LogOnError
        );
    }
}
