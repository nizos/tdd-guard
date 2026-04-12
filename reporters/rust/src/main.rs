// src/main.rs - Simplified main module with unified execution paths

use clap::Parser;
use std::fs;
use std::io::{self, BufRead, BufReader, IsTerminal, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};

mod error_parser;
mod parser;
mod transformer;

use error_parser::parse_error_buffer;
use parser::TestEvent;
use transformer::{transform_events, TddGuardOutput};

#[derive(Parser, Debug)]
#[command(name = "tdd-guard-rust")]
#[command(about = "Rust test reporter for TDD Guard validation")]
#[command(version = "0.1.0")]
#[command(
    long_about = "A test reporter that captures Rust test results for TDD Guard validation. \
    Supports both cargo test and cargo nextest with optional JSON output for detailed reporting."
)]
struct Args {
    /// Path to project root directory
    #[arg(long, value_name = "PATH")]
    project_root: Option<String>,

    /// Pass through mode - read from stdin instead of running tests
    #[arg(long, default_value_t = false)]
    passthrough: bool,

    /// Disable implicit passthrough when stdin is piped (auto on by default)
    #[arg(long, default_value_t = false)]
    no_auto_passthrough: bool,

    /// Test runner to use (nextest, cargo, auto)
    #[arg(long, default_value = "auto")]
    runner: String,

    /// Pass remaining args to test runner
    #[arg(trailing_var_arg = true)]
    test_args: Vec<String>,
}

fn main() -> io::Result<()> {
    let args = Args::parse();

    let exit_code = run(&args, &std::env::current_dir()?)?;

    std::process::exit(exit_code);
}

fn run(args: &Args, base_dir: &Path) -> io::Result<i32> {
    let project_root = resolve_project_root(args.project_root.as_deref(), base_dir)?;

    let auto_enabled = !args.no_auto_passthrough && env_auto_enabled();
    let use_pass = should_passthrough(args.passthrough, auto_enabled);

    if use_pass {
        process_passthrough(&project_root)
    } else {
        run_and_process(args, &project_root)
    }
}

/// Unified function to run tests and process output
fn run_and_process(args: &Args, project_root: &Path) -> io::Result<i32> {
    let runner = detect_runner(&args.runner);

    let mut cmd = build_test_command(&runner, &args.test_args);

    // Execute and collect output
    let mut child = cmd.stdout(Stdio::piped()).stderr(Stdio::piped()).spawn()?;

    let (test_lines, stderr_lines) = read_output(&mut child, false)?;

    process_output(&test_lines, &stderr_lines, project_root)?;

    let status = child.wait()?;
    Ok(status.code().unwrap_or(1))
}

/// Build test command based on runner type
fn build_test_command(runner: &str, test_args: &[String]) -> Command {
    let mut cmd = Command::new("cargo");

    match runner {
        "nextest" => {
            cmd.arg("nextest").arg("run");
            cmd.env("NEXTEST_EXPERIMENTAL_LIBTEST_JSON", "1");
            cmd.arg("--message-format").arg("libtest-json");
            cmd.arg("--no-fail-fast");
        }
        _ => {
            cmd.arg("test");
            cmd.arg("--no-fail-fast");

            // Add test arguments
            for arg in test_args {
                cmd.arg(arg);
            }

            // Add JSON output flags (best effort - may require nightly)
            cmd.arg("--");
            cmd.arg("-Z").arg("unstable-options");
            cmd.arg("--format").arg("json");
            cmd.arg("--show-output");

            // Return here to avoid adding test_args twice
            return cmd;
        }
    }

    // Add test arguments for nextest
    for arg in test_args {
        cmd.arg(arg);
    }

    cmd
}

