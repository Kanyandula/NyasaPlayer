package com.example.nyasaplayer.data.local

fun toPipeDelimited(items: List<String>): String {
    if (items.isEmpty()) return ""
    return "|${items.joinToString("|")}|"
}

fun fromPipeDelimited(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    return value.trim('|').split("|").filter { it.isNotEmpty() }
}
