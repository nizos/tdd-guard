# TDD Guard .NET Reporter

Microsoft Testing Platform (MTP) V2 extension that captures test results
for TDD Guard validation. Works with any .NET test framework that supports
MTP V2 — one package covers TUnit, MSTest, xUnit, NUnit, and more.

## How it works

The reporter is an MTP V2 extension that loads in-process alongside your
test framework. It subscribes to `TestNodeUpdateMessage` events — the
standard MTP event type that all frameworks publish when a test completes.

When a test session finishes, the extension writes results to
`.claude/tdd-guard/data/test.json` in the TDD Guard wire format. The
TDD Guard validation hook reads this file to enforce TDD discipline.

The extension is framework-agnostic. It does not know or care which
framework is running. MTP handles the framework integration; we handle
the test result capture.

## Requirements

- .NET 10+
- [TDD Guard](https://github.com/nizos/tdd-guard)

## Installation

Planned distribution: NuGet.org (published by the maintainer).

Add a PackageReference to your test project:

```xml
<ItemGroup>
  <PackageReference Include="TddGuard.Dotnet" Version="*" />
</ItemGroup>
```

For solutions with multiple test projects, add it once via
`Directory.Build.props` in your tests directory:

```xml
<Project>
  <ItemGroup>
    <PackageReference Include="TddGuard.Dotnet" Version="*" />
  </ItemGroup>
</Project>
```

No manual hook code is needed — the package auto-registers via
`buildTransitive/*.props` which injects the MTP builder hook at build time.

## Usage

The extension registers automatically. Running `dotnet test` is enough
once the package is referenced.

## Configuration

### Project Root

Set the `TDD_GUARD_PROJECT_ROOT` environment variable to your project root:

```bash
export TDD_GUARD_PROJECT_ROOT="/absolute/path/to/project/root"
```

`CLAUDE_PROJECT_DIR` is used as a fallback when `TDD_GUARD_PROJECT_ROOT`
is not set. Claude Code sets this variable for hook commands, though it
may not be available in all execution contexts.

### Rules

- Absolute and relative paths are both accepted (per ADR-009)
- Current directory must be within the configured project root
- When neither env var is set, the extension logs a diagnostic to stderr
  and disables itself (per ADR-010) — it does not silently fall back to cwd

## Compatibility

Verified with smoke tests in `TddGuard.Dotnet.Compat.*` projects:

| Framework                                        | MTP V2 support | Status   | Notes                                            |
| ------------------------------------------------ | -------------- | -------- | ------------------------------------------------ |
| [TUnit](https://github.com/thomhurst/TUnit)      | Native         | Verified | File path as module ID                           |
| [MSTest v4](https://github.com/microsoft/testfx) | Native         | Verified | File path as module ID                           |
| [xUnit v3](https://xunit.net/)                   | Native         | Verified | Via `xunit.v3.mtp-v2` package                    |
| [xUnit v2](https://xunit.net/)                   | Via adapter    | Verified | Via `YTest.MTP.XUnit2` (third-party, unofficial) |
| [NUnit 4](https://docs.nunit.org/)               | Via runner     | Verified | UID as module ID (NUnit omits file paths)        |
| [Reqnroll](https://reqnroll.net/)                | Inherited      | Expected | BDD layer over MSTest/NUnit/xUnit/TUnit          |

## Project structure

```
TddGuard.Dotnet.Core/       Domain types and pure functions (no MTP dependency)
TddGuard.Dotnet/             MTP V2 extension (listener, builder, hook)
TddGuard.Dotnet.Tests/       Unit tests, property-based tests, test infrastructure
TddGuard.Dotnet.Compat.*/    Framework compatibility smoke tests
```

`Core` has no dependency on MTP. It defines the domain types
(`TestState`, `CollectedResult`, `TestRunOutput`), the serializer,
the file writer, and the project root resolver. `Dotnet` depends on
Core and MTP, wiring the domain logic into the MTP extension model.

This separation means the domain logic can be tested without MTP,
and the MTP integration can change without affecting the domain.

## Development

All commands run inside the devcontainer:

```bash
docker exec -w /workspace/reporters/dotnet <container> dotnet build
docker exec -w /workspace/reporters/dotnet <container> dotnet test --project TddGuard.Dotnet.Tests
```

Find the container name via `docker ps`.

See `TddGuard.Dotnet.Tests/README.md` for testing conventions,
infrastructure, and the framework smoke test guide.

## License

MIT