/// Process passthrough mode - read from stdin and echo
fn process_passthrough(project_root: &Path) -> io::Result<i32> {
    let stdin = io::stdin();
    let reader = stdin.lock();

    let mut all_lines = Vec::new();
    for line in reader.lines() {
        let line = line?;
        println!("{}", line);
        io::stdout().flush()?;
        all_lines.push(line);
    }

    // Separate JSON and non-JSON lines
    let (json_lines, other_lines): (Vec<_>, Vec<_>) = all_lines
        .into_iter()
        .partition(|line| line.trim().starts_with('{') && line.contains("\"type\":"));

    process_output(&json_lines, &other_lines, project_root)?;
    Ok(0)
}

/// Read output from child process, optionally echoing to stdout/stderr
fn read_output(child: &mut Child, echo: bool) -> io::Result<(Vec<String>, Vec<String>)> {
    let stdout = child.stdout.take().expect("Failed to capture stdout");
    let stderr = child.stderr.take().expect("Failed to capture stderr");

    let mut test_lines = Vec::new();
    let mut stderr_lines = Vec::new();

    for line in BufReader::new(stdout).lines() {
        let line = line?;
        if echo {
            println!("{}", line);
            io::stdout().flush()?;
        }
        test_lines.push(line);
    }

    for line in BufReader::new(stderr).lines() {
        let line = line?;
        if echo {
            eprintln!("{}", line);
        }
        stderr_lines.push(line);
    }

    if !echo {
        for line in &test_lines {
            println!("{}", line);
        }
        for line in &stderr_lines {
            eprintln!("{}", line);
        }
        io::stdout().flush()?;
    }

    Ok((test_lines, stderr_lines))
}

/// Process test output and save results
fn process_output(
    test_lines: &[String],
    stderr_lines: &[String],
    project_root: &Path,
) -> io::Result<()> {
    // Parse JSON events
    let mut events = Vec::new();
    for line in test_lines {
        if let Ok(event) = serde_json::from_str::<TestEvent>(line) {
            events.push(event);
        }
    }

    let mut compilation_errors = parse_error_buffer(stderr_lines);

    // If no JSON events found, also check test output for errors
    if events.is_empty() && !test_lines.is_empty() {
        let test_errors = parse_error_buffer(test_lines);
        compilation_errors.extend(test_errors);
    }

    // Transform to TDD Guard format
    let output = transform_events(events, compilation_errors);

    save_results(project_root, &output)?;

    Ok(())
}

fn resolve_project_root(project_root: Option<&str>, base_dir: &Path) -> io::Result<PathBuf> {
    let path = match project_root {
        Some(root) => {
            let p = PathBuf::from(root);
            if p.is_absolute() {
                p
            } else {
                base_dir.join(p)
            }
        }
        None => base_dir.to_path_buf(),
    };
    path.canonicalize()
}

/// Save test results to TDD Guard format
fn save_results(project_root: &Path, output: &TddGuardOutput) -> io::Result<()> {
    let output_dir = project_root.join(".claude").join("tdd-guard").join("data");
    fs::create_dir_all(&output_dir)?;

    let output_file = output_dir.join("test.json");
    let temp_file = output_dir.join("test.json.tmp");

    let json = serde_json::to_string_pretty(output)?;
    fs::write(&temp_file, json)?;
    fs::rename(&temp_file, &output_file)?;

    Ok(())
}

/// Detect which test runner to use
fn detect_runner(preference: &str) -> String {
    match preference {
        "nextest" => "nextest".to_string(),
        "cargo" => "cargo".to_string(),
        "auto" => {
            // Check if nextest is available (cache this in real impl)
            if Command::new("cargo")
                .args(&["nextest", "--version"])
                .output()
                .map(|output| output.status.success())
                .unwrap_or(false)
            {
                "nextest".to_string()
            } else {
                "cargo".to_string()
            }
        }
        _ => "cargo".to_string(),
    }
}

fn env_auto_enabled() -> bool {
    match std::env::var("TDD_GUARD_AUTO_PASSTHROUGH") {
        Ok(val) => {
            let v = val.trim().to_ascii_lowercase();
            !(v == "0" || v == "false" || v == "no" || v == "off")
        }
        Err(_) => true,
    }
}

