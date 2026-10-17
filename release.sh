#!/usr/bin/env bash
set -euo pipefail

# Cuts a new release: bumps mod_version in gradle.properties, commits it,
# tags the commit, and pushes both.
#
# Usage: ./release.sh <version>
# Example: ./release.sh v1.1.2
#   -> git tag becomes v1.1.2+mc<current minecraft_version> (matches the vX.X.X convention).
#   -> mod_version becomes 1.1.2+mc<current minecraft_version> (no "v" - Fabric Loader
#      requires strict SemVer there and warns about a leading "v").
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

TAG_VERSION="${NEW_VERSION}+mc${MINECRAFT_VERSION}"
MOD_VERSION_STRING="${NEW_VERSION#v}+mc${MINECRAFT_VERSION}"

if git rev-parse "$TAG_VERSION" >/dev/null 2>&1; then
    echo "Tag $TAG_VERSION already exists." >&2
    exit 1
fi

sed -i "s/^mod_version=.*/mod_version=${MOD_VERSION_STRING}/" gradle.properties

if [ -n "$(git status --porcelain -- gradle.properties)" ]; then
    git add gradle.properties
    git commit -m "Release ${TAG_VERSION}"
else
    echo "gradle.properties already has mod_version=${MOD_VERSION_STRING}; nothing to commit."
fi

git tag "$TAG_VERSION"
git push
git push origin "$TAG_VERSION"

echo "Released $TAG_VERSION (mod_version=$MOD_VERSION_STRING)"
