#!/usr/bin/env groovy
/**
 * Run Lint & Type Checks Stage
 * Executes ESLint and TypeScript type checking with GitHub status reporting
 *
 * Features:
 * - GitHub status reporting (pending/success/failure)
 * - ESLint for code quality
 * - TypeScript type checking
 *
 * Usage:
 *   runLintChecks()  // Use defaults (npm run lint + npm run typecheck)
 *   runLintChecks(lintCommand: 'npm run lint:fix')
 *   runLintChecks(skipTypeCheck: true)
 */

def call(Map config = [:]) {
    def statusContext = config.statusContext ?: 'jenkins/lint'
    def lintCommand = config.lintCommand ?: 'npm run lint'
    def typeCheckCommand = config.typeCheckCommand ?: 'npm run typecheck'
    def skipLint = config.skipLint ?: false
    def skipTypeCheck = config.skipTypeCheck ?: false
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
        description: 'Linting in progress'
    )

    try {
        // Run lint
        if (!skipLint) {
            echo "Running ESLint..."
            sh lintCommand
        }

        // Run type check
        if (!skipTypeCheck) {
            echo "Running TypeScript type check..."
            sh typeCheckCommand
        }

        // Report success
        githubStatusReporter(
            status: 'success',
            context: statusContext,
            description: 'Lint passed'
        )

    } catch (Exception e) {
        // Report failure
        githubStatusReporter(
            status: 'failure',
            context: statusContext,
            description: 'Lint failed'
        )
        throw e
    }
}

return this
