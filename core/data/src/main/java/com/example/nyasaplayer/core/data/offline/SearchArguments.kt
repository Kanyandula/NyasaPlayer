package com.example.nyasaplayer.core.data.offline

/**
 * Normalizes a user-typed query for the DAO search queries: surrounding whitespace off, LIKE
 * wildcards escaped, to pair with their `ESCAPE '\'`.
 *
 * Trimming belongs here rather than in each caller because two of them are the same search from
 * the driver's point of view — the car launcher and Assistant through `MediaBrowseTree` — and a
 * trim in only one of them makes " grace " mean different things on the two paths (spec 3.5).
 *
 * Without it a driver searching for "50%" matches the whole catalogue, and "_" matches any single
 * character. The backslash itself is escaped first, or escaping "%" would produce a second
 * escape sequence out of a literal backslash the user typed.
 */
internal fun escapeLikeArgument(query: String): String = query
    .trim()
    .replace("""\""", """\\""")
    .replace("%", """\%""")
    .replace("_", """\_""")