fn decide_passthrough(explicit: bool, auto_enabled: bool, stdin_is_terminal: bool) -> bool {
    if explicit {
        return true;
    }
    if auto_enabled {
        return !stdin_is_terminal;
    }
    false
}

fn should_passthrough(explicit: bool, auto_enabled: bool) -> bool {
    let stdin_is_terminal = io::stdin().is_terminal();
    decide_passthrough(explicit, auto_enabled, stdin_is_terminal)
}

#[cfg(test)]
mod tests {
    use super::*;
    use tempfile::TempDir;

    #[test]
    fn test_run_accepts_relative_project_root() {
        let ctx = TestContext::setup().with_sub_dir();

        let args = TestContext::make_args(Some("."));
        let result = run(&args, &ctx.base_dir);

        assert!(result.is_ok(), "Expected success, got: {:?}", result.err());
        assert!(TestContext::test_json_path(&ctx.base_dir).exists());
    }

    #[test]
    fn test_run_defaults_to_cwd_without_project_root() {
        let ctx = TestContext::setup();

        let args = TestContext::make_args(None);
        let result = run(&args, &ctx.project_root);

        assert!(result.is_ok(), "Expected success, got: {:?}", result.err());
        assert!(TestContext::test_json_path(&ctx.project_root).exists());
    }

