using Microsoft.Testing.Platform.Extensions.Messages;
using static TddGuard.Dotnet.Tests.MtpStubs;

namespace TddGuard.Dotnet.Tests;

internal sealed class TddGuardListenerTests
{
    [Test("writes 'passed' reason for passing test")]
    public async Task WritesPassedReasonForPassingTest()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Reason()).IsEqualTo("passed");
            });
    }

    [Test("writes 'failed' reason when any test fails")]
    public async Task WritesFailedReasonWhenAnyTestFails()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Passed(), default);
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test2").Failed("boom"), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Reason()).IsEqualTo("failed");
            });
    }

    [Test("skipped tests count as passed for reason")]
    public async Task SkippedTestsCountAsPassedForReason()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Skipped(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Reason()).IsEqualTo("passed");
                await Assert.That(root.Module().Test().State()).IsEqualTo("skipped");
            });
    }

    [Test("does not write when no tests run")]
    public async Task DoesNotWriteWhenNoTestsRun()
    {
        await ListenerFixture
            .Arrange()
            .Act()
            .AssertNoOutput();
    }

    [Test("includes error message from exception")]
    public async Task IncludesErrorMessageFromException()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Failed("Expected 6 but got 5"), default);
            })
            .Act()
            .Assert(async root =>
            {
                var test = root.Module().Test();
                await Assert.That(test.State()).IsEqualTo("failed");
                await Assert.That(test.ErrorMessage()).Contains("Expected 6 but got 5");
            });
    }

    [Test("uses explanation when no exception")]
    public async Task UsesExplanationWhenNoException()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").FailedWithExplanation("assertion failed"), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().ErrorMessage()).IsEqualTo("assertion failed");
            });
    }

    [Test("passed test has no errors in JSON")]
    public async Task PassedTestHasNoErrorsInJson()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().HasErrors()).IsFalse();
            });
    }

    [Test("uses file path as module ID")]
    public async Task UsesFilePathAsModuleId()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").InFile("/src/Tests.cs").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().ModuleId()).IsEqualTo("/src/Tests.cs");
            });
    }

    [Test("falls back to UID as module ID")]
    public async Task FallsBackToUidAsModuleId()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").WithNoFilePath().Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().ModuleId()).Contains("assembly/TestClass/test1");
            });
    }

    [Test("strips parameters from UID")]
    public async Task StripsParametersFromUid()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                var node = new TestNode
                {
                    Uid = new TestNodeUid("assembly/TestClass/TestMethod(1, 2)"),
                    DisplayName = "TestMethod",
                    Properties = new PropertyBag(new PassedTestNodeStateProperty()),
                };
                var update = new TestNodeUpdateMessage(default, node);
                await listener.ConsumeAsync(StubProducer(), update, default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().FullName()).IsEqualTo("assembly/TestClass/TestMethod");
            });
    }

    [Test("uses display name as test name")]
    public async Task UsesDisplayNameAsTestName()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("Should_add_numbers").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().Name()).IsEqualTo("Should_add_numbers");
            });
    }

    [Test("ignores in-progress nodes")]
    public async Task IgnoresInProgressNodes()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").InProgress(), default);
            })
            .Act()
            .AssertNoOutput();
    }

    [Test("ignores discovered nodes")]
    public async Task IgnoresDiscoveredNodes()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Discovered(), default);
            })
            .Act()
            .AssertNoOutput();
    }

    [Test("maps error state to failed")]
    public async Task MapsErrorStateToFailed()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Error("setup exploded"), default);
            })
            .Act()
            .Assert(async root =>
            {
                var test = root.Module().Test();
                await Assert.That(test.State()).IsEqualTo("failed");
                await Assert.That(test.ErrorMessage()).Contains("setup exploded");
            });
    }

    [Test("ignores non-TestNodeUpdateMessage data")]
    public async Task IgnoresNonTestNodeUpdateMessageData()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                var stubData = new MtpStubs.StubData("not a test update", null);
                await listener.ConsumeAsync(StubProducer(), stubData, default);
            })
            .Act()
            .AssertNoOutput();
    }

    [Test("failed with no exception and no explanation produces empty errors array")]
    public async Task FailedWithNoExceptionNoExplanationProducesEmptyErrorsArray()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").FailedBare(), default);
            })
            .Act()
            .Assert(async root =>
            {
                var test = root.Module().Test();
                await Assert.That(test.State()).IsEqualTo("failed");
                await Assert.That(test.GetProperty("errors").GetArrayLength()).IsEqualTo(0);
            });
    }

    [Test("error with no exception and no explanation produces empty errors array")]
    public async Task ErrorWithNoExceptionNoExplanationProducesEmptyErrorsArray()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").ErrorBare(), default);
            })
            .Act()
            .Assert(async root =>
            {
                var test = root.Module().Test();
                await Assert.That(test.State()).IsEqualTo("failed");
                await Assert.That(test.GetProperty("errors").GetArrayLength()).IsEqualTo(0);
            });
    }

    [Test("clears results between sessions")]
    public async Task ClearsResultsBetweenSessions()
    {
        var (listener, readJson, tempDir) = ListenerFixture.Create();
        try
        {
            // First session: one passing test produces output
            await listener.OnTestSessionStartingAsync(StubSessionContext());
            await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Passed(), default);
            await listener.OnTestSessionFinishingAsync(StubSessionContext());

            await Assert.That(ListenerFixture.HasTestJson(tempDir)).IsTrue();

            // Delete the file so we can detect whether session 2 writes
            File.Delete(ListenerFixture.JsonPath(tempDir));

            // Second session: no tests, should not recreate file
            await listener.OnTestSessionStartingAsync(StubSessionContext());
            await listener.OnTestSessionFinishingAsync(StubSessionContext());

            await Assert.That(ListenerFixture.HasTestJson(tempDir)).IsFalse();
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }

    [Test("groups tests by module")]
    public async Task GroupsTestsByModule()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").InFile("/src/ModuleA.cs").Passed(), default);
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test2").InFile("/src/ModuleB.cs").Passed(), default);
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test3").InFile("/src/ModuleA.cs").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                var modules = root.GetProperty("testModules");
                await Assert.That(modules.GetArrayLength()).IsEqualTo(2);
                await Assert.That(modules[0].ModuleId()).IsEqualTo("/src/ModuleA.cs");
                await Assert.That(modules[0].Tests().GetArrayLength()).IsEqualTo(2);
                await Assert.That(modules[1].ModuleId()).IsEqualTo("/src/ModuleB.cs");
                await Assert.That(modules[1].Tests().GetArrayLength()).IsEqualTo(1);
            });
    }

    [Test("uses camelCase keys and omits nulls")]
    public async Task UsesCamelCaseKeysAndOmitsNulls()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Passed(), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.TryGetProperty("testModules", out _)).IsTrue();
                await Assert.That(root.TryGetProperty("TestModules", out _)).IsFalse();

                var module = root.Module();
                await Assert.That(module.TryGetProperty("moduleId", out _)).IsTrue();
                await Assert.That(module.TryGetProperty("ModuleId", out _)).IsFalse();

                await Assert.That(module.Test().TryGetProperty("errors", out _)).IsFalse();
            });
    }

    [Test("escapes quotes in error messages")]
    public async Task EscapesQuotesInErrorMessages()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Failed("Expected \"hello\" but got \"world\""), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().ErrorMessage()).Contains("Expected \"hello\" but got \"world\"");
            });
    }

    [Test("escapes newlines in error messages")]
    public async Task EscapesNewlinesInErrorMessages()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Failed("line1\nline2\nline3"), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().ErrorMessage()).Contains("line1\nline2\nline3");
            });
    }

    [Test("escapes backslashes in error messages")]
    public async Task EscapesBackslashesInErrorMessages()
    {
        await ListenerFixture
            .Arrange(async listener =>
            {
                await listener.ConsumeAsync(StubProducer(), An.Event().Named("test1").Failed("path: C:\\Users\\test\\file.cs"), default);
            })
            .Act()
            .Assert(async root =>
            {
                await Assert.That(root.Module().Test().ErrorMessage()).Contains("C:\\Users\\test\\file.cs");
            });
    }

    [Test("handles concurrent ConsumeAsync calls safely")]
    public async Task HandlesConcurrentConsumeAsyncCallsSafely()
    {
        var (listener, readJson, tempDir) = ListenerFixture.Create();
        try
        {
            await listener.OnTestSessionStartingAsync(StubSessionContext());

            using var barrier = new Barrier(100);
            var tasks = Enumerable.Range(0, 100).Select(i => Task.Run(async () =>
            {
                barrier.SignalAndWait();
                await listener.ConsumeAsync(
                    StubProducer(),
                    An.Event().Named($"Test_{i}").InFile("/src/Tests.cs").Passed(),
                    default);
            }));
            await Task.WhenAll(tasks);

            await listener.OnTestSessionFinishingAsync(StubSessionContext());

            var root = TestJsonAssert.Parse(readJson());
            var tests = root.Module().Tests();
            await Assert.That(tests.GetArrayLength()).IsEqualTo(100);
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }

    // Coverage: exercises IExtension property getters (lines 19-26) which are
    // called by MTP framework via reflection but never by application code.
    [Test("exposes correct IExtension metadata")]
    public async Task ExposesCorrectExtensionMetadata()
    {
        var (listener, _, tempDir) = ListenerFixture.Create();
        try
        {
            await Assert.That(listener.Uid).IsNotNull();
            await Assert.That(listener.Version).IsNotNull();
            await Assert.That(listener.DisplayName).IsNotNull();
            await Assert.That(listener.Description).IsNotNull();

            var enabled = await listener.IsEnabledAsync();
            await Assert.That(enabled).IsTrue();

            await Assert.That(listener.DataTypesConsumed).Contains(typeof(TestNodeUpdateMessage));
        }
        finally
        {
            if (Directory.Exists(tempDir)) Directory.Delete(tempDir, true);
        }
    }
}
