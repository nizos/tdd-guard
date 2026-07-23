using System.Text.Json;
using FsCheck;
using FsCheck.Fluent;
using Microsoft.Testing.Platform.Extensions.Messages;
using static TddGuard.Dotnet.Tests.MtpStubs;

namespace TddGuard.Dotnet.Tests;

internal sealed class TddGuardListenerPropertyTests
{
    private static ListenerFixture.PipelineResult RunEvents(IReadOnlyList<TaggedEvent> events)
    {
        return ListenerFixture
            .Arrange(async listener =>
            {
                foreach (var evt in events)
                    await listener.ConsumeAsync(StubProducer(), evt.Message, default);
            })
            .Act()
            .Execute();
    }

    [Test("every consumed terminal event produces exactly one test entry in the output")]
    public void CountPreservation()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            var totalTests = result.Output!.Value
                .GetProperty("testModules").EnumerateArray()
                .Sum(m => m.GetProperty("tests").GetArrayLength());
            return totalTests == events.Count;
        }).QuickCheckThrowOnFailure();
    }

    [Test("reason is 'failed' iff any consumed event was a failure")]
    public void ReasonReflectsFailures()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            var anyFailed = events.Any(e => e.IsFailed);

            using var result = RunEvents(events);

            var reason = result.Output!.Value.GetProperty("reason").GetString();
            return reason == (anyFailed ? "failed" : "passed");
        }).QuickCheckThrowOnFailure();
    }

    [Test("output has one module per distinct file path in the input")]
    public void ModuleGroupingMatchesFilePaths()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            // Compute expected module count from the tagged events.
            // Null file paths fall back to UID as module ID — each is unique per event.
            var expectedModules = events
                .Select((e, i) =>
                {
                    var node = e.Message.TestNode;
                    var filePath = node.Properties.SingleOrDefault<TestFileLocationProperty>()?.FilePath;
                    return filePath ?? node.Uid.Value;
                })
                .Distinct()
                .Count();

            using var result = RunEvents(events);

            var moduleCount = result.Output!.Value.GetProperty("testModules").GetArrayLength();
            return moduleCount == expectedModules;
        }).QuickCheckThrowOnFailure();
    }

    [Test("every state string in the output is one of passed, failed, skipped")]
    public void AllStateStringsAreValid()
    {
        var validStates = new HashSet<string> { "passed", "failed", "skipped" };
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            return result.Output!.Value
                .GetProperty("testModules").EnumerateArray()
                .SelectMany(m => m.GetProperty("tests").EnumerateArray())
                .All(t => validStates.Contains(t.GetProperty("state").GetString()!));
        }).QuickCheckThrowOnFailure();
    }

    [Test("output JSON conforms to the TDD Guard wire-format schema")]
    public void JsonSchemaIsValid()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            var root = result.Output!.Value;
            if (root.GetProperty("reason").ValueKind != JsonValueKind.String) return false;
            if (root.GetProperty("testModules").ValueKind != JsonValueKind.Array) return false;

            foreach (var module in root.GetProperty("testModules").EnumerateArray())
            {
                if (module.GetProperty("moduleId").ValueKind != JsonValueKind.String) return false;
                if (module.GetProperty("tests").ValueKind != JsonValueKind.Array) return false;

                foreach (var test in module.GetProperty("tests").EnumerateArray())
                {
                    if (test.GetProperty("name").ValueKind != JsonValueKind.String) return false;
                    if (test.GetProperty("fullName").ValueKind != JsonValueKind.String) return false;
                    if (test.GetProperty("state").ValueKind != JsonValueKind.String) return false;
                    if (test.TryGetProperty("errors", out var errors))
                    {
                        if (errors.ValueKind != JsonValueKind.Array) return false;
                        foreach (var err in errors.EnumerateArray())
                            if (err.GetProperty("message").ValueKind != JsonValueKind.String) return false;
                    }
                }
            }

            return true;
        }).QuickCheckThrowOnFailure();
    }

    [Test("passing and skipped tests do not have an errors property in the JSON")]
    public void NullErrorsOmitted()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            return result.Output!.Value
                .GetProperty("testModules").EnumerateArray()
                .SelectMany(m => m.GetProperty("tests").EnumerateArray())
                .Where(t => t.GetProperty("state").GetString() is "passed" or "skipped")
                .All(t => !t.TryGetProperty("errors", out _));
        }).QuickCheckThrowOnFailure();
    }

    [Test("error messages from failed events appear in the corresponding test entry")]
    public void ErrorMessagesPreserved()
    {
        // Use only events with file paths so module grouping is predictable
        var genPath = Gen.Elements("/src/Single.cs");
        var genName = ArbMap.Default.GeneratorFor<NonEmptyString>().Select(s => s.Get);
        var genFailedWithMessage = from n in genName from p in genPath
            select new TaggedEvent(
                MakeTestUpdate(n, new FailedTestNodeStateProperty(
                    new InvalidOperationException("expected failure message"), "assertion"), p), true);
        var genEvents = Gen.NonEmptyListOf(genFailedWithMessage);

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            return result.Output!.Value
                .GetProperty("testModules").EnumerateArray()
                .SelectMany(m => m.GetProperty("tests").EnumerateArray())
                .Where(t => t.GetProperty("state").GetString() == "failed")
                .All(t => t.TryGetProperty("errors", out var errors)
                    && errors.EnumerateArray().Any(e =>
                        e.GetProperty("message").GetString()!.Contains("expected failure message", StringComparison.Ordinal)));
        }).QuickCheckThrowOnFailure();
    }

    [Test("appending a failure to an all-passing sequence flips reason to 'failed'")]
    public void AddingFailureFlipsReason()
    {
        var genPassedEvent = from n in ArbMap.Default.GeneratorFor<NonEmptyString>().Select(s => s.Get)
                             from p in Gen.Elements("/src/A.cs", "/src/B.cs")
                             select new TaggedEvent(MakeTestUpdate(n, new PassedTestNodeStateProperty(), p), false);
        var genPassedEvents = Gen.NonEmptyListOf(genPassedEvent);

        Prop.ForAll(Arb.From(genPassedEvents), passingEvents =>
        {
            // Baseline: all passing → reason "passed"
            using var baseline = ListenerFixture
                .Arrange(async listener =>
                {
                    foreach (var evt in passingEvents)
                        await listener.ConsumeAsync(StubProducer(), evt.Message, default);
                })
                .Act()
                .Execute();

            var baseReason = baseline.Output!.Value.GetProperty("reason").GetString();
            if (baseReason != "passed") return false;

            // Append one failure → reason "failed"
            var failedEvent = An.Event().Named("injected_failure").InFile("/src/A.cs").Failed("boom");

            using var withFailure = ListenerFixture
                .Arrange(async listener =>
                {
                    foreach (var evt in passingEvents)
                        await listener.ConsumeAsync(StubProducer(), evt.Message, default);
                    await listener.ConsumeAsync(StubProducer(), failedEvent, default);
                })
                .Act()
                .Execute();

            return withFailure.Output!.Value.GetProperty("reason").GetString() == "failed";
        }).QuickCheckThrowOnFailure();
    }

    [Test("the same event sequence always produces identical JSON output")]
    public void Determinism()
    {
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var first = RunEvents(events);
            using var second = RunEvents(events);

            return first.Output!.Value.GetRawText() == second.Output!.Value.GetRawText();
        }).QuickCheckThrowOnFailure();
    }

    [Test("output JSON survives a deserialize-reserialize roundtrip")]
    public void JsonRoundtrip()
    {
        var roundtripOptions = new JsonSerializerOptions
        {
            PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
            WriteIndented = false,
        };
        var genEvents = Gen.NonEmptyListOf(GenTaggedEvent());

        Prop.ForAll(Arb.From(genEvents), events =>
        {
            using var result = RunEvents(events);

            var originalJson = result.Output!.Value.GetRawText();
            var deserialized = JsonSerializer.Deserialize<JsonElement>(originalJson);
            var reserialized = JsonSerializer.Serialize(deserialized, roundtripOptions);

            // Normalize both through JsonElement to compare structurally
            var original = JsonSerializer.Deserialize<JsonElement>(originalJson);
            var roundtripped = JsonSerializer.Deserialize<JsonElement>(reserialized);
            return original.GetRawText() == roundtripped.GetRawText();
        }).QuickCheckThrowOnFailure();
    }
}
