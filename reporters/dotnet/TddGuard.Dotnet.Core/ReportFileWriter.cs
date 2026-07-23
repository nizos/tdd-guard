namespace TddGuard.Dotnet.Core;

/// <summary>
/// Creates a <see cref="WriteTestOutput"/> delegate that writes serialised test output
/// to <c>{projectRoot}/.claude/tdd-guard/data/test.json</c>.
/// Uses atomic write (temp file + rename) to prevent partial reads.
/// </summary>
public static class ReportFileWriter
{
    private const string DataPath = ".claude/tdd-guard/data";
    private const string FileName = "test.json";

    public static WriteTestOutput Create(string projectRoot)
    {
        return output =>
        {
            try
            {
                var dir = Path.Combine(projectRoot, DataPath);
                Directory.CreateDirectory(dir);

                var targetPath = Path.Combine(dir, FileName);
                var tempPath = targetPath + ".tmp";

                var json = output.Serialize();
                File.WriteAllText(tempPath, json);
                File.Move(tempPath, targetPath, overwrite: true);

                return new WriteResult.Success();
            }
            catch (Exception ex)
            {
                return new WriteResult.Error(ex.ToString());
            }
        };
    }
}
