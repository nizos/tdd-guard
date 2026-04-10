# frozen_string_literal: true

# Entry point used to guarantee that the tdd-guard-minitest reporter is in
# place before the user's test file is loaded. Loading Minitest::Autorun
# here registers Minitest's own at_exit hook, and the at_exit block below
# is registered *after* it, which means it fires *before* Minitest's hook
# in LIFO order. That lets us intercept unhandled exceptions raised while
# loading the user's test file (typically a LoadError from a missing
# require) and write a synthetic failed test to test.json so the
# validation agent can see that a test tried to run and failed.
#
# Usage:
#
#   ruby -rtdd_guard_minitest/autorun path/to/test.rb
#
# or from test/test_helper.rb:
#
#   require "tdd_guard_minitest/autorun"

require "minitest/autorun"
require "tdd_guard_minitest/reporter"

at_exit do
  exc = $!
  next if exc.nil?
  next if exc.is_a?(SystemExit)

  TddGuardMinitest::Reporter.handle_load_error(exc)
end
