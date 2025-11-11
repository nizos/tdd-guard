export type FileType = 'python' | 'javascript' | 'php' | 'java' | 'kotlin' | 'go' | 'rust' | 'typescript'
export type LanguageCategory = 'compiled' | 'interpreted'

/**
 * Detects programming language from file extension.
 * Used to apply language-specific TDD rules.
 */
export function detectFileType(hookData: unknown): FileType {
  const toolInput = (hookData as { tool_input?: Record<string, unknown> }).tool_input
  if (!toolInput || typeof toolInput !== 'object' || !('file_path' in toolInput)) {
    return 'javascript'
  }

  const filePath = toolInput.file_path
  if (typeof filePath !== 'string') {
    return 'javascript'
  }

  return detectLanguageFromPath(filePath)
}

function detectLanguageFromPath(filePath: string): FileType {
  // Compiled languages - require compilation phase before test execution
  if (filePath.endsWith('.java')) return 'java'
  if (filePath.endsWith('.kt') || filePath.endsWith('.kts')) return 'kotlin'
  if (filePath.endsWith('.go')) return 'go'
  if (filePath.endsWith('.rs')) return 'rust'

  // Interpreted languages - tests execute directly
  if (filePath.endsWith('.py')) return 'python'
  if (filePath.endsWith('.php')) return 'php'
  if (filePath.endsWith('.ts') || filePath.endsWith('.tsx')) return 'typescript'
  if (filePath.endsWith('.js') || filePath.endsWith('.jsx')) return 'javascript'

  return 'javascript'
}

/**
 * Categorizes languages to apply appropriate TDD rules.
 * Java and Kotlin get special POJO/data class treatment.
 * Go/Rust/Python/JavaScript/TypeScript: Strict incremental TDD (original behavior)
 */
export function getLanguageCategory(fileType: FileType): LanguageCategory {
  // Java and Kotlin: Allow POJO/data class patterns
  const compiledLanguages: FileType[] = ['java', 'kotlin']
  return compiledLanguages.includes(fileType) ? 'compiled' : 'interpreted'
}