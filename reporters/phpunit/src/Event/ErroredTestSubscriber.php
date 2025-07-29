<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\Test\Errored;
use PHPUnit\Event\Test\ErroredSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class ErroredTestSubscriber implements ErroredSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(Errored $event): void
    {
        $this->collector->addTestResult(
            $event->test(),
            'failed',
            $event->throwable()->message()
        );
    }
}
