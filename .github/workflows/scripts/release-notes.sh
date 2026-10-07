#!/usr/bin/env bash

# Copyright 2026 Unicorn Operations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Release notes for a Family Chat release tag (#7): the Conventional Commits since the previous release tag, grouped by
# type, plus the upstream release it is based on. Usage: release-notes.sh <tag>. Runs from a full clone.

set -euo pipefail

tag="$1"
upstream="$(sed -E 's/^(v[0-9.]+)-fc\.[0-9]+$/\1/' <<< "${tag}")"
previous="$(git tag --list 'v*-fc.*' --sort=-creatordate | grep -vx "${tag}" | head -n 1 || true)"
if [[ -z "${previous}" ]]; then
    # First release: everything since the fork, whose first commit of ours is the rebrand.
    first="$(git log --reverse --format='%H' --grep='^feat(brand): Family Chat branding' "${tag}" | head -n 1)"
    previous="${first}^"
fi
range="${previous}..${tag}"

echo "Family Chat for Android, based on [Element X Android ${upstream}](https://github.com/element-hq/element-x-android/releases/tag/${upstream})."
echo
echo "Source: [${tag}](https://github.com/unicornops/familychat-android/tree/${tag}) (AGPL-3.0)."
echo
# Pull requests are merged with merge commits on familychat: their titles (Conventional Commits) are the first line of
# each first-parent merge's body. Upstream's own commits arrive through merges of its tags and are not listed.
titles="$(git log --first-parent --merges --format='%b%x00' "${range}" | awk 'BEGIN { RS = "\0" } { sub(/^\n+/, ""); split($0, l, "\n"); if (l[1] != "") print l[1] }')"
section() {
    local title="$1" pattern="$2" lines
    lines="$(grep -E "${pattern}" <<< "${titles}" | sed -E 's/^[a-z]+(\([^)]*\))?!?: /- /' || true)"
    if [[ -n "${lines}" ]]; then
        echo "### ${title}"
        echo
        echo "${lines}"
        echo
    fi
}
section "Features" '^feat(\(|!|:)'
section "Fixes" '^fix(\(|!|:)'
section "Upstream" '^chore\(upstream\)'
titles="$(grep -v '^chore(upstream)' <<< "${titles}" || true)"
section "Other changes" '^(chore|ci|test|docs|refactor|perf|build)(\([^)]*\))?!?: '
echo "Install \`*-arm64-v8a.apk\` on most phones, \`*-armeabi-v7a.apk\` on older 32-bit ones, or the (much larger)"
echo "\`*-universal.apk\` if unsure."
echo
echo "These APKs are signed with the Family Chat direct-distribution key. They cannot replace the Google Play version"
echo "of the app (and the other way round) without uninstalling first. Check it against SHA256SUMS and the"
echo "attestation: \`gh attestation verify <file> --repo unicornops/familychat-android\`."
