import { describe, it, expect, beforeEach } from 'vitest'
import { GuardManager } from './GuardManager'
import { Storage } from '../storage/Storage'
import { MemoryStorage } from '../storage/MemoryStorage'
import { FileStorage } from '../storage/FileStorage'

describe('GuardManager', () => {
  let storage: Storage
  let guardManager: GuardManager

  beforeEach(() => {
    storage = new MemoryStorage()
    guardManager = new GuardManager(storage)
  })

  describe('constructor', () => {
    it('accepts a Storage instance', () => {
      const customStorage = new MemoryStorage()
      const manager = new GuardManager(customStorage)

      expect(manager['storage']).toBe(customStorage)
    })

    it('uses FileStorage by default', () => {
      const manager = new GuardManager()

      expect(manager['storage']).toBeInstanceOf(FileStorage)
    })
  })

  describe('isEnabled', () => {
    it('returns true when no config exists (default enabled)', async () => {
      expect(await guardManager.isEnabled()).toBe(true)
    })

    it('returns false when guard is disabled in config', async () => {
      await storage.saveConfig(JSON.stringify({ guardEnabled: false }))

      expect(await guardManager.isEnabled()).toBe(false)
    })

    it('returns true when guard is enabled in config', async () => {
      await storage.saveConfig(JSON.stringify({ guardEnabled: true }))

      expect(await guardManager.isEnabled()).toBe(true)
    })
  })

  describe('enable', () => {
    it('enables the guard when no config exists', async () => {
      await guardManager.enable()

      expect(await guardManager.isEnabled()).toBe(true)
    })

    it('enables the guard when previously disabled', async () => {
      await guardManager.disable()
      await guardManager.enable()

      expect(await guardManager.isEnabled()).toBe(true)
    })

    it('preserves existing ignore patterns when enabling', async () => {
      const customPatterns = ['*.custom', 'test/**']
      await setupIgnorePatterns(storage, customPatterns)

      await guardManager.enable()

      expect(await guardManager.getIgnorePatterns()).toEqual(customPatterns)
    })
  })

  describe('disable', () => {
    it('disables the guard when no config exists', async () => {
      await guardManager.disable()

      expect(await guardManager.isEnabled()).toBe(false)
    })

    it('disables the guard when previously enabled', async () => {
      await guardManager.enable()
      await guardManager.disable()

      expect(await guardManager.isEnabled()).toBe(false)
    })

    it('preserves existing ignore patterns when disabling', async () => {
      const customPatterns = ['*.custom', 'test/**']
      await setupIgnorePatterns(storage, customPatterns)

      await guardManager.disable()

      expect(await guardManager.getIgnorePatterns()).toEqual(customPatterns)
    })
  })

  describe('getIgnorePatterns', () => {
    it('returns default ignore patterns when no config exists', async () => {
      const patterns = await guardManager.getIgnorePatterns()

      expect(patterns).toEqual(GuardManager.DEFAULT_IGNORE_PATTERNS)
    })

    it('returns defaults when config exists but ignorePatterns is not provided', async () => {
      await storage.saveConfig(
        JSON.stringify({
          guardEnabled: true,
          // No ignorePatterns field
        })
      )

      const patterns = await guardManager.getIgnorePatterns()

      expect(patterns).toEqual(GuardManager.DEFAULT_IGNORE_PATTERNS)
    })

    it('returns custom patterns from config', async () => {
      await setupIgnorePatterns(storage, [
        '**/*.spec.ts',
        'dist/**',
        'build/**',
      ])

      const patterns = await guardManager.getIgnorePatterns()

      expect(patterns).toContain('**/*.spec.ts')
      expect(patterns).toContain('dist/**')
      expect(patterns).toContain('build/**')
    })

    it('does not include default patterns when custom patterns are provided', async () => {
      const customPatterns = ['*.custom', '**/*.unique', 'special/**']
      await setupIgnorePatterns(storage, customPatterns)

      const patterns = await guardManager.getIgnorePatterns()

      GuardManager.DEFAULT_IGNORE_PATTERNS.forEach((defaultPattern) => {
        expect(patterns).not.toContain(defaultPattern)
      })
    })
  })

  describe('shouldIgnoreFile', () => {
    it('ignores files matching default patterns', async () => {
      for (const pattern of GuardManager.DEFAULT_IGNORE_PATTERNS) {
        // Convert pattern to file path (e.g., '*.md' -> 'file.md')
        const filePath = pattern.replaceAll('*', 'file')
        expect(await guardManager.shouldIgnoreFile(filePath)).toBe(true)
      }
    })

    describe('suffix patterns with *.suffix.ext', () => {
      it.each([
        {
          description: 'file with matching suffix in root',
          path: 'bundle.min.js',
          shouldIgnore: true,
        },
        {
          description: 'file with matching suffix in nested directory',
          path: 'dist/assets/app.min.js',
          shouldIgnore: true,
        },
        {
          description: 'file without the required suffix',
          path: 'bundle.js',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['*.min.js'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('brace expansion', () => {
      it.each([
        {
          description: 'file matching first option in braces',
          path: 'config.yml',
          shouldIgnore: true,
        },
        {
          description: 'file matching second option in braces',
          path: 'config.yaml',
          shouldIgnore: true,
        },
        {
          description: 'nested file with brace-expanded extension',
          path: 'src/components/App.tsx',
          shouldIgnore: true,
        },
        {
          description: 'file not matching any brace option',
          path: 'config.json',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['*.{yml,yaml}', '**/*.{ts,tsx}'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('directory/** patterns', () => {
      it.each([
        {
          description: 'file directly in specified directory',
          path: 'dist/bundle.js',
          shouldIgnore: true,
        },
        {
          description: 'file deeply nested in specified directory',
          path: 'dist/assets/images/logo.png',
          shouldIgnore: true,
        },
        {
          description: 'file in directory with similar name',
          path: 'distribute/file.js',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['dist/**'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('**/directory/** patterns', () => {
      it.each([
        {
          description: 'directory at root level',
          path: 'artifacts/output.txt',
          shouldIgnore: true,
        },
        {
          description: 'directory nested in another directory',
          path: 'build/artifacts/output.txt',
          shouldIgnore: true,
        },
        {
          description: 'directory with partial name match',
          path: 'my-artifacts/file.txt',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['**/artifacts/**'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('**/*.extension patterns', () => {
      it.each([
        {
          description: 'file with matching extension in root',
          path: 'app.test.ts',
          shouldIgnore: true,
        },
        {
          description: 'file with matching extension in subdirectory',
          path: 'src/app.test.ts',
          shouldIgnore: true,
        },
        {
          description: 'file with matching extension deeply nested',
          path: 'tests/unit/user.test.ts',
          shouldIgnore: true,
        },
        {
          description: 'file without matching extension',
          path: 'src/app.ts',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['**/*.test.ts'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('path/**/*.extension patterns', () => {
      it.each([
        {
          description: 'file matching both path and extension',
          path: 'src/tests/e2e.spec.js',
          shouldIgnore: true,
        },
        {
          description: 'file with matching extension outside specified path',
          path: 'lib/tests/e2e.spec.js',
          shouldIgnore: false,
        },
        {
          description: 'file in correct path but different extension',
          path: 'src/tests/e2e.test.js',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['src/**/*.spec.js'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    describe('hidden directories', () => {
      it.each([
        {
          description: 'file directly in .venv',
          path: '.venv/bin/python',
          shouldIgnore: true,
        },
        {
          description: 'deeply nested file in .venv',
          path: '.venv/lib/python3.9/site-packages/module.py',
          shouldIgnore: true,
        },
        {
          description: 'file in visible directory',
          path: 'venv/bin/python',
          shouldIgnore: false,
        },
      ])('$description', async ({ path, shouldIgnore }) => {
        await setupIgnorePatterns(storage, ['.venv/**'])
        expect(await guardManager.shouldIgnoreFile(path)).toBe(shouldIgnore)
      })
    })

    it('ignores ERB templates by default', async () => {
      expect(
        await guardManager.shouldIgnoreFile('app/views/users/show.html.erb')
      ).toBe(true)
    })
  })
})

// Helper function to set up ignore patterns
async function setupIgnorePatterns(
  storage: Storage,
  patterns: string[]
): Promise<void> {
  await storage.saveConfig(
    JSON.stringify({
      guardEnabled: true,
      ignorePatterns: patterns,
    })
  )
}
