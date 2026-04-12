# frozen_string_literal: true

require "json"
require "fileutils"
require "minitest"

module TddGuardMinitest
  # Minitest reporter that captures test results for TDD Guard validation.
  # Mirrors the RSpec reporter's single-class architecture.
  class Reporter < Minitest::StatisticsReporter
    DEFAULT_DATA_DIR = ".claude/tdd-guard/data"

    def initialize(io = $stdout, options = {})
      super
      @test_results = []
      @storage_dir = determine_storage_dir
    end

    # Writes a synthetic failed-test JSON for an exception raised before
    # Minitest had a chance to run (typically a LoadError from a missing
    # require at the top of a test file). Called from the autorun entry
    # point's at_exit hook when $! is set.
    #
    # Injects a synthetic entry into @test_results and writes the JSON
    # through the normal report path. Skips if test.json already exists
    # to avoid clobbering real results.
    def self.handle_load_error(exception)
      new(StringIO.new).handle_load_error(exception)
    end

    def handle_load_error(exception)
      return if File.exist?(File.join(@storage_dir, "test.json"))

      add_load_error(exception)
      report
    end

    def record(result)
      state = if result.skipped?
                "skipped"
              elsif result.passed?
                "passed"
              else
                "failed"
              end

      file_path = extract_file_path(result)
      test = {
        "name" => result.name,
        "fullName" => "#{file_path}::#{result.klass}##{result.name}",
        "state" => state
      }

      if state == "failed" && result.failures.any?
        test["errors"] = result.failures.map do |failure|
          { "message" => failure.message }
        end
      end

      @test_results << test
    end

    def report
      modules_map = {}
      @test_results.each do |test|
        module_path = test["fullName"].split("::").first
        modules_map[module_path] ||= { "moduleId" => module_path, "tests" => [] }
        modules_map[module_path]["tests"] << test
      end

      has_failures = @test_results.any? { |t| t["state"] == "failed" }
      result = {
        "testModules" => modules_map.values,
        "reason" => has_failures ? "failed" : "passed"
      }

      FileUtils.mkdir_p(@storage_dir)
      File.write(File.join(@storage_dir, "test.json"), JSON.pretty_generate(result))
    end

    def passed?
      @test_results.none? { |t| t["state"] == "failed" }
    end

    private

    def extract_file_path(result)
      source = result.source_location
      return "unknown" unless source

      path = source.first
      # Convert absolute path to relative path from cwd
      cwd = "#{Dir.pwd}/"
      path = path.delete_prefix(cwd) if path.start_with?(cwd)
      path.sub(%r{^\./}, "")
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

    # Injects a synthetic failed test entry derived from an exception raised
    # before Minitest could run.
    def add_load_error(exception)
      frame = first_user_frame(exception.backtrace)
      file_path = frame ? frame.split(":", 2).first.to_s.sub(%r{^\./}, "") : "unknown"
      name = "#{exception.class}: #{exception.message.lines.first.to_s.strip}"
      message = build_load_error_message(exception, frame)

      @test_results << {
        "name" => name,
        "fullName" => "#{file_path}::#{name}",
        "state" => "failed",
        "errors" => [{ "message" => message }]
      }
    end

    def first_user_frame(backtrace)
      return nil unless backtrace

      backtrace.find do |line|
        (line.include?("_test.rb") || line.include?("_spec.rb")) && !line.include?("/gems/")
      end
    end

    def build_load_error_message(exception, frame)
      header = "#{exception.class}: #{exception.message}"
      return header unless frame

      "#{header}\n    #{frame.sub(%r{^\./}, '')}"
    end
  end
end
