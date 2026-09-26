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
  before any credentials are sent. The server picker and account creation are hidden: accounts are created by a
  parent in the control panel.
* App Links on `safechat.family/app/...`; Element's `*.element.io` link handling removed.
* Website, privacy, terms and OAuth client metadata point at `safechat.family`.
* No third-party analytics or crash reporting. PostHog and Sentry are excluded from the build
  entirely, and the MapTiler key is empty (location sharing stays disabled).
* Push goes to our own gateway at `https://push.safechat.family`. UnifiedPush is kept and is
  currently the only push provider; see [Push notifications](#push-notifications).
* `LICENSE-COMMERCIAL` removed: that is Element's commercial offer, not ours. This fork is AGPL-3.0
  only. Upstream copyright and licence notices are kept.

Work still to do is tracked in [unicornops/family-chat#234](https://github.com/unicornops/family-chat/issues/234).

## Status

Pre-release. Nothing has been published to Google Play yet.

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

Upstream's two store flavours are kept: `gplay` and `fdroid`. They currently produce the same push
behaviour because Firebase is disabled (see below), so `gplay` is the one to build.

### Signing

There are no release signing keys yet. `release` and `nightly` builds fall back to upstream's
checked-in debug keystore, so they must not be published. Play App Signing and an upload key stored in
a GitHub environment are tracked in the parent issue.

### Push notifications

There is no Firebase project for `family.safechat.android` yet, so
`BuildTimeConfig.PUSH_CONFIG_INCLUDE_FIREBASE` is `false` and the FCM push provider is left out of the
build. Element's Firebase credentials have been removed from
`libraries/pushproviders/firebase/src/*/res/values/firebase.xml` and replaced with obvious
placeholders. To enable FCM: create the Firebase Android app, copy the values from its
`google-services.json` into those files, and flip the flag back to `true`.

UnifiedPush works today and defaults to our gateway at `https://push.safechat.family`
(hosting is tracked in unicornops/family-chat#241).

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

Conflicts concentrate in a small number of files, in roughly this order of likelihood:

1. `plugins/src/main/kotlin/config/BuildTimeConfig.kt` and `plugins/src/main/kotlin/ModulesConfig.kt`
2. `app/build.gradle.kts`, `settings.gradle.kts` and `app/src/main/AndroidManifest.xml`
3. `appconfig/`
4. `features/enterprise/impl-foss/`
5. `.github/workflows/` (several upstream workflows are deleted in the fork, see below)

Always keep our values, and read upstream's diff for *new* configuration keys that need a Family Chat
value. After merging, run `./gradlew :app:assembleGplayDebug test` and open a PR against `familychat`.

The following upstream workflows are intentionally absent because they depend on Element's
secrets, accounts or infrastructure: `build_enterprise`, `danger`, `fork-pr-notice`,
`generate_github_pages`, `maestro-local`, `nightly`, `nightlyReports`, `post-release`, `pull_request`,
`release`, `stale-issues`, `sync-localazy`, `sync-sas-strings`, `triage-incoming` and
`triage-labelled`. `sonar.yml` is kept but runs on manual dispatch only until it is repointed at our
self-hosted SonarQube server.

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
