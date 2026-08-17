import path from 'path'
import { ConfigOptions } from '../contracts/types/ConfigOptions'
import { ClientType } from '../contracts/types/ClientType'

const TEST_RESULTS_FILENAME = 'test.json'
const TODOS_FILENAME = 'todos.json'
const MODIFICATIONS_FILENAME = 'modifications.json'
const LINT_FILENAME = 'lint.json'
const CONFIG_FILENAME = 'config.json'
const INSTRUCTIONS_FILENAME = 'instructions.md'

export const DEFAULT_MODEL_VERSION = 'claude-sonnet-4-6'
export const DEFAULT_CLIENT: ClientType = 'sdk'
export const DEFAULT_DATA_DIR = path.join('.claude', 'tdd-guard', 'data')

/**
 * Environment variables used as fallbacks when TDD_GUARD_MODEL_VERSION is
 * not set. This lets the validation model follow the Claude Code session's
 * own model configuration, which is essential when running against custom
 * API endpoints (ANTHROPIC_BASE_URL) that do not serve the hardcoded
 * default model.
 */
const MODEL_VERSION_ENV_FALLBACKS = [
  'ANTHROPIC_DEFAULT_SONNET_MODEL',
  'ANTHROPIC_MODEL',
  'ANTHROPIC_SMALL_FAST_MODEL',
] as const

/**
 * Strip context-window suffixes (e.g. "[1M]") that Claude Code appends to
 * model identifiers in its own env vars. Those suffixes are valid for the
 * model selector UI but are not part of the model ID sent to the API.
 * Implemented with indexOf instead of a regex to avoid super-linear
 * backtracking on untrusted input.
 */
function stripModelContextSuffix(model: string): string {
  const trimmed = model.trim()
  if (!trimmed.endsWith(']')) {
    return trimmed
  }
  const openBracket = trimmed.lastIndexOf('[', trimmed.length - 2)
  if (openBracket === -1) {
    return trimmed
  }
  const inner = trimmed.slice(openBracket + 1, trimmed.length - 1)
  if (/^\d+[kKmM]?$/.test(inner)) {
    return trimmed.slice(0, openBracket).trimEnd()
  }
  return trimmed
}

const VALID_CLIENTS = new Set<string>(['api', 'cli', 'sdk'])
const MODEL_TYPE_TO_CLIENT: Record<string, ClientType> = {
  anthropic_api: 'api',
  claude_cli: 'cli',
}

export class Config {
  readonly dataDir: string
  readonly useSystemClaude: boolean
  readonly anthropicApiKey: string | undefined
  readonly modelType: string
  readonly linterType: string | undefined
  readonly modelVersion: string
  readonly validationClient: ClientType

  constructor(options?: ConfigOptions) {
    const mode = options?.mode ?? 'production'

    this.dataDir = this.getDataDir(options)
    this.useSystemClaude = this.getUseSystemClaude(options)
    this.anthropicApiKey = this.getAnthropicApiKey(options)
    this.modelType = this.getModelType(options, mode)
    this.linterType = this.getLinterType(options)
    this.modelVersion = this.getModelVersion(options)
    this.validationClient = this.getValidationClient(options)
  }

  private getDataDir(options?: ConfigOptions): string {
    const baseDir = options?.projectRoot ?? this.getValidatedClaudeProjectDir()

    if (baseDir) {
      return path.join(stripTrailingClaude(baseDir), DEFAULT_DATA_DIR)
    }

    const cwd = process.cwd()
    if (path.basename(cwd) === '.claude') {
      return path.join(path.dirname(cwd), DEFAULT_DATA_DIR)
    }
    return DEFAULT_DATA_DIR
  }

  private getUseSystemClaude(options?: ConfigOptions): boolean {
    return options?.useSystemClaude ?? process.env.USE_SYSTEM_CLAUDE === 'true'
  }

  private getAnthropicApiKey(options?: ConfigOptions): string | undefined {
    return options?.anthropicApiKey ?? process.env.TDD_GUARD_ANTHROPIC_API_KEY
  }

  private getModelType(
    options: ConfigOptions | undefined,
    mode: string
  ): string {
    return (
      options?.modelType ?? this.getEnvironmentModelType(mode) ?? 'claude_cli'
    )
  }

