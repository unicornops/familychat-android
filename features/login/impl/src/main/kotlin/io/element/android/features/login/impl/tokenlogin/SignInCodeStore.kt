/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.tokenlogin

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Keeps the single-use token of a control panel sign-in link in memory, and in memory only.
 *
 * Navigation state is parcelled into the Activity's saved state (and from there can outlive the process), so the
 * login params only ever carry the random id returned by [put]; the token itself stays here. After a process death
 * the id no longer resolves and the login flow falls back to the password sign-in instead of replaying a code that
 * has most likely been redeemed already.
 *
 * It holds at most one code, the latest link's: a new link replaces it, [consume] and [discard] clear it, and it
 * expires on its own after [MAX_AGE], longer than the server ever lets a code live.
 */
@SingleIn(AppScope::class)
@Inject
class SignInCodeStore {
    /** Only for tests. */
    internal var timeSource: TimeSource = TimeSource.Monotonic

    private var entry: Entry? = null

    /** Stores [token], replacing any previous one, and returns the id to look it up with. */
    @Synchronized
    fun put(token: String): String {
        val current = entry
        // The same link handled twice (e.g. the launch intent re-delivered) resolves to the same entry.
        if (current != null && current.token == token && !current.isExpired()) return current.id
        return Entry(id = UUID.randomUUID().toString(), token = token, createdAt = timeSource.markNow())
            .also { entry = it }
            .id
    }

    /** Whether a code is still available for [id]. */
    @Synchronized
    fun contains(id: String?): Boolean = find(id) != null

    /** Returns the code for [id] and forgets it: a code is only ever handed out once. */
    @Synchronized
    fun consume(id: String?): String? {
        val found = find(id) ?: return null
        entry = null
        return found.token
    }

    /** Forgets the code for [id], if it is still there. */
    @Synchronized
    fun discard(id: String?) {
        if (find(id) != null) entry = null
    }

    private fun find(id: String?): Entry? {
        val current = entry ?: return null
        if (current.isExpired()) {
            entry = null
            return null
        }
        return current.takeIf { id != null && it.id == id }
    }

    private fun Entry.isExpired() = createdAt.elapsedNow() > MAX_AGE

    private data class Entry(val id: String, val token: String, val createdAt: TimeMark) {
        override fun toString() = "Entry(id=$id, token=<redacted>)"
    }

    private companion object {
        /** The control panel mints codes for 5 minutes and the homeserver clamps any request to 15. */
        val MAX_AGE = 15.minutes
    }
}
