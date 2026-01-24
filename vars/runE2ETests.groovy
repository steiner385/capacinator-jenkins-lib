#!/usr/bin/env groovy
/**
 * Run E2E Tests Stage
 * Executes Playwright E2E tests against local dev servers
 *
 * Features:
 * - Starts E2E servers (server + client)
 * - Waits for health check
 * - Runs Playwright tests
 * - GitHub status reporting
 * - Playwright report publishing
 *
 * Usage:
 *   runE2ETests()  // Use defaults
 *   runE2ETests(browsers: ['chromium'])
 *   runE2ETests(testCommand: 'npm run test:e2e:smoke')
 */

def call(Map config = [:]) {
    def statusContext = config.statusContext ?: 'jenkins/e2e'
    def testCommand = config.testCommand ?: 'npm run test:e2e'
    def browsers = config.browsers ?: ['chromium']
    def skipCheckout = config.skipCheckout ?: false

    // Ensure source code is present (runners don't share filesystems)
    if (!skipCheckout) {
        checkout scm
    }

    // Install dependencies if needed
    installDependencies()

    // Report pending status
    githubStatusReporter(
        status: 'pending',
        context: statusContext,
        description: 'E2E tests running'
    )

    try {
        // Clean previous results
        sh 'rm -rf playwright-report test-results'

        // Start E2E servers (server on 3110, client on 3120)
        echo "Starting E2E servers..."
        sh 'npm run e2e:start'

        // Wait for health check (server should be ready on port 3110)
        echo "Waiting for E2E servers to be ready..."
        sh '''
            echo "Waiting for server health check (max 60 seconds)..."
            for i in $(seq 1 60); do
                if curl -f -s http://localhost:3110/api/health > /dev/null 2>&1; then
                    echo "✅ Server is healthy!"
                    break
                fi
                echo "Waiting for server... $i/60"
                sleep 1
            done

            # Final check
            if ! curl -f -s http://localhost:3110/api/health > /dev/null 2>&1; then
                echo "❌ ERROR: Server failed to start within 60 seconds"
                npm run e2e:logs || true
                exit 1
            fi

            echo "Waiting for client to be ready (max 30 seconds)..."
            for i in $(seq 1 30); do
                if curl -f -s http://localhost:3120 > /dev/null 2>&1; then
                    echo "✅ Client is ready!"
                    break
                fi
                echo "Waiting for client... $i/30"
                sleep 1
            done
        '''

        // Install Playwright browsers if not cached
        echo "Installing Playwright browsers..."
        sh "npx playwright install ${browsers.join(' ')}"

        // Run Playwright tests
        echo "Running Playwright tests..."
        sh testCommand

        // Report success
        githubStatusReporter(
            status: 'success',
            context: statusContext,
            description: 'E2E tests passed'
        )

    } catch (Exception e) {
        // Show server logs for debugging
        echo "=== E2E Server Logs (last 50 lines) ==="
        sh 'npm run e2e:logs || true'

        // Report failure status to GitHub
        echo "E2E tests failed with error: ${e.message}"
        githubStatusReporter(
            status: 'failure',
            context: statusContext,
            description: 'E2E tests failed'
        )

        throw e

    } finally {
        // Always stop E2E servers
        echo "Stopping E2E servers..."
        sh 'npm run e2e:stop || true'

        // Always publish reports
        publishReports(
            junit: true,
            playwright: true
        )
    }
}

/**
 * Run E2E tests on specific browsers
 */
def withBrowsers(List browsers, Map config = [:]) {
    call(config + [browsers: browsers])
}

return this
