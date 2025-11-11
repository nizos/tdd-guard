export interface ProcessedLintData {
  hasIssues: boolean
  summary: string
  issuesByFile: Map<string, string[]>
  totalIssues: number
  errorCount: number
  warningCount: number
}

export type Context = {
  modifications: string
  todo?: string
  test?: string
  lint?: ProcessedLintData
  instructions?: string
  testFileExists?: boolean
  language?: string
  languageCategory?: 'compiled' | 'interpreted'
}
