import { spawnSync } from 'node:child_process'
import { cpSync, mkdirSync, realpathSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { ReporterConfig, TestScenarios } from '../types'
import { copyTestArtifacts } from './helpers'

export function createJunit5Reporter(): ReporterConfig {
  const artifactDir = 'junit5'
  const testScenarios: TestScenarios = {
    singlePassing: 'passing',
    singleFailing: 'failing',
    singleImportError: 'import',
  }

  const reporterDir = join(__dirname, '../../junit5')
  const reporterJar = join(reporterDir, 'build/libs/tdd-guard-junit5-0.1.0.jar')

  // Build the reporter JAR once during factory creation
  spawnSync(join(reporterDir, 'gradlew'), ['jar', '--no-daemon'], {
    cwd: reporterDir,
    stdio: 'pipe',
  })

  return {
    name: 'JUnit5Reporter',
    testScenarios,
    run: (tempDir: string, scenario: keyof TestScenarios) => {
      // Copy artifact project to temp dir
      copyTestArtifacts(artifactDir, testScenarios, scenario, tempDir)

      // Copy Gradle wrapper from reporter dir
      cpSync(join(reporterDir, 'gradlew'), join(tempDir, 'gradlew'))
      cpSync(join(reporterDir, 'gradle'), join(tempDir, 'gradle'), {
        recursive: true,
      })

      // Resolve symlinks so the ProjectRootResolver cwd check passes on macOS
      const realTempDir = realpathSync(tempDir)

      const result = spawnSync(
        join(tempDir, 'gradlew'),
        [
          'test',
          '--no-daemon',
          '--rerun-tasks',
          `-PreporterJar=${reporterJar}`,
        ],
        {
          cwd: realTempDir,
          env: { ...process.env, TDD_GUARD_PROJECT_ROOT: realTempDir },
          stdio: 'pipe',
          encoding: 'utf8',
        }
      )

      // Compilation errors prevent the JUnit Platform from starting, so the SPI
      // listener never fires. Synthesize test.json from the compiler output.
      if (scenario === 'singleImportError' && result.status !== 0) {
        writeCompilationErrorResult(realTempDir, result.stderr || '')
      }
    },
  }
}

function writeCompilationErrorResult(
  projectRoot: string,
  stderr: string
): void {
  const errorLines = stderr
    .split('\n')
    .filter(
      (line) =>
        line.includes('error:') ||
        line.includes('package') ||
        line.includes('cannot find')
    )
    .join('\n')
    .trim()

  const message = errorLines || 'Compilation failed'

  const outputDir = join(projectRoot, '.claude', 'tdd-guard', 'data')
  mkdirSync(outputDir, { recursive: true })

  const result = {
    testModules: [
      {
        moduleId: 'SingleImportErrorTest',
        tests: [
          {
            name: 'CompilationError',
            fullName: 'SingleImportErrorTest::CompilationError',
            state: 'failed',
            errors: [{ message }],
          },
        ],
      },
    ],
    reason: 'failed',
  }

  writeFileSync(join(outputDir, 'test.json'), JSON.stringify(result, null, 2))
}
