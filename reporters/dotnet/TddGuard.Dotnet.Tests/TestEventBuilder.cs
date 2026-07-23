using Microsoft.Testing.Platform.Extensions.Messages;

namespace TddGuard.Dotnet.Tests;

/// <summary>
/// Test Data Builder for MTP test events. Provides a fluent API that hides
/// MTP-specific types behind intent-revealing methods.
/// Entry point: <c>An.Event()</c>
/// </summary>
internal sealed class TestEventBuilder
{
    private string _name = "test1";
    private string? _filePath = "DefaultFile.cs";

    internal TestEventBuilder Named(string name)
    {
        _name = name;
        return this;
    }

    internal TestEventBuilder InFile(string filePath)
    {
        _filePath = filePath;
        return this;
    }

    internal TestEventBuilder WithNoFilePath()
    {
        _filePath = null;
        return this;
    }

    internal TestNodeUpdateMessage Passed()
        => MtpStubs.MakeTestUpdate(_name, new PassedTestNodeStateProperty(), _filePath);

    internal TestNodeUpdateMessage Failed(string message)
        => MtpStubs.MakeTestUpdate(_name,
            new FailedTestNodeStateProperty(new InvalidOperationException(message), message), _filePath);

    internal TestNodeUpdateMessage FailedWithExplanation(string explanation)
        => MtpStubs.MakeTestUpdate(_name, new FailedTestNodeStateProperty(explanation), _filePath);

    internal TestNodeUpdateMessage FailedBare()
        => MtpStubs.MakeTestUpdate(_name, new FailedTestNodeStateProperty(), _filePath);

    internal TestNodeUpdateMessage Skipped()
        => MtpStubs.MakeTestUpdate(_name, new SkippedTestNodeStateProperty(), _filePath);

    internal TestNodeUpdateMessage Error(string message)
        => MtpStubs.MakeTestUpdate(_name,
            new ErrorTestNodeStateProperty(new InvalidOperationException(message), message), _filePath);

    internal TestNodeUpdateMessage ErrorBare()
        => MtpStubs.MakeTestUpdate(_name, new ErrorTestNodeStateProperty(), _filePath);

    internal TestNodeUpdateMessage InProgress()
        => MtpStubs.MakeTestUpdate(_name, new InProgressTestNodeStateProperty(), _filePath);

    internal TestNodeUpdateMessage Discovered()
        => MtpStubs.MakeTestUpdate(_name, new DiscoveredTestNodeStateProperty(), _filePath);
}

/// <summary>
/// Entry point for the Test Data Builder: <c>An.Event().Named("x").Passed()</c>
/// </summary>
internal static class An
{
    internal static TestEventBuilder Event() => new();
}
