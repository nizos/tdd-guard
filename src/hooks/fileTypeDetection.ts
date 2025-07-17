export function detectFileType(hookData: unknown): 'python' | 'javascript' {
  // Handle different tool operation types
  const toolInput = (hookData as { tool_input?: Record<string, unknown> }).tool_input
  if (toolInput && typeof toolInput === 'object' && 'file_path' in toolInput) {
    const filePath = toolInput.file_path
    if (typeof filePath === 'string' && filePath.endsWith('.py')) {
      return 'python'
    }
  }
  return 'javascript'
}
