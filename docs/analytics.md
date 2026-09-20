# Analytics in Family Chat

<!--- TOC -->

* [There are none](#there-are-none)

<!--- END -->

## There are none

Family Chat ships **no** third-party analytics or crash reporting. Both PostHog and Sentry are
excluded from the build: `SERVICES_POSTHOG_APIKEY`, `SERVICES_POSTHOG_HOST`, `SERVICES_SENTRY_DSN`
and `SERVICES_SENTRY_DSN_RUST` are empty in `plugins/src/main/kotlin/config/BuildTimeConfig.kt`, so
`ModulesConfig.analyticsConfig` resolves to `AnalyticsConfig.Disabled` and `:services:analytics:noop`
is compiled in instead of `:services:analyticsproviders:posthog` and `:services:analyticsproviders:sentry`.

Upstream Element X sends analytics to Element's own PostHog and Sentry projects; those keys and DSNs
are not present in this fork and must not be reintroduced. See
[unicornops/family-chat#232](https://github.com/unicornops/family-chat/issues/232) decisions and the
"no third-party processing of user content" stance.
