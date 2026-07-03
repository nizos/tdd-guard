# frozen_string_literal: true

require "spec_helper"
require "json"
require "tmpdir"
require "fileutils"
require "open3"

# Integration test that runs a real RSpec process end-to-end to verify that the
# formatter reports "interrupted" when a run is genuinely cut short.
#
# This is the only reporter that can trigger a real mid-run interruption in-process.
# Setting RSpec.world.wants_to_quit = true in an after(:each) hook causes RSpec to
# stop after the first example, so only 1 of 2 examples runs.
RSpec.describe "interrupted integration" do
  let(:repo_lib) { File.expand_path("../lib", __dir__) }

  def run_rspec(tmpdir, spec_body)
    spec_dir = File.join(tmpdir, "spec")
    FileUtils.mkdir_p(spec_dir)
    File.write(File.join(spec_dir, "hook_spec.rb"), spec_body)

    env = { "TDD_GUARD_PROJECT_ROOT" => tmpdir }
    cmd = [
      "bundle", "exec", "rspec",
      "-I", repo_lib,
      "--require", "tdd_guard_rspec/formatter",
      "--format", "TddGuardRspec::Formatter",
      "spec/hook_spec.rb"
    ]
    Open3.capture3(env, *cmd, chdir: tmpdir)

    json_path = File.join(tmpdir, ".claude", "tdd-guard", "data", "test.json")
    return nil unless File.exist?(json_path)
    JSON.parse(File.read(json_path))
  end

  it "reports interrupted when the run is stopped after the first example" do
    spec_body = <<~RUBY
      RSpec.configure do |config|
        config.after(:each) do
          RSpec.world.wants_to_quit = true
        end
      end

      RSpec.describe "Interrupted" do
        it("passes") { expect(1).to eq(1) }
        it("never runs") { expect(2).to eq(2) }
      end
    RUBY

    Dir.mktmpdir do |tmpdir|
      data = run_rspec(tmpdir, spec_body)

      expect(data).not_to be_nil, "test.json was not written"
      expect(data["reason"]).to eq("interrupted"),
        "expected interrupted when only 1 of 2 examples ran"
    end
  end
end
