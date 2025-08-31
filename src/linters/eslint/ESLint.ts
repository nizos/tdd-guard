import {
  LintResult,
  LintIssue,
  ESLintResult,
  ESLintMessage,
} from '../../contracts/schemas/lintSchemas'
import { execFile } from 'child_process'
import { promisify } from 'util'
import { Linter } from '../Linter'

const execFileAsync = promisify(execFile)

export class ESLint implements Linter {
  async lint(filePaths: string[], configPath?: string): Promise<LintResult> {
    const timestamp = new Date().toISOString()
    const args = buildArgs(filePaths, configPath)

    try {
      await execFileAsync('npx', args, { shell: process.platform === 'win32' })
      return createLintData(timestamp, filePaths, [])
    } catch (error) {
      if (!isExecError(error)) throw error

      const results = parseResults(error.stdout)
      return createLintData(timestamp, filePaths, results)
    }
  }
}

// Helper functions
const buildArgs = (files: string[], configPath?: string): string[] => {
  const args = ['eslint', ...files, '--format', 'json']
  if (configPath) {
    args.push('-c', configPath)
  }
  return args
}

const isExecError = (error: unknown): error is Error & { stdout?: string } =>
  error !== null && typeof error === 'object' && 'stdout' in error

const parseResults = (stdout?: string): ESLintResult[] => {
  try {
    return JSON.parse(stdout ?? '[]')
  } catch {
    return []
  }
}

const createLintData = (
  timestamp: string,
  files: string[],
  results: ESLintResult[]
): LintResult => {
  const issues = extractIssues(results)
  return {
    timestamp,
    files,
    issues,
    errorCount: countBySeverity(issues, 'error'),
    warningCount: countBySeverity(issues, 'warning'),
  }
}

const extractIssues = (results: ESLintResult[]): LintIssue[] =>
  results.flatMap((file) => (file.messages ?? []).map(toIssue(file.filePath)))

const toIssue =
  (filePath: string) =>
  (msg: ESLintMessage): LintIssue => ({
    file: filePath,
    line: msg.line ?? 0,
    column: msg.column ?? 0,
    severity: (msg.severity === 2 ? 'error' : 'warning') as 'error' | 'warning',
    message: msg.message,
    rule: msg.ruleId,
  })

const countBySeverity = (
  issues: LintIssue[],
  severity: 'error' | 'warning'
): number => issues.filter((i) => i.severity === severity).length