    #[test]
    fn test_resolve_accepts_absolute_path() {
        let ctx = TestContext::setup();
        let result =
            resolve_project_root(Some(ctx.project_root.to_str().unwrap()), &ctx.project_root);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), ctx.project_root.canonicalize().unwrap());
    }

    #[test]
    fn test_resolve_accepts_relative_path() {
        let ctx = TestContext::setup().with_sub_dir();

        let result = resolve_project_root(Some("."), &ctx.base_dir);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), ctx.base_dir.canonicalize().unwrap());
    }

    #[test]
    fn test_resolve_accepts_path_containing_dotdot() {
        let ctx = TestContext::setup().with_sub_dir();

        let input = ctx.base_dir.join("..").to_str().unwrap().to_string();
        let result = resolve_project_root(Some(&input), &ctx.base_dir);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), ctx.project_root.canonicalize().unwrap());
    }

    #[test]
    fn test_resolve_defaults_to_base_dir_when_none() {
        let ctx = TestContext::setup();

        let result = resolve_project_root(None, &ctx.project_root);
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), ctx.project_root.canonicalize().unwrap());
    }

    #[test]
    fn test_save_results() {
        let ctx = TestContext::setup();

        let output = TddGuardOutput {
            test_modules: vec![],
            reason: Some("passed".to_string()),
        };

        save_results(&ctx.project_root, &output).unwrap();

        assert!(TestContext::test_json_path(&ctx.project_root).exists());
        assert_eq!(
            TestContext::read_test_output(&ctx.project_root)["reason"],
            "passed"
        );
    }

    #[test]
    fn test_env_auto_enabled() {
        use std::env;
        let key = "TDD_GUARD_AUTO_PASSTHROUGH";

        // Unset -> true
        env::remove_var(key);
        assert!(super::env_auto_enabled());

        env::set_var(key, "0");
        assert!(!super::env_auto_enabled());
        env::set_var(key, "false");
        assert!(!super::env_auto_enabled());
        env::set_var(key, "no");
        assert!(!super::env_auto_enabled());
        env::set_var(key, "off");
        assert!(!super::env_auto_enabled());

        env::set_var(key, "1");
        assert!(super::env_auto_enabled());
        env::set_var(key, "true");
        assert!(super::env_auto_enabled());
        env::set_var(key, "yes");
        assert!(super::env_auto_enabled());
        env::set_var(key, "on");
        assert!(super::env_auto_enabled());

        env::remove_var(key);
    }

    #[test]
    fn test_decide_passthrough_matrix() {
        // explicit always wins
        assert!(super::decide_passthrough(true, true, true));
        assert!(super::decide_passthrough(true, true, false));
        assert!(super::decide_passthrough(true, false, true));
        assert!(super::decide_passthrough(true, false, false));

        // auto disabled: never passthrough unless explicit
        assert!(!super::decide_passthrough(false, false, true));
        assert!(!super::decide_passthrough(false, false, false));

        // auto enabled: passthrough when stdin is not a terminal
        assert!(!super::decide_passthrough(false, true, true));
        assert!(super::decide_passthrough(false, true, false));
    }

    #[test]
    fn test_detect_runner() {
        assert_eq!(detect_runner("nextest"), "nextest");
        assert_eq!(detect_runner("cargo"), "cargo");
        assert_eq!(detect_runner("invalid"), "cargo");
    }

    #[test]
    fn test_process_empty_output() {
        let ctx = TestContext::setup();

        process_output(&[], &[], &ctx.project_root).unwrap();

        assert!(TestContext::test_json_path(&ctx.project_root).exists());
    }

    #[test]
    fn test_process_output_merges_stderr_compilation_errors_when_no_json() {
        let ctx = TestContext::setup();

        let test_lines = vec!["running 0 tests".to_string()];
        let stderr_lines = vec![
            "error[E0425]: cannot find value `x` in this scope".to_string(),
            " --> src/lib.rs:2:9".to_string(),
        ];

        let v = ctx.process_and_read(&test_lines, &stderr_lines);
        assert_eq!(v["reason"], "failed");
        assert!(v["testModules"]
            .as_array()
            .unwrap()
            .iter()
            .any(|m| m["moduleId"] == "compilation"));
    }

    #[test]
    fn test_process_passthrough_partitions_json_and_non_json() {
        use crate::parser::TestEvent;

        let ctx = TestContext::setup();

        let json_test_event = serde_json::to_string(&TestEvent::Test {
            name: "crate::tests::adds$works".to_string(),
            event: "failed".to_string(),
            stdout: Some("assertion `left == right` failed\n  left: 1\n  right: 2".to_string()),
            stderr: None,
        })
        .unwrap();

        let test_lines = vec![json_test_event];
        let stderr_lines = vec![
            "error[E0599]: no method named `foo` found for type `u8`".to_string(),
            " --> src/main.rs:10:5".to_string(),
        ];

        let v = ctx.process_and_read(&test_lines, &stderr_lines);
        assert_eq!(v["reason"], "failed");
        let modules = v["testModules"].as_array().unwrap();
        assert!(modules
            .iter()
            .any(|m| m["moduleId"].as_str().unwrap().contains("crate")));
        assert!(modules.iter().any(|m| m["moduleId"] == "compilation"));
    }

    // Test helpers

    struct TestContext {
        project_root: PathBuf,
        base_dir: PathBuf,
        _temp_dir: TempDir,
    }

    impl TestContext {
        fn setup() -> Self {
            let temp_dir = TempDir::new().unwrap();
            let project_root = temp_dir.path().to_path_buf();
            let base_dir = project_root.clone();
            Self {
                project_root,
                base_dir,
                _temp_dir: temp_dir,
            }
        }

        fn with_sub_dir(mut self) -> Self {
            let sub_dir = self.project_root.join("sub");
            fs::create_dir_all(&sub_dir).unwrap();
            self.base_dir = sub_dir;
            self
        }

        fn test_json_path(root: &Path) -> PathBuf {
            root.join(".claude")
                .join("tdd-guard")
                .join("data")
                .join("test.json")
        }

        fn read_test_output(root: &Path) -> serde_json::Value {
            let content = fs::read_to_string(Self::test_json_path(root)).unwrap();
            serde_json::from_str(&content).unwrap()
        }

        fn process_and_read(
            &self,
            test_lines: &[String],
            stderr_lines: &[String],
        ) -> serde_json::Value {
            process_output(test_lines, stderr_lines, &self.project_root).unwrap();
            Self::read_test_output(&self.project_root)
        }

        fn make_args(project_root: Option<&str>) -> Args {
            Args {
                project_root: project_root.map(|s| s.to_string()),
                passthrough: true,
                no_auto_passthrough: true,
                runner: "auto".to_string(),
                test_args: vec![],
            }
        }
    }
}
