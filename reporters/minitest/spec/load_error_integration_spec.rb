# frozen_string_literal: true

require "spec_helper"
require "json"
require "tmpdir"
require "fileutils"
require "open3"

# Integration test that runs a real Ruby process end-to-end to verify that
# the at_exit hook in lib/tdd_guard_minitest/autorun.rb captures load errors
# correctly.
#
# This test exists to detect regressions if Minitest or Ruby changes the
# ordering semantics of at_exit blocks or the visibility of $! during
# process teardown. A failure here signals that the load-error capture
# path is broken end-to-end, even if the unit specs still pass.
RSpec.describe "load error integration" do
  let(:repo_lib) { File.expand_path("../lib", __dir__) }

  def run_minitest(tmpdir, test_body)
    test_dir = File.join(tmpdir, "test")
    FileUtils.mkdir_p(test_dir)
    File.write(File.join(test_dir, "load_error_test.rb"), test_body)

    env = { "TDD_GUARD_PROJECT_ROOT" => tmpdir }
    cmd = [
      "bundle", "exec", "ruby",
      "-I", repo_lib,
      "-rtdd_guard_minitest/autorun",
      "test/load_error_test.rb"
    ]
    Open3.capture3(env, *cmd, chdir: tmpdir)

    json_path = File.join(tmpdir, ".claude", "tdd-guard", "data", "test.json")
    return nil unless File.exist?(json_path)
    JSON.parse(File.read(json_path))
  end

  it "captures a LoadError from a real ruby process" do
    test_body = <<~RUBY
      require "non_existent_module"
      require "minitest/autorun"

      class CalculatorTest < Minitest::Test
        def test_should_add
          assert_equal 5, 2 + 3
        end
      end
    RUBY

    Dir.mktmpdir do |tmpdir|
      data = run_minitest(tmpdir, test_body)

      expect(data).not_to be_nil,
        "test.json was not written -- at_exit ordering or $! visibility may have changed"
      expect(data["reason"]).to eq("failed")
      expect(data["testModules"].length).to eq(1)

      test = data["testModules"][0]["tests"][0]
      expect(test["state"]).to eq("failed")
      expect(test["name"]).to include("LoadError")
      expect(test["name"]).to include("non_existent_module")
      expect(test["errors"][0]["message"]).to include("load_error_test.rb")
    end
  end

  it "does not write a synthetic failure when the test file loads cleanly" do
    test_body = <<~RUBY
      require "minitest/autorun"

      class CalculatorTest < Minitest::Test
        def test_should_add
          assert_equal 5, 2 + 3
        end
      end
    RUBY

    Dir.mktmpdir do |tmpdir|
      data = run_minitest(tmpdir, test_body)

      expect(data).not_to be_nil, "test.json should still be written via the normal report path"
      tests = data["testModules"].flat_map { |m| m["tests"] }
      expect(tests.map { |t| t["name"] }).to eq(["test_should_add"])
      expect(tests[0]["state"]).to eq("passed")
    end
  end
end
