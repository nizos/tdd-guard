# ADR-009: Swift Reporter Design

## Status

Proposed

## Context

TDD Guard needs Swift support for Apple platform development. Swift has two testing frameworks:

- **XCTest**: Traditional framework, all Swift versions, all platforms
- **Swift Testing**: New framework (Swift 5.9+)

Like Go and Rust reporters, the Swift reporter must:

- Capture test results and write to `.claude/tdd-guard/data/test.json`
- Recognize compilation errors as valid RED-phase failures
- Follow the standard TDD Guard test result schema

## Decision

Implement `tdd-guard-swift` as a Swift Package with executable product.

### XCTest Support (Phase 1)

**Use XCTestObservation protocol** - Apple's official test observation API with tight object contracts established over years.

```swift
final class TDDGuardObserver: NSObject, XCTestObservation {
    func testCase(_ testCase: XCTestCase,
                  didFailWithDescription description: String,
                  inFile filePath: String?,
                  atLine lineNumber: Int) {
        // Capture failure with complete metadata
    }

    func testBundleDidFinish(_ testBundle: Bundle) {
        // Write accumulated results to test.json
    }
}
```

**Why XCTestObservation:**

- Tight, stable protocol contracts (10+ years)
- Rich structured data (file paths, line numbers, error messages)
- Works across all Apple platforms + Linux
- No fragile text parsing required

### Swift Testing Support (Phase 2)

**Parse XUnit XML output** - stable, available today.

```bash
swift test --enable-swift-testing --xunit-output /path/to/results.xml
```

**Note:** Unlike Go (`go test -json`) and Rust (`cargo nextest`), `swift test` does not support JSON output.

**Why XUnit XML:**

- Stable, documented format (industry standard)
- Works with current Swift Testing releases
- Simple XML parsing vs experimental unstable APIs

**Tradeoff accepted:** Missing file/line numbers, but provides working solution today with low risk.

### Output Format

Both approaches write to standard TDD Guard format:

```json
{
  "testModules": [
    {
      "moduleId": "TodoAppTests",
      "tests": [
        {
          "name": "testCreation",
          "fullName": "TodoItemTests.testCreation",
          "state": "failed",
          "errors": [
            {
              "message": "XCTAssertEqual failed: (\"expected\") is not equal to (\"actual\")",
              "stack": "TodoItemTests.swift:25"
            }
          ]
        }
      ]
    }
  ],
  "reason": "failed"
}
```

### Compilation Errors

Parse Swift compiler output and create synthetic failed tests (like Go reporter):

```json
{
  "testModules": [
    {
      "moduleId": "TodoAppTests",
      "tests": [
        {
          "name": "CompilationError",
          "fullName": "TodoAppTests/CompilationError",
          "state": "failed",
          "errors": [
            {
              "message": "TodoItem.swift:10:15: error: cannot find 'TodoItem' in scope"
            }
          ]
        }
      ]
    }
  ],
  "reason": "failed"
}
```

## Consequences

### Positive

- Native Swift integration using official Apple APIs
- Compilation errors recognized as valid test failures
- Follows Go/Rust reporter patterns
- Works on all Apple platforms
- Detailed error information with file/line numbers (XCTest)
- Dual framework support (XCTest + Swift Testing)

### Negative

- Swift Testing: Missing file/line numbers in XUnit XML output
- XML contract could change between Swift Testing versions
- Requires Swift toolchain installed

### Neutral

- XCTestObservation: Stable protocol contracts (10+ years, low risk)
- XUnit XML: Industry standard (low risk), but specific to Swift Testing implementation
- Results saved to `.claude/tdd-guard/data/test.json` (standard location)

## Implementation

**Phase 1**: XCTest support (Apple platforms)
**Phase 2**: Swift Testing support via XUnit XML

**Package structure:**

```
reporters/swift/
├── Package.swift
├── Sources/TDDGuardSwift/
│   ├── main.swift          # CLI entry point
│   ├── Observer.swift      # XCTestObservation
│   └── Storage.swift       # Write to test.json
└── Tests/
```

**Usage:**

```bash
# Install
swift build -c release
cp .build/release/tdd-guard-swift /usr/local/bin/

# Use with Makefile
test:
    swift test 2>&1 | tdd-guard-swift --project-root $(PWD)
```
