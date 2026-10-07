#!/usr/bin/env bash

# Copyright 2026 Unicorn Operations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Gate of the release workflow (#7, docs/RELEASING.md). Fails unless:
# - TAG is v<yy.mm.r>-fc.<n> with n in 1..9 and r in 0..9 (see plugins/src/main/kotlin/config/FamilyChatVersion.kt);
# - the upstream version in the tag is the one the tagged commit is built from (Versions.kt);
# - the tagged commit is on familychat;
# - CI passed on that commit: the latest push runs of Test, APK Build and Code Quality succeeded.
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

if ! git merge-base --is-ancestor "${sha}" origin/familychat; then
    echo "::error::${TAG} (${sha}) is not on familychat"
    exit 1
fi

# CI is judged on the push runs of the three CI workflows only: scheduled workflows (upstream sync, Gradle wrapper
# update) also run on the familychat tip and attach their own, unrelated check runs to it.
runs="$(gh api --paginate "repos/${REPO}/actions/runs?head_sha=${sha}&event=push&per_page=100" \
    --jq '.workflow_runs[] | {path, status, conclusion, run_number}' | jq -s .)"
for workflow in build.yml tests.yml quality.yml; do
    latest="$(jq -c --arg p ".github/workflows/${workflow}" '[.[] | select(.path == $p)] | max_by(.run_number) // empty' <<< "${runs}")"
    if [[ -z "${latest}" ]]; then
        echo "::error::${workflow} has not run on ${sha} (a push to familychat runs it)"
        exit 1
    fi
    if [[ "$(jq -r '.status + " " + (.conclusion // "")' <<< "${latest}")" != "completed success" ]]; then
        echo "::error::${workflow} is not green on ${sha}: $(jq -r '.status + " " + (.conclusion // "")' <<< "${latest}")"
        exit 1
    fi
done

echo "Release ${TAG}: upstream ${built_version}, Family Chat release ${fc}, commit ${sha}"
{
    echo "tag=${TAG}"
    echo "fc=${fc}"
    echo "sha=${sha}"
} >> "${GITHUB_OUTPUT}"
