package main

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/nizos/tdd-guard/reporters/go/internal/storage"
)

func TestProcess(t *testing.T) {
	// Setup temp directory for all tests
	oldWd, _ := os.Getwd()
	tempDir := t.TempDir()
	os.Chdir(tempDir)
	t.Cleanup(func() {
		os.Chdir(oldWd)
	})

	t.Run("without project root", func(t *testing.T) {
		t.Run("creates output file", func(t *testing.T) {
			runProcess(t, "")
			assertFileExists(t, tempDir)
		})
	})

	t.Run("with valid project root", func(t *testing.T) {
		t.Run("uses provided project root", func(t *testing.T) {
			runProcess(t, tempDir)
			assertFileExists(t, tempDir)
		})

		t.Run("parses and transforms input", func(t *testing.T) {
			input := `{"Action":"pass","Package":"example.com/pkg","Test":"TestExample"}`
			data := processAndReadOutput(t, input, tempDir)

			// Check it contains expected transformed data
			if !bytes.Contains(data, []byte(`"state":"passed"`)) {
				t.Fatalf("Expected output to contain transformed test state, got: %s", data)
			}
		})
	})

	t.Run("project root resolution", func(t *testing.T) {
		t.Run("accepts absolute path", func(t *testing.T) {
			err := runProcess(t, tempDir)
			if err != nil {
				t.Fatalf("Expected no error for absolute path, got: %v", err)
			}
			assertFileExists(t, tempDir)
		})

		t.Run("accepts relative path", func(t *testing.T) {
			subDir := filepath.Join(tempDir, "reltest")
			os.MkdirAll(subDir, 0755)
			oldCwd, _ := os.Getwd()
			os.Chdir(subDir)
			defer os.Chdir(oldCwd)

			err := runProcess(t, ".")
			if err != nil {
				t.Fatalf("Expected no error for relative path, got: %v", err)
			}
			assertFileExists(t, subDir)
		})

		t.Run("accepts path containing ..", func(t *testing.T) {
			subDir := filepath.Join(tempDir, "dotdot")
			os.MkdirAll(subDir, 0755)
			oldCwd, _ := os.Getwd()
			os.Chdir(subDir)
			defer os.Chdir(oldCwd)

			err := runProcess(t, filepath.Join(subDir, ".."))
			if err != nil {
				t.Fatalf("Expected no error for path with .., got: %v", err)
			}
			assertFileExists(t, tempDir)
		})

		t.Run("accepts path equal to current directory", func(t *testing.T) {
			cwd, _ := os.Getwd()
			err := runProcess(t, cwd)
			if err != nil {
				t.Fatalf("Expected no error when project root equals cwd, got: %v", err)
			}
			assertFileExists(t, cwd)
		})

		t.Run("accepts ancestor of current directory", func(t *testing.T) {
			subDir := filepath.Join(tempDir, "ancestor")
			os.MkdirAll(subDir, 0755)
			oldCwd, _ := os.Getwd()
			os.Chdir(subDir)
			defer os.Chdir(oldCwd)

			err := runProcess(t, tempDir)
			if err != nil {
				t.Fatalf("Expected no error when project root is ancestor, got: %v", err)
			}
			assertFileExists(t, tempDir)
		})

		t.Run("rejects path outside current directory", func(t *testing.T) {
			outsideRoot := filepath.Join(filepath.Dir(tempDir), "outside")
			os.MkdirAll(outsideRoot, 0755)

			err := runProcess(t, outsideRoot)
			assertErrorContains(t, err, "current directory must be within project root")
		})

		t.Run("returns error when current directory is unavailable", func(t *testing.T) {
			doomed := filepath.Join(tempDir, "doomed")
			os.MkdirAll(doomed, 0755)
			oldCwd, _ := os.Getwd()
			os.Chdir(doomed)
			defer os.Chdir(oldCwd)
			os.RemoveAll(doomed)

			err := runProcess(t, tempDir)
			if err == nil {
				t.Fatal("Expected error when current directory is unavailable")
			}
			if !strings.Contains(err.Error(), "cannot determine current directory") {
				t.Fatalf("Expected error about current directory, got: %v", err)
			}
		})
	})

	t.Run("formatted output", func(t *testing.T) {
		t.Run("formats package pass event", func(t *testing.T) {
			input := `{"Action":"pass","Package":"example.com/pkg","Elapsed":0.003}`
			output := &bytes.Buffer{}

			err := process(bytes.NewReader([]byte(input)), tempDir, output)
			if err != nil {
				t.Fatalf("Expected no error, got: %v", err)
			}

			expected := "ok  \texample.com/pkg\t0.003s\n"
			if output.String() != expected {
				t.Errorf("Expected formatted output '%s', got '%s'", expected, output.String())
			}
		})

		t.Run("passes through compilation errors", func(t *testing.T) {
			input := "# command-line-arguments"
			output := &bytes.Buffer{}

			err := process(bytes.NewReader([]byte(input)), tempDir, output)
			if err != nil {
				t.Fatalf("Expected no error, got: %v", err)
			}

			expected := "# command-line-arguments\n"
			if output.String() != expected {
				t.Errorf("Expected '%s', got '%s'", expected, output.String())
			}
		})

		t.Run("filters out JSON start events", func(t *testing.T) {
			input := `{"Action":"start","Package":"example.com/pkg"}`
			output := &bytes.Buffer{}

			err := process(bytes.NewReader([]byte(input)), tempDir, output)
			if err != nil {
				t.Fatalf("Expected no error, got: %v", err)
			}

			if output.String() != "" {
				t.Errorf("Expected empty output for start event, got '%s'", output.String())
			}
		})
	})

	t.Run("compilation error handling", func(t *testing.T) {
		t.Run("handles JSON-only build failure correctly", func(t *testing.T) {
			// This simulates a build failure that produces JSON output
			// The package fails but has no test entries
			input := `{"Action":"fail","Package":"example.com/pkg","Elapsed":0}`
			data := processAndReadOutput(t, input, tempDir)

			// Should mark as failed
			if !bytes.Contains(data, []byte(`"reason":"failed"`)) {
				t.Fatalf("Expected reason to be 'failed' for build failure, got: %s", data)
			}

			// Should add CompilationError test entry
			if !bytes.Contains(data, []byte(`"CompilationError"`)) {
				t.Fatalf("Expected CompilationError test entry for JSON-only failure, got: %s", data)
			}
		})

		t.Run("captures each compilation error as separate error entry", func(t *testing.T) {
			// Multiple error lines should each be a separate error in the errors array
			input := `# example.com/pkg
example.go:9:8: undefined: NewFormatter
example.go:10:12: undefined: TestEvent
{"Action":"fail","Package":"example.com/pkg","Elapsed":0}`
			data := processAndReadOutput(t, input, tempDir)

			// Check for separate error entries in the JSON structure
			if !bytes.Contains(data, []byte(`"message":"example.go:9:8: undefined: NewFormatter"`)) {
				t.Fatalf("Expected first error as separate entry, got: %s", data)
			}
			if !bytes.Contains(data, []byte(`"message":"example.go:10:12: undefined: TestEvent"`)) {
				t.Fatalf("Expected second error as separate entry, got: %s", data)
			}
			// Ensure they're not concatenated
			if bytes.Contains(data, []byte(`NewFormatter\nexample.go`)) {
				t.Fatalf("Errors should not be concatenated, got: %s", data)
			}
		})

		t.Run("produces non-empty output for compilation error", func(t *testing.T) {
			input := `# command-line-arguments`
			data := processAndReadOutput(t, input, tempDir)

			if bytes.Contains(data, []byte(`"testModules":[]`)) {
				t.Fatalf("Expected non-empty testModules, got: %s", data)
			}
		})

		t.Run("only adds synthetic test for lines starting with #", func(t *testing.T) {
			input := `some random error text`
			data := processAndReadOutput(t, input, tempDir)

			if !bytes.Contains(data, []byte(`"testModules":[]`)) {
				t.Fatalf("Expected empty testModules for non-# input, got: %s", data)
			}
		})

		t.Run("uses package name from compilation error", func(t *testing.T) {
			input := `# command-line-arguments`
			data := processAndReadOutput(t, input, tempDir)

			if !bytes.Contains(data, []byte("command-line-arguments")) {
				t.Fatalf("Expected command-line-arguments in output, got: %s", data)
			}
		})

		t.Run("names the test CompilationError", func(t *testing.T) {
			input := `# command-line-arguments`
			data := processAndReadOutput(t, input, tempDir)

			if !bytes.Contains(data, []byte("CompilationError")) {
				t.Fatalf("Expected CompilationError in output, got: %s", data)
			}
		})

		t.Run("includes compilation error message", func(t *testing.T) {
			input := `# command-line-arguments
single_import_error_test.go:5:2: no required module`
			data := processAndReadOutput(t, input, tempDir)

			if !bytes.Contains(data, []byte("single_import_error_test.go:5:2")) {
				t.Fatalf("Expected error message in output, got: %s", data)
			}
		})

		t.Run("uses actual error message from input", func(t *testing.T) {
			input := `# command-line-arguments
main.go:10:5: undefined: SomeFunction`
			data := processAndReadOutput(t, input, tempDir)

			if !bytes.Contains(data, []byte("main.go:10:5: undefined: SomeFunction")) {
				t.Fatalf("Expected actual error message in output, got: %s", data)
			}
		})

		t.Run("does not add CompilationError for passing package with no tests", func(t *testing.T) {
			// Package passes but has no tests (like an empty test file)
			input := `{"Action":"pass","Package":"example.com/pkg","Elapsed":0}`
			data := processAndReadOutput(t, input, tempDir)

			if bytes.Contains(data, []byte(`"CompilationError"`)) {
				t.Fatalf("Should not add CompilationError for passing package, got: %s", data)
			}
			if !bytes.Contains(data, []byte(`"reason":"passed"`)) {
				t.Fatalf("Expected reason to be 'passed' for empty passing package, got: %s", data)
			}
		})
	})
}

// Test helpers
func runProcess(t *testing.T, projectRoot string) error {
	t.Helper()
	json := `{"Action":"pass","Package":"example.com/pkg","Test":"TestExample"}`
	return process(bytes.NewReader([]byte(json)), projectRoot, io.Discard)
}

func assertFileExists(t *testing.T, projectRoot string) {
	t.Helper()
	expectedPath := getTestFilePath(projectRoot)
	if _, err := os.Stat(expectedPath); os.IsNotExist(err) {
		t.Fatal("Expected output file to be created")
	}
}

func assertErrorContains(t *testing.T, err error, expected string) {
	t.Helper()
	if err == nil || err.Error() != expected {
		t.Fatalf("Expected error '%s', got: %v", expected, err)
	}
}

func getTestFilePath(projectRoot string) string {
	parts := append([]string{projectRoot}, storage.TestResultsPath...)
	return filepath.Join(parts...)
}

func processAndReadOutput(t *testing.T, input string, projectRoot string) []byte {
	t.Helper()
	err := process(bytes.NewReader([]byte(input)), projectRoot, io.Discard)
	if err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(getTestFilePath(projectRoot))
	return data
}
