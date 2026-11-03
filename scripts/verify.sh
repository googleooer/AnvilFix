#!/usr/bin/env bash
# Build, self-test and smoke-launch Stonecutter nodes, recording results in port-status.tsv.
# Usage: scripts/verify.sh 1.19-fabric [1.19-forge ...]
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
status_file=port-status.tsv
logs=build/verify-logs
mkdir -p "$logs"
[ -f "$status_file" ] || printf 'node\tbuild\tselftest\tlaunch\tnote\n' > "$status_file"

record() { # node build selftest launch note
    grep -v "^$1	" "$status_file" > "$status_file.tmp"; mv "$status_file.tmp" "$status_file"
    printf '%s\t%s\t%s\t%s\t%s\n' "$@" >> "$status_file"
    sort -t. -k1,1n -k2,2n -k3,3n -o "$status_file.tmp" <(tail -n +2 "$status_file")
    { printf 'node\tbuild\tselftest\tlaunch\tnote\n'; cat "$status_file.tmp"; } > "$status_file"; rm "$status_file.tmp"
}

for node in "$@"; do
    echo "=== $node"
    b=fail; t=-; l=-; note=""
    if ./gradlew ":$node:build" --no-daemon > "$logs/$node-build.log" 2>&1; then
        b=pass
        if timeout 900 ./gradlew ":$node:runSelftest" --no-daemon > "$logs/$node-selftest.log" 2>&1; then t=pass; else t=fail; fi
        note=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$logs/$node-selftest.log" | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
        if timeout 900 ./gradlew ":$node:runClientSmoke" --no-daemon > "$logs/$node-launch.log" 2>&1 && grep -q ANVILFIX_SMOKE_OK "$logs/$node-launch.log"; then l=pass; else l=fail; fi
    fi
    record "$node" "$b" "$t" "$l" "$note"
    echo "$node build=$b selftest=$t launch=$l $note"
done
