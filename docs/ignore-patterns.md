# Ignore Patterns Guide

Configure TDD Guard to skip validation for specific files using glob patterns.

## Why Use Ignore Patterns?

Control exactly which files TDD Guard validates. Useful for monorepos, rapid prototyping, or when different parts of your codebase need different validation rules.

## Default Ignore Patterns

By default, TDD Guard ignores files with these extensions:

- `*.md` - Markdown documentation
- `*.txt` - Text files
- `*.log` - Log files
- `*.json` - JSON configuration files
- `*.yml` / `*.yaml` - YAML configuration files
- `*.xml` - XML files
- `*.html` - HTML files
- `*.css` - Stylesheets
- `*.erb` - Rails ERB templates
- `*.rst` - reStructuredText documentation

## Custom Ignore Patterns

You can configure custom ignore patterns by creating a `config.json` file in the TDD Guard data directory (`.claude/tdd-guard/data/`):

```json
{
  "guardEnabled": true,
  "ignorePatterns": [
    "*.md",
    "*.css",
    "*.json",
    "*.yml",
    "**/*.generated.ts",
    "**/public/**",
    "*.config.*"
  ]
}
```

**Note**: Custom patterns replace the default patterns entirely. If you want to keep some defaults (like `*.md` or `*.json`), include them in your custom list.

## Pattern Syntax

Patterns use minimatch syntax (similar to `.gitignore`):

- `*.ext` - Match files with extension (e.g., `*.md`)
- `dir/**` - Match all files in directory (e.g., `dist/**`)
- `**/*.ext` - Match extension anywhere (e.g., `**/*.test.ts`)
- `*.{js,ts}` - Match multiple extensions (e.g., `*.{yml,yaml}`)
- `path/**/*.ext` - Match in specific path (e.g., `src/**/*.spec.js`)

## Managing Patterns

### Viewing Current Patterns

To see which patterns are currently active, check your `config.json` file:

```bash
cat .claude/tdd-guard/data/config.json
```

If no custom patterns are configured, the default patterns listed above are used.

### Updating Patterns

1. Create or edit `.claude/tdd-guard/data/config.json`
2. Add your `ignorePatterns` array
3. The changes take effect immediately

### Testing Patterns

To verify your patterns work as expected:

1. Edit a file that should be ignored
2. TDD Guard should skip validation immediately

## Summary

Ignore patterns provide the flexibility to apply TDD validation exactly where you want it in your codebase. Start with the defaults, then customize as your project's needs evolve.
