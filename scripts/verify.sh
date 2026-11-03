#!/usr/bin/env bash
# Build, self-test and in-game test Stonecutter nodes, recording results in port-status.tsv.
#
# For each node:
#   build     ./gradlew :<node>:build
#   selftest  the self-test server runs the anvil scenarios, then stays up (-Panvilfix.host=true)
#   ingame    the real client starts, connects to that server and uses an anvil over the network; this doubles
#             as the launch check
#
# Usage: scripts/verify.sh [-j LANES] 1.19-fabric [1.19-forge ...]
#   -j N runs N nodes at a time; each lane has its own server port and client game directory.
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
status_file=port-status.tsv
logs=build/verify-logs
header='node\tbuild\tselftest\tingame\tscenarios\tingame checks\n'
mkdir -p "$logs"
[ -f "$status_file" ] || printf "$header" > "$status_file"

lanes=1
if [ "${1:-}" = "-j" ]; then lanes=$2; shift 2; fi

record() { # node build selftest ingame scenarios checks
    # Lanes share the status file: take a lock (mkdir is atomic) while rewriting it
    until mkdir "$status_file.lock" 2>/dev/null; do sleep 0.2; done
    { grep -v -e "^$1	" -e '^node	' "$status_file"; printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$@"; } | sort -t. -k1,1n -k2,2n -k3,3n > "$status_file.tmp"
    { printf "$header"; cat "$status_file.tmp"; } > "$status_file"
    rm "$status_file.tmp"
    rmdir "$status_file.lock"
}

# Drop what each node's run regenerates anyway, so all ~80 nodes fit on disk (KEEP_CACHES=1 to skip). Only
# per-node files go here: the shared ForgeGradle/NeoForm caches may be in use by another lane, so clear those
# between runs instead (scripts/sweep.sh does).
cleanup() { # node
    [ "${KEEP_CACHES:-0}" = 1 ] && return
    rm -rf "versions/$1/build/moddev" "versions/$1/build/neoForm" "versions/$1/build/selftest-run/world"
    # Loom keeps a copy of each Minecraft version that only this node uses
    case "$1" in *-fabric) rm -rf ~/.gradle/caches/fabric-loom/"${1%-*}" ;; esac
}

verify_node() { # node lane
    local node=$1 lane=$2
    local port=$((25599 + lane)) client_dir="$PWD/build/lanes/$lane/client"
    local props=(-Panvilfix.port="$port" -Panvilfix.clientDir="$client_dir")
    local build=fail selftest=- ingame=- scenarios= checks=
    local server_log="$logs/$node-selftest.log" client_log="$logs/$node-ingame.log"
    mkdir -p "$client_dir"
    if ./gradlew ":$node:build" "${props[@]}" > "$logs/$node-build.log" 2>&1; then
        build=pass
        # Self-test server in host mode, in the background
        ( timeout 1500 ./gradlew ":$node:runSelftest" -Panvilfix.host=true "${props[@]}" > "$server_log" 2>&1; echo "ANVILFIX_SERVER_EXIT $?" >> "$server_log" ) &
        local server=$!
        for _ in $(seq 1 400); do
            grep -q -E 'ANVILFIX_HOST_READY|ANVILFIX_SERVER_EXIT' "$server_log" 2>/dev/null && break
            sleep 3
        done
        if grep -q ANVILFIX_HOST_READY "$server_log"; then
            if timeout 900 ./gradlew ":$node:runClientSmoke" -Panvilfix.ingame=true "${props[@]}" > "$client_log" 2>&1 \
                && grep -q ANVILFIX_SMOKE_OK "$client_log"; then ingame=pass; else ingame=fail; fi
        fi
        wait "$server"
        # Forge exits 0 even after a mod loading crash, so also require the scenario result line
        if grep -q 'ANVILFIX_SERVER_EXIT 0' "$server_log" && grep -q 'ANVILFIX_SELFTEST_RESULT [0-9]* passed, 0 failed' "$server_log"; then selftest=pass; else selftest=fail; fi
        scenarios=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$server_log" | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
        checks=$(grep -o 'ANVILFIX_INGAME_RESULT.*' "$client_log" 2>/dev/null | head -1 | sed 's/ANVILFIX_INGAME_RESULT //')
    fi
    record "$node" "$build" "$selftest" "$ingame" "$scenarios" "$checks"
    cleanup "$node"
    echo "$node build=$build selftest=$selftest ingame=$ingame scenarios=[$scenarios] ingame-checks=[$checks]"
}

# Lanes take nodes from a shared queue so a slow node doesn't hold up the others
queue=build/lanes/queue
mkdir -p build/lanes
printf '%s\n' "$@" > "$queue"
next_node() {
    until mkdir "$queue.lock" 2>/dev/null; do sleep 0.2; done
    local node; node=$(head -1 "$queue"); tail -n +2 "$queue" > "$queue.tmp"; mv "$queue.tmp" "$queue"
    rmdir "$queue.lock"
    echo "$node"
}

for lane in $(seq 0 $((lanes - 1))); do
    (
        while node=$(next_node) && [ -n "$node" ]; do
            verify_node "$node" "$lane"
        done
    ) &
    sleep 20 # stagger lane start-up
done
wait
