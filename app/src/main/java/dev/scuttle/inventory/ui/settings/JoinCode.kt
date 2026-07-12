package dev.scuttle.inventory.ui.settings

private const val JOIN_PATH = "/join/"

/**
 * Reduce whatever the user hands us — a scanned QR, a pasted link, a typed code — to the bare
 * join code the API stores (`XXXX-XXXX`, uppercase).
 *
 * The invite QR deliberately encodes the *link* (`https://inventory.{domain}/join/ABCD-2345`) so a
 * plain camera app lands on the web join page instead of showing gibberish. `POST /households/join`
 * matches `join_code` exactly, so the code has to be lifted back out of that link before we send it
 * (#30) — otherwise every scan 404s.
 *
 * Input we don't recognise is only cleaned up, not rejected: the server stays the authority on what
 * is a valid code.
 */
fun parseJoinCode(raw: String): String {
    val trimmed = raw.trim()
    val cleaned =
        trimmed
            .substringAfterLast(JOIN_PATH, trimmed)
            .takeWhile { it != '?' && it != '#' }
            .trim('/')
            .trim()
            .uppercase()

    val body = cleaned.replace("-", "")
    return if (body.length == 8 && body.all { it.isLetterOrDigit() }) {
        "${body.take(4)}-${body.drop(4)}"
    } else {
        cleaned
    }
}
