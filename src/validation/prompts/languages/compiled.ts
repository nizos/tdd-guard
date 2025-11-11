/**
 * Java/Kotlin-specific TDD rules - ONLY applied to .java and .kt files.
 * Based on Kent Beck's "Obvious Implementation" - allows complete POJOs/data classes.
 */
export const COMPILED_LANGUAGE_RULES = `## Java/Kotlin TDD Rules

**Kent Beck Principle**: "If you know what to type and can do it quickly, then do it"

### Decision Tree

**IF test file exists:**
1. **Pure POJO/data class/enum/record/interface/constants/companion object/sealed/@interface** (only fields + constructor + getters/setters, NO logic) → ALLOW complete structure
2. **Has business logic** (if/else, calculations, validation, method calls) → BLOCK, need test output
3. **Empty stub** (return null, return 0, empty body) → ALLOW (fixes compilation)

**IF no test file** → BLOCK all implementation

### Structural Constructs Definition

**ALLOW as ONE edit when test file exists** - Zero business logic only:

#### 1. POJOs (Plain Old Java Objects)
- Fields: private String name;
- Constructor: this.field = param; (simple assignments only)
- Getters: return field; (no calculations)
- Setters: this.field = value; (no validation)
- Annotations: @Override, @NotNull, @JsonProperty

**Example ALLOWED**:
\`\`\`java
private String email, name;
public Customer(String id, String email, String name) {
    this.id = id; this.email = email; this.name = name;
}
public String getEmail() { return email; }
\`\`\`

#### 2. Enums
**ALLOWED** (constants only):
\`\`\`java
public enum Status { ACTIVE, INACTIVE, PENDING }

// With fields (no logic)
public enum Priority {
    LOW(1), MEDIUM(2), HIGH(3);
    private final int value;
    Priority(int value) { this.value = value; }
    public int getValue() { return value; }
}
\`\`\`

**BLOCKED** (business logic):
\`\`\`java
public enum Status {
    ACTIVE {
        @Override
        public boolean canTransition() {
            return checkRules(); // Business logic
        }
    }
}
\`\`\`

#### 3. Records (Java 14+) / Data Classes (Kotlin)
**ALLOWED**:
\`\`\`java
public record Point(int x, int y) {}
public record Email(String value) {} // No validation
\`\`\`

\`\`\`kotlin
data class User(val name: String, val email: String)
\`\`\`

**BLOCKED** (validation logic):
\`\`\`java
public record Email(String value) {
    public Email {
        if (!value.contains("@")) throw new IllegalArgumentException();
    }
}
\`\`\`

#### 4. Interfaces
**ALLOWED** (method signatures only):
\`\`\`java
public interface Repository<T> {
    void save(T entity);
    T find(String id);
}
\`\`\`

**BLOCKED** (default methods with logic):
\`\`\`java
public interface Calculator {
    default int add(int a, int b) {
        return a + b; // Business logic
    }
}
\`\`\`

#### 5. Exception/Throwable Classes
**ALLOWED** (structure only, delegates to super):
\`\`\`java
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
\`\`\`

#### 6. Constants Classes
**ALLOWED**:
\`\`\`java
public class ApiConfig {
    public static final String BASE_URL = "https://api.example.com";
    public static final int TIMEOUT = 5000;
    private ApiConfig() {} // Prevent instantiation
}
\`\`\`

#### 7. Companion Objects (Kotlin)
**ALLOWED**:
\`\`\`kotlin
class Settings {
    companion object {
        const val MAX_SIZE = 100
        const val API_KEY = "default-key"
    }
}
\`\`\`

#### 8. Sealed Classes/Interfaces
**ALLOWED** (declaration only):
\`\`\`java
public sealed interface Shape permits Circle, Square {}
\`\`\`

\`\`\`kotlin
sealed class Result {
    data class Success(val data: String) : Result()
    data class Error(val message: String) : Result()
}
\`\`\`

#### 9. Annotation Interfaces
**ALLOWED**:
\`\`\`java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {
    int ttl() default 300;
}
\`\`\`

**BLOCKED (all structural constructs with business logic)**:
- Conditionals: if/else, switch, ternary
- Calculations: arithmetic, string operations
- Validation: null checks, throwing exceptions (except in exception constructors)
- Method calls: UUID.randomUUID(), LocalDate.now(), external APIs
- Default values with logic: this.active = determineStatus();

### Compilation Stubs

When test file exists, ALLOW empty stubs even with test output:
- Empty methods: return null; return 0; empty {}
- Purpose: Fix compilation before test can run

**Rationale**: Compilation failures are valid TDD failures. Empty stubs enable the RED phase.
`
