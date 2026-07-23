# TddGuard.Dotnet.Tests

## Testing philosophy

Tests verify **behaviour, not implementation**. The system under test (SUT) is
`TddGuardListener` exercised through its MTP interface — events go in, JSON
comes out. Tests never reference internal types like `CollectedResult`,
`TestRunSummariser`, or `TestReportSerializer` directly.

This means we can refactor internals freely without breaking tests, and every
test failure points to a genuine change in observable behaviour.

## Test layers

### Unit tests (`TddGuardListenerTests`)

Example-based tests that verify specific, named behaviours: "writes 'passed'
reason for passing test", "ignores in-progress nodes", etc. Each test follows
strict **Arrange/Act/Assert** via the `ListenerFixture` DSL:

```csharp
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
```

- **Arrange** feeds MTP events to the listener
- **Act** triggers `OnTestSessionFinishingAsync` (the write)
- **Assert** inspects the resulting JSON

These tests are fast, deterministic, and produce pinpoint failure messages.

### Property-based tests (`TddGuardListenerPropertyTests`)

FsCheck-driven tests that verify universal properties across hundreds of
randomly generated event sequences: "count is preserved", "reason reflects
failures", "output is valid JSON", etc.

Properties use `ListenerFixture.Execute()` which returns a synchronous
`PipelineResult` suitable for FsCheck's `Prop.ForAll`:

```csharp
Prop.ForAll(Arb.From(genEvents), events =>
{
    using var result = RunEvents(events);
    return result.Output!.Value.GetProperty("reason").GetString() == expected;
});
```

These tests explore the input space far more broadly than hand-crafted examples
and catch edge cases a person would never consider.

### Supporting test files

| File                       | Purpose                                                            |
| -------------------------- | ------------------------------------------------------------------ |
| `ProjectRootResolverTests` | Unit tests for env var resolution (pure function, tested directly) |
| `ReportFileWriterTests`    | Unit tests for file I/O (temp dir, error paths, overwrite)         |
| `DiagnosticDecoratorTests` | Unit tests for logging decorators (spy-based)                      |
| `TestReportWriterTests`    | Unit tests for the write-or-skip orchestrator                      |
| `TestNodeMapperTests`      | Unit tests for UID parsing (pure function)                         |
| `TddGuardBuilderTests`     | Unit tests for the MTP registration composition root               |

These test lower-level components directly because their contracts are stable
public APIs (file paths, env vars, JSON output). They don't test internal
wiring — that's covered by the outside-in listener tests and PBTs.

## Test infrastructure

### `ListenerFixture`

The test harness that manages the listener lifecycle. Provides three APIs:

- **`Arrange(...).Act().Assert(...)`** — async DSL for unit tests
- **`Arrange(...).Act().AssertNoOutput()`** — for tests expecting no JSON output
- **`Arrange(...).Act().Execute()`** — synchronous pipeline for PBTs, returns `PipelineResult`
- **`Create()`** — manual session control for tests that need multiple sessions or concurrency

### `TestEventBuilder` (`An.Event()`)

Test Data Builder for MTP events. Hides MTP-specific types behind
intent-revealing methods:

```csharp
An.Event().Named("test1").InFile("/src/A.cs").Passed()
An.Event().Named("test1").Failed("assertion message")
An.Event().Named("test1").FailedWithExplanation("no exception, just text")
An.Event().Named("test1").FailedBare()       // no exception, no explanation
An.Event().Named("test1").Error("setup failed")
An.Event().Named("test1").Skipped()
An.Event().Named("test1").InProgress()
An.Event().Named("test1").Discovered()
An.Event().Named("test1").WithNoFilePath().Passed()  // null file path
```

### `MtpStubs`

Low-level MTP stubs and the FsCheck generator (`GenTaggedEvent`). The generator
produces `TaggedEvent` records that pair an MTP message with a `bool IsFailed`
flag, so PBT properties can compute expected outcomes without inspecting MTP
internals.

### `TestJsonAssert`

Extension methods for navigating `JsonElement` in assertions: `root.Reason()`,
`root.Module().Test().State()`, `root.Module().Test().ErrorMessage()`, etc.

### `TempDir`

Disposable temp directory helper for file I/O tests.

## Framework compatibility smoke tests

