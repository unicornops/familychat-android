# Releasing Family Chat for Android

<!--- TOC -->

* [What a release produces](#what-a-release-produces)
* [Version numbers](#version-numbers)
* [One-time setup](#one-time-setup)
  * [The release environment](#the-release-environment)
  * [Signing keys](#signing-keys)
  * [Google Play](#google-play)
* [Cutting a release](#cutting-a-release)
* [Promoting and rolling back](#promoting-and-rolling-back)
* [Rotating credentials](#rotating-credentials)
* [When a store rejects a build](#when-a-store-rejects-a-build)

<!--- END -->

## What a release produces

Pushing a tag `v<upstream version>-fc.<n>` on `familychat` runs `.github/workflows/release.yml`:

| Job | Environment | Does |
|---|---|---|
| Check the release tag | none (no secrets) | Checks the tag format, that the tagged commit is on `familychat`, that the tag's upstream version is the one the commit is built from, and that the latest push runs of Test, APK Build and Code Quality succeeded on it (scheduled workflows on the same commit are ignored). |
| Build and sign | `release` (needs Rob's approval) | Builds the **F-Droid APKs** (UnifiedPush only) signed with the **direct-distribution key**, and, once the upload key exists, the **Play AAB** signed with the **upload key** (kept 30 days as a workflow artefact), uploaded to the Play **internal** track once the Play service account exists; a failed Play upload is a warning, not a failure. Generates a CycloneDX SBOM of the F-Droid release dependencies (CycloneDX Gradle plugin, applied by `.github/workflows/scripts/cyclonedx.init.gradle.kts`) and `SHA256SUMS`. |
| Publish the GitHub pre-release | none | Attests the build provenance of every file and publishes a **pre-release** with the APKs (arm64-v8a, armeabi-v7a and universal), their R8 mapping, the SBOM and `SHA256SUMS`, and notes made of the pull requests merged since the previous release tag. |

Every release has a public tag with its exact source, which is how the AGPL promise of family-chat#232 decision 2 is
kept. The AAB is not attached to the GitHub release: it is not installable.

**Two channels, two keys.** Google re-signs Play installs with its app-signing key, which the direct APK can never
share. The GitHub APK is therefore signed with a key of its own, and a device must pick one channel: switching
between Play and the direct APK means uninstalling first (the release notes say so).

## Version numbers

Release tags are `v<upstream version>-fc.<n>`, e.g. `v26.09.4-fc.1`: the upstream Element X release it is based on,
and our release number on top of it, from 1 to 9.

The version name is `26.09.4-fc.1`. The version code, from `plugins/src/main/kotlin/config/FamilyChatVersion.kt`, is
`yyyy·10000 + mm·100 + r·10 + n` (r: upstream's release number of the month), so `v26.09.4-fc.1` is `20260941`, times 10
plus the ABI code for APKs. It only increases: between our releases of one upstream version, and across upstream
merges. Builds without `-Pfamilychat.fc` (local, CI) use `n = 0`.

The gate refuses an upstream release number above 9 or a tenth Family Chat release on one upstream version; either
would need a new scheme in `FamilyChatVersion` first.

## One-time setup

### The release environment

All release credentials are secrets of the GitHub environment **`release`**, with Rob as required reviewer, and
nowhere else (no repo-level secrets). The environment itself is managed with Terragrunt in
`unicornops/gitops-environments` (gitops-environments#31), with:

- **required reviewer:** Rob, so nothing signs or uploads without his approval;
- **deployment policy:** only tags matching `v*-fc.*` and the `familychat` branch (for re-runs by hand) may use it;
- a **tag ruleset** restricting the creation, update and deletion of `v*-fc.*` tags to admins, and **branch
  protection** on `familychat`.

The gate job is not a security boundary on its own: it runs from the tagged commit, so whoever can push a tag can
change it. The approval, the deployment policy and the tag ruleset are what keep the keys safe. Create the environment
before the first tag: a job naming a missing environment creates it without any protection.

| Secret | What | Needed for |
|---|---|---|
| `DIRECT_KEYSTORE_BASE64` | the direct-distribution keystore, `base64 -w0` | every release |
| `DIRECT_STORE_PASSWORD`, `DIRECT_KEY_ALIAS`, `DIRECT_KEY_PASSWORD` | its passwords and alias | every release |
| `UPLOAD_KEYSTORE_BASE64` | the Play upload keystore, `base64 -w0` | the Play AAB |
| `UPLOAD_STORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` | its passwords and alias | the Play AAB |
| `PLAY_SERVICE_ACCOUNT_JSON` | the Google Play Developer API service account key (gitops-environments, same GCP stack as #30) | the upload to Play |

Without the upload key there is no AAB, and without the Play service account nothing goes to Play: both are notices,
not failures, until family-chat#240 is done. Without the direct key the release fails.

### Signing keys

Generate each key once, on a trusted machine, and keep an **offline backup** of the keystore and its passwords (for
example in the company password manager). Never commit a keystore.

```bash
# Direct-distribution key (GitHub APK). Losing it means users of the direct APK must uninstall to update.
keytool -genkeypair -v -keystore familychat-direct.jks -alias familychat-direct \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Family Chat, O=Unicorn Operations Ltd, C=IE"

# Play upload key. Losing it means an upload-key reset in Play Console (Play App Signing keeps the app key).
keytool -genkeypair -v -keystore familychat-upload.jks -alias familychat-upload \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Family Chat, O=Unicorn Operations Ltd, C=IE"

# The values to store in the release environment
base64 -w0 familychat-direct.jks   # DIRECT_KEYSTORE_BASE64
base64 -w0 familychat-upload.jks   # UPLOAD_KEYSTORE_BASE64

# Fingerprints: Firebase (gitops-environments#30) and the API key restrictions want the SHA-1 and SHA-256 of the
# signing certificates
keytool -list -v -keystore familychat-direct.jks -alias familychat-direct | grep -E 'SHA1|SHA256'
```

`keytool` writes PKCS12 keystores, which have a single password: it is both the store password and the key password,
so `*_STORE_PASSWORD` and `*_KEY_PASSWORD` hold the same value. Use a different password for each of the two keystores.

### Google Play

1. Play Console organisation account (family-chat#240), app record `family.safechat.android`.
2. Enrol in **Play App Signing** and register the upload key's certificate.
3. Put the **app-signing key**'s SHA-256 fingerprint (Play Console → Setup → App signing) in
   `https://safechat.family/.well-known/assetlinks.json` (family-chat#236, #256), replacing the all-zero placeholder,
   so that sign-in links open the app directly.
4. The very first AAB must be uploaded **by hand** in Play Console (the API cannot create the first release of an app):
   cut a release with the upload key set but **without** `PLAY_SERVICE_ACCOUNT_JSON`, and download the AAB from the
   run's `play-<tag>` artefact.
5. Only then create the Play Developer API service account in gitops-environments, grant it **Release to testing
   tracks** on the app in Play Console (Users and permissions), and store its key as `PLAY_SERVICE_ACCOUNT_JSON`.

## Cutting a release

1. Merge everything for the release into `familychat` and wait for CI to pass on the merge commit. For a release on
   top of a new upstream version, the upstream merge's per-merge checklist (README) is done.
2. For a store build: the security review of family-chat#232 ("a security review before each store submission") is
   ticked on the release issue.
3. Tag and push:

   ```bash
   git switch familychat && git pull --ff-only
   git tag -s v26.09.4-fc.1 -m "Family Chat v26.09.4-fc.1"
   git push origin v26.09.4-fc.1
   ```

4. Approve the `release` environment when GitHub asks (Actions → the Release run → Review deployments).
5. Check the GitHub pre-release: APK, mapping, SBOM, `SHA256SUMS`, and `gh attestation verify <apk> --repo
   unicornops/familychat-android`.
6. Install the APK on a device and smoke-test sign-in, messages and push; testers get the Play internal build.

To build an existing tag again (a new secret, a failed GitHub release), run the Release workflow by hand with that
tag. A rebuild produces new (not byte-identical) files and replaces the release's assets, so avoid rebuilding a release
that has been promoted; Play refuses a version code it already has, which shows as a warning.

## Promoting and rolling back

- **Play:** promote internal → closed → production by hand in Play Console, with a staged rollout. To roll back, halt
  the staged rollout, or promote the previous release again.
- **GitHub:** when the build goes to production, untick "pre-release". To withdraw an APK, edit the release: mark it
  withdrawn at the top of the notes, link the fixed release, and delete the APK asset (keep the source tag).

## Rotating credentials

- **Direct-distribution key:** only if compromised: a new key forces every direct-APK user to uninstall. Publish the
  reason in the release notes.
- **Upload key:** request an upload-key reset in Play Console with the new certificate, then replace the `UPLOAD_*`
  secrets.
- **Play service account key:** create a new key in gitops-environments, replace `PLAY_SERVICE_ACCOUNT_JSON`, delete
  the old key.

## When a store rejects a build

Read the rejection in Play Console (Policy status). Families-policy issues usually trace back to a permission, an SDK
or a link out: check `tools/check/check_families_manifest.sh`, the network capture baseline (#9) and the parental gate
(`libraries/parentalgate/README.md`). Fix on a branch, release the next `-fc.<n>`, and answer the rejection with what
changed.
