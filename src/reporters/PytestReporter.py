"""
TDD Guard pytest plugin - equivalent to VitestReporter for Python

Usage:
Add to pytest.ini:
[tool:pytest]
addopts = -p tdd_guard.pytest_plugin

Or use via command line:
pytest -p tdd_guard.pytest_plugin
"""

import json
import os
from pathlib import Path


class TDDGuardPytestPlugin:
    """Pytest plugin that captures test results for TDD Guard"""
    
    def __init__(self):
        self.test_results = []
        # Use same path as Config.dataDir default: .claude/tdd-guard/data
        self.storage_dir = Path('.claude/tdd-guard/data')
    
    def pytest_collectreport(self, report):
        """Capture collection errors (import failures, etc.)"""
        if report.failed:
            # Create a synthetic test result for collection failures
            test_result = {
                'name': f"collection_error_{report.nodeid or 'unknown'}",
                'fullName': report.nodeid or 'collection_error',
                'state': 'failed',
                'errors': [{'message': str(report.longrepr)}]
            }
            self.test_results.append(test_result)
    
    def pytest_runtest_logreport(self, report):
        """Collect test results during pytest execution."""
        if report.when == 'call':  # Only capture the main test execution phase
            test_result = {
                'name': report.nodeid.split('::')[-1] if '::' in report.nodeid else report.nodeid,
                'fullName': report.nodeid,
                'state': 'passed' if report.passed else ('failed' if report.failed else 'skipped')
            }
            
            if report.failed and hasattr(report, 'longrepr') and report.longrepr:
                test_result['errors'] = [{'message': str(report.longrepr)}]
            
            self.test_results.append(test_result)
    
    def pytest_sessionfinish(self, session, exitstatus):
        """Save results to TDD Guard storage - equivalent to VitestReporter.onTestRunEnd()"""
        # Group tests by module (same format as VitestReporter)
        modules_map = {}
        for test in self.test_results:
            module_path = test['fullName'].split('::')[0]
            if module_path not in modules_map:
                modules_map[module_path] = {
                    'moduleId': module_path,
                    'tests': []
                }
            modules_map[module_path]['tests'].append(test)
        
        output = {
            'testModules': list(modules_map.values())
        }
        
        # Save to TDD Guard storage (same as VitestReporter.storage.saveTest())
        # Create directory recursively like FileStorage.ensureDirectory()
        self.storage_dir.mkdir(parents=True, exist_ok=True)
        
        # Save as JSON (matching FileStorage.saveTest format)
        storage_path = self.storage_dir / 'test.json'
        with open(storage_path, 'w') as f:
            json.dump(output, f, indent=2)


# Plugin instance
tdd_guard_plugin = TDDGuardPytestPlugin()

# Pytest hook implementations
pytest_collectreport = tdd_guard_plugin.pytest_collectreport
pytest_runtest_logreport = tdd_guard_plugin.pytest_runtest_logreport
pytest_sessionfinish = tdd_guard_plugin.pytest_sessionfinish