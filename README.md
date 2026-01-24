# capacinator-jenkins-lib

Jenkins CI/CD configuration for Capacinator using the "decoupled CI/CD" pattern.

## Overview

This repository contains the real Jenkins pipeline configuration for Capacinator, while the main Capacinator repository contains only a minimal stub Jenkinsfile.

## Architecture

- **config/jenkins.yaml** - Jenkins Configuration as Code (JCasC) additions for Capacinator
- **vars/*.groovy** - Shared library functions (pipeline helpers)
- **Jenkinsfile.multibranch** - Main multibranch pipeline definition
- **Jenkinsfile.nightly** - Nightly comprehensive build (future)

## Pipeline Stages

1. **Initialize** - Branch filtering, checkout, GitHub status reporting
2. **Install Dependencies** - npm ci with caching
3. **Lint + Type Check** - ESLint + TypeScript validation
4. **Unit Tests** - Jest with coverage reporting (threshold: 45%)
5. **E2E Tests** - Playwright tests (main/develop/PRs only)
6. **Build** - Client, server, and Electron Linux builds (main/develop only)

## Build Agents

- **runner-1, runner-2**: Unit tests (2 executors each)
- **runner-3, runner-4**: E2E tests (1 executor each, Playwright)
- **runner-5**: Electron builds (1 executor)

All agents: Linux + Docker + Node.js 20+

## Usage

The Capacinator repository contains a stub Jenkinsfile that loads this library:

```groovy
library identifier: 'capacinator-lib@main',
    retriever: modernSCM([...])

capacinatorMultibranchPipeline()
```

## Integration

This pipeline configuration is registered in the main Jenkins server at jenkins.kindash.com via the kindash-jenkins-lib JCasC configuration.

## Related Repositories

- [Capacinator](https://github.com/steiner385/Capacinator) - Main application repo
- [kindash-jenkins-lib](https://github.com/steiner385/kindash-jenkins-lib) - Shared Jenkins infrastructure

## Pattern

This follows the same "decoupled CI/CD" pattern as:
- [KinDash](https://github.com/steiner385/KinDash) → [kindash-jenkins-lib](https://github.com/steiner385/kindash-jenkins-lib)
- [uniteDiscord](https://github.com/steiner385/uniteDiscord) → [unitediscord-jenkins-lib](https://github.com/steiner385/unitediscord-jenkins-lib)
