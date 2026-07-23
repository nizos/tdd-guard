using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet.Tests;

internal sealed class ProjectRootResolverTests
{
    [Test("prefers TDD_GUARD_PROJECT_ROOT env var")]
    public async Task PrefersProjectRootEnvVar()
    {
        var root = "/custom/root";
        GetEnvironmentVariable getEnv = name => name == "TDD_GUARD_PROJECT_ROOT" ? root : null;

        var result = ProjectRootResolver.Resolve(getEnv, () => root);
        await Assert.That(result.IsT0).IsTrue();
        await Assert.That(result.AsT0.Path).IsEqualTo(Path.GetFullPath(root));
    }

    [Test("falls back to CLAUDE_PROJECT_DIR")]
    public async Task FallsBackToClaudeProjectDir()
    {
        var dir = "/claude/dir";

        var result = ProjectRootResolver.Resolve(
            name => name == "CLAUDE_PROJECT_DIR" ? dir : null,
            () => dir);
        await Assert.That(result.IsT0).IsTrue();
        await Assert.That(result.AsT0.Path).IsEqualTo(Path.GetFullPath(dir));
    }

    [Test("returns error when neither env var is set (ADR-010)")]
    public async Task ReturnsErrorWhenNeitherEnvVarIsSet()
    {
        var result = ProjectRootResolver.Resolve(
            _ => null,
            () => "/fallback/cwd");
        await Assert.That(result.IsT1).IsTrue();
        await Assert.That(result.AsT1.Reason).Contains("TDD_GUARD_PROJECT_ROOT");
    }

    [Test("returns error when env var is empty string")]
    public async Task ReturnsErrorWhenEnvVarIsEmptyString()
    {
        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? string.Empty : null,
            () => "/some/dir");
        await Assert.That(result.IsT1).IsTrue();
    }

    [Test("returns error when cwd is outside project root")]
    public async Task ReturnsErrorWhenCwdIsOutsideProjectRoot()
    {
        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? "/project/root" : null,
            () => "/somewhere/else");
        await Assert.That(result.IsT1).IsTrue();
    }

    [Test("accepts cwd equal to root")]
    public async Task AcceptsCwdEqualToRoot()
    {
        var root = Path.Combine(Path.GetTempPath(), "test-root");

        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? root : null,
            () => root);
        await Assert.That(result.IsT0).IsTrue();
    }

    [Test("accepts cwd as descendant of root")]
    public async Task AcceptsCwdAsDescendantOfRoot()
    {
        var root = Path.Combine(Path.GetTempPath(), "test-root");
        var cwd = Path.Combine(root, "src", "tests");

        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? root : null,
            () => cwd);
        await Assert.That(result.IsT0).IsTrue();
    }

    [Test("normalises parent segments in root path")]
    public async Task NormalisesParentSegmentsInRootPath()
    {
        var root = Path.Combine(Path.GetTempPath(), "test-root");
        var rootWithDotDot = Path.Combine(root, "subdir", "..");

        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? rootWithDotDot : null,
            () => root);
        await Assert.That(result.IsT0).IsTrue();
        await Assert.That(result.AsT0.Path).IsEqualTo(Path.GetFullPath(root));
    }

    [Test("handles trailing separator on root path")]
    public async Task HandlesTrailingSeparatorOnRootPath()
    {
        var root = Path.Combine(Path.GetTempPath(), "test-root");
        var rootWithTrailing = root + Path.DirectorySeparatorChar;

        var result = ProjectRootResolver.Resolve(
            name => name == "TDD_GUARD_PROJECT_ROOT" ? rootWithTrailing : null,
            () => root);
        await Assert.That(result.IsT0).IsTrue();
    }

    [Test("resolves symlinked root against real cwd path")]
    public async Task ResolvesSymlinkedRootAgainstRealCwdPath()
    {
        // macOS: /var -> /private/var, so env var might have /var/... while cwd resolves to /private/var/...
        var realDir = Path.Combine(Path.GetTempPath(), "tdd-guard-symlink-test");
        var linkDir = Path.Combine(Path.GetTempPath(), "tdd-guard-symlink-link");
        try
        {
            Directory.CreateDirectory(realDir);
            if (Directory.Exists(linkDir)) Directory.Delete(linkDir);
            Directory.CreateSymbolicLink(linkDir, realDir);

            // Root is the symlink path, cwd is the real path
            var result = ProjectRootResolver.Resolve(
                name => name == "TDD_GUARD_PROJECT_ROOT" ? linkDir : null,
                () => realDir);
            await Assert.That(result.IsT0).IsTrue();
        }
        finally
        {
            if (Directory.Exists(linkDir)) Directory.Delete(linkDir);
            if (Directory.Exists(realDir)) Directory.Delete(realDir, true);
        }
    }

    [Test("TDD_GUARD_PROJECT_ROOT takes precedence over CLAUDE_PROJECT_DIR")]
    public async Task ProjectRootTakesPrecedenceOverClaudeProjectDir()
    {
        var preferred = Path.Combine(Path.GetTempPath(), "preferred");
        var fallback = Path.Combine(Path.GetTempPath(), "fallback");

        var result = ProjectRootResolver.Resolve(
            name => name switch
            {
                "TDD_GUARD_PROJECT_ROOT" => preferred,
                "CLAUDE_PROJECT_DIR" => fallback,
                _ => null
            },
            () => preferred);

        await Assert.That(result.IsT0).IsTrue();
        await Assert.That(result.AsT0.Path).IsEqualTo(Path.GetFullPath(preferred));
    }
}
