using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet.Tests;

internal sealed class ReportFileWriterTests
{
    [Test("writes JSON to .claude/tdd-guard/data/")]
    public async Task WritesJsonToDataDirectory()
    {
        await TempDir.Run(async tempDir =>
        {
            var write = ReportFileWriter.Create(tempDir);
            var result = write(MakePassingOutput());

            await Assert.That(result is WriteResult.Success).IsTrue();

            var expectedPath = Path.Combine(tempDir, ".claude", "tdd-guard", "data", "test.json");
            await Assert.That(File.Exists(expectedPath)).IsTrue();

            var json = await File.ReadAllTextAsync(expectedPath);
            await Assert.That(json).Contains("\"testModules\"");
        });
    }

    [Test("returns error for invalid path")]
    public async Task ReturnsErrorForInvalidPath()
    {
        var write = ReportFileWriter.Create("/dev/null/impossible/path");
        var result = write(MakePassingOutput());

        await Assert.That(result is WriteResult.Error).IsTrue();
        await Assert.That(((WriteResult.Error)result).Message).Contains("impossible");
    }

    [Test("creates intermediate directories when they do not exist")]
    public async Task CreatesIntermediateDirectories()
    {
        await TempDir.Run(async tempDir =>
        {
            await Assert.That(Directory.Exists(tempDir)).IsFalse();

            var write = ReportFileWriter.Create(tempDir);
            var result = write(MakePassingOutput());

            await Assert.That(result is WriteResult.Success).IsTrue();
            var expectedPath = Path.Combine(tempDir, ".claude", "tdd-guard", "data", "test.json");
            await Assert.That(File.Exists(expectedPath)).IsTrue();
        });
    }

    [Test("overwrites existing test.json with new content")]
    public async Task OverwritesExistingTestJson()
    {
        await TempDir.Run(async tempDir =>
        {
            var write = ReportFileWriter.Create(tempDir);

            var firstOutput = MakeOutputWithReason("passed");
            var firstResult = write(firstOutput);
            await Assert.That(firstResult is WriteResult.Success).IsTrue();

            var secondOutput = MakeOutputWithReason("failed");
            var secondResult = write(secondOutput);
            await Assert.That(secondResult is WriteResult.Success).IsTrue();

            var expectedPath = Path.Combine(tempDir, ".claude", "tdd-guard", "data", "test.json");
            var json = await File.ReadAllTextAsync(expectedPath);
            await Assert.That(json).Contains("\"failed\"");
            await Assert.That(json).DoesNotContain("\"passed\"");
        });
    }

    private static TestRunOutput MakePassingOutput()
        => MakeOutputWithReason("passed");

    private static TestRunOutput MakeOutputWithReason(string reason)
    {
        var state = reason == "failed" ? "failed" : "passed";
        var entry = new TestEntryOutput("test1", "Module/test1", state, null);
        var module = new TestModuleOutput("Module", [entry]);
        return new TestRunOutput([module], reason);
    }
}
