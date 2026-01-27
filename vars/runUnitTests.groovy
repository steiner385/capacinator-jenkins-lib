#!/usr/bin/env groovy
/**
 * Run Unit Tests Stage
 * Executes Jest unit tests with coverage reporting and threshold checks
 *
 * Features:
 * - Jest test framework
 * - GitHub status reporting (pending/success/failure)
 * - Coverage threshold enforcement
 * - JUnit and coverage report publishing
 * - Platform-aware maxWorkers (Windows: 1, Linux: 50%)
 *
 * Usage:
 *   runUnitTests()  // Use defaults
 *   runUnitTests(testCommand: 'npm run test')
 *   runUnitTests(coverageThreshold: 45)
 */

def call(Map config = [:]) {
    def statusContext = config.statusContext ?: 'jenkins/unit-tests'
    def coverageThreshold = config.coverageThreshold ?: 45
    def skipCheckout = config.skipCheckout ?: false

    // Ensure source code is present (runners don't share filesystems)
    if (!skipCheckout) {
        checkout scm
    }

    // Install dependencies if needed
    installDependencies()

    // Jest configuration - platform-aware maxWorkers
    // Windows: maxWorkers=1 due to better-sqlite3 concurrency
    // Linux: maxWorkers=50% for parallel execution
    echo "✓ Using Jest with platform-aware configuration"
    def isWindows = isUnix() ? false : true
    def testCommand = config.testCommand ?: (isWindows ?
        'npm run test -- --coverage --bail --forceExit --detectOpenHandles --maxWorkers=1' :
        'npm run test -- --coverage --bail --forceExit --detectOpenHandles --maxWorkers=50%')
    def coverageDir = config.coverageDir ?: 'coverage'

    // Report pending status
    githubStatusReporter(
        status: 'pending',
        context: statusContext,
        description: 'Unit tests running'
    )

    try {
        // Clean previous results
        sh 'rm -rf coverage'

        // Run unit tests with coverage
        echo "Running: ${testCommand}"
        def testResult = sh(script: testCommand, returnStatus: true)

        if (testResult != 0) {
            echo "⚠ Unit tests had failures (exit code: ${testResult})"
            // Mark as unstable instead of failing
            unstable(message: "Unit tests have failures - investigation needed")
            // Report failure status to GitHub but don't fail the build
            githubStatusReporter(
                status: 'failure',
                context: statusContext,
                description: 'Unit tests failed (non-blocking)'
            )
            return  // Skip coverage check since tests failed
        }

        // Check coverage threshold (only if coverage was generated)
        script {
            def coverageSummaryFile = 'coverage/coverage-summary.json'

            if (fileExists(coverageSummaryFile)) {
                echo "Coverage report generated at ${coverageSummaryFile}"

                def coverageReport = readFile(coverageSummaryFile)
                def coverage = readJSON(text: coverageReport)
                def lineCoverage = coverage.total.lines.pct

                echo "Line coverage: ${lineCoverage}%"

                if (lineCoverage < coverageThreshold) {
                    unstable("Coverage ${lineCoverage}% below ${coverageThreshold}% threshold")
                    githubStatusReporter(
                        status: 'failure',
                        context: statusContext,
                        description: "Coverage ${lineCoverage}% below threshold"
                    )
                    return
                }
            } else {
                echo "⚠ Warning: Coverage summary file not found at ${coverageSummaryFile}"
                echo "This may indicate test failures prevented coverage generation"
                echo "Check test output above for failures"
            }
        }

        // Report success
        githubStatusReporter(
            status: 'success',
            context: statusContext,
            description: 'Unit tests passed'
        )

    } catch (Exception e) {
        // Report failure but don't fail the build
        echo "✗ Unit tests stage encountered an error: ${e.message}"
        unstable(message: "Unit tests stage had errors - investigation needed")
        githubStatusReporter(
            status: 'failure',
            context: statusContext,
            description: 'Unit tests failed (non-blocking)'
        )
        // Don't throw - let the build continue

    } finally {
        // Always publish reports (even if tests fail)
        publishReports(
            junit: true,
            coverage: true,
            coverageDir: coverageDir
        )
    }
}

return this