  private getEnvironmentModelType(mode: string): string | undefined {
    if (mode === 'test' && process.env.TEST_MODEL_TYPE) {
      return process.env.TEST_MODEL_TYPE
    }
    return process.env.MODEL_TYPE
  }

  get testResultsFilePath(): string {
    return path.join(this.dataDir, TEST_RESULTS_FILENAME)
  }

  get todosFilePath(): string {
    return path.join(this.dataDir, TODOS_FILENAME)
  }

  get modificationsFilePath(): string {
    return path.join(this.dataDir, MODIFICATIONS_FILENAME)
  }

  get lintFilePath(): string {
    return path.join(this.dataDir, LINT_FILENAME)
  }

  get configFilePath(): string {
    return path.join(this.dataDir, CONFIG_FILENAME)
  }

  get instructionsFilePath(): string {
    return path.join(this.dataDir, INSTRUCTIONS_FILENAME)
  }

  private getLinterType(options?: ConfigOptions): string | undefined {
    if (options && 'linterType' in options) {
      return options.linterType?.toLowerCase()
    }
    const envValue = process.env.LINTER_TYPE?.toLowerCase()
    return envValue && envValue.trim() !== '' ? envValue : undefined
  }

  private getModelVersion(options?: ConfigOptions): string {
    if (options?.modelVersion) {
      return stripModelContextSuffix(options.modelVersion)
    }
    const envValue = process.env.TDD_GUARD_MODEL_VERSION
    if (envValue && envValue.trim() !== '') {
      return stripModelContextSuffix(envValue)
    }
    for (const envName of MODEL_VERSION_ENV_FALLBACKS) {
      const fallbackValue = process.env[envName]
      if (fallbackValue && fallbackValue.trim() !== '') {
        return stripModelContextSuffix(fallbackValue)
      }
    }
    return DEFAULT_MODEL_VERSION
  }

  private getValidationClient(options?: ConfigOptions): ClientType {
    return (
      getClientType(options) ?? getLegacyClientType(options) ?? DEFAULT_CLIENT
    )
  }

  private getValidatedClaudeProjectDir(): string | null {
    const projectDir = process.env.CLAUDE_PROJECT_DIR
    if (!projectDir) {
      return null
    }

    // Validate that CLAUDE_PROJECT_DIR is an absolute path (POSIX or Windows)
    if (
      !path.posix.isAbsolute(projectDir) &&
      !path.win32.isAbsolute(projectDir)
    ) {
      throw new Error('CLAUDE_PROJECT_DIR must be an absolute path')
    }

    // Validate that CLAUDE_PROJECT_DIR does not contain path traversal
    if (projectDir.includes('..')) {
      throw new Error('CLAUDE_PROJECT_DIR must not contain path traversal')
    }

    // Validate that current working directory is within CLAUDE_PROJECT_DIR
    const cwd = toComparablePath(process.cwd())
    if (!cwd.startsWith(toComparablePath(projectDir))) {
      throw new Error(
        'CLAUDE_PROJECT_DIR must contain the current working directory'
      )
    }

    return projectDir
  }
}

function stripTrailingClaude(dir: string): string {
  return path.basename(dir) === '.claude' ? path.dirname(dir) : dir
}

function toComparablePath(p: string): string {
  const unified = p.replace(/^\/([a-zA-Z])(?=\/)/, '$1:').replace(/\\/g, '/')
  return /^[a-zA-Z]:/.test(unified) ? unified.toLowerCase() : unified
}

function getClientType(options?: ConfigOptions): ClientType | undefined {
  const directValue = options?.validationClient ?? process.env.VALIDATION_CLIENT
  const normalizedValue = directValue?.toLowerCase()
  return isValidClient(normalizedValue) ? normalizedValue : undefined
}

function getLegacyClientType(options?: ConfigOptions): ClientType | undefined {
  const modelType = options?.modelType ?? process.env.MODEL_TYPE
  return mapModelTypeToClient(modelType)
}

function isValidClient(value?: string): value is ClientType {
  return VALID_CLIENTS.has(value ?? '')
}

function mapModelTypeToClient(modelType?: string): ClientType | undefined {
  const normalizedType = modelType?.toLowerCase()
  return normalizedType ? MODEL_TYPE_TO_CLIENT[normalizedType] : undefined
}
