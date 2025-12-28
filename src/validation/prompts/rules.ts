export const RULES = `## TDD Fundamentals

### The TDD Cycle
The foundation of TDD is the Red-Green-Refactor cycle:

1. **Red Phase**: Write ONE failing test that describes desired behavior
   - The test must fail for the RIGHT reason (not syntax/import errors, BUT compilation errors ARE valid test failures in compiled languages)
   - **Compilation errors ARE valid test failures** in compiled languages (Go, Java, Swift, Kotlin, Rust, C++, etc.)
   - Examples of valid failures: undefined functions/types, missing methods, type mismatches
   - Only one test at a time - this is critical for TDD discipline
   - **Adding a single test to a test file is ALWAYS allowed** - no prior test output needed
   - Starting TDD for a new feature is always valid, even if test output shows unrelated work

2. **Green Phase**: Write MINIMAL code to make the test pass
   - Implement only what's needed for the current failing test
   - No anticipatory coding or extra features
   - Address the specific failure message

3. **Refactor Phase**: Improve code structure while keeping tests green
   - Only allowed when relevant tests are passing
   - Requires proof that tests have been run and are green
   - Applies to BOTH implementation and test code
   - No refactoring with failing tests - fix them first

### Core Violations

1. **Multiple Test Addition**
   - Adding more than one new test at once
   - Exception: Initial test file setup or extracting shared test utilities

2. **Over-Implementation**  
   - Code that exceeds what's needed to pass the current failing test
   - Adding untested features, methods, or error handling
   - Implementing multiple methods when test only requires one

3. **Premature Implementation**
   - Adding implementation before a test exists and fails properly
   - Adding implementation without running the test first
   - Refactoring when tests haven't been run or are failing

### Critical Principle: Incremental Development

Each step in TDD should address ONE specific issue. The definition of "minimal" differs between interpreted and compiled languages:

**Interpreted languages (JavaScript, Python, Ruby, PHP):**
- Test fails "not defined" → Create empty stub/class only
- Test fails "not a function" → Add method stub only
- Test fails with assertion → Implement minimal logic only

**Compiled languages (Go, Java, Swift, Kotlin, Rust, C++):**
Each compilation error is a separate TDD step:
1. "undefined: TypeName" → Create empty struct/class (type TodoItem struct {})
2. "undefined: function" → Add function signature with zero-value return
3. "missing return statement" → Return zero value/default
4. "type mismatch" → Adjust type, keep zero-value return
5. "field/property undefined" → Add field/property to type
6. Assertion failure → NOW implement actual logic

**Zero values ARE minimal implementation for compiled languages:**

When a test causes a compilation error, creating the minimal structure to satisfy the compiler is the correct first step. This includes:
- Creating empty types/structs/classes
- Adding method signatures that return zero/default values
- Using language-specific unimplemented markers when needed

Progressive compilation errors guide development - this IS the Red phase working correctly.

### Compiled Language Specifics

**Compiler satisfaction is required, not over-implementation:**

When a compilation error occurs, the minimum code to satisfy the compiler IS the correct minimal implementation. This includes:

- **Required imports**: Add import statements needed for types used in test
- **Type definitions**: Create structs/classes/interfaces referenced by test
- **Method signatures**: Add methods with correct signature but zero-value returns
- **Return statements**: Use language defaults (zero values, null, Default trait)

**Unimplemented markers for untested code:**

If implementing an interface/protocol/trait forces methods not yet tested, use explicit unimplemented markers:
- Go: panic("not implemented") or return ZeroValue
- Rust: todo!() or unimplemented!()
- Kotlin: TODO("not implemented")
- Swift: fatalError("not implemented")
- Java: throw new UnsupportedOperationException("not implemented")

These markers satisfy the compiler while making it clear the code is intentionally incomplete. They will fail loudly if accidentally called before being implemented.

**What constitutes over-implementation in compiled languages:**

- ❌ Adding business logic when test only shows compilation error
- ❌ Implementing multiple methods when test only requires one
- ❌ Adding validation, error handling, or complex logic before assertions fail
- ❌ Setting non-zero values when test hasn't shown what values are needed
- ✅ Creating empty types when compilation error says type is undefined
- ✅ Adding fields/properties when compilation error says they're missing
- ✅ Returning zero/default values to satisfy "missing return" errors
- ✅ Adding required imports and type signatures

**Example progression:**

Step 1 - Test: NewTodoItem("task")
  Error: undefined: NewTodoItem
  → Add: func NewTodoItem(title string) TodoItem { return TodoItem{} }

Step 2 - Test runs: type TodoItem has no field Title
  → Add: type TodoItem struct { Title string }

Step 3 - Test runs: Expected "task", got ""
  → Modify: return TodoItem{Title: title}

Step 4 - Test passes ✓

Each step addresses exactly one failure - this is minimal implementation for compiled languages.

### General Information
- Sometimes the test output shows no tests have been run when a test is failing due to a missing import or constructor. In such cases, allow the agent to create simple stubs. Ask them if they forgot to create a stub if they are stuck.
- **For all languages**: It is never allowed to introduce business logic without evidence of relevant failing tests.
- **For interpreted languages** (JavaScript, Python, Ruby, PHP): Only implement what the test explicitly requires. No anticipatory code or infrastructure setup.
- **For compiled languages** (Go, Java, Swift, Kotlin, Rust, C++): Compiler satisfaction is required - stubs, imports, type definitions, and zero-value returns are necessary to reach test assertions (see Compiled Language Specifics for details).
- In the refactor phase, it is perfectly fine to refactor both teest and implementation code. That said, completely new functionality is not allowed. Types, clean up, abstractions, and helpers are allowed as long as they do not introduce new behavior.
- Adding types, interfaces, or a constant in order to replace magic values is perfectly fine during refactoring.
- Provide the agent with helpful directions so that they do not get stuck when blocking them.
`
