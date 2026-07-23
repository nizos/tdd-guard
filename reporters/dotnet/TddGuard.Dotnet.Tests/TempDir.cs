namespace TddGuard.Dotnet.Tests;

internal static class TempDir
{
    internal static async Task Run(Func<string, Task> test)
    {
        var dir = Path.Combine(Path.GetTempPath(), Path.GetRandomFileName());
        try
        {
            await test(dir);
        }
        finally
        {
            if (Directory.Exists(dir)) Directory.Delete(dir, true);
        }
    }
}
