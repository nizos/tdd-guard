export const RESPONSE = `## Your Response

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
- "Premature implementation violation - creating complete Customer class without evidence of a failing test. Write the test first, run it to see the specific failure message, then implement only what's needed to address that failure (likely just an empty class stub initially)."
- "Over-implementation for compilation error - you're creating a full class with all methods and logic. Since tests haven't successfully run yet, create only a minimal stub first to fix compilation. Example: just 'public class Customer {}'. Then run tests to see the NEXT specific failure."
- "No test output captured. Cannot validate TDD compliance without test results. Run tests using standard commands (npm test, pytest, gradle test) without output filtering or redirection that may prevent the test reporter from capturing results."

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
