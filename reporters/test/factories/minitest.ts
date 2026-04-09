import { spawnSync } from 'node:child_process'
import { existsSync, symlinkSync } from 'node:fs'
import { join } from 'node:path'
import type { ReporterConfig, TestScenarios } from '../types'
import { copyTestArtifacts } from './helpers'

// Use hardcoded absolute path for security when available, fall back to PATH for CI environments
const bundleBinary =
  ['/usr/local/bin/bundle', '/usr/bin/bundle', '/opt/homebrew/bin/bundle'].find(
    existsSync
  ) ?? 'bundle'

export function createMinitestReporter(): ReporterConfig {
  const artifactDir = 'minitest'
  const testScenarios = {
    singlePassing: 'single_passing_test.rb',
    singleFailing: 'single_failing_test.rb',
    singleImportError: 'single_import_error_test.rb',
  }

  return {
    name: 'MinitestReporter',
    testScenarios,
    run: (tempDir, scenario: keyof TestScenarios) => {
      copyTestArtifacts(artifactDir, testScenarios, scenario, tempDir)

      // Symlink vendor/bundle, Gemfile, and gemspec from minitest reporter
      const reporterDir = join(__dirname, '../../minitest')
      symlinkSync(join(reporterDir, 'vendor'), join(tempDir, 'vendor'))
      symlinkSync(join(reporterDir, 'Gemfile'), join(tempDir, 'Gemfile'))
      symlinkSync(
        join(reporterDir, 'Gemfile.lock'),
        join(tempDir, 'Gemfile.lock')
      )
      symlinkSync(
        join(reporterDir, 'tdd_guard_minitest.gemspec'),
        join(tempDir, 'tdd_guard_minitest.gemspec')
      )

      // Run minitest via bundle exec, letting Bundler handle path resolution
      const reporterLibPath = join(reporterDir, 'lib')
      const testFile = testScenarios[scenario]

      spawnSync(
        bundleBinary,
        ['exec', 'ruby', '-I', reporterLibPath, testFile],
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
