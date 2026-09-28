package app.egxwatch.ui

import app.egxwatch.domain.*
import java.time.Instant

/** Presentation only: missing entitlement must never turn a cached value into a live feed. */
fun egxMonitoringLabel(enabled: Boolean, configured: Boolean, sessionOpen: Boolean): String = when {
    !configured -> "DATA UNAVAILABLE"
    !enabled -> "PAUSED"
    !sessionOpen -> "SESSION CLOSED"
    else -> "ACTIVE"
}

fun observationLabel(quote: Quote?, configured: Boolean, hasSavedValue: Boolean, now: Instant): String {
    if (!configured) return if (hasSavedValue) "UNAVAILABLE · last saved value retained" else "UNAVAILABLE · no verified source"
    if (quote == null) return if (hasSavedValue) "Saved value · freshness unverified" else "UNAVAILABLE · no observation"
    val freshness = quote.freshness(now)
    if (freshness == Freshness.UNAVAILABLE) return "UNAVAILABLE · unverified observation"
    return if (quote.kind == DataKind.NAV) {
        if (freshness == Freshness.STALE) "STALE · published NAV" else "Published NAV · valuation date below"
    } else freshness.name
}

fun missingFeedMessage(ticker: String, isFund: Boolean): String =
    "$ticker · ${if (isFund) "NAV" else "price"} unavailable: no verified automatic source. Custom authorized gateway available in Settings."
