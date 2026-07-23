namespace TddGuard.Dotnet.Core;

/// <summary>
/// Groups collected test results by module and converts them into
/// the wire-format <see cref="TestRunOutput"/> for JSON serialisation.
/// Pure function with no side effects.
/// </summary>
public static class TestRunSummariser
{
    public static TestRunOutput Summarise(this IReadOnlyCollection<CollectedResult> results)
    {
        var modules = results
            .GroupBy(r => r.ModuleId)
            .Select(g => new TestModuleOutput(
                ModuleId: g.Key,
                Tests: g.Select(r => r.State switch
                {
                    TestState.Passed => new TestEntryOutput(r.Name, r.FullName, "passed", null),
                    TestState.Failed f => new TestEntryOutput(r.Name, r.FullName, "failed",
                        f.Errors.Select(e => new TestEntryErrorOutput(e.Message)).ToList()),
                    TestState.Skipped => new TestEntryOutput(r.Name, r.FullName, "skipped", null),
                    // Unreachable: TestState is a sealed DU with 3 variants. C# pattern matching
                    // cannot prove exhaustiveness on sealed abstract records, so this satisfies the compiler.
                    _ => throw new InvalidOperationException($"Unknown TestState: {r.State}")
                }).ToList()))
            .ToList();

        var reason = results.Any(r => r.State is TestState.Failed)
            ? "failed"
            : "passed";

        return new TestRunOutput(modules, reason);
    }
}
