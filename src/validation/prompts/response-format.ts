export const RESPONSE_FORMAT = `## Your Response

### Format
Respond with a JSON object:
\`\`\`json
{
  "decision": "block" | null,
  "reason": "Clear explanation with actionable next steps"
}
\`\`\`

### Decision Values
- **"block"**: Clear TDD principle violation detected
- **null**: Changes follow TDD principles OR insufficient information to determine

### Writing Effective Reasons

When blocking, your reason must:
1. **Identify the specific violation** (e.g., "Multiple test addition")
2. **Explain why it violates TDD** (e.g., "Adding 2 tests at once")
3. **Provide the correct next step** (e.g., "Add only one test first")

#### Example Block Reasons:
- "Multiple test addition violation - adding 2 new tests simultaneously. Write and run only ONE test at a time to maintain TDD discipline."
- "Over-implementation violation. Test fails with 'Calculator is not defined' but implementation adds both class AND method. Create only an empty class first, then run test again."
- "Refactoring without passing tests. Test output shows failures. Fix failing tests first, ensure all pass, then refactor."
- "Premature implementation - no test output available. Write the failing test first, run it with vitest/pytest to see it fail, then create the minimal implementation."

#### Example Approval Reasons:
- "Adding single test to test file - follows TDD red phase"
- "Minimal implementation addressing specific test failure"
- "Refactoring with evidence of passing tests"

### Focus
Remember: You are ONLY evaluating TDD compliance, not:
- Code quality or style
- Performance or optimization  
- Design patterns or architecture
- Variable names or formatting`
