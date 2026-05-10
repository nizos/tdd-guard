# frozen_string_literal: true

require "spec_helper"
require "helpers"
require "json"
require "tmpdir"
require "fileutils"

RSpec.describe TddGuardRspec::Formatter do
  include TddGuardRspecHelpers

  let(:output) { StringIO.new }
  let(:default_data_dir) { TddGuardRspec::Formatter::DEFAULT_DATA_DIR }

  # Helper: create a formatter with storage_dir pointing to a tmpdir
  def create_formatter_in(tmpdir)
    real_tmpdir = File.realpath(tmpdir)
    Dir.chdir(real_tmpdir) do
      ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => real_tmpdir) do
        yield described_class.new(StringIO.new), real_tmpdir
      end
    end
  end

  # Helper: run the full flow and return parsed JSON
  def run_and_read_json(formatter, storage_dir)
    formatter.close(double("notification"))
    json_path = File.join(storage_dir, default_data_dir, "test.json")
    JSON.parse(File.read(json_path))
  end

  # Helper: extract flat list of tests from JSON data
  def all_tests(data)
    data["testModules"].flat_map { |m| m["tests"] }
  end

  describe "#example_passed" do
    it "captures passed test result" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "does something",
            full_description: "MyClass does something",
            file_path: "./spec/my_class_spec.rb"
          )
          formatter.example_passed(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]).to eq(
            "name" => "does something",
            "fullName" => "spec/my_class_spec.rb::MyClass does something",
            "state" => "passed"
          )
        end
      end
    end
  end

  describe "#example_failed" do
    it "captures failed test result with error message" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "raises error",
            full_description: "MyClass raises error",
            file_path: "./spec/my_class_spec.rb"
          )
          formatter.example_failed(build_failed_notification(example, message: "expected true, got false"))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]).to eq(
            "name" => "raises error",
            "fullName" => "spec/my_class_spec.rb::MyClass raises error",
            "state" => "failed",
            "errors" => [{ "message" => "expected true, got false" }]
          )
        end
      end
    end
  end

  describe "#example_pending" do
    it "captures pending test as skipped" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "is pending",
            full_description: "MyClass is pending",
            file_path: "./spec/my_class_spec.rb"
          )
          formatter.example_pending(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]["state"]).to eq("skipped")
        end
      end
    end
  end

  describe "#close" do
    it "saves empty testModules when no tests ran" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          data = run_and_read_json(formatter, storage_dir)
          expect(data["testModules"]).to eq([])
          # No examples actually ran, so the reporter no longer claims
          # "passed" — see #177.
          expect(data["reason"]).to eq("interrupted")
        end
      end
    end

    it "saves results grouped by module to JSON" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          [
            { desc: "test_one", full: "Model test_one", file: "./spec/model_spec.rb", method: :example_passed },
            { desc: "test_two", full: "Model test_two", file: "./spec/model_spec.rb", method: :example_failed, error: "Error" },
            { desc: "test_other", full: "Service test_other", file: "./spec/service_spec.rb", method: :example_passed }
          ].each do |t|
            example = build_example(description: t[:desc], full_description: t[:full], file_path: t[:file])
            if t[:method] == :example_failed
              formatter.example_failed(build_failed_notification(example, message: t[:error]))
            else
              formatter.example_passed(build_notification(example))
            end
          end

          data = run_and_read_json(formatter, storage_dir)
          expect(data["testModules"].length).to eq(2)

          model_module = data["testModules"].find { |m| m["moduleId"] == "spec/model_spec.rb" }
          expect(model_module["tests"].length).to eq(2)
          expect(model_module["tests"][0]["name"]).to eq("test_one")

          service_module = data["testModules"].find { |m| m["moduleId"] == "spec/service_spec.rb" }
          expect(service_module["tests"].length).to eq(1)
          expect(service_module["tests"][0]["name"]).to eq("test_other")
        end
      end
    end
  end

  describe "reason field" do
    it "reports passed when all tests pass" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          %w[test_one test_two].each do |desc|
            example = build_example(description: desc, full_description: "MyClass #{desc}")
            formatter.example_passed(build_notification(example))
          end

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("passed")
        end
      end
    end

    it "reports failed when one test fails" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          passing = build_example(description: "passes", full_description: "MyClass passes")
          formatter.example_passed(build_notification(passing))

          failing = build_example(description: "fails", full_description: "MyClass fails")
          formatter.example_failed(build_failed_notification(failing, message: "expected true"))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end

    it "reports failed when all tests fail" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          %w[test_one test_two].each do |desc|
            example = build_example(description: desc, full_description: "MyClass #{desc}")
            formatter.example_failed(build_failed_notification(example, message: "error"))
          end

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end

    it "reports interrupted when fewer results than expected" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.start(build_start_notification(count: 5))

          %w[test_one test_two].each do |desc|
            example = build_example(description: desc, full_description: "MyClass #{desc}")
            formatter.example_passed(build_notification(example))
          end

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("interrupted")
        end
      end
    end

    it "reports failed not interrupted when failures exist with fewer results" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.start(build_start_notification(count: 5))

          passing = build_example(description: "passes", full_description: "MyClass passes")
          formatter.example_passed(build_notification(passing))

          failing = build_example(description: "fails", full_description: "MyClass fails")
          formatter.example_failed(build_failed_notification(failing, message: "expected true"))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end

    it "reports passed when all expected tests complete" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.start(build_start_notification(count: 2))

          %w[test_one test_two].each do |desc|
            example = build_example(description: desc, full_description: "MyClass #{desc}")
            formatter.example_passed(build_notification(example))
          end

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("passed")
        end
      end
    end

    it "reports interrupted when expected count is zero and nothing ran" do
      # An empty `RSpec.describe` (or a `--tag` filter that matches no
      # examples) collects zero examples. Before #177, this returned
      # "passed", which silently lied to TDD Guard about a suite that
      # never executed an assertion.
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.start(build_start_notification(count: 0))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("interrupted")
        end
      end
    end

    it "reports failed when an unhandled error is captured (no examples ran)" do
      # Simulates a SyntaxError in a spec file: RSpec aborts during file
      # loading, the start callback never fires, but the :message callback
      # records the error via @unhandled_errors. Reason must reflect the
      # failure even though @expected_count stays zero (see #177).
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.instance_variable_set(:@unhandled_errors, [
            { "name" => "SyntaxError", "message" => "Unmatched (" }
          ])

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end

    it "reports failed when load errors produce synthetic failures" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          load_error = <<~MSG.chomp
            An error occurred while loading ./spec/my_class_spec.rb.
            Failure/Error: require "my_class"

            LoadError:
              cannot load such file -- my_class
          MSG
          formatter.message(build_message_notification(load_error))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 1))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end
  end

  describe "stack field" do
    it "includes first relevant spec line from backtrace" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "fails",
            full_description: "MyClass fails",
            file_path: "./spec/my_class_spec.rb"
          )
          backtrace = [
            "./spec/my_class_spec.rb:5:in `block (2 levels) in <top (required)>'",
            "/path/to/gems/rspec-core-3.13.0/lib/rspec/core/example.rb:263:in `instance_exec'"
          ]
          formatter.example_failed(build_failed_notification(example, message: "error", backtrace: backtrace))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to eq("spec/my_class_spec.rb:5:in `block (2 levels) in <top (required)>'")
        end
      end
    end

    it "excludes stack when backtrace contains only gem frames" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(description: "fails", full_description: "MyClass fails")
          backtrace = [
            "/path/to/gems/rspec-core-3.13.0/lib/rspec/core/example.rb:263:in `instance_exec'",
            "/path/to/gems/rspec-core-3.13.0/lib/rspec/core/runner.rb:121:in `run_specs'"
          ]
          formatter.example_failed(build_failed_notification(example, message: "error", backtrace: backtrace))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]).not_to have_key("stack")
        end
      end
    end

    it "excludes stack when backtrace is nil" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(description: "fails", full_description: "MyClass fails")
          formatter.example_failed(build_failed_notification(example, message: "error"))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]).not_to have_key("stack")
        end
      end
    end

    it "strips leading ./ from stack frame" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(description: "fails", full_description: "MyClass fails")
          backtrace = ["./spec/foo_spec.rb:10:in `block'"]
          formatter.example_failed(build_failed_notification(example, message: "error", backtrace: backtrace))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to start_with("spec/foo_spec.rb")
        end
      end
    end

    it "extracts spec line from absolute path backtrace" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(description: "fails", full_description: "MyClass fails")
          backtrace = [
            "/path/to/gems/rspec-support-3.13.0/lib/rspec/support.rb:110:in `block'",
            "/private/tmp/my-project/spec/calc_spec.rb:3:in `block (2 levels) in <top (required)>'"
          ]
          formatter.example_failed(build_failed_notification(example, message: "error", backtrace: backtrace))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to eq("spec/calc_spec.rb:3:in `block (2 levels) in <top (required)>'")
        end
      end
    end
  end

  describe "name extraction" do
    it "uses example.description as name" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "returns correct value"
          )
          formatter.example_passed(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          expect(all_tests(data)[0]["name"]).to eq("returns correct value")
        end
      end
    end
  end

  describe "fullName format" do
    it "uses file_path::full_description as fullName" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            full_description: "Widget works",
            file_path: "./spec/widget_spec.rb"
          )
          formatter.example_passed(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to eq("spec/widget_spec.rb::Widget works")
        end
      end
    end
  end

  describe "path handling" do
    it "strips leading ./ from file path" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            file_path: "./spec/foo_spec.rb"
          )
          formatter.example_passed(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to start_with("spec/foo_spec.rb")
        end
      end
    end

    it "handles file path without leading ./" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            file_path: "spec/bar_spec.rb"
          )
          formatter.example_passed(build_notification(example))

          data = run_and_read_json(formatter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to start_with("spec/bar_spec.rb::")
        end
      end
    end
  end

  describe "storage directory determination" do
    it "uses default relative path when no env var set" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        Dir.chdir(real_tmpdir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => nil) do
            formatter = described_class.new(StringIO.new)
            formatter.close(double("notification"))

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
          end
        end
      end
    end

    it "rejects relative path in env var" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        Dir.chdir(real_tmpdir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => "../some/path") do
            formatter = described_class.new(StringIO.new)
            formatter.close(double("notification"))

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
            # Should NOT have written to the project root path
            expect(File.exist?(File.join("../some/path", default_data_dir, "test.json"))).to be false
          end
        end
      end
    end

    it "rejects project root when cwd is outside" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        Dir.chdir(real_tmpdir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => "/other/project") do
            formatter = described_class.new(StringIO.new)
            formatter.close(double("notification"))

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
          end
        end
      end
    end

    it "rejects project root that is a prefix of cwd but not an ancestor" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        project_dir = File.join(real_tmpdir, "foo")
        similar_dir = File.join(real_tmpdir, "foobar")
        FileUtils.mkdir_p(similar_dir)

        Dir.chdir(similar_dir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => project_dir) do
            formatter = described_class.new(StringIO.new)
            formatter.close(double("notification"))

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
            # Should NOT have written under the project root
            expect(File.exist?(File.join(project_dir, default_data_dir, "test.json"))).to be false
          end
        end
      end
    end

    it "uses project root from env var when valid" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        Dir.chdir(real_tmpdir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => real_tmpdir) do
            formatter = described_class.new(StringIO.new)
            formatter.close(double("notification"))

            json_path = File.join(real_tmpdir, default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
          end
        end
      end
    end
  end

  describe "load error handling" do
    let(:load_error_message) do
      <<~MSG.chomp
        An error occurred while loading ./spec/my_class_spec.rb.
        Failure/Error: require "my_class"

        LoadError:
          cannot load such file -- my_class
        # ./spec/my_class_spec.rb:3:in `<top (required)>'
      MSG
    end

    it "captures load errors as synthetic test failures" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.message(build_message_notification(load_error_message))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 1))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]["state"]).to eq("failed")
        end
      end
    end

    it "extracts file path from load error" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.message(build_message_notification(load_error_message))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 1))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["testModules"][0]["moduleId"]).to eq("spec/my_class_spec.rb")
        end
      end
    end

    it "extracts error name from load error" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.message(build_message_notification(load_error_message))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 1))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["name"]).to eq("LoadError: cannot load such file -- my_class")
        end
      end
    end

    it "includes full error message in errors array" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.message(build_message_notification(load_error_message))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 1))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["message"]).to include("cannot load such file -- my_class")
        end
      end
    end

    it "does not create synthetic failures when tests ran normally" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(
            description: "works",
            full_description: "MyClass works",
            file_path: "./spec/my_class_spec.rb"
          )
          formatter.example_passed(build_notification(example))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 0))

          data = run_and_read_json(formatter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]["name"]).to eq("works")
        end
      end
    end

    it "ignores non-load-error messages" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          formatter.message(build_message_notification("No examples found."))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 0))

          data = run_and_read_json(formatter, storage_dir)
          expect(data["testModules"]).to eq([])
        end
      end
    end
  end

  describe "unhandledErrors field" do
    # All message strings below are verbatim captures from running real RSpec
    # 3.13.6 against minimal reproduction cases. They exercise the parser for
    # every shape the internal ExceptionPresenter is known to produce when
    # reporting errors outside of examples.

    let(:after_suite_message) do
      <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise RuntimeError, "cleanup failed in after(:suite)"

        RuntimeError:
          cleanup failed in after(:suite)
        # ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'
      MSG
    end

    let(:before_suite_message) do
      <<~MSG

        An error occurred in a `before(:suite)` hook.
        Failure/Error: raise StandardError, "setup failed in before(:suite)"

        StandardError:
          setup failed in before(:suite)
        # ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'
      MSG
    end

    let(:after_context_message) do
      <<~MSG

        An error occurred in an `after(:context)` hook.
        Failure/Error: raise RuntimeError, "context cleanup failed"

        RuntimeError:
          context cleanup failed
        # ./spec/my_class_spec.rb:3:in `block (2 levels) in <top (required)>'
      MSG
    end

    def run_with_message(messages, count: 1)
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          Array(messages).each do |msg|
            formatter.message(build_message_notification(msg))
          end
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: count))
          yield run_and_read_json(formatter, storage_dir)
        end
      end
    end

    it "captures after(:suite) hook failures" do
      run_with_message(after_suite_message) do |data|
        expect(data["unhandledErrors"]).to eq([
          {
            "name" => "RuntimeError",
            "message" => "cleanup failed in after(:suite)",
            "stack" => "spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'"
          }
        ])
      end
    end

    it "captures before(:suite) hook failures" do
      run_with_message(before_suite_message) do |data|
        expect(data["unhandledErrors"].first["name"]).to eq("StandardError")
        expect(data["unhandledErrors"].first["message"]).to eq("setup failed in before(:suite)")
      end
    end

    it "captures after(:context) hook failures" do
      run_with_message(after_context_message) do |data|
        expect(data["unhandledErrors"].first["name"]).to eq("RuntimeError")
        expect(data["unhandledErrors"].first["message"]).to eq("context cleanup failed")
      end
    end

    it "captures namespaced exception classes" do
      msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise MyApp::DatabaseError, "namespaced error"

        MyApp::DatabaseError:
          namespaced error
        # ./spec/my_class_spec.rb:5:in `block (2 levels) in <top (required)>'
      MSG

      run_with_message(msg) do |data|
        expect(data["unhandledErrors"].first["name"]).to eq("MyApp::DatabaseError")
      end
    end

    it "captures anonymous exception classes" do
      msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise anon, "anonymous class error"

        (anonymous error class):
          anonymous class error
        # ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'
      MSG

      run_with_message(msg) do |data|
        expect(data["unhandledErrors"].first["name"]).to eq("(anonymous error class)")
      end
    end

    it "captures exception classes without an Error suffix" do
      msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise WeirdName, "no Error suffix"

        WeirdName:
          no Error suffix
        # ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'
      MSG

      run_with_message(msg) do |data|
        expect(data["unhandledErrors"].first["name"]).to eq("WeirdName")
      end
    end

    it "preserves multi-line exception messages" do
      msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise RuntimeError, "line one\\nline two"

        RuntimeError:
          line one
          line two
        # ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'
      MSG

      run_with_message(msg) do |data|
        expect(data["unhandledErrors"].first["message"]).to eq("line one\nline two")
      end
    end

    it "strips ANSI escape codes from the message body" do
      msg = "\nAn error occurred in an `after(:suite)` hook.\n" \
            "Failure/Error: raise RuntimeError, \"boom\"\n\n" \
            "RuntimeError:\n" \
            "  \e[1;31mboom\e[0m\n" \
            "# ./spec/my_class_spec.rb:4:in `block (2 levels) in <top (required)>'\n"

      run_with_message(msg) do |data|
        expect(data["unhandledErrors"].first["message"]).to eq("boom")
      end
    end

    it "handles exceptions with empty backtrace" do
      msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: Unable to find matching line from backtrace

        RuntimeError:
          error with empty backtrace
      MSG

      run_with_message(msg) do |data|
        entry = data["unhandledErrors"].first
        expect(entry["name"]).to eq("RuntimeError")
        expect(entry["message"]).to eq("error with empty backtrace")
        expect(entry).not_to have_key("stack")
      end
    end

    it "accumulates multiple unhandled errors in one run" do
      second_msg = <<~MSG

        An error occurred in an `after(:suite)` hook.
        Failure/Error: raise StandardError, "second"

        StandardError:
          second
        # ./spec/my_class_spec.rb:6:in `block (2 levels) in <top (required)>'
      MSG

      run_with_message([after_suite_message, second_msg], count: 2) do |data|
        expect(data["unhandledErrors"].length).to eq(2)
        expect(data["unhandledErrors"].map { |e| e["name"] }).to eq(["RuntimeError", "StandardError"])
      end
    end

    it "skips RSpec-prefixed class names as a safe fallback" do
      # ExceptionPresenter suppresses the class name line when the class name
      # matches /RSpec/, leaving us with no reliable way to extract the name.
      # Parsing must fail gracefully so the error is dropped rather than stored
      # with corrupt data.
      msg = "\nAn error occurred in an `after(:suite)` hook.\n" \
            "Failure/Error: raise RSpecCustom::SomeError, \"rspec prefixed\"\n" \
            "  rspec prefixed\n" \
            "# ./spec/my_class_spec.rb:7:in `block (2 levels) in <top (required)>'\n"

      run_with_message(msg) do |data|
        expect(data).not_to have_key("unhandledErrors")
      end
    end

    it "keeps load errors on the existing synthetic-test path" do
      load_msg = <<~MSG
        An error occurred while loading ./spec/my_class_spec.rb.
        Failure/Error: require "my_class"

        LoadError:
          cannot load such file -- my_class
        # ./spec/my_class_spec.rb:3:in `<top (required)>'
      MSG

      run_with_message(load_msg) do |data|
        expect(data).not_to have_key("unhandledErrors")
        expect(all_tests(data).length).to eq(1)
      end
    end

    it "omits the unhandledErrors key when no unhandled errors occurred" do
      Dir.mktmpdir do |tmpdir|
        create_formatter_in(tmpdir) do |formatter, storage_dir|
          example = build_example(file_path: "./spec/my_class_spec.rb")
          formatter.example_passed(build_notification(example))
          formatter.dump_summary(build_summary_notification(errors_outside_of_examples_count: 0))

          data = run_and_read_json(formatter, storage_dir)
          expect(data).not_to have_key("unhandledErrors")
        end
      end
    end
  end
end
