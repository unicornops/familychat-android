[![APK Build](https://github.com/unicornops/familychat-android/actions/workflows/build.yml/badge.svg?branch=familychat)](https://github.com/unicornops/familychat-android/actions/workflows/build.yml?query=branch%3Afamilychat)
[![Test](https://github.com/unicornops/familychat-android/actions/workflows/tests.yml/badge.svg?branch=familychat)](https://github.com/unicornops/familychat-android/actions/workflows/tests.yml?query=branch%3Afamilychat)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](LICENSE)

# Family Chat Android

Family Chat Android is the Android client for [Family Chat](https://safechat.family), a private
[Matrix](https://matrix.org/) chat server for each family. It is a fork of
[Element X Android](https://github.com/element-hq/element-x-android) by Element, licensed under the
AGPL-3.0. The source for this fork is at https://github.com/unicornops/familychat-android.

The app is written in Kotlin with [Jetpack Compose](https://developer.android.com/jetpack/compose) on
top of the [Matrix Rust SDK](https://github.com/matrix-org/matrix-rust-sdk), with navigation managed by
[Appyx](https://github.com/bumble-tech/appyx).

<!--- TOC -->

* [What is different from upstream](#what-is-different-from-upstream)
* [Status](#status)
* [Minimum SDK version](#minimum-sdk-version)
* [Build instructions](#build-instructions)
  * [Build variants](#build-variants)
  * [Signing](#signing)
  * [Push notifications](#push-notifications)
* [Merging upstream releases](#merging-upstream-releases)
  * [Where upstream merges conflict](#where-upstream-merges-conflict)
  * [Per-merge checklist](#per-merge-checklist)
  * [Deleted upstream workflows](#deleted-upstream-workflows)
  * [Automated sync](#automated-sync)
* [Contributing](#contributing)
* [Support](#support)
* [Copyright and License](#copyright-and-license)

<!--- END -->

## What is different from upstream

The fork keeps as close to upstream as an honest rebrand allows, and pushes almost all of its
configuration through the objects upstream already provides for branded builds
(`plugins/src/main/kotlin/config/BuildTimeConfig.kt`, `appconfig/`, `features/enterprise/impl-foss/`).
There is deliberately **no** private enterprise overlay: everything is rebranded in this public repo.

* Application id `family.safechat.android`, app name "Family Chat", our own launcher icons and brand
  colours (teal `#0D9488`, accent `#F97316`).
* Sign-in locked to family servers under `safechat.family`: a server name (a family's own domain
  included) is accepted only if its `.well-known` resolves to an `https://*.safechat.family` homeserver, checked
  before a password is sent or an OAuth sign-in starts, and again after login. The check is on the homeserver,
  not on the OAuth issuer it advertises. The server picker and account creation are hidden: accounts are created by a
  parent in the control panel.
* App Links on `safechat.family/app/...`; Element's `*.element.io` link handling removed.
* Website, privacy, terms and OAuth client metadata point at `safechat.family`.
* No third-party analytics or crash reporting. PostHog and Sentry are excluded from the build
  entirely, and the MapTiler key is empty (location sharing stays disabled).
* Push goes to our own gateway at `https://push.safechat.family`: FCM in the `gplay` flavour, UnifiedPush in
  both; see [Push notifications](#push-notifications).
* `LICENSE-COMMERCIAL` removed: that is Element's commercial offer, not ours. This fork is AGPL-3.0
  only. Upstream copyright and licence notices are kept.

Work still to do is tracked in [unicornops/family-chat#234](https://github.com/unicornops/family-chat/issues/234).

## Status

Pre-release. Nothing has been published to Google Play yet. Releases are cut from `v<upstream>-fc.<n>` tags by the
Release workflow, which publishes signed APKs as GitHub pre-releases: see [docs/RELEASING.md](docs/RELEASING.md).

## Minimum SDK version

Family Chat Android requires a minimum SDK version of 24 (Android 7.0, Nougat).

## Build instructions

Clone the project and open it in Android Studio, or build from the command line with JDK 21:

```bash
./gradlew :app:assembleGplayDebug
```

To build against a local copy of the Rust SDK, see the
[Developer onboarding](docs/_developer_onboarding.md#building-the-sdk-locally) instructions.

### Build variants

Upstream's two store flavours are kept: `gplay` (Google Play: FCM and UnifiedPush) and `fdroid` (direct download:
UnifiedPush only, no Google code).

### Signing

Release builds are signed by the Release workflow from `v<upstream>-fc.<n>` tags, with keys held in the protected
`release` environment: see [docs/RELEASING.md](docs/RELEASING.md). Local and CI `release` and `nightly` builds without
those keys fall back to the checked-in debug keystore and must not be published.

### Push notifications

**FCM** (`gplay` only): Firebase project `unicornops-familychat-push`, managed with Terragrunt in
unicornops/gitops-environments (`google/unicornops/familychat-push-firebase`), with one Firebase Android app per build
type. Its values are in `libraries/pushproviders/firebase/src/*/res/values/firebase.xml` (not secret: they ship in
every APK) and `BuildTimeConfig.PUSH_CONFIG_INCLUDE_FIREBASE` is `true`. The Firebase messaging dependency excludes
Analytics and measurement; `tools/check/check_families_manifest.sh` fails the build if they or the advertising ID
ever come back.

**UnifiedPush** (both flavours) defaults to our gateway at `https://push.safechat.family`.

Both register their pusher with `format: event_id_only` (`RustPushersService`), so the homeserver sends only the event
and room ids through the gateway: no message content reaches Google or a UnifiedPush distributor. The gateway is
Sygnal (unicornops/family-chat#241), whose app ids are `family.safechat.android`, `.debug` and `.nightly`.

## Merging upstream releases

The fork keeps a GitHub fork relationship with `element-hq/element-x-android`, and the default branch
is `familychat`. Upstream ships a release roughly monthly, tagged `vYY.MM.N`.

```bash
# once per clone
git remote add upstream https://github.com/element-hq/element-x-android.git
# never push to upstream
git remote set-url --push upstream DISABLED

git fetch upstream --tags
git checkout familychat
git checkout -b chore/merge-upstream-vYY.MM.N
git merge vYY.MM.N
```

Merge the release **tag**, never rebase: `familychat` is public, and every release must map to a
tag. Only merge stable releases: upstream publishes some `vYY.MM.N` tags as GitHub pre-releases (for
example `v26.09.3`), so check the release page before merging one.

### Where upstream merges conflict

Keep this list current after every merge. Always keep our values, and read upstream's diff for *new*
configuration keys or code paths that need a Family Chat answer.

1. **Build configuration:** `plugins/src/main/kotlin/config/*` (`BuildTimeConfig`, `ModulesConfig`,
   the push config), `appconfig/`, the application ids and the launcher icons.
2. **Sign-in:** `DefaultEnterpriseService` (the homeserver allowlist, `forcedAccountProvider()`,
   `isElementProEnforced()`, which must stay `false`: since v26.09.2 upstream fetches
   `.well-known/element/element.json` there to send users to Element Pro), `LoginLinkPolicy`, the
   sign-in-code login (`SignInCodeStore`, `LoginTokenExchanger`) and `RustMatrixAuthenticationService`
   (the resolved-homeserver backstop). Since v26.09.3 upstream's `EnterpriseService` and
   `AccountProviderAccessControl` take an `AccountProvider` (`accountProviderAllowList()`,
   `canConnectToAnyAccountProvider()`); our additions on top are `forcedAccountProvider()`,
   `isAllowedToConnectToHomeserver()` and `isAllowedResolvedHomeserverUrl()`. `AccountProvider` sanitising passes
   server names to discovery without `https://`.
7. **Settings:** since v26.09.3 settings are split into app and account settings. The location settings row is
   hidden by `PreferencesRootState.showLocationSettings`, and the control-panel row lives in the account section
   (`PreferencesAccountView`).
3. **The parental gate:** the `libraries/parentalgate` module, the `androidutils` openers,
   `SafeUriHandler`/`ParentalGateSafeContent` in `ElementThemeApp`, the Konsist tripwire, the
   notification builders (`setAllowSystemGeneratedContextualActions(false)`), the Application's
   Compose flags (`isSmartSelectionEnabled` and the Process-Text reflection hook, whose ProGuard keep
   rule is in `libraries/designsystem/consumer-rules.pro`: re-check both on every Compose upgrade),
   the manifest's removed `REQUEST_INSTALL_PACKAGES`, and the map overlay in
   `MapBottomSheetScaffold`. Since maplibre-compose 0.15 the MapLibre logo and attribution are
   composables that open the browser through `LocalUriHandler`; we draw the logo with
   `onClick = null` and use our `MapAttribution` instead of `ExpandingAttributionButton`.
4. **Strings:** ours live in `temporary.xml`, never `localazy.xml`. Localazy sync stays off.
5. **Workflows:** `.github/workflows/`, see the list of deleted workflows below. Upstream edits to a
   workflow we deleted show up as modify/delete conflicts: keep the deletion.
6. **Dependencies:** for the Rust SDK Maven artefact (`matrix_sdk`) and other library versions, take
   upstream's.

After merging, run `./gradlew :app:assembleGplayDebug :app:assembleFdroidDebug test` and open a PR
against `familychat` with the checklist below.

### Per-merge checklist

- [ ] Konsist/detekt/ktlint green (the parental-gate tripwire must not be widened by an allow-list
      entry without a reason)
- [ ] Paparazzi goldens: note any needing re-record
- [ ] No upstream workflow re-added (or re-deleted + still disabled at repo level:
      `gh workflow list --all`)
- [ ] Brand check: no "Element"/Element URLs reintroduced in user-facing strings or config.
      Run `tools/familychat/brand_translations.py` to regenerate the translation overrides; with `--check` it
      also lists any new translated string that names Element and needs classifying (CI runs `--check`)
- [ ] Parental gate still covers every new way to leave the app: new `open`/URL/link code, new web
      views, new library-provided buttons. On a Compose BOM or `wysiwyg` bump, `ComposeProcessTextSwitchTest`
      (the Process-Text reflection hook still empties Compose's query) and `RichTextEditorSelectionActionsTest`
      (the editor is still an `EditText` created by `RichTextEditor`, and the library sets no selection callbacks or
      text classifier of its own) fail if those hooks stop holding
- [ ] Sign-in allowlist and sign-in-code rules unchanged (family-chat `docs/client-login-links.md`)
- [ ] No third-party analytics/telemetry re-enabled (Kids/Families declarations, family-chat#232
      decisions 4 and 8)
- [ ] CI green

### Deleted upstream workflows

The following upstream workflows are intentionally absent because they depend on Element's
secrets, accounts or infrastructure, or enforce Element's own PR rules: `build_enterprise`, `danger`,
`fork-pr-notice`, `generate_github_pages`, `maestro-local`, `nightly`, `nightlyReports`,
`post-release`, `pr-checks`, `pull_request`, `release`, `stale-issues`, `sync-localazy`,
`sync-sas-strings`, `triage-incoming` and `triage-labelled`. Workflows that run on
`pull_request_target`, `workflow_run` or `schedule` execute from the base branch, so if a merge
re-adds one, delete it again **and** check that it is still disabled at repo level. `sonar.yml` is
kept but runs on manual dispatch only until it is repointed at our self-hosted SonarQube server.

### Automated sync

`.github/workflows/upstream-sync.yml` runs daily (and on manual dispatch) and does the merge above
for the newest **stable** upstream release, using
[`scripts/upstream-sync.sh`](.github/workflows/scripts/upstream-sync.sh):

- A clean merge is pushed to `upstream/vYY.MM.N` with a pull request titled
  `chore(upstream): merge element-hq/element-x-android vYY.MM.N`. It lists the files both sides
  changed and carries the per-merge checklist. It is never merged automatically.
- Upstream edits to workflows the fork deleted are resolved by keeping the deletion, and workflows
  new in the release are dropped in a second commit. Both are listed in the pull request.
- Any other conflict pushes nothing: the workflow opens or updates an issue labelled
  `upstream-sync` with the conflicting paths and the commands to reproduce the merge.
- When the release notes mention a security fix, or an upstream GHSA advisory is patched in that
  release, the pull request or issue is also labelled `security` and pings the CODEOWNERS.

It runs in the `upstream-sync` environment (deployments from `familychat` only), which holds
`UPSTREAM_SYNC_TOKEN`: a fine-grained token for this repository only, with read and write access to
contents, pull requests and issues. `GITHUB_TOKEN` cannot be used, because its pushes do not trigger
CI on the pull request. A manual dispatch defaults to a dry run, which writes the pull request or
issue it would open to the job summary. The script also runs locally:

```bash
GITHUB_REPOSITORY=unicornops/familychat-android DRY_RUN=true TAG=vYY.MM.N \
  .github/workflows/scripts/upstream-sync.sh
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md). Commits follow
[Conventional Commits](https://www.conventionalcommits.org/).

Changes that are not Family Chat specific are better sent
[upstream](https://github.com/element-hq/element-x-android) so everyone benefits.

## Support

Family Chat users should use the help pages at https://safechat.family/docs/ or contact support
through the control panel. Bugs in this fork can be raised as
[GitHub issues](https://github.com/unicornops/familychat-android/issues).

## Copyright and License

Copyright (c) 2026 unicornops
Copyright (c) 2025 Element Creations Ltd.
Copyright (c) 2022 - 2025 New Vector Ltd.

Upstream Element X Android is dual licensed by Element Creations Ltd under the GNU Affero General
Public License v3 or a paid-for Element Commercial License. This fork is distributed **only** under
the terms of the GNU Affero General Public License, either version 3 of the License, or (at your
option) any later version. See [LICENSE](LICENSE).

Family Chat is a fork of Element X Android by Element (AGPL-3.0); source at
https://github.com/unicornops/familychat-android

"Element" and the Element logo are trademarks of Element Creations Ltd and are not used by this fork.

Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing permissions and limitations under the
License.
