#!/usr/bin/env bash
# Checks that everything is ready to release the given tag, e.g. ./scripts/check-release.sh v1.2.3
# Run it before tagging; the release workflow runs it too and stops if anything is missing.
set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <tag, e.g. v1.2.3>" >&2
    exit 2
fi
tag="$1"
gradle_file="app/build.gradle.kts"
metadata_dir="fastlane/metadata/android"

# Prints the versionCode or versionName written in the given build file content.
version_code_of() { sed -n 's/^ *versionCode = \([0-9]*\)$/\1/p'; }
version_name_of() { sed -n 's/^ *versionName = "\(.*\)"$/\1/p'; }

version_code="$(version_code_of < "$gradle_file")"
version_name="$(version_name_of < "$gradle_file")"
failed=false

if [ "$tag" != "v$version_name" ]; then
    echo "The tag $tag does not match versionName \"$version_name\" in $gradle_file." >&2
    failed=true
fi

# The newest tag before this one, if there is any.
previous_tag="$(git describe --tags --abbrev=0 --match 'v*' --exclude "$tag" HEAD 2>/dev/null || true)"
if [ -n "$previous_tag" ]; then
    previous_code="$(git show "$previous_tag:$gradle_file" | version_code_of)"
    if [ "$version_code" -le "$previous_code" ]; then
        echo "versionCode $version_code is not higher than $previous_code of $previous_tag." >&2
        failed=true
    fi
fi

for locale in en-US de-DE; do
    changelog="$metadata_dir/$locale/changelogs/$version_code.txt"
    if [ ! -s "$changelog" ]; then
        echo "The changelog $changelog is missing or empty." >&2
        failed=true
    fi
done

if [ "$failed" = true ]; then
    exit 1
fi
echo "Ready to release $tag (versionCode $version_code)."
