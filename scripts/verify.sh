#!/usr/bin/env bash
# Build, self-test and in-game test Stonecutter nodes, recording results in port-status.tsv.
#
# For each node:
#   build     ./gradlew :<node>:build
#   selftest  the self-test server runs the anvil scenarios, then stays up (-Panvilfix.host=true)
#   ingame    the real client starts, connects to that server and uses an anvil over the network; this doubles
#             as the launch check
#
# Usage: scripts/verify.sh 1.19-fabric [1.19-forge ...]
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
status_file=port-status.tsv
logs=build/verify-logs
header='node\tbuild\tselftest\tingame\tscenarios\tingame checks\n'
mkdir -p "$logs"
[ -f "$status_file" ] || printf "$header" > "$status_file"

record() { # node build selftest ingame scenarios checks
    { grep -v -e "^$1	" -e '^node	' "$status_file"; printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$@"; } | sort -t. -k1,1n -k2,2n -k3,3n > "$status_file.tmp"
    { printf "$header"; cat "$status_file.tmp"; } > "$status_file"
    rm "$status_file.tmp"
}

for node in "$@"; do
    echo "=== $node"
    build=fail selftest=- ingame=- scenarios= checks=
    server_log="$logs/$node-selftest.log" client_log="$logs/$node-ingame.log"
    if ./gradlew ":$node:build" --no-daemon > "$logs/$node-build.log" 2>&1; then
        build=pass
        # Self-test server in host mode, in the background
        ( timeout 1500 ./gradlew ":$node:runSelftest" -Panvilfix.host=true --no-daemon > "$server_log" 2>&1; echo "ANVILFIX_SERVER_EXIT $?" >> "$server_log" ) &
        server=$!
        for _ in $(seq 1 400); do
            grep -q -E 'ANVILFIX_HOST_READY|ANVILFIX_SERVER_EXIT' "$server_log" 2>/dev/null && break
            sleep 3
        done
        if grep -q ANVILFIX_HOST_READY "$server_log"; then
            # Separate project cache so this build doesn't wait on the server build's locks
            if timeout 900 ./gradlew ":$node:runClientSmoke" -Panvilfix.ingame=true --no-daemon --project-cache-dir build/client-gradle-cache > "$client_log" 2>&1 \
                && grep -q ANVILFIX_SMOKE_OK "$client_log"; then ingame=pass; else ingame=fail; fi
        fi
        wait "$server"
        if grep -q 'ANVILFIX_SERVER_EXIT 0' "$server_log"; then selftest=pass; else selftest=fail; fi
        scenarios=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$server_log" | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
        checks=$(grep -o 'ANVILFIX_INGAME_RESULT.*' "$client_log" 2>/dev/null | head -1 | sed 's/ANVILFIX_INGAME_RESULT //')
    fi
    record "$node" "$build" "$selftest" "$ingame" "$scenarios" "$checks"
    echo "$node build=$build selftest=$selftest ingame=$ingame scenarios=[$scenarios] ingame-checks=[$checks]"
done
