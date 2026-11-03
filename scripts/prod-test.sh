#!/usr/bin/env bash
# Test the release jars the way players run them: install a real server with each loader's own installer, drop in
# the release jar plus a production build of the test mod, and run the self-test scenarios. This catches problems
# dev runs can't see (remapped jars, refmaps, manifests, metadata). Results go to build/prod-test/results.tsv.
#
# Usage: scripts/prod-test.sh [-j LANES] <node>...
set -u
cd "$(dirname "$0")/.."
root=$PWD
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
work=build/prod-test
cache=$work/cache
mkdir -p "$work" "$cache"
results=$work/results.tsv
fabric_installer=1.1.0

lanes=1
if [ "${1:-}" = "-j" ]; then lanes=$2; shift 2; fi

# Players install the bundled Fabric API jar from Modrinth (the Maven "fabric-api" artifact is an empty parent)
fabric_api_url() { # version number
    [ -s "$cache/fabric-api-versions.json" ] || curl -sfL -o "$cache/fabric-api-versions.json" "https://api.modrinth.com/v2/project/fabric-api/version"
    python3 -I -c "
import json, sys
for v in json.load(open(sys.argv[1])):
    if v['version_number'] == sys.argv[2]:
        # old releases don't flag a primary file; their only file is the mod
        print(next((f for f in v['files'] if f['primary']), v['files'][0])['url']); break
" "$cache/fabric-api-versions.json" "$1"
}

prop() { grep "^$2=" "versions/$1/gradle.properties" | cut -d= -f2-; }
fetch() { # url file
    [ -s "$2" ] || curl -sfL -o "$2.tmp" "$1" && mv "$2.tmp" "$2" 2>/dev/null
    [ -s "$2" ]
}

test_node() { # node lane
    local node=$1 port=$((25700 + $2)) mc=${1%-*} loader=${1##*-}
    local dir="$work/$node" log="$work/$node.log" java
    java="$(/usr/libexec/java_home -v "$(prop "$node" java_version)")/bin/java"
    rm -rf "$dir"; mkdir -p "$dir/mods"

    # The release jar and the test mod built for production
    local task=selftestJar
    [ "$loader" = fabric ] && [ -z "$(echo "$mc" | grep '^26')" ] && task=remapSelftestJar
    [ "$loader" = forge ] && [ "$(printf '%s\n' "$mc" 1.20.1 | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)" = 1.20.1 ] && task=reobfSelftestJar
    if ! ./gradlew ":$node:build" ":$node:$task" -q > "$log" 2>&1; then echo "$node	fail	build" >> "$results"; echo "$node build failed"; return; fi
    cp "$(ls versions/$node/build/libs/*.jar | grep -v -E -- '-(sources|dev|selftest|selftest-dev)\.jar$' | head -1)" "$dir/mods/"
    cp "versions/$node/build/libs/"*-selftest.jar "$dir/mods/"

    # The loader's own server install
    local launch=()
    case "$loader" in
        fabric)
            local fl; fl=$(prop "$node" fabric_loader)
            fetch "https://meta.fabricmc.net/v2/versions/loader/$mc/$fl/$fabric_installer/server/jar" "$dir/fabric-server-launch.jar" \
                && fetch "$(fabric_api_url "$(prop "$node" fabric_api)")" "$dir/mods/fabric-api.jar" \
                || { echo "$node	fail	install" >> "$results"; echo "$node install failed"; return; }
            launch=("$java" -Danvilfix.selftest=true -jar fabric-server-launch.jar nogui) ;;
        forge|neoforge)
            local installer
            if [ "$loader" = forge ]; then
                local v="$mc-$(prop "$node" forge_version)"
                installer="$cache/forge-$v-installer.jar"
                fetch "https://maven.minecraftforge.net/net/minecraftforge/forge/$v/forge-$v-installer.jar" "$installer"
            else
                local v; v=$(prop "$node" neoforge_version)
                installer="$cache/neoforge-$v-installer.jar"
                fetch "https://maven.neoforged.net/releases/net/neoforged/neoforge/$v/neoforge-$v-installer.jar" "$installer"
            fi
            if ! (cd "$dir" && "$java" -jar "$root/$installer" --installServer . >> "$root/$log" 2>&1); then
                echo "$node	fail	install" >> "$results"; echo "$node install failed"; return
            fi
            if [ -f "$dir/run.sh" ]; then
                # The installer's file doesn't end in a newline; without one this would land on its last comment line
                printf '\n-Danvilfix.selftest=true\n' >> "$dir/user_jvm_args.txt"
                launch=(env "PATH=$(dirname "$java"):$PATH" bash run.sh nogui)
            else
                launch=("$java" -Danvilfix.selftest=true -jar "$(cd "$dir" && ls *.jar | grep -i -E 'shim|server' | head -1)" nogui)
            fi ;;
    esac

    printf 'eula=true\n' > "$dir/eula.txt"
    printf 'online-mode=false\nwhite-list=false\nserver-port=%s\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=-1\n' "$port" > "$dir/server.properties"
    (cd "$dir" && timeout 900 "${launch[@]}" >> "$root/$log" 2>&1 < /dev/null)
    local summary; summary=$(grep -o 'ANVILFIX_SELFTEST_RESULT.*' "$log" | head -1 | sed 's/ANVILFIX_SELFTEST_RESULT //')
    if echo "$summary" | grep -q '^[0-9]* passed, 0 failed'; then
        echo "$node	pass	$summary" >> "$results"; echo "$node prod=pass $summary"
    else
        echo "$node	fail	${summary:-no result}" >> "$results"; echo "$node prod=fail ${summary:-no result}"
    fi
    rm -rf "$dir/world" "$dir/libraries"
}

queue=$work/queue
printf '%s\n' "$@" > "$queue"
next_node() {
    until mkdir "$queue.lock" 2>/dev/null; do sleep 0.2; done
    local node; node=$(head -1 "$queue"); tail -n +2 "$queue" > "$queue.tmp"; mv "$queue.tmp" "$queue"
    rmdir "$queue.lock"
    echo "$node"
}
for lane in $(seq 0 $((lanes - 1))); do
    ( while node=$(next_node) && [ -n "$node" ]; do test_node "$node" "$lane"; done ) &
    sleep 5
done
wait
