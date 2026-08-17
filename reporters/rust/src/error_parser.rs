// src/error_parser.rs - Simplified rustc error parsing

use lazy_static::lazy_static;
use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CompilationError {
    pub code: Option<String>,
    pub file: Option<String>,
    pub line: Option<u32>,
    pub column: Option<u32>,
    pub message: String,
    pub help: Option<String>,
    pub note: Option<String>,
}

lazy_static! {
    // Core patterns only - keep it simple
    static ref ERROR_CODE_RE: Regex = Regex::new(r"error\[E(\d{4})\]:\s*(.+)").unwrap();
    static ref LOCATION_RE: Regex = Regex::new(r"(?:-->|--&gt;)\s*(.+?):(\d+):(\d+)").unwrap();
    static ref SIMPLE_ERROR_RE: Regex = Regex::new(r"error:\s*(.+)").unwrap();
    static ref HELP_RE: Regex = Regex::new(r"^\s*help:\s*(.+)").unwrap();
    static ref NOTE_RE: Regex = Regex::new(r"^\s*note:\s*(.+)").unwrap();
    static ref ANSI_ESCAPE_RE: Regex = Regex::new(r"\x1b\[[0-9;]*m").unwrap();
}

/// Strip ANSI escape codes from a string
fn strip_ansi_codes(s: &str) -> String {
    ANSI_ESCAPE_RE.replace_all(s, "").to_string()
}

/// Parse a buffer of lines to extract compilation errors
pub fn parse_error_buffer(lines: &[String]) -> Vec<CompilationError> {
    // Preprocess: strip ANSI codes from all lines once
    let cleaned: Vec<String> = lines.iter().map(|line| strip_ansi_codes(line)).collect();
    let lines = &cleaned[..];

    let mut errors = Vec::new();
    let mut current_error: Option<CompilationError> = None;

    for line in lines {
        // Skip common boilerplate
        if line.contains("aborting due to")
            || line.contains("test failed, to rerun")
            || line.contains("test run failed")
        {
            continue;
        }

        // Check for new error
        if let Some(error) = parse_error_line(line) {
            // Save previous error if any
            if let Some(err) = current_error.take() {
                errors.push(err);
            }
            current_error = Some(error);
            continue;
        }

        // If we have a current error, check for additional info
        if let Some(ref mut error) = current_error {
            // Location
            if let Some(captures) = LOCATION_RE.captures(line) {
                error.file = Some(captures[1].to_string());
                error.line = captures[2].parse().ok();
                error.column = captures[3].parse().ok();
            }
            // Help
            else if let Some(captures) = HELP_RE.captures(line) {
                append_to_field(&mut error.help, &captures[1]);
            }
            // Note
            else if let Some(captures) = NOTE_RE.captures(line) {
                append_to_field(&mut error.note, &captures[1]);
            }
        }
    }

    // Don't forget the last error
    if let Some(err) = current_error {
        errors.push(err);
    }

    // If no errors found but there are error indicators, create a generic one
    if errors.is_empty()
        && lines.iter().any(|l| {
            (l.contains("error:") || l.contains("error[E")) &&
        // Skip common non-compilation-terminal messages
        !l.contains("test failed") &&
        !l.contains("test run failed") &&
        !l.contains("aborting due to")
        })
    {
        errors.push(CompilationError {
            code: None,
            file: None,
            line: None,
            column: None,
            message: "Compilation failed".to_string(),
            help: None,
            note: Some(lines.join("\n")),
        });
    }

    errors
}

/// Parse a single line for error patterns
fn parse_error_line(line: &str) -> Option<CompilationError> {
    // Try error with code
    if let Some(captures) = ERROR_CODE_RE.captures(line) {
        return Some(CompilationError {
            code: Some(format!("E{}", &captures[1])),
            message: captures[2].to_string(),
            file: None,
            line: None,
            column: None,
            help: None,
            note: None,
        });
    }

    // Try simple error
    if let Some(captures) = SIMPLE_ERROR_RE.captures(line) {
        let message = captures[1].to_string();

        // Skip non-compilation errors
        if message.starts_with("could not compile ")
            || message.starts_with("failed to parse")
            || message.starts_with("failed to compile")
        {
            return Some(CompilationError {
                code: None,
                message,
                file: None,
                line: None,
                column: None,
                help: None,
                note: None,
            });
        }

        // Skip test runner errors
        if message.contains("test failed")
            || message.contains("test run failed")
            || message.contains("aborting due to")
        {
            return None;
        }

        return Some(CompilationError {
            code: None,
            message,
            file: None,
            line: None,
            column: None,
            help: None,
            note: None,
        });
    }

    None
}

