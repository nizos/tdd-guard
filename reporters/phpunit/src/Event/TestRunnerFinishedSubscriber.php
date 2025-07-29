<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Event;

use PHPUnit\Event\TestRunner\Finished;
use PHPUnit\Event\TestRunner\FinishedSubscriber;
use TddGuard\PHPUnit\TestResultCollector;

final class TestRunnerFinishedSubscriber implements FinishedSubscriber
{
    private TestResultCollector $collector;

    public function __construct(TestResultCollector $collector)
    {
        $this->collector = $collector;
    }

    public function notify(Finished $event): void
    {
        $this->collector->saveResults();
    }
}
