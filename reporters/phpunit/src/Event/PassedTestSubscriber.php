<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\Test\Passed;
use PHPUnit\Event\Test\PassedSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class PassedTestSubscriber implements PassedSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(Passed $event): void
    {
        $this->collector->addTestResult(
            $event->test(),
            'passed'
        );
    }
}
