namespace TddGuard.Dotnet.Core;

/// <summary>
/// Wire-format types serialised to <c>test.json</c>.
/// Field names match the TDD Guard Zod schema after camelCase conversion.
/// </summary>
public record TestRunOutput(IReadOnlyList<TestModuleOutput> TestModules, string? Reason);

/// <summary>A group of tests sharing the same source file (module).</summary>
public record TestModuleOutput(string ModuleId, IReadOnlyList<TestEntryOutput> Tests);

/// <summary>A single test entry in the JSON output.</summary>
public record TestEntryOutput(string Name, string FullName, string State, IReadOnlyList<TestEntryErrorOutput>? Errors);

/// <summary>An error message attached to a failing test entry.</summary>
public record TestEntryErrorOutput(string Message);
