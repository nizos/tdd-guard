<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\Test\Skipped;
use PHPUnit\Event\Test\SkippedSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class SkippedTestSubscriber implements SkippedSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(Skipped $event): void
    {
        $this->collector->addTestResult(
            $event->test(),
            'skipped',
            $event->message()
        );
    }
}
