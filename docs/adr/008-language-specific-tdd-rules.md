# ADR-008: Language-Specific TDD Validation Rules

## Status

Accepted

## Context

TDD Guard originally applied identical validation rules to all programming languages, enforcing strict incremental Test-Driven Development regardless of language characteristics. This created a fundamental incompatibility with compiled languages like Java and Kotlin.

The problem with compiled languages:

**Compilation Phase Blocks TDD Workflow**:

- Write test → Compilation fails (class doesn't exist)
- TDD Guard requires test.json to validate implementation
- test.json only generated when tests successfully compile and run
- Cannot compile without creating stubs
- Cannot create stubs without test.json (chicken-and-egg problem)

**Example**:

```java
// 1. Write CustomerTest.java
@Test
void shouldCreateCustomer() {
    Customer c = new Customer("id", "email");  // Compilation error: Customer doesn't exist
    assertEquals("id", c.getId());
}

// 2. Run test → Compilation fails, no test.json created
// 3. Try to create Customer class → TDD Guard blocks (no test.json)
// 4. Stuck: Can't get test.json without compilation, can't compile without implementation
```

**Research Findings**:

From Kent Beck's "Test-Driven Development By Example" and Java TDD community:

- POJOs (Plain Old Java Objects) are structural code, not behavioral
- Community consensus: Testing simple getters/setters is wasteful
- Kent Beck's "Obvious Implementation": When you know what to type and can do it quickly, type it
- Data classes are covered adequately by integration tests

We considered several approaches:

1. **Maintain strict incremental for all languages** - Forces users to create empty stubs one at a time
2. **Capture compilation errors as test failures** - Parse compiler output and write to test.json
3. **Language-specific rules** - Different validation for compiled vs interpreted languages
4. **Disable TDD Guard for Java** - Not acceptable, defeats the purpose

## Decision

We will implement language-specific TDD validation rules, with different treatment for compiled languages (Java, Kotlin) versus interpreted languages (Python, JavaScript, TypeScript, Go, Rust).

**Architecture**:

1. **Language Detection**: Detect language from file extension
2. **Language Categorization**: Classify as "compiled" or "interpreted"
3. **Test File Detection**: Proactively check if test file exists (CustomerTest.java for Customer.java)
4. **Conditional Prompts**: Include language-specific rules only for matching languages

**Implementation**:

```typescript
// Language detection
export type FileType = 'java' | 'kotlin' | 'python' | 'javascript' | ...
export type LanguageCategory = 'compiled' | 'interpreted'

// Context enhancement
export type Context = {
  modifications: string
  test?: string
  language?: string
  languageCategory?: 'compiled' | 'interpreted'
  testFileExists?: boolean  // NEW: Detects test file without test.json
  // ... other fields
}

// Conditional prompt assembly
if (languageCategory === 'compiled') {
  include COMPILED_LANGUAGE_RULES  // Only for Java/Kotlin
}
```

**Java/Kotlin Rules** (applies ONLY when `languageCategory === 'compiled'`):

When test file exists (proves test-first intent):

- ✅ ALLOW: Complete POJO/data class (fields + constructor + getters) in ONE edit
- ✅ ALLOW: Empty method stubs to fix compilation
- ✅ ALLOW: Simple field assignments in constructors
- ✅ ALLOW: Simple getters (`return field;`) and setters (`this.field = value;`)
- ❌ BLOCK: Business logic (if/else, calculations, validation, method calls)
- ❌ BLOCK: Default values, object creation, complex initialization

When test file does NOT exist:

- ❌ BLOCK: All implementation (same as other languages)

**Other Languages** (Python, JavaScript, TypeScript, Go, Rust):

- No changes - original strict incremental TDD rules apply
- Receive empty string for language-specific rules section
- Behavior completely unchanged

**Test File Detection**:

Supports common test naming conventions:

- Java/Kotlin: `Customer.java` → `CustomerTest.java`, `CustomerTest.kt`
- Python: `customer.py` → `test_customer.py`
- JavaScript: `customer.js` → `customer.test.js`

Supports directory mirroring:

- `src/main/java/Customer.java` → `src/test/java/CustomerTest.java`
- Read-only file existence checks
- No path traversal (uses safe path operations)

## Consequences

### Positive

- **Enables Java/Kotlin TDD** - Resolves compilation phase chicken-and-egg problem
- **Research-backed** - Based on Kent Beck's TDD principles and community consensus
- **Zero impact on existing languages** - Python/JavaScript/TypeScript completely unchanged
- **Productive workflow** - Allows complete data class setup while still blocking business logic
- **Maintains TDD discipline** - Still requires test-first development, just adapted for compilation phase

### Negative

- **Increased complexity** - More code paths for validation
- **Language-specific maintenance** - Must consider language differences in future changes
- **Potential for rule drift** - Could diverge too far between languages if not careful

### Neutral

- Language detection is straightforward (file extension based)
- Test file detection adds minimal overhead (single file existence check)
- Prompt assembly is conditional but deterministic

## Validation

**Manual testing verified**:

- Java POJO: Complete data class allowed when test file exists ✅
- Java business logic: Blocked without test evidence ✅
- Kotlin data class: Allowed when test file exists ✅
- Python: No changes, strict incremental TDD maintained ✅
- JavaScript/TypeScript: No changes, strict incremental TDD maintained ✅

**Automated testing**:

- All 648 unit tests passing ✅
- Language isolation verified programmatically ✅
- Test file detection tested across languages ✅

## Implementation Notes

**Files Created**:

- `src/validation/prompts/languages/compiled.ts` - Java/Kotlin rules
- `src/validation/context/testFileDetector.ts` - Test file detection

**Files Modified**:

- `src/hooks/fileTypeDetection.ts` - Language detection and categorization
- `src/contracts/types/Context.ts` - Added language fields
- `src/cli/buildContext.ts` - Language detection and test file detection
- `src/validation/context/context.ts` - Conditional prompt assembly

**Security Considerations**:

- Test file detection uses read-only operations
- No path traversal (uses Node.js path.join safely)
- Fails gracefully if test file not found (returns null)

## References

- Kent Beck: "Test-Driven Development By Example"
- Java TDD community consensus on POJO testing
- Stack Overflow: "Is it necessary to cover POJO by tests according to TDD?"
- ADR-004: Monorepo Architecture (reporters structure)
- ADR-005: CLAUDE_PROJECT_DIR Support (project root detection)
