<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit;

use TddGuard\PHPUnit\Event\ErroredTestSubscriber;
use TddGuard\PHPUnit\Event\FailedTestSubscriber;
use TddGuard\PHPUnit\Event\IncompleteTestSubscriber;
use TddGuard\PHPUnit\Event\PassedTestSubscriber;
use TddGuard\PHPUnit\Event\SkippedTestSubscriber;
use TddGuard\PHPUnit\Event\TestRunnerFinishedSubscriber;

final class TddGuardSubscriber
{
    private TestResultCollector $collector;

    public function __construct(string $projectRoot)
    {
        $storage = new Storage($projectRoot);
        $this->collector = new TestResultCollector($storage, $projectRoot);
    }

    public function getSubscribers(): array
    {
        return [
            new PassedTestSubscriber($this->collector),
            new FailedTestSubscriber($this->collector),
            new ErroredTestSubscriber($this->collector),
            new SkippedTestSubscriber($this->collector),
            new IncompleteTestSubscriber($this->collector),
            new TestRunnerFinishedSubscriber($this->collector),
        ];
    }
}
