#!/usr/bin/env bash
# Verify every node, one loader at a time on parallel lanes, clearing that loader's shared caches in between so the
# whole matrix fits on disk. Results go to port-status.tsv.
set -u
cd "$(dirname "$0")/.."
lanes="${LANES:-3}"
nodes() { ls versions | grep -- "-$1\$" | sort -t. -k1,1n -k2,2n -k3,3n; }

scripts/verify.sh -j "$lanes" $(nodes fabric)
rm -rf ~/.gradle/caches/fabric-loom

scripts/verify.sh -j "$lanes" $(nodes neoforge)
rm -rf ~/.gradle/caches/neoformruntime ~/.gradle/caches/ng_execute

scripts/verify.sh -j "$lanes" $(nodes forge)
rm -rf ~/.gradle/caches/minecraftforge/forgegradle/mavenizer .gradle/mavenizer
