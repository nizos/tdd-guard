namespace TddGuard.Dotnet.Core;

/// <summary>
/// Sealed discriminated union representing the outcome of a single test.
/// </summary>
public abstract record TestState
{
    private TestState() { }

    public sealed record Passed : TestState;
    public sealed record Failed(IReadOnlyList<TestEntryError> Errors) : TestState;
    public sealed record Skipped : TestState;
}

/// <summary>Error message captured from a failed test assertion or exception.</summary>
public record TestEntryError(string Message);

/// <summary>
/// Raw input from the MTP test node, before module grouping.
/// Decoupled from MTP types so Core has no platform dependency.
/// </summary>
public record TestNodeInput(string Uid, string DisplayName, string? FilePath, TestState State);

/// <summary>
/// Processed test result after UID parsing and module assignment.
/// Produced by <see cref="TestNodeMapper.ToCollectedResult"/>.
/// </summary>
public record CollectedResult(string Name, string FullName, string ModuleId, TestState State);
