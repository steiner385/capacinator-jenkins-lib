#!/usr/bin/env groovy
/**
 * Build Electron App Stage
 * Builds Electron desktop application for specified platforms
 *
 * Features:
 * - Platform-specific builds (Linux, Windows, macOS)
 * - Artifact archival
 * - Build caching support
 *
 * Usage:
 *   buildElectronApp()  // Defaults to Linux only
 *   buildElectronApp(platforms: ['linux'])
 *   buildElectronApp(platforms: ['linux', 'win', 'mac'])
 */

def call(Map config = [:]) {
    def platforms = config.platforms ?: ['linux']
    def skipCheckout = config.skipCheckout ?: false

    // Ensure source code is present (runners don't share filesystems)
    if (!skipCheckout) {
        checkout scm
    }

    // Install dependencies if needed
    installDependencies()

    // Build server and client first (required for Electron)
    echo "Building server and client..."
    sh 'npm run build:server'
    sh 'npm run build:client'

    // Build Electron for each platform
    platforms.each { platform ->
        echo "Building Electron app for ${platform}..."

        def buildCommand = ''
        def artifacts = ''

        switch (platform) {
            case 'linux':
                buildCommand = 'npm run dist:linux'
                artifacts = 'dist-electron/*.AppImage,dist-electron/*.deb,dist-electron/*.rpm'
                break
            case 'win':
                buildCommand = 'npm run dist:win'
                artifacts = 'dist-electron/*.exe'
                break
            case 'mac':
                buildCommand = 'npm run dist:mac'
                artifacts = 'dist-electron/*.dmg'
                break
            default:
                error "Unknown platform: ${platform}"
        }

        // Run build
        sh buildCommand

        // Archive artifacts
        if (artifacts) {
            archiveArtifacts artifacts: artifacts, allowEmptyArchive: true
        }

        echo "✅ Electron build completed for ${platform}"
    }

    // Archive build artifacts
    archiveArtifacts artifacts: 'dist/**,dist-client/**,dist-electron/**', allowEmptyArchive: true
}

/**
 * Build for all platforms
 */
def allPlatforms(Map config = [:]) {
    call(config + [platforms: ['linux', 'win', 'mac']])
}

/**
 * Build for Linux only
 */
def linux(Map config = [:]) {
    call(config + [platforms: ['linux']])
}

return this
