using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet.Tests;

internal sealed class TestReportWriterTests
{
    [Test("returns Skipped when results collection is empty")]
    public async Task ReturnsSkippedWhenEmpty()
    {
        IReadOnlyCollection<CollectedResult> results = [];
        WriteTestOutput spy = _ => throw new InvalidOperationException("should not be called");

        var result = results.WriteTestReport(spy);

        await Assert.That(result is WriteResult.Skipped).IsTrue();
    }

    [Test("delegates to writeOutput when results are present")]
    public async Task DelegatesToWriteOutputWhenResultsPresent()
    {
        TestRunOutput? captured = null;
        WriteTestOutput spy = output =>
        {
            captured = output;
            return new WriteResult.Success();
        };
        IReadOnlyCollection<CollectedResult> results =
        [
            new("test1", "Ns.Class.test1", "Module.cs", new Core.TestState.Passed())
        ];

        var result = results.WriteTestReport(spy);

        await Assert.That(result is WriteResult.Success).IsTrue();
        await Assert.That(captured).IsNotNull();
        await Assert.That(captured!.TestModules.Count).IsEqualTo(1);
    }
}
