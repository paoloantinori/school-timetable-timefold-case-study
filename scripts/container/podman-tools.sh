#!/bin/bash
#
# podman-tools.sh - Safe podman operations for the planner
#
# Usage: podman-tools.sh <command> [args]
#
# Commands:
#   list                  List planner containers
#   logs [profile]        Show container logs (default: dev)
#   status [profile]      Check if container is running
#   shell [profile]       Open shell in container
#   stop-all              Stop all planner containers
#   volumes               List planner volumes
#   clean-volumes         Remove unused volumes (DANGEROUS)
#
# Options:
#   -h, --help            Show this help
#
# Security: Most operations are read-only. stop-all and clean-volumes are
#           destructive and should NOT be whitelisted without consideration.
#
# Examples:
#   podman-tools.sh list
#   podman-tools.sh logs dev
#   podman-tools.sh status dev
#

set -euo pipefail

COMMAND="${1:-help}"
shift || true

case "$COMMAND" in
    list)
        echo "=== Planner Containers ==="
        podman ps -a --filter "name=planner-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
        ;;

    logs)
        PROFILE="${1:-dev}"
        CONTAINER="planner-postgres-${PROFILE}"
        if podman ps --filter "name=${CONTAINER}" --format "{{.Names}}" | grep -q "${CONTAINER}"; then
            podman logs --tail 50 "${CONTAINER}"
        else
            echo "Container ${CONTAINER} is not running"
            exit 1
        fi
        ;;

    status)
        PROFILE="${1:-dev}"
        CONTAINER="planner-postgres-${PROFILE}"
        if podman ps --filter "name=${CONTAINER}" --format "{{.Names}}" | grep -q "${CONTAINER}"; then
            echo "RUNNING: ${CONTAINER}"
            exit 0
        else
            echo "STOPPED: ${CONTAINER}"
            exit 1
        fi
        ;;

    shell)
        PROFILE="${1:-dev}"
        CONTAINER="planner-postgres-${PROFILE}"
        exec podman exec -it "${CONTAINER}" /bin/bash
        ;;

    stop-all)
        echo "Stopping all planner containers..."
        podman ps --filter "name=planner-" --format "{{.Names}}" | while read -r container; do
            echo "Stopping $container..."
            podman stop "$container" || true
        done
        echo "Done."
        ;;

    volumes)
        echo "=== Planner Volumes ==="
        podman volume ls --filter "name=planner-" --format "table {{.Name}}\t{{.Driver}}"
        ;;

    clean-volumes)
        echo "WARNING: This will remove unused planner volumes!"
        echo "Press Ctrl+C to cancel, Enter to continue..."
        read -r
        podman volume ls --filter "name=planner-" --format "{{.Name}}" | while read -r vol; do
            echo "Checking $vol..."
            # Only remove if not in use
            if ! podman ps -a --format "{{.Names}}" | xargs -I{} podman inspect {} 2>/dev/null | grep -q "$vol"; then
                echo "Removing unused volume: $vol"
                podman volume rm "$vol" || true
            fi
        done
        ;;

    help|--help|-h)
        sed -n '/^# Usage:/,/^#$/p' "$0" | grep -v '^#$' | sed 's/^# //'
        ;;

    *)
        echo "Unknown command: $COMMAND" >&2
        echo "Commands: list, logs, status, shell, stop-all, volumes, clean-volumes" >&2
        exit 2
        ;;
esac
