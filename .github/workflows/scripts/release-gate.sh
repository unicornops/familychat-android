#!/usr/bin/env bash

# Copyright 2026 Unicorn Operations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Gate of the release workflow (#7, docs/RELEASING.md). Fails unless:
# - TAG is v<yy.mm.r>-fc.<n> with n in 1..9 and r in 0..9 (see plugins/src/main/kotlin/config/FamilyChatVersion.kt);
# - the upstream version in the tag is the one the tagged commit is built from (Versions.kt);
# - the tagged commit is on familychat;
# - CI passed on that commit: Test, APK Build and Code Quality, nothing failed.
# Writes tag, fc and sha to GITHUB_OUTPUT. Needs TAG, REPO and GH_TOKEN; runs from a full clone (fetch-depth: 0).

set -euo pipefail

if [[ ! "${TAG}" =~ ^v([0-9]{2})\.([0-9]{2})\.([0-9])-fc\.([1-9])$ ]]; then
    echo "::error::${TAG} is not a release tag: expected v<yy.mm.r>-fc.<n>, r in 0..9 and n in 1..9"
    exit 1
fi
tag_version="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
fc="${BASH_REMATCH[4]}"

sha="$(git rev-list -n 1 "refs/tags/${TAG}")"

versions="$(git show "${sha}:plugins/src/main/kotlin/Versions.kt")"
read_const() {
    sed -n "s/^private const val $1 = \([0-9]*\)$/\1/p" <<< "${versions}"
}
year="$(read_const versionYear)"
month="$(read_const versionMonth)"
release="$(read_const versionReleaseNumber)"
built_version="$(printf '%02d.%02d.%d' "${year}" "${month}" "${release}")"
if [[ "${tag_version}" != "${built_version}" ]]; then
    echo "::error::${TAG} says upstream ${tag_version}, but the tagged commit is built from upstream ${built_version}"
    exit 1
fi

git fetch --no-tags origin familychat
if ! git merge-base --is-ancestor "${sha}" FETCH_HEAD; then
    echo "::error::${TAG} (${sha}) is not on familychat"
    exit 1
fi

# The check runs of the tagged commit, except this workflow's own jobs.
release_jobs='["Check the release tag","Build and sign","Publish the GitHub pre-release"]'
checks="$(gh api --paginate "repos/${REPO}/commits/${sha}/check-runs?per_page=100" \
    --jq ".check_runs[] | select(.name as \$n | ${release_jobs} | index(\$n) | not) | {name, status, conclusion}" \
    | jq -s .)"
failed="$(jq -r '.[] | select(.status != "completed" or (.conclusion | IN("success", "skipped", "neutral") | not)) | "\(.name): \(.status) \(.conclusion)"' <<< "${checks}")"
if [[ -n "${failed}" ]]; then
    echo "::error::CI is not green on ${sha}:"
    echo "${failed}"
    exit 1
fi
for required in "Runs unit tests" "Build debug APKs" "Project Check Suite"; do
    if ! jq -e --arg n "${required}" 'any(.[]; .name == $n)' <<< "${checks}" > /dev/null; then
        echo "::error::CI check \"${required}\" has not run on ${sha}"
        exit 1
    fi
done

echo "Release ${TAG}: upstream ${built_version}, Family Chat release ${fc}, commit ${sha}"
{
    echo "tag=${TAG}"
    echo "fc=${fc}"
    echo "sha=${sha}"
} >> "${GITHUB_OUTPUT}"
