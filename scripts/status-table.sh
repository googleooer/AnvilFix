#!/usr/bin/env bash
# Build PORT_STATUS.md from the latest verify logs of every node (build/verify-logs), so results survive a sweep
# being stopped and restarted. Each cell is build / self-test / in-game (the in-game test is also the launch check).
set -u
cd "$(dirname "$0")/.."
logs=build/verify-logs
prod=build/prod-test/results.tsv

prod_result() { # node -> pass/fail/-
    local r; r=$(awk -F'\t' -v n="$1" '$1 == n { r = $2 } END { print r }' "$prod" 2>/dev/null)
    echo "${r:--}"
}

result() { # node -> "build selftest ingame when"
    local n=$1 b=- t=- g=- when=-
    if [ -f "$logs/$n-build.log" ]; then
        grep -q "BUILD SUCCESSFUL" "$logs/$n-build.log" && b=pass || b=fail
        when=$(date -r "$logs/$n-build.log" "+%Y-%m-%d %H:%M")
    fi
    if [ -f "$logs/$n-selftest.log" ]; then
        if grep -q 'ANVILFIX_SERVER_EXIT 0' "$logs/$n-selftest.log" && grep -q 'ANVILFIX_SELFTEST_RESULT [0-9]* passed, 0 failed' "$logs/$n-selftest.log"; then t=pass; else t=fail; fi
    fi
    if [ -f "$logs/$n-ingame.log" ]; then
        grep -q ANVILFIX_SMOKE_OK "$logs/$n-ingame.log" && g=pass || g=fail
    fi
    echo "$b $t $g $when"
}

cell() { # node
    [ -d "versions/$1" ] || { echo "n/a"; return; }
    read -r b t g _ <<< "$(result "$1")"
    local p; p=$(prod_result "$1")
    if [ "$b $t $g $p" = "pass pass pass pass" ]; then echo "pass"; else echo "build $b, test $t, launch $g, real install $p"; fi
}

{
    echo "# Port status"
    echo
    echo "Each cell combines the latest \`scripts/verify.sh\` run (build, the 35 self-test scenarios on a dev server, and the"
    echo "in-game test: the real client launches, connects and uses an anvil, 16 checks) with \`scripts/prod-test.sh\` (the"
    echo "release jar on a server installed with the loader's own installer, running the 35 scenarios). \"pass\" means all four passed."
    echo "\"n/a\" means the loader has no release for that Minecraft version."
    echo
    echo "| Minecraft | Fabric | Forge | NeoForge |"
    echo "| --- | --- | --- | --- |"
    for mc in $(ls versions | sed 's/-[a-z]*$//' | sort -u | sort -t. -k1,1n -k2,2n -k3,3n); do
        echo "| $mc | $(cell "$mc-fabric") | $(cell "$mc-forge") | $(cell "$mc-neoforge") |"
    done
    echo
    echo "## Runs"
    echo
    echo "| Node | Build | Self-test | In-game | Real install | Scenarios | In-game checks | Last run |"
    echo "| --- | --- | --- | --- | --- | --- | --- | --- |"
    for n in $(ls versions | sort -t. -k1,1n -k2,2n -k3,3n); do
        read -r b t g when <<< "$(result "$n")"
        s=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$logs/$n-selftest.log" 2>/dev/null | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
        c=$(grep -o 'ANVILFIX_INGAME_RESULT.*' "$logs/$n-ingame.log" 2>/dev/null | head -1 | sed 's/ANVILFIX_INGAME_RESULT //')
        echo "| $n | $b | $t | $g | $(prod_result "$n") | ${s:--} | ${c:--} | $when |"
    done
} > PORT_STATUS.md
echo "wrote PORT_STATUS.md"
