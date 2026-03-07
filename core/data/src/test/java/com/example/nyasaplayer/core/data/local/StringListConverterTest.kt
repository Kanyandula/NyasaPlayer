package com.example.nyasaplayer.core.data.local

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun fromStringList_emptyList_returnsEmptyJsonArray() {
        assertEquals("[]", converter.fromStringList(emptyList()))
    }

    @Test
    fun fromStringList_singleElement_returnsJsonArrayWithOneElement() {
        val json = converter.fromStringList(listOf("rock"))
        assertEquals("""["rock"]""", json)
    }

    @Test
    fun fromStringList_multipleElements_returnsJsonArrayPreservingOrder() {
        val json = converter.fromStringList(listOf("rock", "pop", "jazz"))
        val arr = JSONArray(json)
        assertEquals(3, arr.length())
        assertEquals("rock", arr.getString(0))
        assertEquals("pop", arr.getString(1))
        assertEquals("jazz", arr.getString(2))
    }

    @Test
    fun toStringList_blankString_returnsEmptyList() {
        assertEquals(emptyList<String>(), converter.toStringList("   "))
    }

    @Test
    fun toStringList_emptyString_returnsEmptyList() {
        assertEquals(emptyList<String>(), converter.toStringList(""))
    }

    @Test
    fun toStringList_emptyJsonArray_returnsEmptyList() {
        assertEquals(emptyList<String>(), converter.toStringList("[]"))
    }

    @Test
    fun toStringList_singleElement_returnsSingletonList() {
        assertEquals(listOf("rock"), converter.toStringList("""["rock"]"""))
    }

    @Test
    fun toStringList_multipleElements_returnsListPreservingOrder() {
        val result = converter.toStringList("""["rock","pop","jazz"]""")
        assertEquals(listOf("rock", "pop", "jazz"), result)
    }

    @Test
    fun roundTrip_preservesData() {
        val original = listOf("genre1", "genre2", "genre3")
        val json = converter.fromStringList(original)
        val restored = converter.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_specialCharacters_quotes() {
        val original = listOf("""he said "hello"""", "it's fine")
        val json = converter.fromStringList(original)
        val restored = converter.toStringList(json)
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_specialCharacters_unicode() {
        val original = listOf("\u00e9lectronique", "\u65e5\u672c\u8a9e", "\ud83c\udfb5")
        val json = converter.fromStringList(original)
        val restored = converter.toStringList(json)
        assertEquals(original, restored)
    }
}
