namespace TddGuard.Dotnet.Core;

/// <summary>
/// Named delegate for environment variable lookup, replacing <c>Func&lt;string, string?&gt;</c>
/// for readability at call sites.
/// </summary>
public delegate string? GetEnvironmentVariable(string name);

/// <summary>
/// Port delegate that writes a completed test run to persistent storage.
/// Returns errors as values rather than throwing exceptions.
/// </summary>
public delegate WriteResult WriteTestOutput(TestRunOutput output);

/// <summary>
/// Named delegate for retrieving the current working directory.
/// </summary>
public delegate string GetCurrentWorkingDirectory();

/// <summary>
/// Port delegate for diagnostic messages (resolve failures, write errors).
/// Production default writes to stderr with a <c>[tdd-guard-dotnet]</c> prefix.
/// </summary>
public delegate void LogDiagnostic(string message);
