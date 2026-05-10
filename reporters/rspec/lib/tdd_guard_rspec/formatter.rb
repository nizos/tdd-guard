# frozen_string_literal: true

require "json"
require "fileutils"
require "rspec/core/formatters/base_formatter"

module TddGuardRspec
  # RSpec formatter that captures test results for TDD Guard validation.
  # Mirrors the pytest reporter's single-class architecture.
  class Formatter < RSpec::Core::Formatters::BaseFormatter
    RSpec::Core::Formatters.register self,
      :start,
      :example_passed,
      :example_failed,
      :example_pending,
      :message,
      :dump_summary,
      :close

    DEFAULT_DATA_DIR = ".claude/tdd-guard/data"

    ANSI_ESCAPE = /\e\[[0-9;]*m/
    CLASS_NAME_LINE = /\A[A-Z(][\w:() ]*:\z/

    def initialize(output)
      super(output)
      @test_results = []
      @load_errors = []
      @unhandled_errors = []
      @errors_outside = 0
      @expected_count = 0
      @storage_dir = determine_storage_dir
    end

    def start(notification)
      super
      @expected_count = notification.count
    end

    def example_passed(notification)
      record_example(notification.example, "passed")
    end

    def example_failed(notification)
      example = notification.example
      error = { "message" => notification.exception.message }
      stack = extract_relevant_stack(notification.exception.backtrace)
      error["stack"] = stack if stack
      record_example(example, "failed", [error])
    end

    def example_pending(notification)
      record_example(notification.example, "skipped")
    end

    def message(notification)
      msg = notification.message
      if msg.include?("An error occurred while loading")
        @load_errors << msg
      elsif msg.include?("Failure/Error:")
        parsed = parse_unhandled_error(msg)
        @unhandled_errors << parsed if parsed
      end
    end

    def dump_summary(notification)
      @errors_outside = notification.errors_outside_of_examples_count
    end

    def close(_notification)
      add_load_error_results if @test_results.empty? && @errors_outside > 0

      modules_map = {}
      @test_results.each do |test|
        module_path = test["fullName"].split("::").first
        modules_map[module_path] ||= { "moduleId" => module_path, "tests" => [] }
        modules_map[module_path]["tests"] << test
      end

      has_failures = @test_results.any? { |t| t["state"] == "failed" }
      has_errors = !@unhandled_errors.empty? || @errors_outside > 0
      reason = if has_failures || has_errors
                 "failed"
               elsif @expected_count > 0 && @test_results.length < @expected_count
                 "interrupted"
               elsif @test_results.empty?
                 # No examples actually ran (e.g. spec file aborted before
                 # the start callback fired due to a SyntaxError, or an
                 # empty `RSpec.describe` collected zero examples). Treat
                 # this as "interrupted" rather than claiming "passed",
                 # since nothing exercised the suite.
                 "interrupted"
               else
                 "passed"
               end
      result = {
        "testModules" => modules_map.values,
        "reason" => reason
      }
      result["unhandledErrors"] = @unhandled_errors unless @unhandled_errors.empty?

      FileUtils.mkdir_p(@storage_dir)
      File.write(File.join(@storage_dir, "test.json"), JSON.pretty_generate(result))
    end

    private

    def record_example(example, state, errors = nil)
      file_path = example.file_path.sub(%r{^\./}, "")
      test = {
        "name" => example.description,
        "fullName" => "#{file_path}::#{example.full_description}",
        "state" => state
      }
      test["errors"] = errors if errors
      @test_results << test
    end

    def add_load_error_results
      @load_errors.each do |error_msg|
        file_path = extract_file_path(error_msg)
        error_name = extract_error_name(error_msg)
        @test_results << {
          "name" => error_name,
          "fullName" => "#{file_path}::#{error_name}",
          "state" => "failed",
          "errors" => [{ "message" => error_msg.strip }]
        }
      end
    end

    def extract_file_path(error_msg)
      match = error_msg.match(/An error occurred while loading (.+)\./)
      return "unknown" unless match

      match[1].sub(%r{^\./}, "").strip
    end

    def extract_error_name(error_msg)
      match = error_msg.match(/^(\w+Error):\s*(.+?)$/m)
      return "LoadError" unless match

      "#{match[1]}: #{match[2].strip}"
    end

    def extract_relevant_stack(backtrace)
      return nil unless backtrace

      frame = backtrace.find { |line| line.include?("spec/") && !line.include?("/gems/") }
      return nil unless frame

      frame.sub(%r{^.*/(?=spec/)}, "").sub(%r{^\./}, "")
    end

    # Parses a formatted exception string produced by RSpec's internal
    # ExceptionPresenter (delivered via the :message callback for errors outside
    # of examples, such as before/after :suite and :context hook failures).
    #
    # Returns a Hash with "name", "message", and optional "stack" on success,
    # or nil if the expected structure cannot be found (safe fallback: the
    # error is dropped rather than producing malformed output).
    def parse_unhandled_error(raw)
      lines = raw.gsub(ANSI_ESCAPE, "").split("\n")

      # The header ends where the backtrace begins (first "# ..." line).
      # If there is no backtrace at all, the entire message is the header.
      bt_start = lines.index { |line| line.start_with?("# ") } || lines.length

      header_lines = lines[0...bt_start]
      class_idx = header_lines.rindex { |line| line =~ CLASS_NAME_LINE }
      return nil unless class_idx

      name = header_lines[class_idx].chomp(":")
      message_lines = header_lines[(class_idx + 1)..-1] || []
      message_body = message_lines.map { |line| line.sub(/\A  /, "") }.join("\n").strip
      return nil if message_body.empty?

      backtrace_frames = lines[bt_start..-1].to_a.select { |line| line.start_with?("# ") }
      backtrace_paths = backtrace_frames.map { |line| line.sub(/\A# /, "") }
      stack = extract_relevant_stack(backtrace_paths)

      result = { "name" => name, "message" => message_body }
      result["stack"] = stack if stack
      result
    end

    def determine_storage_dir
      project_root = ENV["TDD_GUARD_PROJECT_ROOT"]
      return DEFAULT_DATA_DIR unless project_root && !project_root.empty?
      return DEFAULT_DATA_DIR unless absolute_path?(project_root)
      return DEFAULT_DATA_DIR unless cwd_within?(project_root)

      File.join(project_root, DEFAULT_DATA_DIR)
    end

    def absolute_path?(path)
      File.absolute_path?(path)
    end

    def cwd_within?(root)
      expanded = File.expand_path(root)
      cwd = Dir.pwd
      cwd == expanded || cwd.start_with?("#{expanded}/")
    end
  end
end