The `TddGuard.Dotnet.Compat.*` projects sit alongside the test project
and prove the MTP extension works with every major .NET test framework.
Each is a standalone runnable test project with a single passing test.

| Project         | Framework | Notes                                                       |
| --------------- | --------- | ----------------------------------------------------------- |
| `Compat.TUnit`  | TUnit 0.x | Native MTP, file path as module ID                          |
| `Compat.MSTest` | MSTest v4 | Native MTP, file path as module ID                          |
| `Compat.XUnit`  | xUnit v3  | Native MTP via `xunit.v3.mtp-v2`, file path as module ID    |
| `Compat.XUnit2` | xUnit v2  | Third-party adapter (`YTest.MTP.XUnit2`), hash as module ID |
| `Compat.NUnit`  | NUnit 4   | MTP via NUnit runner, UID as module ID (no file path)       |

### How they work

Each project has three files:

- **`.csproj`** targets net10.0, references the framework package and
  `TddGuard.Dotnet` via ProjectReference. A `<TestingPlatformBuilderHook>`
  MSBuild item tells MTP's codegen where to find the hook class.
- **`TestingPlatformBuilderHook.cs`** is the MTP entry point. MTP's
  source generator calls `AddExtensions` at startup, which delegates to
  `TddGuardBuilder.Register(builder)` to wire up the listener.
- **`SmokeTests.cs`** has one passing test in the framework's native syntax.

When `dotnet test` runs, MTP starts the test host, calls the hook,
registers our listener, the framework discovers and runs the test,
MTP publishes `TestNodeUpdateMessage` events, our listener collects
them, and `OnTestSessionFinishingAsync` writes `test.json`.

The extension never knows which framework is running. It subscribes to
`TestNodeUpdateMessage` which is a generic MTP event type. The only
observable differences between frameworks are in module ID source
(file path vs UID fallback) and UID format, both handled by existing
null-checks in `TestNodeMapper`.

### Why ProjectReference instead of NuGet

The compat projects reference `TddGuard.Dotnet` via ProjectReference
(local source) rather than PackageReference (NuGet). This avoids the
NuGet pack/restore cycle and firewall issues with CDN IPs. The manual
`<TestingPlatformBuilderHook>` MSBuild item replaces what the NuGet
package's `buildTransitive` props would normally inject. The integration
tests (`reporters/test/factories/dotnet.ts`) separately test the NuGet
packaging path.

### Running the smoke tests

```bash
export TDD_GUARD_PROJECT_ROOT=/workspace
cd /workspace/reporters/dotnet/TddGuard.Dotnet.Compat.MSTest
dotnet test
cat /workspace/.claude/tdd-guard/data/test.json
```

Repeat for each `Compat.*` project. If `test.json` appears with
`"reason":"passed"`, the framework works.

## Why outside-in

Tests are written from the perspective of the consumer, not the
implementer. The SUT (system under test) is the `TddGuardListener`
exercised through its MTP interface: feed it events, trigger the
session finish, read the JSON.

This matters because:

- **Refactoring is free.** You can restructure `Summarise`, change how
  `Serialize` works, rename internal types, or merge classes — and no
  test breaks as long as the JSON output stays the same.
- **Tests document behaviour.** Each test says what the system does
  ("writes 'passed' reason for passing test"), not how it does it.
  New contributors read the tests to understand the contract.
- **Agents continue the pattern.** If you are an AI agent adding tests:
  use `ListenerFixture`, use `An.Event()` to build inputs, assert on
  the JSON output. Do not reference `CollectedResult`, `TestRunOutput`,
  `Summarise`, or `Serialize` in test assertions. Those are internals.

The supporting tests (`ProjectRootResolverTests`, `ReportFileWriterTests`,
etc.) test lower-level components directly because their contracts are
stable public boundaries (env vars, file paths, JSON structure). They
follow the same principle: test through the public API, not the internals.

## Running tests

All commands run inside the devcontainer:

```bash
docker exec -w /workspace/reporters/dotnet <container> dotnet test --project TddGuard.Dotnet.Tests
```

For coverage:

```bash
docker exec -w /workspace/reporters/dotnet <container> dotnet test --project TddGuard.Dotnet.Tests \
  -- --coverage --coverage-output-format cobertura --coverage-output /tmp/coverage.xml
```
