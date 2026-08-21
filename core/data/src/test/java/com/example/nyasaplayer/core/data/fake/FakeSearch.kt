package com.example.nyasaplayer.core.data.fake

import com.example.nyasaplayer.core.common.models.Album
import com.example.nyasaplayer.core.common.models.Artist

/**
 * The DAO search ordering, in Kotlin, for the repository fakes.
 *
 * A mirror of SQL is a copy that can drift, so the SQL itself is covered by
 * `CatalogSearchDaoTest` against a real in-memory database. This one only has to be close enough
 * that a caller under test sees best-match-first.
 */
internal fun List<Album>.searchByName(
    query: String,
    limit: Int,
    secondary: (Album) -> String,
): List<Album> = searchRanked(query, limit, { it.name }, secondary, { it.popularity }, { it.id })

internal fun List<Artist>.searchByName(query: String, limit: Int): List<Artist> =
    searchRanked(query, limit, { it.name }, { "" }, { it.popularity }, { it.id })

private fun <T> List<T>.searchRanked(
    query: String,
    limit: Int,
    name: (T) -> String,
    secondary: (T) -> String,
    popularity: (T) -> Int,
    id: (T) -> String,
): List<T> {
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return emptyList()
    return filter {
        name(it).lowercase().contains(needle) || secondary(it).lowercase().contains(needle)
    }.sortedWith(
        compareBy(
            { tierOf(name(it).lowercase(), needle) },
            { -popularity(it) },
            { name(it).lowercase() },
            { id(it) },
        ),
    ).take(limit)
}

private fun tierOf(lowercaseName: String, needle: String): Int = when {
    lowercaseName == needle -> 0
    lowercaseName.startsWith(needle) -> 1
    lowercaseName.contains(needle) -> 2
    else -> 3
}
