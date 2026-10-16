#!/usr/bin/env bash
set -euo pipefail

# Cuts a new release: bumps mod_version in gradle.properties, commits it,
# tags the commit to match exactly, and pushes both.
#
# Usage: ./release.sh <version>
# Example: ./release.sh v1.1.2
#   -> mod_version becomes v1.1.2+mc<current minecraft_version>, tagged the same way.
# Version must be in vX.X.X form; bare X.X.X (no leading "v") is rejected.

if [ $# -ne 1 ]; then
    echo "Usage: $0 <vX.X.X>   e.g. $0 v1.1.2" >&2
    exit 1
fi

NEW_VERSION="$1"

if ! [[ "$NEW_VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Version must look like vX.X.X (e.g. v1.1.2), got: $NEW_VERSION" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "Working tree is not clean. Commit or stash changes before releasing." >&2
    exit 1
fi

MINECRAFT_VERSION=$(grep -E '^minecraft_version=' gradle.properties | cut -d= -f2)
if [ -z "$MINECRAFT_VERSION" ]; then
    echo "Could not read minecraft_version from gradle.properties." >&2
    exit 1
fi

FULL_VERSION="${NEW_VERSION}+mc${MINECRAFT_VERSION}"

if git rev-parse "$FULL_VERSION" >/dev/null 2>&1; then
    echo "Tag $FULL_VERSION already exists." >&2
    exit 1
fi

sed -i "s/^mod_version=.*/mod_version=${FULL_VERSION}/" gradle.properties

if [ -n "$(git status --porcelain -- gradle.properties)" ]; then
    git add gradle.properties
    git commit -m "Release ${FULL_VERSION}"
else
    echo "gradle.properties already has mod_version=${FULL_VERSION}; nothing to commit."
fi

git tag "$FULL_VERSION"
git push
git push origin "$FULL_VERSION"

echo "Released $FULL_VERSION"
