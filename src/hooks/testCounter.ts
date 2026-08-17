import { parse, Lang, registerDynamicLanguage } from '@ast-grep/napi'
import pythonLang from '@ast-grep/lang-python'
import goLang from '@ast-grep/lang-go'
import rubyLang from '@ast-grep/lang-ruby'
import phpLang from '@ast-grep/lang-php'
import rustLang from '@ast-grep/lang-rust'
import type { Language } from './fileTypeDetection'

registerDynamicLanguage({
  python: pythonLang,
  go: goLang,
  ruby: rubyLang,
  php: phpLang,
  rust: rustLang,
})

const JS_TEST_PATTERNS = [
  { pattern: 'test($$$A)' },
  { pattern: 'it($$$A)' },
  { pattern: 'test.skip($$$A)' },
  { pattern: 'test.only($$$A)' },
  { pattern: 'test.todo($$$A)' },
  { pattern: 'test.each($$$I)($$$A)' },
  { pattern: 'test.concurrent($$$A)' },
  { pattern: 'it.skip($$$A)' },
  { pattern: 'it.only($$$A)' },
  { pattern: 'it.todo($$$A)' },
  { pattern: 'it.each($$$I)($$$A)' },
  { pattern: 'it.concurrent($$$A)' },
]

const JS_LANGS = {
  javascript: Lang.JavaScript,
  typescript: Lang.TypeScript,
  tsx: Lang.Tsx,
} as const

function countJavaScriptTests(code: string, lang: Lang): number {
  const ast = parse(lang, code)
  const root = ast.root()

  const matches = new Set<number>()
  for (const pattern of JS_TEST_PATTERNS) {
    for (const match of root.findAll({ rule: pattern })) {
      matches.add(match.range().start.line)
    }
  }
  return matches.size
}

function countPythonTests(code: string): number {
  const ast = parse('python', code)
  const matches = ast.root().findAll({
    rule: {
      kind: 'function_definition',
      has: {
        field: 'name',
        regex: '^test_',
      },
    },
  })
  return matches.length
}

function countGoTests(code: string): number {
  const ast = parse('go', code)
  const matches = ast.root().findAll({
    rule: {
      kind: 'function_declaration',
      has: {
        field: 'name',
        regex: '^(Test|Benchmark)',
      },
    },
  })
  return matches.length
}

function countPhpTests(code: string): number {
  const wrappedCode = wrapPhpSnippet(code)
  const ast = parse('php', wrappedCode)
  const root = ast.root()

  const testLines = new Set<number>()

  const byNaming = root.findAll({
    rule: {
      kind: 'method_declaration',
      has: {
        field: 'name',
        regex: '^test',
      },
    },
  })
  for (const match of byNaming) {
    testLines.add(match.range().start.line)
  }

  const byAttribute = root.findAll({
    rule: {
      kind: 'method_declaration',
      has: {
        kind: 'attribute_list',
        stopBy: 'end',
        has: {
          kind: 'name',
          regex: '^Test$',
          stopBy: 'end',
        },
      },
    },
  })
  for (const match of byAttribute) {
    testLines.add(match.range().start.line)
  }

  return testLines.size
}

function wrapPhpSnippet(code: string): string {
  const needsTag = !code.trimStart().startsWith('<?php')
  const needsClass = !code.includes('class ')

  if (needsTag && needsClass) {
    return `<?php\nclass Wrapper {\n${code}\n}`
  }
  if (needsTag) {
    return `<?php\n${code}`
  }
  return code
}

function countRubyTests(code: string): number {
  const ast = parse('ruby', code)
  const matches = ast.root().findAll({
    rule: {
      kind: 'call',
      has: {
        field: 'method',
        regex: '^it$',
      },
    },
  })
  return matches.length
}

function countRustTests(code: string): number {
  const ast = parse('rust', code)
  const attributes = ast.root().findAll({
    rule: {
      pattern: '#[test]',
    },
  })
  return attributes.length
}

export function countTestDefinitions(code: string, language: Language): number {
  switch (language) {
    case 'javascript':
    case 'typescript':
    case 'tsx':
      return countJavaScriptTests(code, JS_LANGS[language])
    case 'python':
      return countPythonTests(code)
    case 'go':
      return countGoTests(code)
    case 'php':
      return countPhpTests(code)
    case 'ruby':
      return countRubyTests(code)
    case 'rust':
      return countRustTests(code)
  }
}
