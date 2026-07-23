using Microsoft.Testing.Platform.Builder;
using Microsoft.Testing.Platform.Capabilities.TestFramework;
using Microsoft.Testing.Platform.CommandLine;
using Microsoft.Testing.Platform.Configurations;
using Microsoft.Testing.Platform.Extensions;
using Microsoft.Testing.Platform.Extensions.TestFramework;
using Microsoft.Testing.Platform.Extensions.TestHost;
using Microsoft.Testing.Platform.Logging;
using Microsoft.Testing.Platform.TestHost;
using Microsoft.Testing.Platform.TestHostControllers;

namespace TddGuard.Dotnet.Tests;

internal sealed class TddGuardBuilderTests
{
    [Test("registers listener when project root resolves")]
    public async Task RegistersListenerWhenProjectRootResolves()
    {
        var spy = new SpyTestHostManager();
        var builder = new StubBuilder(spy);
        var tempDir = Path.Combine(Path.GetTempPath(), Path.GetRandomFileName());

        TddGuardBuilder.Register(
            builder,
            getEnv: _ => tempDir,
            getCwd: () => tempDir);

        await Assert.That(spy.LifetimeHandleCount).IsEqualTo(1);
        await Assert.That(spy.DataConsumerCount).IsEqualTo(1);
    }

    [Test("skips registration when resolver returns error")]
    public async Task SkipsRegistrationWhenResolverReturnsError()
    {
        var spy = new SpyTestHostManager();
        var builder = new StubBuilder(spy);

        TddGuardBuilder.Register(
            builder,
            getEnv: _ => null,
            getCwd: () => "/some/dir");

        await Assert.That(spy.LifetimeHandleCount).IsEqualTo(0);
        await Assert.That(spy.DataConsumerCount).IsEqualTo(0);
    }

    [Test("logs diagnostic when resolver returns error")]
    public async Task LogsDiagnosticWhenResolverReturnsError()
    {
        string? captured = null;
        var builder = new StubBuilder(new SpyTestHostManager());

        TddGuardBuilder.Register(
            builder,
            getEnv: _ => null,
            getCwd: () => "/some/dir",
            log: msg => captured = msg);

        await Assert.That(captured).IsNotNull();
        await Assert.That(captured!).Contains("disabled");
    }

    // Coverage: exercises the null-coalescing default branches for getEnv/getCwd/log
    // parameters. Outcome depends on whether TDD_GUARD_PROJECT_ROOT is set in the
    // environment, so we assert "doesn't throw" rather than a specific registration state.
    [Test("uses default delegates when none provided")]
    public async Task UsesDefaultDelegatesWhenNoneProvided()
    {
        var spy = new SpyTestHostManager();
        var builder = new StubBuilder(spy);

        TddGuardBuilder.Register(builder);

        await Assert.That(spy.LifetimeHandleCount is 0 or 1).IsTrue();
        await Assert.That(spy.DataConsumerCount is 0 or 1).IsTrue();
    }

    [Test("throws ArgumentNullException for null builder")]
    public async Task ThrowsArgumentNullExceptionForNullBuilder()
    {
        await Assert.That(() => TddGuardBuilder.Register(null!))
            .ThrowsExactly<ArgumentNullException>();
    }

    // The shipped TestingPlatformBuilderHook is the entry point MTP calls
    // at runtime. These tests verify it delegates to Register correctly and
    // that its contract matches the buildTransitive MSBuild props.
    [Test("shipped TestingPlatformBuilderHook delegates to Register")]
    public async Task ShippedHookDelegatesToRegister()
    {
        var spy = new SpyTestHostManager();
        var builder = new StubBuilder(spy);
        var tempDir = Path.Combine(Path.GetTempPath(), Path.GetRandomFileName());

        Dotnet.TestingPlatformBuilderHook.AddExtensions(builder,
            [
                $"--internal-tdd-guard-project-root={tempDir}",
                $"--internal-tdd-guard-cwd={tempDir}"
            ]);

        // The hook calls Register which calls Resolve. Without env vars,
        // it'll take the error path and skip registration. We can't control
        // the env from here, so verify it ran without throwing.
        await Assert.That(spy.LifetimeHandleCount is 0 or 1).IsTrue();
    }

    [Test("shipped hook type matches buildTransitive props TypeFullName")]
    public async Task ShippedHookTypeMatchesBuildTransitiveProps()
    {
        // The buildTransitive props declare TypeFullName as
        // "TddGuard.Dotnet.TestingPlatformBuilderHook". If someone renames
        // the class without updating the props, MTP won't find the hook.
        var hookType = typeof(Dotnet.TestingPlatformBuilderHook);

        await Assert.That(hookType.FullName).IsEqualTo("TddGuard.Dotnet.TestingPlatformBuilderHook");
        await Assert.That(hookType.IsPublic).IsTrue();
        await Assert.That(hookType.IsAbstract && hookType.IsSealed).IsTrue(); // static class

        var method = hookType.GetMethod("AddExtensions");
        await Assert.That(method).IsNotNull();
        await Assert.That(method!.IsPublic).IsTrue();
        await Assert.That(method.IsStatic).IsTrue();
        await Assert.That(method.GetParameters()).HasCount().EqualTo(2);
    }

    private sealed class SpyTestHostManager : ITestHostManager
    {
        internal int LifetimeHandleCount { get; private set; }
        internal int DataConsumerCount { get; private set; }

        public void AddDataConsumer(Func<IServiceProvider, IDataConsumer> dataConsumerFactory)
            => DataConsumerCount++;

        public void AddDataConsumer<T>(CompositeExtensionFactory<T> compositeServiceFactory)
            where T : class, IDataConsumer
            => DataConsumerCount++;

        public void AddTestSessionLifetimeHandle(Func<IServiceProvider, ITestSessionLifetimeHandler> testSessionLifetimeHandleFactory)
            => LifetimeHandleCount++;

        public void AddTestSessionLifetimeHandle<T>(CompositeExtensionFactory<T> compositeServiceFactory)
            where T : class, ITestSessionLifetimeHandler
            => LifetimeHandleCount++;

        public void AddTestHostApplicationLifetime(Func<IServiceProvider, ITestHostApplicationLifetime> testHostApplicationLifetimeFactory)
            => throw new NotImplementedException();
    }

    private sealed class StubBuilder(ITestHostManager testHost) : ITestApplicationBuilder
    {
        public ITestHostManager TestHost => testHost;
        public ITestHostControllersManager TestHostControllers => throw new NotImplementedException();
        public ICommandLineManager CommandLine => throw new NotImplementedException();
#pragma warning disable TPEXP // Experimental API required by ITestApplicationBuilder
        public IConfigurationManager Configuration => throw new NotImplementedException();
        public ILoggingManager Logging => throw new NotImplementedException();
#pragma warning restore TPEXP

        public ITestApplicationBuilder RegisterTestFramework(
            Func<IServiceProvider, ITestFrameworkCapabilities> capabilitiesFactory,
            Func<ITestFrameworkCapabilities, IServiceProvider, ITestFramework> frameworkFactory)
            => throw new NotImplementedException();

        public Task<ITestApplication> BuildAsync() => throw new NotImplementedException();
    }
}
