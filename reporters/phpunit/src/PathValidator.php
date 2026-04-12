<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit;

final class PathValidator
{
    public static function resolveProjectRoot(string $configuredRoot): string
    {
        if ($configuredRoot !== '') {
            $validated = self::validateProjectRoot($configuredRoot);
            if ($validated === null) {
                throw new \InvalidArgumentException('Configured project root is invalid');
            }

            return $validated;
        }

        $envRoot = getenv('TDD_GUARD_PROJECT_ROOT');
        if ($envRoot !== false && $envRoot !== '') {
            $validated = self::validateProjectRoot($envRoot);
            if ($validated === null) {
                throw new \InvalidArgumentException('TDD_GUARD_PROJECT_ROOT is invalid');
            }

            return $validated;
        }

        throw new \InvalidArgumentException(
            'project root must be configured via projectRoot parameter or TDD_GUARD_PROJECT_ROOT environment variable'
        );
    }

    private static function validateProjectRoot(string $path): ?string
    {
        if ($path === '') {
            return null;
        }

        if (!is_dir($path)) {
            return null;
        }

        $normalizedPath = realpath($path);
        if ($normalizedPath === false) {
            return null;
        }

        $cwd = realpath(getcwd());
        if ($cwd === false) {
            return null;
        }

        if (!self::isAncestorOrSame($normalizedPath, $cwd)) {
            return null;
        }

        return $normalizedPath;
    }

    private static function isAncestorOrSame(string $potentialAncestor, string $path): bool
    {
        $potentialAncestor = rtrim($potentialAncestor, DIRECTORY_SEPARATOR);
        $path = rtrim($path, DIRECTORY_SEPARATOR);

        if ($potentialAncestor === $path) {
            return true;
        }

        return strpos($path, $potentialAncestor . DIRECTORY_SEPARATOR) === 0;
    }
}
