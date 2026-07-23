using FsCheck;
using FsCheck.Fluent;
using Microsoft.Testing.Platform.Extensions.Messages;
using Microsoft.Testing.Platform.Services;

namespace TddGuard.Dotnet.Tests;

internal static class MtpStubs
{
    internal static StubTestSessionContext StubSessionContext()
    {
        return new StubTestSessionContext();
    }

    internal static StubDataProducer StubProducer()
    {
        return new StubDataProducer();
    }

    internal static TestNodeUpdateMessage MakeTestUpdate(
        string name,
        TestNodeStateProperty stateProperty,
        string? filePath = "DefaultFile.cs")
    {
        var properties = filePath != null
            ? new PropertyBag(stateProperty, new TestFileLocationProperty(filePath, new LinePositionSpan(new LinePosition(1, 0), new LinePosition(1, 0))))
            : new PropertyBag(stateProperty);

        var node = new TestNode
        {
            Uid = new TestNodeUid($"assembly/TestClass/{name}"),
            DisplayName = name,
            Properties = properties,
        };

        return new TestNodeUpdateMessage(default, node);
    }

    /// <summary>
    /// Tagged wrapper pairing an MTP message with whether the event represents a failure,
    /// so PBT properties can compute expected outcomes without inspecting MTP internals.
    /// </summary>
    internal sealed record TaggedEvent(TestNodeUpdateMessage Message, bool IsFailed);

    internal static Gen<TaggedEvent> GenTaggedEvent()
    {
        var genName = Gen.OneOf(
            ArbMap.Default.GeneratorFor<NonEmptyString>().Select(s => s.Get),
            Gen.Constant("has \"quotes\""),
            Gen.Constant("has\nnewlines"),
            Gen.Constant("has\\backslashes"),
            Gen.Constant("has\ttabs"),
            Gen.Constant("unicode: \u00e9\u00f1\u00fc"));

        var genPath = Gen.OneOf(
            Gen.Constant<string?>("/src/A.cs"),
            Gen.Constant<string?>("/src/B.cs"),
            Gen.Constant<string?>("/tests/C.cs"),
            Gen.Constant<string?>(@"C:\Src\D.cs"),
            Gen.Constant<string?>(null));

        var genPassed = from n in genName from p in genPath
            select new TaggedEvent(MakeTestUpdate(n, new PassedTestNodeStateProperty(), p), false);

        var genFailedWithException = from n in genName from p in genPath
            select new TaggedEvent(
                MakeTestUpdate(n, new FailedTestNodeStateProperty(
                    new InvalidOperationException("generated failure"), "assertion"), p), true);

        var genFailedWithExplanation = from n in genName from p in genPath
            select new TaggedEvent(
                MakeTestUpdate(n, new FailedTestNodeStateProperty("explanation only"), p), true);

        var genFailedBare = from n in genName from p in genPath
            select new TaggedEvent(
                MakeTestUpdate(n, new FailedTestNodeStateProperty(), p), true);

        var genSkipped = from n in genName from p in genPath
            select new TaggedEvent(MakeTestUpdate(n, new SkippedTestNodeStateProperty(), p), false);

        var genError = from n in genName from p in genPath
            select new TaggedEvent(
                MakeTestUpdate(n, new ErrorTestNodeStateProperty(
                    new InvalidOperationException("error state"), "error"), p), true);

        return Gen.OneOf(genPassed, genFailedWithException, genFailedWithExplanation,
            genFailedBare, genSkipped, genError);
    }

    internal sealed class StubTestSessionContext : ITestSessionContext
    {
        public Microsoft.Testing.Platform.TestHost.SessionUid SessionUid => new("stub-session");
        public CancellationToken CancellationToken => CancellationToken.None;
    }

    internal sealed class StubData(string displayName, string? description) : IData
    {
        public string DisplayName => displayName;
        public string? Description => description;
    }

    internal sealed class StubDataProducer : IDataProducer
    {
        public string Uid => "stub";
        public string Version => "1.0.0";
        public string DisplayName => "Stub";
        public string Description => "Stub producer";
        public Type[] DataTypesProduced => [];
        public Task<bool> IsEnabledAsync() => Task.FromResult(true);
    }

}
