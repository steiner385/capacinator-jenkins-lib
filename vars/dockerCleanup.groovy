#!/usr/bin/env groovy
/**
 * Docker Cleanup Utility
 * Stops Docker containers and kills processes on specified ports
 *
 * Features:
 * - Docker Compose v1/v2 fallback support
 * - Port cleanup via lsof/kill
 * - Container cleanup by project name pattern
 * - Optional lockfile cleanup
 *
 * Usage:
 *   dockerCleanup()  // Use defaults
 *   dockerCleanup(composeFile: 'docker-compose.test.yml', ports: [3131, 5432])
 *   dockerCleanup(cleanLockfiles: true)
 *   dockerCleanup(projectPattern: 'capacinator-')  // Clean all containers matching pattern
 */

def call(Map config = [:]) {
    def composeFile = config.composeFile ?: 'docker-compose.test.yml'
    def ports = config.ports ?: pipelineHelpers.getServicePorts()
    def cleanLockfiles = config.cleanLockfiles != null ? config.cleanLockfiles : true
    def silent = config.silent ?: false
    def projectPattern = config.projectPattern ?: ''

    def portsString = ports.join(' ')
    def lockfileCleanup = cleanLockfiles ? 'rm -f .e2e-port.json .e2e-jwt-token.json .dev-server-pid .e2e-services-pid' : ''

    if (!silent) {
        echo "Cleaning up Docker and ports..."
    }

    // Stop Docker containers (try v2 first, fall back to v1)
    sh """
        (docker compose -f ${composeFile} down -v --remove-orphans 2>/dev/null || \
         docker-compose -f ${composeFile} down -v --remove-orphans 2>/dev/null) || true
    """

    // Clean containers by project pattern if specified
    if (projectPattern) {
        cleanContainersByPattern(projectPattern, silent)
    }

    // Kill processes on specified ports
    sh """
        # Try lsof first, fall back to fuser, then ss+kill
        for port in ${portsString}; do
            (lsof -ti :\$port 2>/dev/null || fuser \$port/tcp 2>/dev/null || ss -tlnp 2>/dev/null | grep ":\$port " | awk '{print \$NF}' | grep -oP 'pid=\\K[0-9]+') | xargs -r kill -9 2>/dev/null || true
        done

        # Clean lockfiles if requested
        ${lockfileCleanup}
    """

    if (!silent) {
        echo "Cleanup complete"
    }
}

/**
 * Pre-test cleanup (more verbose)
 */
def preCleanup(Map config = [:]) {
    echo "Pre-test cleanup..."
    call(config + [silent: false])
}

/**
 * Post-test cleanup (quieter)
 */
def postCleanup(Map config = [:]) {
    echo "Post-test cleanup..."
    call(config + [silent: false])
}

/**
 * Clean all containers matching a project name pattern
 * Handles containers created by docker compose with COMPOSE_PROJECT_NAME
 *
 * @param pattern The pattern to match (e.g., 'capacinator-' matches 'capacinator-postgres-1')
 * @param silent If true, suppress output
 */
def cleanContainersByPattern(String pattern, boolean silent = false) {
    if (!silent) {
        echo "Cleaning containers matching pattern: ${pattern}*"
    }

    sh """
        # Remove all containers matching the pattern
        CONTAINERS=\$(docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E '^${pattern}' || true)
        if [ -n "\$CONTAINERS" ]; then
            echo "Removing containers: \$CONTAINERS"
            echo "\$CONTAINERS" | xargs -r docker rm -f 2>/dev/null || true
        fi

        # Remove networks matching the pattern
        NETWORKS=\$(docker network ls --format '{{.Name}}' 2>/dev/null | grep -E '^${pattern}' || true)
        if [ -n "\$NETWORKS" ]; then
            echo "Removing networks: \$NETWORKS"
            echo "\$NETWORKS" | xargs -r docker network rm 2>/dev/null || true
        fi

        # Remove volumes matching the pattern (be careful - only unnamed/test volumes)
        VOLUMES=\$(docker volume ls --format '{{.Name}}' 2>/dev/null | grep -E '^${pattern}' || true)
        if [ -n "\$VOLUMES" ]; then
            echo "Removing volumes: \$VOLUMES"
            echo "\$VOLUMES" | xargs -r docker volume rm 2>/dev/null || true
        fi
    """
}

/**
 * Clean stale Docker networks that might prevent new network creation
 */
def cleanStaleNetworks() {
    echo "Cleaning stale Docker networks..."

    sh '''
        # Remove networks with 'e2e' or 'test' in the name
        docker network ls --format '{{.Name}}' 2>/dev/null | grep -E 'e2e|test' | xargs -r docker network rm 2>/dev/null || true

        # Prune unused networks
        docker network prune -f 2>/dev/null || true
    '''
}

return this
