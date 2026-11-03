#!/usr/bin/env bash
# Copy the release jar of every node into dist/ (no sources or dev jars). Run after the nodes are built.
set -u
cd "$(dirname "$0")/.."
rm -rf dist
mkdir -p dist
missing=0
for n in $(ls versions | sort -t. -k1,1n -k2,2n -k3,3n); do
    jar=$(ls "versions/$n/build/libs/"*.jar 2>/dev/null | grep -v -E -- '-(sources|dev|all|selftest|selftest-dev)\.jar$' | head -1)
    if [ -n "$jar" ]; then
        cp "$jar" dist/
    else
        echo "no jar for $n"
        missing=$((missing + 1))
    fi
done
echo "$(ls dist | wc -l | tr -d ' ') jars in dist/, $missing missing"
