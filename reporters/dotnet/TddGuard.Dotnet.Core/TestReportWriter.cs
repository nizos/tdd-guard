namespace TddGuard.Dotnet.Core;

/// <summary>
/// Orchestrates the final step of a test session: summarises collected results
/// and delegates to the <see cref="WriteTestOutput"/> port.
/// Skips writing when no results were collected.
/// </summary>
public static class TestReportWriter
{
    public static WriteResult WriteTestReport(
        this IReadOnlyCollection<CollectedResult> results,
        WriteTestOutput writeOutput)
    {
        if (results.Count == 0)
            return new WriteResult.Skipped();

        var output = results.Summarise();
        return writeOutput(output);
    }
}
