<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\Test\MarkedIncomplete;
use PHPUnit\Event\Test\MarkedIncompleteSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class IncompleteTestSubscriber implements MarkedIncompleteSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(MarkedIncomplete $event): void
    {
        $this->collector->addTestResult(
            $event->test(),
            'skipped',
            'Incomplete: ' . $event->throwable()->message()
        );
    }
}
