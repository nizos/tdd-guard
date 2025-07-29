<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit;

use PHPUnit\Runner\Extension\Extension;
use PHPUnit\Runner\Extension\Facade;
use PHPUnit\Runner\Extension\ParameterCollection;
use PHPUnit\TextUI\Configuration\Configuration;

/**
 * @psalm-api
 */
final class TddGuardExtension implements Extension
{
    public function bootstrap(Configuration $configuration, Facade $facade, ParameterCollection $parameters): void
    {
        $projectRoot = $this->getProjectRoot($parameters);

        $subscriber = new TddGuardSubscriber($projectRoot);
        foreach ($subscriber->getSubscribers() as $eventSubscriber) {
            $facade->registerSubscriber($eventSubscriber);
        }
    }

    private function getProjectRoot(ParameterCollection $parameters): string
    {
        $configuredRoot = $parameters->has('projectRoot') ? $parameters->get('projectRoot') : '';

        return PathValidator::resolveProjectRoot($configuredRoot);
    }
}
