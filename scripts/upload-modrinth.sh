#!/usr/bin/env bash
# Upload every release jar to Modrinth (project "anvilfix"), one version per Minecraft version and loader.
# Asks for a Modrinth personal access token (scope: create versions); it's only kept in this process.
# Add --dry-run to print what would be uploaded without contacting Modrinth.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 25)}"
dry=()
if [ "${1:-}" = "--dry-run" ]; then dry=(-PmodrinthDryRun=true); export MODRINTH_TOKEN=dry-run; fi
if [ -z "${MODRINTH_TOKEN:-}" ]; then
    printf 'Modrinth token: '
    read -rs MODRINTH_TOKEN
    echo
    export MODRINTH_TOKEN
fi
# --continue: one failed upload shouldn't stop the others; Gradle lists any failures at the end
./gradlew modrinth --continue "${dry[@]}" 2>&1 | tee build/modrinth-upload.log | grep -E "Successfully uploaded|Not going to upload|FAILED|What went wrong|BUILD"
echo "Uploaded versions: $(grep -c 'Successfully uploaded' build/modrinth-upload.log || true)"
