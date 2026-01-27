#!/usr/bin/env groovy
/**
 * Capacinator Multibranch Pipeline
 * Main pipeline entry point for Capacinator CI/CD
 *
 * Usage:
 *   @Library('capacinator-lib@main') _
 *   capacinatorMultibranchPipeline()
 */

def call(Map config = [:]) {
    pipeline {
        agent any

        environment {
            CI = 'true'
            NODE_ENV = 'test'
            NODE_OPTIONS = '--max-old-space-size=4096'
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
                        // Skip non-PR feature branches to save resources
                        def isPR = env.CHANGE_ID != null
                        def isProtected = env.BRANCH_NAME in ['main', 'develop']

                        if (!isPR && !isProtected) {
                            echo "Skipping build for non-PR feature branch: ${env.BRANCH_NAME}"
                            currentBuild.result = 'NOT_BUILT'
                            currentBuild.displayName = "#${env.BUILD_NUMBER} - ${env.BRANCH_NAME} (skipped)"
                            return
                        }

                        // Enable Windows builds only for main branch
                        env.BUILD_WINDOWS = (env.BRANCH_NAME == 'main') ? 'true' : 'false'
                        echo "Windows builds: ${env.BUILD_WINDOWS}"

                        githubStatusReporter(
                            status: 'pending',
                            context: 'jenkins/ci',
                            description: 'Build started'
                        )
                    }
                    checkout scm
                }
            }

            stage('Install Dependencies') {
                when { expression { currentBuild.result != 'NOT_BUILT' } }
                steps {
                    installDependencies()
                }
            }

            stage('Lint + Type Check') {
                when { expression { currentBuild.result != 'NOT_BUILT' } }
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
                when { expression { currentBuild.result != 'NOT_BUILT' } }
                steps {
                    runUnitTests(
                        testCommand: 'npm run test:unit',
                        coverageThreshold: 45,
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
                    expression { currentBuild.result != 'NOT_BUILT' }
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
                script {
                    if (currentBuild.result != 'NOT_BUILT') {
                        publishReports(
                            junit: true,
                            playwright: fileExists('playwright-report'),
                            coverage: fileExists('coverage')
                        )
                    }
                }
            }
            success {
                script {
                    if (currentBuild.result != 'NOT_BUILT') {
                        githubStatusReporter(
                            status: 'success',
                            context: 'jenkins/ci',
                            description: 'Build succeeded'
                        )
                    }
                }
            }
            failure {
                script {
                    if (currentBuild.result != 'NOT_BUILT') {
                        githubStatusReporter(
                            status: 'failure',
                            context: 'jenkins/ci',
                            description: 'Build failed'
                        )
                    }
                }
            }
            cleanup {
                cleanWs()
            }
        }
    }
}

return this
