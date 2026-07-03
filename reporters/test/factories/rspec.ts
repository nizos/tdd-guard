import { spawnSync } from 'node:child_process'
import { existsSync, symlinkSync } from 'node:fs'
import { join } from 'node:path'
import type { ReporterConfig, TestScenarios } from '../types'
import { copyTestArtifacts } from './helpers'

// Use hardcoded absolute paths to satisfy the no-os-command-from-path lint rule.
// Checks known locations in priority order: /usr/local/bin first (covers many CI runners
// and devcontainers); Homebrew next; rbenv shim on macOS developer machines; /usr/bin as
// the system fallback. Falls back to bare 'bundle' via PATH if none of the paths exist.
const bundleBinary =
  [
    '/usr/local/bin/bundle',
    '/opt/homebrew/bin/bundle',
    `${process.env.HOME}/.rbenv/shims/bundle`,
    '/usr/bin/bundle',
  ].find(existsSync) ?? 'bundle'

export function createRspecReporter(): ReporterConfig {
  const artifactDir = 'rspec'
  const testScenarios = {
    singlePassing: 'single_passing_spec.rb',
    singleFailing: 'single_failing_spec.rb',
    singleImportError: 'single_import_error_spec.rb',
  }

  return {
    name: 'RSpecReporter',
    testScenarios,
    run: (tempDir, scenario: keyof TestScenarios) => {
      copyTestArtifacts(artifactDir, testScenarios, scenario, tempDir)

      // Symlink vendor/bundle, Gemfile, and gemspec from rspec reporter
      const reporterDir = join(__dirname, '../../rspec')
      symlinkSync(join(reporterDir, 'vendor'), join(tempDir, 'vendor'))
      symlinkSync(join(reporterDir, 'Gemfile'), join(tempDir, 'Gemfile'))
      symlinkSync(
        join(reporterDir, 'Gemfile.lock'),
        join(tempDir, 'Gemfile.lock')
      )
      symlinkSync(
        join(reporterDir, 'tdd_guard_rspec.gemspec'),
        join(tempDir, 'tdd_guard_rspec.gemspec')
      )

      // Run rspec via bundle exec, letting Bundler handle path resolution
      const formatterLibPath = join(reporterDir, 'lib')
      const testFile = testScenarios[scenario]

      spawnSync(
        bundleBinary,
        [
          'exec',
          'rspec',
          testFile,
          '-I',
          formatterLibPath,
          '--format',
          'TddGuardRspec::Formatter',
        ],
        {
          cwd: tempDir,
          env: {
            ...process.env,
            TDD_GUARD_PROJECT_ROOT: tempDir,
            BUNDLE_PATH: 'vendor/bundle',
          },
          stdio: 'pipe',
          encoding: 'utf8',
        }
      )
    },
  }
}
