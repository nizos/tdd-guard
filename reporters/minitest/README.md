# TDD Guard Minitest Reporter

Minitest reporter that captures test results for TDD Guard validation.

## Requirements

- Ruby 3.3+
- Minitest 5.0+
- [TDD Guard](https://github.com/nizos/tdd-guard)

## Installation

Install TDD Guard by following the instructions in the [TDD Guard repository](https://github.com/nizos/tdd-guard).

Add the reporter to your Gemfile:

```ruby
gem "tdd-guard-minitest"
```

Or install directly:

```bash
gem install tdd-guard-minitest
```

## Usage

Require the autorun entry point before your test files. This registers the reporter as a Minitest plugin and also installs an `at_exit` hook that captures load errors (for example, a new test file that `require`s an implementation file that doesn't exist yet) as synthetic failed tests, so TDD Guard can see that a test tried to run.

Run a single test file directly:

```bash
ruby -rtdd_guard_minitest/autorun test/my_test.rb
```

For Rails or Rake projects, require it from `test/test_helper.rb`:

```ruby
require "tdd_guard_minitest/autorun"
```

Or pass it to your `Rake::TestTask` so every `rake test` invocation loads it first:

```ruby
Rake::TestTask.new do |t|
  t.ruby_opts << "-rtdd_guard_minitest/autorun"
end
```

## Configuration

### Project Root Configuration

Set the `TDD_GUARD_PROJECT_ROOT` environment variable to your project root:

```bash
export TDD_GUARD_PROJECT_ROOT="/absolute/path/to/project/root"
```

### Configuration Rules

- Path must be absolute
- Current directory must be within the configured project root
- Falls back to current directory if configuration is invalid

## Development

```bash
bundle install
bundle exec rspec
```

## More Information

- Test results are saved to `.claude/tdd-guard/data/test.json`
- See [TDD Guard documentation](https://github.com/nizos/tdd-guard) for complete setup

## License

MIT
