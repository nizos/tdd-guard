import { ValidationResult } from '../contracts/types/ValidationResult'
import { block } from '../contracts/validationResults'
import { Context } from '../contracts/types/Context'
import { ModelClient } from '../contracts/types/ModelClient'
import { ClaudeCli } from './models/ClaudeCli'
import { generateDynamicContext } from './context/context'

interface ModelResponseJson {
  decision: string | null
  reason?: string
}

export async function validator(
  context: Context,
  modelClient: ModelClient = new ClaudeCli()
): Promise<ValidationResult> {
  try {
    const prompt = generateDynamicContext(context)
    const response = await modelClient.ask(prompt)
    if (!response) {
      // Fail open: an unresponsive validation model must not block the
      // user's file operations.
      return {
        decision: undefined,
        reason: 'No response from model, validation skipped',
      }
    }
    return parseModelResponse(response)
  } catch (error) {
    const errorMessage =
      error instanceof Error ? error.message : 'Unknown error'
    // Fail open: validation infrastructure errors (API outages, unsupported
    // model names, etc.) must not block the user's file operations. The
    // reason is surfaced so the failure is visible without interrupting work.
    return {
      decision: undefined,
      reason: `Validation skipped: ${errorMessage}`,
    }
  }
}

function parseModelResponse(response: string): ValidationResult {
  const jsonString = extractJsonString(response)
  let parsed: ModelResponseJson
  try {
    parsed = JSON.parse(jsonString)
  } catch {
    throw new Error(`The model did not return valid JSON: ${response}`)
  }
  return normalizeValidationResult(parsed)
}

function extractJsonString(response: string): string {
  const jsonFromCodeBlock = extractFromJsonCodeBlock(response)
  if (jsonFromCodeBlock) {
    return jsonFromCodeBlock
  }

  const jsonFromGenericBlock = extractFromGenericCodeBlock(response)
  if (jsonFromGenericBlock) {
    return jsonFromGenericBlock
  }

  // Try to extract plain JSON from text
  const plainJson = extractPlainJson(response)
  if (plainJson) {
    return plainJson
  }

  return response
}

function extractFromJsonCodeBlock(response: string): string | null {
  // Find all json code blocks
  const startPattern = '```json'
  const endPattern = '```'
  const blocks: string[] = []

  let startIndex = 0
  let blockStart = response.indexOf(startPattern, startIndex)

  while (blockStart !== -1) {
    const contentStart = blockStart + startPattern.length
    const blockEnd = response.indexOf(endPattern, contentStart)
    if (blockEnd === -1) break

    const content = response.substring(contentStart, blockEnd).trim()
    blocks.push(content)
    startIndex = blockEnd + endPattern.length
    blockStart = response.indexOf(startPattern, startIndex)
  }

  if (blocks.length > 0) {
    return blocks[blocks.length - 1]
  }

  return null
}

function extractPlainJson(response: string): string | null {
  // Find the JSON object carrying the decision; the reason is optional.
  const pattern = /\{[^{}]*"decision"[^{}]*}/g
  const matches = response.match(pattern)

  if (!matches) return null

  // Return the last match (most likely the final decision)
  const lastMatch = matches[matches.length - 1]

  // Validate it's proper JSON
  if (isValidJson(lastMatch)) {
    return lastMatch
  }

  return null
}

function extractFromGenericCodeBlock(response: string): string | null {
  const codeBlock = findCodeBlock(response)
  if (!codeBlock) return null

  const content = codeBlock.trim()
  return isValidJson(content) ? content : null
}

function findCodeBlock(response: string): string | null {
  const startPattern = '```'
  const blockStart = response.indexOf(startPattern)
  if (blockStart === -1) return null

  const contentStart = skipWhitespace(
    response,
    blockStart + startPattern.length
  )
  const blockEnd = response.indexOf(startPattern, contentStart)
  if (blockEnd === -1) return null

  return response.substring(contentStart, blockEnd)
}

function skipWhitespace(text: string, startIndex: number): number {
  let index = startIndex
  while (index < text.length && /\s/.test(text[index])) {
    index++
  }
  return index
}

function isValidJson(str: string): boolean {
  try {
    JSON.parse(str)
    return true
  } catch {
    return false
  }
}

function normalizeValidationResult(
  parsed: ModelResponseJson
): ValidationResult {
  if (parsed.decision === 'block') {
    return block(parsed.reason ?? '')
  }
  if (parsed.decision === null) {
    return { decision: undefined, reason: parsed.reason ?? '' }
  }
  throw new Error(
    `The model response did not include a valid decision: ${JSON.stringify(parsed)}`
  )
}
