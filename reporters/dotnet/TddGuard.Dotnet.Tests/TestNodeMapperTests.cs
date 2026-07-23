using TddGuard.Dotnet.Core;

namespace TddGuard.Dotnet.Tests;

internal sealed class TestNodeMapperTests
{
    [Test("preserves UID when no parentheses present")]
    public async Task PreservesUidWithNoParentheses()
    {
        var input = new TestNodeInput("assembly/Class/Method", "Method", "/file.cs", new Core.TestState.Passed());
        var result = input.ToCollectedResult();
        await Assert.That(result.FullName).IsEqualTo("assembly/Class/Method");
    }

    [Test("strips at first open parenthesis including nested parens")]
    public async Task StripsAtFirstOpenParenthesis()
    {
        var input = new TestNodeInput("assembly/Class/Method((1, 2))", "Method", "/file.cs", new Core.TestState.Passed());
        var result = input.ToCollectedResult();
        await Assert.That(result.FullName).IsEqualTo("assembly/Class/Method");
    }

    [Test("handles empty UID string without throwing")]
    public async Task HandlesEmptyUidString()
    {
        var input = new TestNodeInput(string.Empty, "Method", "/file.cs", new Core.TestState.Passed());
        var result = input.ToCollectedResult();
        await Assert.That(result.FullName).IsEqualTo(string.Empty);
    }

    [Test("uses stripped fullName as module ID when file path is null")]
    public async Task UsesStrippedFullNameAsModuleIdWhenFilePathNull()
    {
        var input = new TestNodeInput("assembly/Class/Method(1)", "Method", null, new Core.TestState.Passed());
        var result = input.ToCollectedResult();
        await Assert.That(result.ModuleId).IsEqualTo("assembly/Class/Method");
        await Assert.That(result.FullName).IsEqualTo("assembly/Class/Method");
    }
}
