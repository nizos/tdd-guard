# frozen_string_literal: true

require "spec_helper"
require "helpers"
require "json"
require "tmpdir"
require "fileutils"

RSpec.describe TddGuardMinitest::Reporter do
  include TddGuardMinitestHelpers

  let(:default_data_dir) { TddGuardMinitest::Reporter::DEFAULT_DATA_DIR }

  # Helper: create a reporter with storage_dir pointing to a tmpdir
  def create_reporter_in(tmpdir)
    real_tmpdir = File.realpath(tmpdir)
    Dir.chdir(real_tmpdir) do
      ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => real_tmpdir) do
        reporter = described_class.new(StringIO.new)
        yield reporter, real_tmpdir
      end
    end
  end

  # Helper: run the full flow and return parsed JSON
  def run_and_read_json(reporter, storage_dir)
    reporter.report
    json_path = File.join(storage_dir, default_data_dir, "test.json")
    JSON.parse(File.read(json_path))
  end

  # Helper: extract flat list of tests from JSON data
  def all_tests(data)
    data["testModules"].flat_map { |m| m["tests"] }
  end

  describe "#record" do
    it "captures passed test result" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          result = build_result(
            name: "test_does_something",
            klass: "MyClassTest",
            source_location: ["./test/my_class_test.rb", 5]
          )
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]).to eq(
            "name" => "test_does_something",
            "fullName" => "test/my_class_test.rb::MyClassTest#test_does_something",
            "state" => "passed"
          )
        end
      end
    end

    it "captures failed test result with error message" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(message: "Expected: 6\n  Actual: 5")
          result = build_result(
            name: "test_raises_error",
            klass: "MyClassTest",
            source_location: ["./test/my_class_test.rb", 10],
            failures: [failure]
          )
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]["name"]).to eq("test_raises_error")
          expect(tests[0]["state"]).to eq("failed")
          expect(tests[0]["errors"][0]["message"]).to eq("Expected: 6\n  Actual: 5")
        end
      end
    end

    it "captures skipped test" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          skip_failure = build_skip(message: "not implemented yet")
          result = build_result(
            name: "test_is_pending",
            klass: "MyClassTest",
            source_location: ["./test/my_class_test.rb", 15],
            failures: [skip_failure]
          )
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests.length).to eq(1)
          expect(tests[0]["state"]).to eq("skipped")
        end
      end
    end
  end

  describe "#report" do
    it "saves empty testModules when no tests ran" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          data = run_and_read_json(reporter, storage_dir)
          expect(data["testModules"]).to eq([])
          expect(data["reason"]).to eq("passed")
        end
      end
    end

    it "saves results grouped by module to JSON" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          [
            { name: "test_one", klass: "ModelTest", file: "test/model_test.rb", failures: [] },
            { name: "test_two", klass: "ModelTest", file: "test/model_test.rb",
              failures: [build_assertion_failure(message: "Error")] },
            { name: "test_other", klass: "ServiceTest", file: "test/service_test.rb", failures: [] }
          ].each do |t|
            result = build_result(
              name: t[:name],
              klass: t[:klass],
              source_location: [t[:file], 5],
              failures: t[:failures]
            )
            reporter.record(result)
          end

          data = run_and_read_json(reporter, storage_dir)
          expect(data["testModules"].length).to eq(2)

          model_module = data["testModules"].find { |m| m["moduleId"] == "test/model_test.rb" }
          expect(model_module["tests"].length).to eq(2)
          expect(model_module["tests"][0]["name"]).to eq("test_one")

          service_module = data["testModules"].find { |m| m["moduleId"] == "test/service_test.rb" }
          expect(service_module["tests"].length).to eq(1)
          expect(service_module["tests"][0]["name"]).to eq("test_other")
        end
      end
    end
  end

  describe "reason field" do
    it "reports passed when all tests pass" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          %w[test_one test_two].each do |name|
            reporter.record(
              build_result(
                name: name,
                klass: "MyClassTest",
                source_location: ["./test/my_class_test.rb", 5]
              )
            )
          end

          data = run_and_read_json(reporter, storage_dir)
          expect(data["reason"]).to eq("passed")
        end
      end
    end

    it "reports failed when one test fails" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          reporter.record(
            build_result(
              name: "test_passes",
              klass: "MyClassTest",
              source_location: ["./test/my_class_test.rb", 5]
            )
          )
          reporter.record(
            build_result(
              name: "test_fails",
              klass: "MyClassTest",
              source_location: ["./test/my_class_test.rb", 10],
              failures: [build_assertion_failure(message: "expected true")]
            )
          )

          data = run_and_read_json(reporter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end

    it "reports failed when all tests fail" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          %w[test_one test_two].each do |name|
            reporter.record(
              build_result(
                name: name,
                klass: "MyClassTest",
                source_location: ["./test/my_class_test.rb", 5],
                failures: [build_assertion_failure(message: "error")]
              )
            )
          end

          data = run_and_read_json(reporter, storage_dir)
          expect(data["reason"]).to eq("failed")
        end
      end
    end
  end

  describe "stack field" do
    it "includes first relevant test line from backtrace" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(
            message: "expected true",
            backtrace: [
              "./test/my_class_test.rb:5:in `block (2 levels) in <top (required)>'",
              "/path/to/gems/minitest-5.27.0/lib/minitest/test.rb:98:in `instance_exec'"
            ]
          )
          reporter.record(
            build_result(name: "test_fails", klass: "MyClassTest",
                         source_location: ["./test/my_class_test.rb", 5],
                         failures: [failure])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to eq("test/my_class_test.rb:5:in `block (2 levels) in <top (required)>'")
        end
      end
    end

    it "excludes stack when backtrace contains only gem frames" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(
            message: "expected true",
            backtrace: [
              "/path/to/gems/minitest-5.27.0/lib/minitest/test.rb:98:in `instance_exec'",
              "/path/to/gems/minitest-5.27.0/lib/minitest/runner.rb:121:in `run_specs'"
            ]
          )
          reporter.record(
            build_result(name: "test_fails", klass: "MyClassTest", failures: [failure])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]).not_to have_key("stack")
        end
      end
    end

    it "excludes stack when backtrace is nil" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(message: "expected true")
          reporter.record(
            build_result(name: "test_fails", klass: "MyClassTest", failures: [failure])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]).not_to have_key("stack")
        end
      end
    end

    it "strips leading ./ from stack frame" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(
            message: "error",
            backtrace: ["./test/foo_test.rb:10:in `block'"]
          )
          reporter.record(
            build_result(name: "test_fails", klass: "FooTest", failures: [failure])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to start_with("test/foo_test.rb")
        end
      end
    end

    it "extracts test line from absolute path backtrace" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          failure = build_assertion_failure(
            message: "error",
            backtrace: [
              "/path/to/gems/minitest-5.27.0/lib/minitest.rb:110:in `block'",
              "/private/tmp/my-project/test/calc_test.rb:3:in `block (2 levels) in <top (required)>'"
            ]
          )
          reporter.record(
            build_result(name: "test_fails", klass: "CalcTest", failures: [failure])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          expect(tests[0]["errors"][0]["stack"]).to eq("test/calc_test.rb:3:in `block (2 levels) in <top (required)>'")
        end
      end
    end

    it "unwraps UnexpectedError to use original exception message and backtrace" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          unexpected = build_unexpected_error(
            error_class: RuntimeError,
            message: "something broke",
            backtrace: [
              "./test/widget_test.rb:8:in `test_boom'",
              "/path/to/gems/minitest-5.27.0/lib/minitest/test.rb:98:in `run'"
            ]
          )
          reporter.record(
            build_result(name: "test_boom", klass: "WidgetTest",
                         source_location: ["./test/widget_test.rb", 8],
                         failures: [unexpected])
          )

          data = run_and_read_json(reporter, storage_dir)
          tests = all_tests(data)
          error = tests[0]["errors"][0]
          expect(error["message"]).to eq("something broke")
          expect(error["stack"]).to eq("test/widget_test.rb:8:in `test_boom'")
        end
      end
    end
  end

  describe "name extraction" do
    it "uses result.name as name" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          result = build_result(name: "test_returns_correct_value")
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          expect(all_tests(data)[0]["name"]).to eq("test_returns_correct_value")
        end
      end
    end
  end

  describe "fullName format" do
    it "uses file_path::klass#name as fullName" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          result = build_result(
            name: "test_works",
            klass: "WidgetTest",
            source_location: ["./test/widget_test.rb", 5]
          )
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to eq("test/widget_test.rb::WidgetTest#test_works")
        end
      end
    end
  end

  describe "path handling" do
    it "strips leading ./ from file path" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          result = build_result(source_location: ["./test/foo_test.rb", 5])
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to start_with("test/foo_test.rb")
        end
      end
    end

    it "handles file path without leading ./" do
      Dir.mktmpdir do |tmpdir|
        create_reporter_in(tmpdir) do |reporter, storage_dir|
          result = build_result(source_location: ["test/bar_test.rb", 5])
          reporter.record(result)

          data = run_and_read_json(reporter, storage_dir)
          expect(all_tests(data)[0]["fullName"]).to start_with("test/bar_test.rb::")
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
            reporter = described_class.new(StringIO.new)
            reporter.report

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
            reporter = described_class.new(StringIO.new)
            reporter.report

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
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
            reporter = described_class.new(StringIO.new)
            reporter.report

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
            reporter = described_class.new(StringIO.new)
            reporter.report

            json_path = File.join(default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
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
            reporter = described_class.new(StringIO.new)
            reporter.report

            json_path = File.join(real_tmpdir, default_data_dir, "test.json")
            expect(File.exist?(json_path)).to be true
          end
        end
      end
    end
  end

  describe ".handle_load_error" do
    # Helper: run handle_load_error in an isolated tmpdir and return parsed JSON
    def run_handle_load_error_in(tmpdir, exception)
      real_tmpdir = File.realpath(tmpdir)
      Dir.chdir(real_tmpdir) do
        ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => real_tmpdir) do
          described_class.handle_load_error(exception)
          json_path = File.join(real_tmpdir, default_data_dir, "test.json")
          JSON.parse(File.read(json_path))
        end
      end
    end

    # Helper: build a LoadError with a synthetic backtrace
    def build_load_error(message:, backtrace:)
      err = LoadError.new(message)
      err.set_backtrace(backtrace)
      err
    end

    it "writes a synthetic failed test module" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: ["./test/my_class_test.rb:3:in `require'"]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        tests = data["testModules"].flat_map { |m| m["tests"] }
        expect(tests.length).to eq(1)
        expect(tests[0]["state"]).to eq("failed")
      end
    end

    it "extracts file path from the first user-land backtrace frame" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: [
            "/path/to/gems/bundler-2.0/lib/bundler/runtime.rb:10:in `require'",
            "./test/my_class_test.rb:3:in `require'",
            "./test/my_class_test.rb:3:in `<top (required)>'"
          ]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        expect(data["testModules"][0]["moduleId"]).to eq("test/my_class_test.rb")
      end
    end

    it "uses exception class and message as the test name" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: ["./test/my_class_test.rb:3:in `require'"]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        tests = data["testModules"].flat_map { |m| m["tests"] }
        expect(tests[0]["name"]).to eq("LoadError: cannot load such file -- my_class")
        expect(tests[0]["fullName"]).to eq("test/my_class_test.rb::LoadError: cannot load such file -- my_class")
      end
    end

    it "includes the class, message, and backtrace frame in the errors entry" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: ["./test/my_class_test.rb:3:in `<top (required)>'"]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        tests = data["testModules"].flat_map { |m| m["tests"] }
        error_msg = tests[0]["errors"][0]["message"]
        expect(error_msg).to include("LoadError")
        expect(error_msg).to include("cannot load such file -- my_class")
        expect(error_msg).to include("test/my_class_test.rb")
      end
    end

    it "emits reason: failed" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: ["./test/my_class_test.rb:3:in `require'"]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        expect(data["reason"]).to eq("failed")
      end
    end

    it "falls back to 'unknown' module when backtrace has only gem frames" do
      Dir.mktmpdir do |tmpdir|
        exc = build_load_error(
          message: "cannot load such file -- my_class",
          backtrace: [
            "/path/to/gems/bundler-2.0/lib/bundler/runtime.rb:10:in `require'",
            "/path/to/gems/minitest-5.27.0/lib/minitest.rb:5:in `require'"
          ]
        )
        data = run_handle_load_error_in(tmpdir, exc)

        expect(data["testModules"][0]["moduleId"]).to eq("unknown")
      end
    end

    it "falls back to 'unknown' module when backtrace is nil" do
      Dir.mktmpdir do |tmpdir|
        exc = LoadError.new("cannot load such file -- my_class")
        data = run_handle_load_error_in(tmpdir, exc)

        expect(data["testModules"][0]["moduleId"]).to eq("unknown")
      end
    end

    it "accepts any Exception subclass (not just LoadError)" do
      Dir.mktmpdir do |tmpdir|
        exc = RuntimeError.new("something else went wrong")
        exc.set_backtrace(["./test/boom_test.rb:5:in `<top (required)>'"])
        data = run_handle_load_error_in(tmpdir, exc)

        tests = data["testModules"].flat_map { |m| m["tests"] }
        expect(tests[0]["name"]).to eq("RuntimeError: something else went wrong")
        expect(data["testModules"][0]["moduleId"]).to eq("test/boom_test.rb")
      end
    end

    it "does not overwrite an existing test.json" do
      Dir.mktmpdir do |tmpdir|
        real_tmpdir = File.realpath(tmpdir)
        Dir.chdir(real_tmpdir) do
          ClimateControl.modify("TDD_GUARD_PROJECT_ROOT" => real_tmpdir) do
            json_path = File.join(real_tmpdir, default_data_dir, "test.json")
            FileUtils.mkdir_p(File.dirname(json_path))
            File.write(json_path, '{"existing":"results"}')

            exc = build_load_error(
              message: "cannot load such file -- my_class",
              backtrace: ["./test/my_class_test.rb:3:in `require'"]
            )
            described_class.handle_load_error(exc)

            expect(File.read(json_path)).to eq('{"existing":"results"}')
          end
        end
      end
    end
  end
end
