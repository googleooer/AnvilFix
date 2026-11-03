#!/usr/bin/env bash
# Build PORT_STATUS.md from the latest verify logs of every node (build/verify-logs), so results survive a sweep
# being stopped and restarted. Each cell is build / self-test / in-game (the in-game test is also the launch check).
set -u
cd "$(dirname "$0")/.."
logs=build/verify-logs

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
    if [ "$b $t $g" = "pass pass pass" ]; then echo "pass"; else echo "build $b, test $t, launch $g"; fi
}

{
    echo "# Port status"
    echo
    echo "Each cell is the latest run of \`scripts/verify.sh\`: build, the 35 self-test scenarios on a dedicated server, and"
    echo "the in-game test (the real client launches, connects and uses an anvil; 16 checks). \"pass\" means all three passed."
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
    echo "| Node | Build | Self-test | In-game | Scenarios | In-game checks | Last run |"
    echo "| --- | --- | --- | --- | --- | --- | --- |"
    for n in $(ls versions | sort -t. -k1,1n -k2,2n -k3,3n); do
        read -r b t g when <<< "$(result "$n")"
        s=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$logs/$n-selftest.log" 2>/dev/null | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
        c=$(grep -o 'ANVILFIX_INGAME_RESULT.*' "$logs/$n-ingame.log" 2>/dev/null | head -1 | sed 's/ANVILFIX_INGAME_RESULT //')
        echo "| $n | $b | $t | $g | ${s:--} | ${c:--} | $when |"
    done
} > PORT_STATUS.md
echo "wrote PORT_STATUS.md"
