<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\Test\Failed;
use PHPUnit\Event\Test\FailedSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class FailedTestSubscriber implements FailedSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(Failed $event): void
    {
        $this->collector->addTestResult(
            $event->test(),
            'failed',
            $event->throwable()->message()
        );
    }
}
