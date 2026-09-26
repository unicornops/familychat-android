# Parental gate

Family Chat declares a child audience (Google Play Families policy, like the iOS app in Apple's Kids Category), so
**every link that leaves the app, and anything purchasable, sits behind a parental gate**: an adult-level question that
has to be answered first. Decision 10 in unicornops/family-chat#232, task in unicornops/family-chat#234.

## What the user sees

A screen titled "Ask a grown-up" with one multiplication, both numbers written in words, for example
"What is twenty-three times seven?". One number is from 13 to 49 and not a multiple of ten, the other a single digit
from 3 to 9, in either order (answers 39 to 441). These are the same ranges as the iOS app. A "type this number in
digits" question was dropped after the iOS review, because 7- and 8-year-olds can do it.

The answer is typed in digits; surrounding spaces and leading zeros are ignored. The right answer opens the link and
closes the gate. A wrong answer replaces the question with a new one (never the same question or the same answer) and
says so; the same question can never be retried. The third wrong answer closes the gate. Cancel or back opens
nothing. There is no timer. Every presentation, for every account, starts with a new random question (`SecureRandom`).

Accessibility: the question is a heading in a polite live region, so TalkBack reads it when the screen opens and again
when a wrong answer replaces it; all text uses the theme's `sp` typography and the page scrolls, so font scaling works.

The question template is in `impl/src/main/res/values/temporary.xml`. The numbers in words come from
`EnglishNumberWords` and are **English only**: they are not localised (a translation needs per-language number grammar,
not string resources).

## Modules

- `api`: no UI. `ParentalGate` (the intent contract), `Context.startActivityBehindParentalGate()`,
  `ParentalGateResultContract` and the `@ParentalGateExempt` opt-in marker. `:libraries:androidutils` depends on it.
- `impl`: `ParentalGateActivity` (not exported), presenter, view, challenge generator. Included in the app by
  `allLibrariesImpl()`.

The gate is its own activity so that the plain `Context`/`Activity` extension functions every screen already uses can
reach it without any call site changing. If the activity is missing from a build, nothing opens (fail closed).

## Which API to use

| You want to | Use |
|---|---|
| Open a web page, `mailto:`, `tel:`, a store listing | `Context.openUrlInExternalApp(url)` or `Activity.openUrlInChromeCustomTab(null, darkTheme, url)` (both gated), `Context.openGooglePlay(appId)` |
| Start any other intent that leaves the app (another app, maps, a future control-panel link, GIF attribution) | `Context.startActivityBehindParentalGate(intent, fallback)` |
| Run something that is not an intent only for an adult (a purchase, if one is ever added) | `rememberLauncherForActivityResult(ParentalGateResultContract()) { passed -> ... }` |
| Open a sign-in page on the family's own account provider | `Activity.openAuthenticationUrlInChromeCustomTab()`, which needs `@OptIn(ParentalGateExempt::class)` and a comment saying why |

Other exits that are closed rather than gated:

- **Text selection actions**: `TextView.hideTextActionsThatLeaveTheApp()` (in `androidutils/text`) turns off the text
  classifier's smart actions (open link, call, email, map) and hides Process-Text / web search items ("Search",
  "Translate") in the markdown composer. Compose text fields show neither. The app declares no `PROCESS_TEXT` package
  query, so on Android 11 and later other apps' Process-Text items are not visible to it anyway.
- **Element Call web view**: long press is disabled (no link menus, previews or text selection), and a link the user
  taps to another site goes through `openUrlInExternalApp()` and so through the gate.

**Future work that must use the gate**: the control-panel settings entry (#234 section 5) and the GIF attribution link
(#238). A `Konsist` test (`KonsistParentalGateTest`) fails if code builds its own `Intent.ACTION_VIEW` or
`CustomTabsIntent` outside the allowed files, which catches new upstream call sites after a rebase.

## Not gated, and why

- **Links the app handles itself**: the openers first resolve the link against the app's own intent filters
  (App Links on `https://safechat.family/app/`, `matrix:`, the notification deep link, the OAuth redirect) and open
  those in-app, pinned to our package. `matrix.to` permalinks never reach the openers: the timeline routes them in-app.
- **Authentication**: the OAuth sign-in Custom Tab (`LoginFlowNode`), the identity-reset approval (`ResetIdentityFlowNode`)
  and new-device approval (`LinkNewDeviceFlowNode`) run on the family's own account provider and hand back to the app
  through its redirect. They are part of signing in, not a way out; gating them would lock children out of their own
  account. "Manage account" in settings is a page to browse, so it is gated.
- **User-initiated sharing and export**: the share sheet (`startSharePlainTextIntent`, media and file share), saving a
  file, and "open with" on a received file.
- **System screens**: app settings, notification settings, location settings, install-unknown-apps permission, ringtone
  picker.
