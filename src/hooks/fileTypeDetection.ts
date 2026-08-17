const TEST_FILE_PATTERNS = [
  /\.test\./,
  /\.spec\./,
  /(?:^|[\\/])tests?[\\/]/,
  /(?:^|[\\/])__tests__[\\/]/,
  /_test\.go$/,
  /Test\.php$/,
  /(?:^|[\\/])test_[^/\\]+\.py$/,
  /(?:^|[\\/])spec[\\/]/,
  /_spec\.rb$/,
]

export function isTestFile(filePath: string): boolean {
  return TEST_FILE_PATTERNS.some((pattern) => pattern.test(filePath))
}

export type Language =
  | 'typescript'
  | 'tsx'
  | 'javascript'
  | 'python'
  | 'go'
  | 'ruby'
  | 'php'
  | 'rust'

const EXTENSION_TO_LANGUAGE: Record<string, Language> = {
  '.ts': 'typescript',
  '.tsx': 'tsx',
  '.js': 'javascript',
  '.jsx': 'javascript',
  '.py': 'python',
  '.go': 'go',
  '.rb': 'ruby',
  '.php': 'php',
  '.rs': 'rust',
}

export function detectLanguage(filePath: string): Language | null {
  const lastDotIndex = filePath.lastIndexOf('.')
  if (lastDotIndex === -1) return null
  const extension = filePath.slice(lastDotIndex)
  return EXTENSION_TO_LANGUAGE[extension] ?? null
}
