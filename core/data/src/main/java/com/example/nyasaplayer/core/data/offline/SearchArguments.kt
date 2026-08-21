package com.example.nyasaplayer.core.data.offline

/**
 * Escapes the LIKE wildcards in a user-typed query, for the DAO search queries that pair this
 * with `ESCAPE '\'`.
 *
 * Without it a driver searching for "50%" matches the whole catalogue, and "_" matches any single
 * character. The backslash itself is escaped first, or escaping "%" would produce a second
 * escape sequence out of a literal backslash the user typed.
 */
internal fun escapeLikeArgument(query: String): String = query
    .replace("""\""", """\\""")
    .replace("%", """\%""")
    .replace("_", """\_""")
