// src/transformer.rs - Simplified transformer with priority-based logic

use crate::error_parser::CompilationError;
use crate::parser::TestEvent;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// TDD Guard output format
#[derive(Debug, Default, Serialize, Deserialize)]
pub struct TddGuardOutput {
    #[serde(rename = "testModules")]
    pub test_modules: Vec<TestModule>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub reason: Option<String>,
}

impl TddGuardOutput {
    /// Add compilation errors to the output
    #[allow(dead_code)]
    pub fn add_compilation_errors(&mut self, errors: Vec<CompilationError>) {
        if errors.is_empty() {
            return;
        }

        // Check if compilation module exists
        let module_exists = self
            .test_modules
            .iter()
            .any(|m| m.module_id == "compilation");

        if !module_exists {
            self.test_modules.push(TestModule {
                module_id: "compilation".to_string(),
                tests: Vec::new(),
            });
        }

        // Find the compilation module (it should exist now)
        let module = self
            .test_modules
            .iter_mut()
            .find(|m| m.module_id == "compilation")
            .unwrap(); // Safe to unwrap since we just ensured it exists

        // Add build test with detailed errors
        module.tests.push(TestResult {
            name: "build".to_string(),
            full_name: "compilation::build".to_string(),
            state: "failed".to_string(),
            errors: errors.into_iter().map(format_compilation_error).collect(),
        });

        self.reason = Some("failed".to_string());
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TestModule {
    #[serde(rename = "moduleId")]
    pub module_id: String,
    pub tests: Vec<TestResult>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TestResult {
    pub name: String,
    #[serde(rename = "fullName")]
    pub full_name: String,
    pub state: String,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub errors: Vec<TestError>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TestError {
    pub message: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub location: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub code: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub help: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub note: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub expected: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub actual: Option<String>,
}

/// Transform test events into TDD Guard format
pub fn transform_events(
    events: Vec<TestEvent>,
    compilation_errors: Vec<CompilationError>,
) -> TddGuardOutput {
    let mut modules: HashMap<String, TestModule> = HashMap::new();

    if !compilation_errors.is_empty() {
        add_compilation_module(&mut modules, compilation_errors);
    }

    // Process test events
    let mut has_test_failure = false;

    for event in events {
        if let TestEvent::Test {
            name,
            event: event_type,
            ..
        } = &event
        {
            let module_name = event.module_name();
            let simple_name = event.simple_test_name();

            let state = match event_type.as_str() {
                "ok" => "passed",
                "failed" => {
                    has_test_failure = true;
                    "failed"
                }
                "ignored" => "skipped",
                _ => continue,
            };

            let module = get_or_create_module(&mut modules, module_name);

            module.tests.push(TestResult {
                name: simple_name,
                full_name: normalize_full_name(name),
                state: state.to_string(),
                errors: if state == "failed" {
                    build_test_errors(&event)
                } else {
                    Vec::new()
                },
            });
        }
    }

    // Determine overall reason with simple priority
    let reason = if modules.contains_key("compilation") || has_test_failure {
        "failed"
    } else {
        "passed"
    };

    let mut test_modules: Vec<TestModule> = modules.into_values().collect();
    test_modules.sort_by(|a, b| a.module_id.cmp(&b.module_id));

    TddGuardOutput {
        test_modules,
        reason: Some(reason.to_string()),
    }
}

/// Add compilation errors as a special module
fn add_compilation_module(
    modules: &mut HashMap<String, TestModule>,
    errors: Vec<CompilationError>,
) {
    let module = get_or_create_module(modules, "compilation".to_string());

    module.tests.push(TestResult {
        name: "build".to_string(),
        full_name: "compilation::build".to_string(),
        state: "failed".to_string(),
        errors: errors.into_iter().map(format_compilation_error).collect(),
    });
}

/// Get or create a module
fn get_or_create_module(
    modules: &mut HashMap<String, TestModule>,
    name: String,
) -> &mut TestModule {
    modules.entry(name.clone()).or_insert_with(|| TestModule {
        module_id: name,
        tests: Vec::new(),
    })
}

/// Format a compilation error for output
fn format_compilation_error(error: CompilationError) -> TestError {
    let mut message = error.message.clone();

    // Add location to message if available
    if let (Some(file), Some(line), Some(col)) = (&error.file, error.line, error.column) {
        message = format!("{}:{}:{}: {}", file, line, col, message);
    }

    // Add error code if present
    if let Some(code) = &error.code {
        message = format!("[{}] {}", code, message);
    }

    TestError {
        message,
        location: error.file.as_ref().map(|f| {
            if let (Some(line), Some(col)) = (error.line, error.column) {
                format!("{}:{}:{}", f, line, col)
            } else {
                f.clone()
            }
        }),
        code: error.code,
        help: error.help,
        note: error.note,
        expected: None,
        actual: None,
    }
}

/// Build test errors from test event
fn build_test_errors(event: &TestEvent) -> Vec<TestError> {
    let message = event
        .error_message()
        .unwrap_or_else(|| "Test failed".to_string());

    let (message, expected, actual) = extract_assertion_details(&message);

    vec![TestError {
        message,
        location: None,
        code: None,
        help: None,
        note: None,
        expected,
        actual,
    }]
}

/// Normalize test name format (nextest uses $ separator)
fn normalize_full_name(name: &str) -> String {
    name.replace('$', "::")
}

/// Extract assertion details from error message
fn extract_assertion_details(message: &str) -> (String, Option<String>, Option<String>) {
    // Check for assert_eq!/assert_ne! pattern
    if message.contains("assertion `left == right` failed")
        || message.contains("assertion `left != right` failed")
    {
        let lines: Vec<&str> = message.lines().collect();
        let mut expected = None;
        let mut actual = None;

        for line in &lines {
            let trimmed = line.trim();
            if trimmed.starts_with("left:") {
                actual = Some(trimmed.trim_start_matches("left:").trim().to_string());
            } else if trimmed.starts_with("right:") {
                expected = Some(trimmed.trim_start_matches("right:").trim().to_string());
            }
        }

        let main_message = lines
            .first()
            .map(|s| s.to_string())
            .unwrap_or_else(|| message.to_string());

        return (main_message, expected, actual);
    }

    // Check for panic pattern
    if let Some(idx) = message.find("panicked at '") {
        let start = idx + "panicked at '".len();
        if let Some(rest) = message.get(start..) {
            if let Some(end) = rest.find('\'') {
                let panic_msg = format!("panic: {}", &rest[..end]);
                return (panic_msg, None, None);
            }
        }
    }

    // Return message as-is
    (message.to_string(), None, None)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_transform_with_compilation_errors() {
        let errors = vec![CompilationError {
            code: Some("E0432".to_string()),
            file: Some("src/lib.rs".to_string()),
            line: Some(1),
            column: Some(5),
            message: "unresolved import".to_string(),
            help: None,
            note: None,
        }];

        let output = transform_events(vec![], errors);

        assert_eq!(output.reason, Some("failed".to_string()));
        assert_eq!(output.test_modules.len(), 1);
        assert_eq!(output.test_modules[0].module_id, "compilation");
        assert_eq!(output.test_modules[0].tests[0].state, "failed");
    }

    #[test]
    fn test_transform_passing_tests() {
        let events = vec![TestEvent::Test {
            name: "my_crate::tests::test_pass".to_string(),
            event: "ok".to_string(),
            stdout: None,
            stderr: None,
        }];

        let output = transform_events(events, vec![]);

        assert_eq!(output.reason, Some("passed".to_string()));
        assert_eq!(output.test_modules.len(), 1);
        assert_eq!(output.test_modules[0].tests[0].state, "passed");
    }

    #[test]
    fn test_transform_failing_tests() {
        let events = vec![TestEvent::Test {
            name: "my_crate::tests::test_fail".to_string(),
            event: "failed".to_string(),
            stdout: Some("assertion failed".to_string()),
            stderr: None,
        }];

        let output = transform_events(events, vec![]);

        assert_eq!(output.reason, Some("failed".to_string()));
        assert_eq!(output.test_modules[0].tests[0].state, "failed");
        assert!(!output.test_modules[0].tests[0].errors.is_empty());
    }

    #[test]
    fn test_extract_assertion_details() {
        let message = "assertion `left == right` failed\n  left: 5\n  right: 6";
        let (msg, expected, actual) = extract_assertion_details(message);

        assert_eq!(msg, "assertion `left == right` failed");
        assert_eq!(expected, Some("6".to_string()));
        assert_eq!(actual, Some("5".to_string()));
    }

    #[test]
    fn test_module_sorting() {
        let events = vec![
            TestEvent::Test {
                name: "crate_b::test".to_string(),
                event: "ok".to_string(),
                stdout: None,
                stderr: None,
            },
            TestEvent::Test {
                name: "crate_a::test".to_string(),
                event: "ok".to_string(),
                stdout: None,
                stderr: None,
            },
        ];

        let output = transform_events(events, vec![]);

        assert_eq!(output.test_modules[0].module_id, "crate_a");
        assert_eq!(output.test_modules[1].module_id, "crate_b");
    }

    #[test]
    fn test_empty_output() {
        let output = transform_events(vec![], vec![]);
        assert_eq!(output.test_modules.len(), 0);
        assert_eq!(output.reason, Some("passed".to_string()));
    }

    #[test]
    fn test_format_compilation_error_includes_code_and_location() {
        use crate::error_parser::CompilationError;
        let err = CompilationError {
            code: Some("E0432".to_string()),
            file: Some("src/lib.rs".to_string()),
            line: Some(1),
            column: Some(5),
            message: "unresolved import".to_string(),
            help: None,
            note: None,
        };
        let te = super::format_compilation_error(err);
        assert!(te.message.contains("[E0432]"));
        assert!(te.message.contains("src/lib.rs:1:5"));
        assert!(te.message.contains("unresolved import"));
        assert_eq!(te.location.as_deref(), Some("src/lib.rs:1:5"));
        assert_eq!(te.code.as_deref(), Some("E0432"));
    }
}
