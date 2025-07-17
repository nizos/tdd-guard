import { Context } from '../../contracts/types/Context'
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
import {
  formatLintDataForContext,
  ProcessedLintData,
} from '../../processors/lintProcessor'
import { detectFileType } from '../../hooks/fileTypeDetection'

// Import core prompts (always included)
import { ROLE_AND_CONTEXT } from '../prompts/role-and-context'
import { TDD_CORE_PRINCIPLES } from '../prompts/tdd-core-principles'
import { FILE_TYPE_RULES } from '../prompts/file-type-rules'
import { RESPONSE_FORMAT } from '../prompts/response-format'

// Import operation-specific analysis
import { EDIT_ANALYSIS } from '../prompts/edit-analysis'
import { MULTI_EDIT_ANALYSIS } from '../prompts/multi-edit-analysis'
import { WRITE_ANALYSIS } from '../prompts/write-analysis'

export function generateDynamicContext(context: Context): string {
  const operation: ToolOperation = JSON.parse(context.modifications)

  // Build prompt in correct order
  const sections: string[] = [
    // 1. Core sections (always included)
    ROLE_AND_CONTEXT,
    TDD_CORE_PRINCIPLES,
    FILE_TYPE_RULES,

    // 2. Operation-specific analysis (only for current operation)
    getOperationAnalysis(operation),

    // 3. Changes under review
    '\n## Changes to Review\n',
    formatOperation(operation),

    // 4. Additional context
    formatTestOutput(context.test ?? '', operation),
    context.todo ? formatTodoList(context.todo) : '',
    context.lint ? formatLintOutput(context.lint) : '',

    // 5. Response format
    RESPONSE_FORMAT,
  ]

  return sections.filter(Boolean).join('\n')
}

function getOperationAnalysis(operation: ToolOperation): string {
  if (isEditOperation(operation)) {
    return EDIT_ANALYSIS
  }

  if (isMultiEditOperation(operation)) {
    return MULTI_EDIT_ANALYSIS
  }

  if (isWriteOperation(operation)) {
    return WRITE_ANALYSIS
  }

  return ''
}

function formatOperation(operation: ToolOperation): string {
  if (isEditOperation(operation)) {
    return formatEditOperation(operation)
  }

  if (isMultiEditOperation(operation)) {
    return formatMultiEditOperation(operation)
  }

  if (isWriteOperation(operation)) {
    return formatWriteOperation(operation)
  }

  return ''
}

// Context section descriptions
const EDIT_MODIFICATIONS_DESCRIPTION = `This section shows the code changes being proposed. Compare the old content with the new content to identify what's being added, removed, or modified.`

const WRITE_MODIFICATIONS_DESCRIPTION = `This section shows the new file being created. Analyze the content to determine if it follows TDD principles for new file creation.`

const TEST_OUTPUT_DESCRIPTION = `This section shows the output from the most recent test run BEFORE this modification.

IMPORTANT: This test output is from PREVIOUS work, not from the changes being reviewed. The modification has NOT been executed yet.

Use this to understand:
- Which tests are failing and why (from previous work)
- What error messages indicate about missing implementation
- Whether tests are passing (indicating refactor phase may be appropriate)

Note: Test output may be from unrelated features. This does NOT prevent starting new test-driven work.`

const TODO_LIST_DESCRIPTION = `This section shows the developer's task list. Use this to understand:
- What the developer is currently working on (in_progress)
- What has been completed (completed)
- What is planned next (pending)
Note: Multiple pending "add test" todos don't justify adding multiple tests at once.`

const LINT_OUTPUT_DESCRIPTION = `This section shows the current code quality status from static analysis.

IMPORTANT: This lint output reflects the CURRENT state of the codebase BEFORE the proposed modification.

Use this to understand:
- Current code quality issues that need attention
- Whether code quality should be addressed before new features
- Patterns of issues that may indicate architectural concerns

Note: During TDD red phase (failing tests), focus on making tests pass before addressing lint issues.
During green phase (passing tests), lint issues should be addressed before proceeding to new features.`

function formatEditOperation(operation: EditOperation): string {
  return [
    EDIT_MODIFICATIONS_DESCRIPTION,
    formatSection('File Path', operation.tool_input.file_path),
    formatSection('Old Content', operation.tool_input.old_string),
    formatSection('New Content', operation.tool_input.new_string),
  ].join('\n')
}

function formatMultiEditOperation(operation: MultiEditOperation): string {
  const editsFormatted = operation.tool_input.edits
    .map((edit, index) => formatEdit(edit, index + 1))
    .join('')

  return [
    EDIT_MODIFICATIONS_DESCRIPTION,
    formatSection('File Path', operation.tool_input.file_path),
    '\n### Edits\n',
    editsFormatted,
  ].join('\n')
}

function formatWriteOperation(operation: WriteOperation): string {
  return [
    WRITE_MODIFICATIONS_DESCRIPTION,
    formatSection('File Path', operation.tool_input.file_path),
    formatSection('New File Content', operation.tool_input.content),
  ].join('\n')
}

function formatEdit(
  edit: { old_string: string; new_string: string },
  index: number
): string {
  const fenceMarker = '```'
  return [
    `\n#### Edit ${index}:\n`,
    `**Old Content:**\n${fenceMarker}\n${edit.old_string}\n${fenceMarker}\n`,
    `**New Content:**\n${fenceMarker}\n${edit.new_string}\n${fenceMarker}\n`,
  ].join('')
}

function formatTestOutput(
  testOutput: string,
  operation: ToolOperation
): string {
  // Handle empty or missing test output
  if (!testOutput || testOutput.trim() === '') {
    return [
      '\n### Test Output\n',
      TEST_OUTPUT_DESCRIPTION,
      '\n```\n',
      'No test output available. Tests must be run before implementing.',
      '\n```\n',
    ].join('')
  }

  const processor = new TestResultsProcessor()

  // Use existing file type detection
  const fileType = detectFileType({ tool_input: operation.tool_input })
  const framework = fileType === 'python' ? 'pytest' : 'vitest'

  const formattedOutput = processor.process(testOutput, framework)

  return [
    '\n### Test Output\n',
    TEST_OUTPUT_DESCRIPTION,
    '\n```\n',
    formattedOutput,
    '\n```\n',
  ].join('')
}

function formatTodoList(todoJson: string): string {
  const todoOperation = JSON.parse(todoJson)
  const todos: Todo[] = todoOperation.tool_input?.todos ?? []

  const todoItems = todos
    .map(
      (todo, index) =>
        `\n${index + 1}. [${todo.status}] ${todo.content} (${todo.priority})`
    )
    .join('')

  return ['\n### Todo List\n', TODO_LIST_DESCRIPTION, todoItems, '\n'].join('')
}

function formatLintOutput(lintData: ProcessedLintData): string {
  const formattedLintData = formatLintDataForContext(lintData)

  return [
    '\n### Code Quality Status\n',
    LINT_OUTPUT_DESCRIPTION,
    '\n```\n',
    formattedLintData,
    '\n```\n',
  ].join('')
}

function formatSection(title: string, content: string): string {
  const fenceMarker = '```'
  return `\n### ${title}\n${fenceMarker}\n${content}\n${fenceMarker}\n`
}
