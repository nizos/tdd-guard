import {
  LintResult,
  LintIssue,
  GolangciLintIssue,
  GolangciLintResult,
} from '../../contracts/schemas/lintSchemas'
import { execFile } from 'child_process'
import { promisify } from 'util'
import { dirname } from 'path'
import { Linter } from '../Linter'

const execFileAsync = promisify(execFile)

export class GolangciLint implements Linter {
  async lint(filePaths: string[], configPath?: string): Promise<LintResult> {
    const timestamp = new Date().toISOString()
    const args = buildArgs(filePaths, configPath)

    try {
      // golangci-lint exits with non-zero code when issues are found
      await execFileAsync('golangci-lint', args)
      return createLintData(timestamp, filePaths, [])
    } catch (error) {
      if (!isExecError(error)) throw error

      const results = parseResults(error.stdout)
      return createLintData(timestamp, filePaths, results)
    }
  }
}

// Helper functions
export const buildArgs = (
  filePaths: string[],
  configPath?: string
): string[] => {
  const args = ['run', '--out-format=json', '--path-prefix=.']

  if (configPath) {
    args.push('--config', configPath)
  } else {
    args.push('--no-config')
  }

  // Convert file paths to unique directories since golangci-lint runs on directories
  const directories = [...new Set(filePaths.map((file) => dirname(file)))]
  args.push(...directories)

  return args
}

const isExecError = (error: unknown): error is Error & { stdout?: string } =>
  error !== null && typeof error === 'object' && 'stdout' in error

// Parse golangci-lint JSON output
const parseResults = (stdout?: string): GolangciLintIssue[] => {
  try {
    if (stdout === undefined || stdout === '') {
      return []
    }

    // golangci-lint v1.64+ outputs full JSON object (not line-based)
    const parsed: GolangciLintResult = JSON.parse(stdout.trim())
    return parsed.Issues ?? []
  } catch {
    return []
  }
}

const createLintData = (
  timestamp: string,
  files: string[],
  results: GolangciLintIssue[]
): LintResult => {
  const issues = results.flatMap(toIssue)
  return {
    timestamp,
    files,
    issues,
    errorCount: countBySeverity(issues, 'error'),
    warningCount: countBySeverity(issues, 'warning'),
  }
}

const toIssue = (issue: GolangciLintIssue): LintIssue[] => {
  // Check if this contains multiple issues in the Text field
  if (issue.Text.includes('\n') && issue.Text.includes('.go:')) {
    // Parse multi-line text to extract individual issues
    const lines = issue.Text.split('\n').slice(1) // Skip module name line
    return lines
      .filter((line) => line.includes('.go:'))
      .map((line) => parseIssueLine(line, issue.FromLinter))
      .filter((parsedIssue): parsedIssue is LintIssue => parsedIssue !== null)
  }

  // Single issue
  return [
    {
      file: issue.Pos.Filename,
      line: issue.Pos.Line,
      column: issue.Pos.Column,
      severity: 'error' as const,
      message: issue.Text,
      rule: issue.FromLinter,
    },
  ]
}

const parseIssueLine = (line: string, linter: string): LintIssue | null => {
  // Parse format: "./file-with-issues.go:8:2: declared and not used: message"
  const parts = line.split(':')
  const MIN_PARTS = 4 // filename:line:column:message

  if (parts.length < MIN_PARTS || !line.includes('.go:')) return null

  const filename = parts[0]
  const lineStr = parts[1]
  const columnStr = parts[2]
  const MESSAGE_START_INDEX = 3
  const message = parts.slice(MESSAGE_START_INDEX).join(':').trim()

  if (
    !filename.endsWith('.go') ||
    !/^\d+$/.test(lineStr) ||
    !/^\d+$/.test(columnStr)
  ) {
    return null
  }

  return {
    file: filename,
    line: parseInt(lineStr, 10),
    column: parseInt(columnStr, 10),
    severity: 'error' as const,
    message,
    rule: linter,
  }
}

const countBySeverity = (
  issues: LintIssue[],
  severity: 'error' | 'warning'
): number => issues.filter((i) => i.severity === severity).length
