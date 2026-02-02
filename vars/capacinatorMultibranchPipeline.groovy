#!/usr/bin/env groovy
/**
 * Capacinator Multibranch Pipeline
 * Main pipeline entry point for Capacinator CI/CD
 *
 * Usage:
 *   @Library('capacinator-lib@main') _
 *   capacinatorMultibranchPipeline()
 *
 * Branch Filtering:
 *   - Only PRs and protected branches (main, develop, staging, deploy/*) are built
 *   - Other branch pushes are skipped immediately without allocating an executor
 *   - To prevent build entries entirely, configure Branch Source in Jenkins UI:
 *     Configure > Branch Sources > GitHub > Behaviors > Filter by name (with regular expression)
 *     Include: (main|develop|staging|deploy/.*)
 */

def call(Map config = [:]) {
    // Pre-flight branch check (runs without allocating an agent)
    def isPR = env.CHANGE_ID != null
    def isProtectedBranch = env.BRANCH_NAME in ['main', 'develop', 'staging'] || env.BRANCH_NAME?.startsWith('deploy/')

    if (!isPR && !isProtectedBranch) {
        echo "⏭️ Skipping build for branch: ${env.BRANCH_NAME}"
        echo "This branch will be built when a PR is created."
        echo ""
        echo "To prevent these build entries entirely, configure Branch Source filtering in Jenkins:"
        echo "  Configure > Branch Sources > Behaviors > Filter by name (with regular expression)"
        echo "  Include: (main|develop|staging|deploy/.*)"
        currentBuild.result = 'NOT_BUILT'
        currentBuild.description = "Skipped: feature branch (no PR)"
        return
    }

    pipeline {
        agent any

        environment {
            CI = 'true'
            NODE_ENV = 'test'
            NODE_OPTIONS = '--max-old-space-size=4096'
            // GitHub feature environment variables for testing
            ENCRYPTION_KEY = 'IwNwIe9IIR+3HKINAJNGLG10dw1gvaGAS1liA7SaXjA='
            GITHUB_CLIENT_ID = 'test-client-id'
            GITHUB_CLIENT_SECRET = 'test-client-secret'
            GITHUB_CALLBACK_URL = 'http://localhost:3131/api/auth/github/callback'
        }

        options {
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '20'))
            timeout(time: 60, unit: 'MINUTES')
            disableConcurrentBuilds(abortPrevious: true)
        }

        stages {
            stage('Initialize') {
                steps {
                    script {
                        // Determine build type for logging and status reporting
                        def buildType = env.CHANGE_ID ? "PR #${env.CHANGE_ID}" : "Branch: ${env.BRANCH_NAME}"
                        echo "=== Multi-Branch Build ==="
                        echo "Build type: ${buildType}"
                        if (env.CHANGE_ID) {
                            echo "PR Title: ${env.CHANGE_TITLE ?: 'N/A'}"
                            echo "PR Author: ${env.CHANGE_AUTHOR ?: 'N/A'}"
                            echo "Target Branch: ${env.CHANGE_TARGET ?: 'N/A'}"
                        }
                        echo "=========================="

                        // Enable Windows builds only for main branch
                        env.BUILD_WINDOWS = (env.BRANCH_NAME == 'main') ? 'true' : 'false'
                        echo "Windows builds: ${env.BUILD_WINDOWS}"

                        githubStatusReporter(
                            status: 'pending',
                            context: 'jenkins/ci',
                            description: "Build started for ${buildType}"
                        )
                    }
                    checkout scm
                }
            }

            stage('Install Dependencies') {
                steps {
                    installDependencies()
                }
            }

            stage('Lint + Type Check') {
                steps {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        runLintChecks(
                            lintCommand: 'npm run lint',
                            typeCheckCommand: 'npm run typecheck',
                            skipCheckout: true
                        )
                    }
                }
            }

            stage('Unit Tests') {
                steps {
                    runUnitTests(
                        testCommand: 'npm run test:unit',
                        coverageThreshold: 70,
                        skipCheckout: true
                    )
                }
            }

            // E2E Tests - TEMPORARILY DISABLED
            // TODO: Re-enable after debugging E2E infrastructure
            // stage('E2E Tests') {
            //     when {
            //         expression { currentBuild.result != 'NOT_BUILT' }
            //         anyOf {
            //             branch 'main'
            //             branch 'develop'
            //             changeRequest target: 'main'
            //             changeRequest target: 'develop'
            //         }
            //     }
            //     agent {
            //         label 'e2e playwright'
            //     }
            //     options {
            //         timeout(time: 15, unit: 'MINUTES')
            //     }
            //     steps {
            //         runE2ETests(
            //             browsers: ['chromium']
            //         )
            //     }
            // }

            stage('Build') {
                when {
                    anyOf {
                        branch 'main'
                        branch 'develop'
                        changeRequest target: 'main'
                        changeRequest target: 'develop'
                    }
                }
                stages {
                    stage('Build Application') {
                        steps {
                            buildProject(
                                buildCommand: 'npm run build:server && npm run build:client'
                            )
                        }
                    }

                    stage('Build Electron') {
                        steps {
                            script {
                                def platforms = ['linux']
                                if (env.BUILD_WINDOWS == 'true') {
                                    platforms << 'win'
                                    echo "Building Electron for Linux and Windows"
                                } else {
                                    echo "Building Electron for Linux only (set BUILD_WINDOWS=true to enable Windows)"
                                }

                                buildElectronApp(
                                    platforms: platforms
                                )
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                publishReports(
                    junit: true,
                    playwright: fileExists('playwright-report'),
                    coverage: fileExists('coverage')
                )
            }
            success {
                script {
                    def buildType = env.CHANGE_ID ? "PR #${env.CHANGE_ID}" : env.BRANCH_NAME
                    githubStatusReporter(
                        status: 'success',
                        context: 'jenkins/ci',
                        description: "Build succeeded for ${buildType}"
                    )
                }
            }
            failure {
                script {
                    def buildType = env.CHANGE_ID ? "PR #${env.CHANGE_ID}" : env.BRANCH_NAME
                    githubStatusReporter(
                        status: 'failure',
                        context: 'jenkins/ci',
                        description: "Build failed for ${buildType}"
                    )
                }
            }
            cleanup {
                cleanWs()
            }
        }
    }
}

return this
