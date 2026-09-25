# Family Chat Android deeplink

<!--- TOC -->

* [Introduction](#introduction)
  * [Asset Links](#asset-links)
  * [Supported links](#supported-links)
* [Developer tools](#developer-tools)

<!--- END -->


## Introduction

Family Chat Android supports deep linking to specific screens in the application. This document explains how to use deep links in Family Chat Android.

### Asset Links

The asset links file will be available at https://safechat.family/.well-known/assetlinks.json once the app has a
release signing certificate. It is published by [unicornops/family-chat#236](https://github.com/unicornops/family-chat/issues/236).

### Supported links

App link handed to a device by the control panel, which selects the account provider and pre-fills the user id:
> https://safechat.family/app/login?account_provider=smith.safechat.family&login_hint=mxid:@alice:smith.safechat.family

The same link with a parent-minted **sign-in code** (Palpo families only; contract in
[`docs/client-login-links.md`](https://github.com/unicornops/family-chat/blob/main/docs/client-login-links.md)):
> https://safechat.family/app/login?account_provider=smith.safechat.family&login_hint=mxid:@alice:smith.safechat.family&hs=smith.safechat.family&token=…

`hs` is the bare host (optionally `:port`) that answers the client-server API. The app first asks the user to confirm
the account ("Sign in as @alice:…?", or the host when there is no `login_hint`), then redeems `token` with
`m.login.token` against `https://<hs>` (`TokenLoginNode`, `MatrixAuthenticationService.loginWithToken`) and goes
straight into the app. A code that signs into another account than `login_hint` names is refused and its new device
signed out again. A malformed `hs`, a host outside the account-provider allowlist, or a `token` without `hs`
degrades the link to the prefill form above (the token is dropped, never redeemed). Declining, or a used or expired
code (the server answers 401/403), falls back to the password form for `hs`, with the user id pre-filled. The token is
never logged and never written to a saved-state bundle: the parcelled `LoginParams` only carry an id naming it in the
in-memory `SignInCodeStore`, so after a process death the link falls back to the password form. While an account is
signed in, a login link only shows "You're already signed in", and its code is not redeemed.

Link to a user:
> https://matrix.to/#/@alice:smith.safechat.family

Link to a room by id or alias:
> https://matrix.to/#/!roomid:smith.safechat.family

`matrix:` URIs are handled as well.

Upstream also registered `app.element.io`, `develop.element.io`, `staging.element.io` and `mobile.element.io`.
Those hosts are Element's and have been removed from the fork. Links to our own web client
(`app.safechat.family`) will be added with [unicornops/family-chat#235](https://github.com/unicornops/family-chat/issues/235).

## Developer tools

Using an Android 12 or higher emulator

Ensure links verification is enabled
```bash
adb shell am compat enable 175408749 family.safechat.android.debug  
```

Reset link verifications for the given package id
```bash
adb shell pm set-app-links --package family.safechat.android.debug 0 all 
```

Force the package id links to be verified
```bash
adb shell pm verify-app-links --re-verify family.safechat.android.debug 
```

Print the link verification of the package id
```bash
adb shell pm get-app-links family.safechat.android.debug
```

```
  family.safechat.android.debug:
    ID: e2ece472-c266-4bf0-829c-be79959a6270
    Signatures: [B0:B0:51:DC:56:5C:81:2F:E1:7F:6F:3E:94:5B:4D:79:04:71:23:AB:0D:A6:12:86:76:9E:B2:94:91:97:13:0E]
    Domain verification state:
      safechat.family: 1024
```
