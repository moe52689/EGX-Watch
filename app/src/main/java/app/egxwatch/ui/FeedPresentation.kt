package app.egxwatch.ui

/** Short, actionable status on cards; full technical detail is available in instrument details. */
fun feedProblem(error: String, hasValue: Boolean): String {
    val issue = when {
        error.contains("resolve host", true) || error.contains("UnknownHost", true) -> "Cannot reach provider. Check Wi-Fi, mobile data or Private DNS."
        error.contains("timeout", true) || error.contains("timed out", true) -> "Provider is taking too long. Try again shortly."
        error.contains("HTTP 429") -> "Provider is busy. Please try later."
        error.contains("HTTP 5") -> "Provider is temporarily unavailable."
        error.contains("No published", true) || error.contains("missing", true) || error.contains("not published", true) -> "This source has no published value for this instrument."
        error.contains("older data", true) -> "Provider returned older data. Keeping the newer saved value."
        error.contains("future", true) -> "Provider supplied a future valuation date. Waiting for a valid observation."
        else -> "Could not update from this provider. See details or retry."
    }
    return issue + if (hasValue) " Your saved value is safe." else ""
}
