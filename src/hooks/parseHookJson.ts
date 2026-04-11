function sanitizeInvalidJsonEscapes(input: string): string {
  return input.replace(
    /((?:^|[^\\])(?:\\\\)*)\\(?!["\\/bfnrtu])/g,
    '$1\\\\'
  )
}

export function parseHookJson(input: string): unknown {
  try {
    return JSON.parse(input)
  } catch {
    return JSON.parse(sanitizeInvalidJsonEscapes(input))
  }
}
