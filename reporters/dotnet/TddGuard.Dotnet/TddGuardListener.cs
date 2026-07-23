using Microsoft.Testing.Platform.Extensions;
using Microsoft.Testing.Platform.Extensions.Messages;
using Microsoft.Testing.Platform.Extensions.TestHost;
using Microsoft.Testing.Platform.Services;
using System.Collections.Concurrent;
using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet;

/// <summary>
/// MTP V2 extension that consumes <see cref="TestNodeUpdateMessage"/> events
/// and writes a <c>test.json</c> report when the test session finishes.
/// Thread-safe: concurrent <see cref="ConsumeAsync"/> calls are supported via <see cref="ConcurrentQueue{T}"/>.
/// </summary>
public sealed class TddGuardListener(WriteTestOutput writeOutput) : ITestSessionLifetimeHandler, IDataConsumer, IExtension
{
    private ConcurrentQueue<CollectedResult> _results = [];

    public string Uid => "TddGuard.Dotnet";
    public string Version => "1.0.0";
    public string DisplayName => "TDD Guard";
    public string Description => "TDD Guard test reporter";

    public Type[] DataTypesConsumed => [typeof(TestNodeUpdateMessage)];

    public Task<bool> IsEnabledAsync() => Task.FromResult(true);

    public Task OnTestSessionStartingAsync(ITestSessionContext testSessionContext)
    {
        _results = [];
        return Task.CompletedTask;
    }

    public Task OnTestSessionFinishingAsync(ITestSessionContext testSessionContext)
    {
        _results.WriteTestReport(writeOutput);
        return Task.CompletedTask;
    }

    public Task ConsumeAsync(IDataProducer dataProducer, IData value, CancellationToken cancellationToken)
    {
        if (value is not TestNodeUpdateMessage update)
            return Task.CompletedTask;

        var node = update.TestNode;
        // MTP fires Discovered during enumeration and InProgress when a test starts;
        // we only collect terminal states (Passed, Failed, Skipped, Error).
        var stateProperty = node.Properties.SingleOrDefault<TestNodeStateProperty>();
        if (stateProperty is null or InProgressTestNodeStateProperty or DiscoveredTestNodeStateProperty)
            return Task.CompletedTask;

        Core.TestState state = stateProperty switch
        {
            FailedTestNodeStateProperty f => new Core.TestState.Failed(
                f.Exception is not null ? [new TestEntryError(f.Exception.Message)]
                : !string.IsNullOrEmpty(f.Explanation) ? [new TestEntryError(f.Explanation)]
                : []),
            ErrorTestNodeStateProperty e => new Core.TestState.Failed(
                e.Exception is not null ? [new TestEntryError(e.Exception.Message)]
                : !string.IsNullOrEmpty(e.Explanation) ? [new TestEntryError(e.Explanation)]
                : []),
            SkippedTestNodeStateProperty => new Core.TestState.Skipped(),
            _ => new Core.TestState.Passed(),
        };
        var filePath = node.Properties.SingleOrDefault<TestFileLocationProperty>()?.FilePath;

        var input = new TestNodeInput(
            Uid: node.Uid.Value,
            DisplayName: node.DisplayName,
            FilePath: filePath,
            State: state);

        _results.Enqueue(input.ToCollectedResult());
        return Task.CompletedTask;
    }
}
