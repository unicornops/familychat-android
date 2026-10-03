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
| Start any other intent that leaves the app (another app, maps, "open with", GIF attribution) | `Context.startActivityBehindParentalGate(intent, fallback)` |
| Run something that is not an intent only for an adult (a purchase, if one is ever added) | `rememberLauncherForActivityResult(ParentalGateResultContract()) { passed -> ... }` |
| Open an account-provider page for an adult action while signed in (adding an account, identity reset, approving a new device) | `Activity.openAccountUrlBehindParentalGate()`: the gate, then a locked-down Custom Tab |
| The FIRST sign-in on the device | `Activity.openAuthenticationUrlInChromeCustomTab()`, which needs `@OptIn(ParentalGateExempt::class)`; only `LoginFlowNode` uses it, and only when no account is signed in |

The control-panel settings entry (#13) uses `openUrlInExternalApp()`, so it is gated like any other link.

**Future work that must use the gate**: the GIF attribution link (#238).

## Exits closed rather than gated

- **Compose text selection menus**: `ElementThemeApp`, which wraps every activity's content (`KonsistParentalGateTest`
  checks it), adds `ParentalGateSafeContent`. It provides `SafeUriHandler` as `LocalUriHandler` (links in Compose text
  go through the gate) and filters every text menu of the window down to cut, copy, paste, select all and autofill. The
  Application also calls `disableTextActionsThatLeaveTheApp()`, which turns off Compose smart selection (Open, Call, Map)
  and, through Compose's internal test hook (reflection, kept by `consumer-rules.pro`), its Process-Text items, for
  dialogs and bottom sheets the root filter cannot reach.
- **View text fields**: `TextView.hideTextActionsThatLeaveTheApp()` (in `androidutils/text`) turns off the text
  classifier and hides Process-Text / web search items in the markdown composer. The rich text composer's EditText is
  created inside the wysiwyg library, so the composer finds it under its host view and applies the same helper, and
  `MainActivity.onActionModeStarted` filters any selection toolbar of the main window as a last line of defence.
  Package visibility does not protect us here: the `CustomTabsService` query makes every browser visible to the app, the
  UnifiedPush queries make distributors visible, and Android 7 to 10 list every app.
- **Notifications**: every notification sets `setAllowSystemGeneratedContextualActions(false)`, so the system adds no
  "open link" / "call" suggestions built from the message text.
- **Map attribution**: MapLibre's attribution button opens the browser from its own dialog, so it is turned off;
  `MapAttribution` shows the MapTiler / OpenStreetMap attribution as text, and a tap opens the licence page through
  the gate.
- **Element Call web view**: long press is disabled (no link menus, previews or text selection), and a link the user
  taps to another site goes through the gate. Scripted navigations and same-site links stay in the web view (our
  own hosted Element Call).
- **Installing apps**: `REQUEST_INSTALL_PACKAGES` is removed from the app manifest and the media viewer has no "Install"
  action; an APK received in a chat can be saved or shared.

## Not gated, and why

- **Links the app handles itself**: the openers first resolve the link against the app's own intent filters
  (App Links on `https://safechat.family/app/`, `matrix:`, the notification deep link, the OAuth redirect) and open
  those in-app, pinned to our package. `matrix.to` permalinks never reach the openers: the timeline routes them in-app.
- **The first sign-in**: the OAuth Custom Tab on the family's own account provider, while no account is signed in on
  the device. It hands back to the app through its redirect; gating it would lock children out of their own account.
  The tab is locked down: no "Open in browser", share, bookmark or download, and links are not handed to other apps.
  (`AuthTabIntent` would be stricter, but it returns the redirect as an activity result, not through the OAuth redirect
  intent filter the sign-in flow is built on.) Adding a second account, resetting the identity and approving a new
  device are gated. "Manage account" in settings is gated.
- **User-initiated sharing and export**: the share sheet (`startSharePlainTextIntent`, media and file share) and saving a
  file. ("Open with" on a received file is gated: the sender chooses the file type.)
- **System screens**: app settings, notification settings, location settings, ringtone picker.

## The Konsist tripwire

`KonsistParentalGateTest` fails when production code outside an allow-list mentions `ACTION_VIEW` (in any form),
`SENDTO` / `DIAL` / `WEB_SEARCH` / `SEND`, `createChooser`, `Intent.parseUri`, `CATEGORY_APP_BROWSER`,
`makeMainSelectorActivity`, Custom Tabs, `getLaunchIntentForPackage`, `AndroidUriHandler`, `URLSpan`,
`LinkMovementMethod` or `Linkify`, which catches new upstream call sites after a rebase. It is a tripwire, not proof of
coverage: it reads only this repository's sources, so exits inside libraries (MapLibre, wysiwyg, WebView, Compose) are
invisible to it, and a file already on the allow-list can gain a new exit unseen.
