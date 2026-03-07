package com.example.nyasaplayer.core.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class StringListConverter {

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        JSONArray(value).toString()

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val jsonArray = JSONArray(value)
        return List(jsonArray.length()) { jsonArray.getString(it) }
    }
}
