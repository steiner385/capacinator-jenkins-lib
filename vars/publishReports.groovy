#!/usr/bin/env groovy
/**
 * Report Publishing Utility
 * Publishes JUnit, HTML, and Playwright reports
 *
 * Features:
 * - Conditional publishing based on config
 * - Archives artifacts
 * - Handles missing reports gracefully
 *
 * Usage:
 *   publishReports(junit: true)
 *   publishReports(playwright: true)
 *   publishReports(coverage: true, coverageDir: 'coverage')
 */

def call(Map config = [:]) {
    // JUnit test results
    if (config.junit) {
        def junitPattern = config.junitPattern ?: '**/junit.xml'
        junit testResults: junitPattern, allowEmptyResults: true
    }

    // Playwright HTML report
    if (config.playwright) {
        def playwrightDir = config.playwrightDir ?: 'playwright-report'
        archiveArtifacts artifacts: "${playwrightDir}/**", allowEmptyArchive: true
        archiveArtifacts artifacts: 'test-results/**', allowEmptyArchive: true

        // Only publish HTML report if playwright-report directory exists
        if (fileExists(playwrightDir)) {
            // Try to publish HTML report if plugin is available
            try {
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: playwrightDir,
                    reportFiles: 'index.html',
                    reportName: config.playwrightReportName ?: 'Playwright Report'
                ])
            } catch (NoSuchMethodError e) {
                echo "WARNING: HTML Publisher plugin not installed - skipping Playwright HTML report"
            }
        } else {
            echo "INFO: Playwright report directory '${playwrightDir}' does not exist - skipping HTML report publishing"
        }
    }

    // Coverage HTML report
    if (config.coverage) {
        def coverageDir = config.coverageDir ?: 'coverage'

        // Only archive and publish if coverage directory exists
        if (fileExists(coverageDir)) {
            archiveArtifacts artifacts: "${coverageDir}/**", allowEmptyArchive: true
            // Try to publish HTML report if plugin is available
            try {
                publishHTML(target: [
                    allowMissing: true,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: coverageDir,
                    reportFiles: 'index.html',
                    reportName: config.coverageReportName ?: 'Coverage Report'
                ])
            } catch (NoSuchMethodError e) {
                echo "WARNING: HTML Publisher plugin not installed - skipping Coverage HTML report"
            }
        } else {
            echo "INFO: Coverage directory '${coverageDir}' does not exist - skipping HTML report publishing"
        }
    }

    // Custom artifacts
    if (config.artifacts) {
        config.artifacts.each { artifact ->
            archiveArtifacts artifacts: artifact, allowEmptyArchive: true
        }
    }
}

/**
 * Publish unit test reports
 */
def unitTestReports(Map config = [:]) {
    call([
        junit: true,
        coverage: true,
        coverageDir: config.coverageDir ?: 'coverage'
    ])
}

/**
 * Publish E2E test reports
 */
def e2eTestReports(Map config = [:]) {
    call([
        junit: config.junit ?: false,
        playwright: true
    ])
}

return this
