using OneOf;

namespace TddGuard.Dotnet.Core;

public record ProjectRoot(string Path);
public record ResolveError(string Reason);

/// <summary>
/// Resolves the project root directory used as the base path for writing
/// <c>.claude/tdd-guard/data/test.json</c>.
/// Checks <c>TDD_GUARD_PROJECT_ROOT</c>, then <c>CLAUDE_PROJECT_DIR</c>.
/// Returns a <see cref="ResolveError"/> when neither env var is set (per ADR-010)
/// or when the working directory is outside the resolved root, allowing callers
/// to gracefully disable instead of crashing.
/// </summary>
public static class ProjectRootResolver
{
    public static OneOf<ProjectRoot, ResolveError> Resolve(
        GetEnvironmentVariable getEnvVar,
        GetCurrentWorkingDirectory getCwd)
    {
        var raw = getEnvVar("TDD_GUARD_PROJECT_ROOT")
            ?? getEnvVar("CLAUDE_PROJECT_DIR");

        if (string.IsNullOrEmpty(raw))
            return new ResolveError("No project root configured (set TDD_GUARD_PROJECT_ROOT)");

        var root = ResolvePath(raw);
        var cwd = ResolvePath(getCwd());

        if (!IsDescendantOf(cwd, root))
            return new ResolveError($"Working directory '{cwd}' is not within project root '{root}'");

        return new ProjectRoot(root);
    }

    private static bool IsDescendantOf(string child, string parent)
    {
        var normalizedParent = parent.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
            + Path.DirectorySeparatorChar;
        var normalizedChild = child.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar)
            + Path.DirectorySeparatorChar;

        // Case-insensitive to match Windows path semantics and .NET's own Path behaviour.
        // On case-sensitive Linux filesystems this is technically lenient, but consistent
        // with how Directory.GetCurrentDirectory() and Path.GetFullPath() normalise paths.
        return normalizedChild.StartsWith(normalizedParent, StringComparison.OrdinalIgnoreCase);
    }

    /// <summary>
    /// Resolves a path to its canonical form: absolute, normalised, and with
    /// symlinks resolved. Handles macOS /var -> /private/var and similar.
    /// Falls back to <see cref="Path.GetFullPath"/> when the path does not exist.
    /// </summary>
    private static string ResolvePath(string path)
    {
        var full = Path.GetFullPath(path);
        try
        {
            var info = new DirectoryInfo(full);
            var target = info.ResolveLinkTarget(returnFinalTarget: true);
            return target?.FullName ?? full;
        }
        catch (IOException)
        {
            return full;
        }
    }
}
