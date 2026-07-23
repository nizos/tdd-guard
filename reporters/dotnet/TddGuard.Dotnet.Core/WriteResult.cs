namespace TddGuard.Dotnet.Core;

/// <summary>
/// Sealed discriminated union for file-write outcomes.
/// Errors are returned as values rather than thrown as exceptions.
/// </summary>
public abstract record WriteResult
{
    private WriteResult() { }

    public sealed record Success : WriteResult;
    public sealed record Skipped : WriteResult;
    public sealed record Error(string Message) : WriteResult;
}