/// Helper to append text to an optional field
fn append_to_field(field: &mut Option<String>, text: &str) {
    *field = Some(match field.take() {
        Some(existing) => format!("{}\n{}", existing, text),
        None => text.to_string(),
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_error_with_code() {
        let lines = vec![
            "error[E0432]: unresolved import `non_existent_module`".to_string(),
            " --> src/lib.rs:1:5".to_string(),
        ];

        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, Some("E0432".to_string()));
        assert_eq!(errors[0].message, "unresolved import `non_existent_module`");
        assert_eq!(errors[0].file, Some("src/lib.rs".to_string()));
        assert_eq!(errors[0].line, Some(1));
        assert_eq!(errors[0].column, Some(5));
    }

    #[test]
    fn test_parse_simple_error() {
        let lines = vec!["error: failed to parse manifest".to_string()];

        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, None);
        assert_eq!(errors[0].message, "failed to parse manifest");
    }

    #[test]
    fn test_skip_test_errors() {
        let lines = vec![
            "error: test failed, to rerun pass `--lib`".to_string(),
            "error: aborting due to 2 previous errors".to_string(),
        ];

        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 0);
    }

    #[test]
    fn test_parse_with_help_and_note() {
        let lines = vec![
            "error[E0502]: cannot borrow `s` as mutable".to_string(),
            " --> src/lib.rs:4:14".to_string(),
            "help: consider moving the mutable borrow".to_string(),
            "note: the borrow checker ensures memory safety".to_string(),
        ];

        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, Some("E0502".to_string()));
        assert_eq!(
            errors[0].help,
            Some("consider moving the mutable borrow".to_string())
        );
        assert_eq!(
            errors[0].note,
            Some("the borrow checker ensures memory safety".to_string())
        );
    }

    #[test]
    fn test_multiple_errors() {
        let lines = vec![
            "error[E0432]: unresolved import".to_string(),
            " --> src/lib.rs:1:5".to_string(),
            "error[E0308]: mismatched types".to_string(),
            " --> src/lib.rs:5:18".to_string(),
        ];

        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 2);
        assert_eq!(errors[0].code, Some("E0432".to_string()));
        assert_eq!(errors[1].code, Some("E0308".to_string()));
    }

    #[test]
    fn test_location_html_escaped_arrow() {
        let lines = vec![
            "error[E0308]: mismatched types".to_string(),
            " --&gt; src/main.rs:10:3".to_string(),
        ];
        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, Some("E0308".to_string()));
        assert_eq!(errors[0].file, Some("src/main.rs".to_string()));
        assert_eq!(errors[0].line, Some(10));
        assert_eq!(errors[0].column, Some(3));
    }

    #[test]
    fn test_multiline_help_and_note_accumulation() {
        let lines = vec![
            "error[E0502]: cannot borrow `s` as mutable".to_string(),
            " --> src/lib.rs:4:14".to_string(),
            "  help: first suggestion".to_string(),
            "  help: second suggestion".to_string(),
            "  note: extra context".to_string(),
            "  note: more context".to_string(),
        ];
        let errors = parse_error_buffer(&lines);
        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, Some("E0502".to_string()));
        assert_eq!(
            errors[0].help.as_deref(),
            Some("first suggestion\nsecond suggestion")
        );
        assert_eq!(
            errors[0].note.as_deref(),
            Some("extra context\nmore context")
        );
    }

    #[test]
    fn test_parse_error_with_ansi_colors() {
        let lines = vec![
            "\u{1b}[0m\u{1b}[1m\u{1b}[38;5;9merror[E0432]\u{1b}[0m\u{1b}[0m\u{1b}[1m: unresolved import `non_existent_module`\u{1b}[0m".to_string(),
            "\u{1b}[0m \u{1b}[0m\u{1b}[0m\u{1b}[1m\u{1b}[38;5;12m--> \u{1b}[0m\u{1b}[0msrc/lib.rs:1:5\u{1b}[0m".to_string(),
        ];

        let errors = parse_error_buffer(&lines);

        assert_eq!(errors.len(), 1);
        assert_eq!(errors[0].code, Some("E0432".to_string()));
        assert_eq!(errors[0].message, "unresolved import `non_existent_module`");
        assert_eq!(errors[0].file, Some("src/lib.rs".to_string()));
        assert_eq!(errors[0].line, Some(1));
        assert_eq!(errors[0].column, Some(5));
    }
}
