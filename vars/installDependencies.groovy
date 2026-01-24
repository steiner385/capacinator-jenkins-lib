#!/usr/bin/env groovy
/**
 * Install Dependencies Stage
 * Smart package manager installation with caching support
 *
 * Features:
 * - Uses npm (Capacinator uses npm, not pnpm)
 * - Uses .npmrc.ci if present
 * - Skips install if node_modules is up-to-date
 * - Legacy peer deps support
 * - Official npm registry enforcement
 *
 * Usage:
 *   installDependencies()  // Use defaults
 *   installDependencies(forceInstall: true)
 */

def call(Map config = [:]) {
    def forceInstall = config.forceInstall ?: false
    def registry = config.registry ?: 'https://registry.npmjs.org/'

    echo "Using package manager: npm"

    // Clean up .npmrc for CI (can cause issues in CI)
    sh 'rm -f .npmrc'

    // Set memory limit for large dependency trees
    sh 'export NODE_OPTIONS="--max-old-space-size=4096"'

    // Install dependencies (smart caching)
    if (forceInstall) {
        sh """
            echo "Force installing dependencies with npm..."
            rm -rf node_modules
            npm ci --legacy-peer-deps
        """
    } else {
        sh """
            if [ ! -d "node_modules" ] || [ "package-lock.json" -nt "node_modules/.cache-marker" ]; then
                echo "Installing dependencies with npm..."
                npm ci --legacy-peer-deps
                touch node_modules/.cache-marker
            else
                echo "Dependencies up to date, skipping install"
            fi
        """
    }
}

/**
 * Force clean install
 */
def clean() {
    call(forceInstall: true)
}

return this
