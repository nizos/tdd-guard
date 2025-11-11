import { Context, ProcessedLintData } from '../../contracts/types/Context'
import { Config } from '../../config/Config'
import {
  isEditOperation,
  isMultiEditOperation,
  isWriteOperation,
  ToolOperation,
  EditOperation,
  MultiEditOperation,
  WriteOperation,
  Todo,
} from '../../contracts/schemas/toolSchemas'
import { TestResultsProcessor } from '../../processors'
import { formatLintDataForContext } from '../../processors/lintProcessor'

// Import core prompts
import { SYSTEM_PROMPT } from '../prompts/system-prompt'
import { RULES } from '../prompts/rules'
import { FILE_TYPES } from '../prompts/file-types'
import { RESPONSE } from '../prompts/response'

// Import operation-specific context
import { EDIT } from '../prompts/operations/edit'
import { MULTI_EDIT } from '../prompts/operations/multi-edit'
import { WRITE } from '../prompts/operations/write'
import { TODOS } from '../prompts/tools/todos'
import { TEST_OUTPUT } from '../prompts/tools/test-output'
import { LINT_RESULTS } from '../prompts/tools/lint-results'
import { COMPILED_LANGUAGE_RULES } from '../prompts/languages/compiled'

export function generateDynamicContext(
  context: Context,
  config?: Config
): string {
  const operation: ToolOperation = JSON.parse(context.modifications)
  const effectiveConfig = config ?? new Config()

  const sections: string[] = [
    // 1. Core sections (system prompt only for CLI)
    getSystemPrompt(effectiveConfig),
    context.instructions ?? RULES,
    FILE_TYPES,

    // 2. Java support: Language-specific rules (only for compiled languages)
    formatLanguageRules(context.languageCategory),

    // 3. Operation-specific context and changes
    formatOperation(operation),

    // 4. Additional context (Java support: includes test file detection + language category)
    formatTestSection(
      context.test,
      context.testFileExists,
      context.languageCategory
    ),
    formatTodoSection(context.todo),
    formatLintSection(context.lint),

    // 5. Response format
    RESPONSE,
  ]

  return sections.filter(Boolean).join('\n')
}

/**
 * Java support: Includes compiled language rules only for Java/Go/Rust.
 * Python/JavaScript/TypeScript get empty string (no additional rules = strict TDD).
 */
function formatLanguageRules(
  languageCategory?: 'compiled' | 'interpreted'
): string {
  if (languageCategory === 'compiled') {
    return COMPILED_LANGUAGE_RULES
  }
  return '' // Interpreted languages: no special rules, original strict TDD behavior
}

function formatOperation(operation: ToolOperation): string {
  if (isEditOperation(operation)) {
    return EDIT + formatEditOperation(operation)
  }

  if (isMultiEditOperation(operation)) {
    return MULTI_EDIT + formatMultiEditOperation(operation)
  }

  if (isWriteOperation(operation)) {
    return WRITE + formatWriteOperation(operation)
  }

  return ''
}

function formatEditOperation(operation: EditOperation): string {
  return (
    formatSection('File Path', operation.tool_input.file_path) +
    formatSection('Old Content', operation.tool_input.old_string) +
    formatSection('New Content', operation.tool_input.new_string)
  )
}

function formatMultiEditOperation(operation: MultiEditOperation): string {
  const editsFormatted = operation.tool_input.edits
    .map((edit, index) => formatEdit(edit, index + 1))
    .join('')

  return `${formatSection(
    'File Path',
    operation.tool_input.file_path
  )}\n### Edits\n${editsFormatted}`
}

function formatWriteOperation(operation: WriteOperation): string {
  return (
    formatSection('File Path', operation.tool_input.file_path) +
    formatSection('New File Content', operation.tool_input.content)
  )
}

function formatEdit(
  edit: { old_string: string; new_string: string },
  index: number
): string {
  return (
    `\n#### Edit ${index}:\n` +
    `**Old Content:**\n${codeBlock(edit.old_string)}` +
    `**New Content:**\n${codeBlock(edit.new_string)}`
  )
}

/**
 * Java support: Formats test output with language-aware messaging.
 * For compiled languages with test file: Adds note about POJO patterns (even if test output exists).
 * For interpreted languages: Maintains original strict behavior (zero changes).
 */
function formatTestSection(
  testOutput?: string,
  testFileExists?: boolean,
  languageCategory?: 'compiled' | 'interpreted'
): string {
  let output: string

  if (testOutput?.trim()) {
    output = new TestResultsProcessor().process(testOutput)

    // Java support: Even with test output, remind about POJO patterns if test file exists
    if (testFileExists && languageCategory === 'compiled') {
      output +=
        '\n\n**NOTE**: Test file exists for this implementation file. Refer to Java/Kotlin data class patterns above if creating POJOs.'
    }
  } else if (testFileExists) {
    // Java support: Different messaging for compiled vs interpreted languages
    if (languageCategory === 'compiled') {
      output =
        'No test output available - tests have not run successfully yet (likely compilation failure).\n\n**IMPORTANT**: A corresponding test file EXISTS for this implementation. **This is COMPILED LANGUAGE TDD** - compilation stubs are REQUIRED and ALLOWED.\n\nRefer to the "Compiled Language Specific Rules" section above for what constitutes an acceptable compilation phase stub.'
    } else {
      // Interpreted languages: original behavior
      output =
        'No test output available - tests have not run successfully yet.\n\n**IMPORTANT**: A corresponding test file EXISTS for this implementation. Allow minimal stubs to fix issues.'
    }
  } else {
    output =
      'No test output available and no corresponding test file detected. Implementation without tests violates TDD principles.'
  }

  return TEST_OUTPUT + codeBlock(output)
}

function formatTodoSection(todoJson?: string): string {
  if (!todoJson) return ''

  const todoOperation = JSON.parse(todoJson)
  const todos: Todo[] = todoOperation.tool_input?.todos ?? []

  const todoItems = todos
    .map(
      (todo, index) =>
        `${index + 1}. [${todo.status}] ${todo.content} (${todo.priority})`
    )
    .join('\n')

  return `${TODOS}${todoItems}\n`
}

function formatLintSection(lintData?: ProcessedLintData): string {
  if (!lintData) return ''

  const formattedLintData = formatLintDataForContext(lintData)
  return LINT_RESULTS + codeBlock(formattedLintData)
}

function formatSection(title: string, content: string): string {
  return `\n### ${title}\n${codeBlock(content)}`
}

function codeBlock(content: string): string {
  return `\`\`\`\n${content}\n\`\`\`\n`
}

function getSystemPrompt(config: Config): string {
  // Only the CLI client requires the system prompt in the query
  return config.validationClient === 'cli' ? SYSTEM_PROMPT : ''
}
