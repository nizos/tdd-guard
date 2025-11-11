import fs from 'fs/promises'
import path from 'path'

/**
 * Detects if a corresponding test file exists for a given implementation file.
 * Supports common test naming conventions across different languages/frameworks.
 */
export async function detectTestFile(
  implementationFilePath: string,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _projectRoot?: string
): Promise<string | null> {
  // Extract file information
  const basename = path.basename(
    implementationFilePath,
    path.extname(implementationFilePath)
  )
  const ext = path.extname(implementationFilePath)

  // Common test file patterns
  const testPatterns = [
    // JavaScript/TypeScript patterns
    `${basename}.test${ext}`,
    `${basename}.spec${ext}`,

    // Java/Kotlin patterns (CustomerTest.java, CustomerTest.kt, TestCustomer.java)
    `${basename}Test${ext}`,
    `Test${basename}${ext}`,

    // Python patterns
    `test_${basename}${ext}`,
    `${basename}_test${ext}`,
  ]

  // Check in same directory first
  const implDir = path.dirname(implementationFilePath)
  for (const pattern of testPatterns) {
    const testPath = path.join(implDir, pattern)
    if (await fileExists(testPath)) {
      return testPath
    }
  }

  // Check in mirrored test directories
  // e.g., src/main/java/com/example/Customer.java -> src/test/java/com/example/CustomerTest.java
  const mirroredPath = implementationFilePath
    .replace(/\/src\/main\//, `${path.sep}src${path.sep}test${path.sep}`)
    .replace(/\\src\\main\\/, `${path.sep}src${path.sep}test${path.sep}`)
    .replace(/\/lib\//, `${path.sep}test${path.sep}`)
    .replace(/\\lib\\/, `${path.sep}test${path.sep}`)
    .replace(/\/source\//, `${path.sep}tests${path.sep}`)
    .replace(/\\source\\/, `${path.sep}tests${path.sep}`)

  // Try each test pattern in the mirrored directory
  const mirroredDir = path.dirname(mirroredPath)
  for (const pattern of testPatterns) {
    const testPath = path.join(mirroredDir, pattern)
    if (await fileExists(testPath)) {
      return testPath
    }
  }

  return null
}

async function fileExists(filePath: string): Promise<boolean> {
  try {
    await fs.access(filePath)
    return true
  } catch {
    return false
  }
}
