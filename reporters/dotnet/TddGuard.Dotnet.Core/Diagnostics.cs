using OneOf;

namespace TddGuard.Dotnet.Core;

/// <summary>
/// Decorators that add diagnostic logging to core operations.
/// Composed at bootstrap time, not at definition time.
/// </summary>
public static class Diagnostics
{
    /// <summary>
    /// Logs a diagnostic message when project root resolution fails.
    /// Passes through both success and error tracks unchanged.
    /// </summary>
    public static OneOf<ProjectRoot, ResolveError> LogOnError(
        this OneOf<ProjectRoot, ResolveError> result,
        LogDiagnostic log)
    {
        result.Switch(_ => { }, error => log($"disabled: {error.Reason}"));
        return result;
    }

    /// <summary>
    /// Wraps a <see cref="WriteTestOutput"/> delegate to log on write failure.
    /// </summary>
    public static WriteTestOutput WithDiagnostics(
        this WriteTestOutput inner,
        LogDiagnostic log)
    {
        return output =>
        {
            var result = inner(output);
            if (result is WriteResult.Error e)
                log($"failed to write test.json: {e.Message}");
            return result;
        };
    }
}
