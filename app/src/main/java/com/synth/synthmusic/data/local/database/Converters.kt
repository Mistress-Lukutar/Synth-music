package com.synth.synthmusic.data.local.database

import androidx.room.TypeConverter

/**
 * Room type converters for complex types.
 */
class Converters {
    @TypeConverter
    fun fromIntList(value: String): List<Int> {
        return if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toInt() }
    }

    @TypeConverter
    fun toIntList(list: List<Int>): String {
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromFloatList(value: String): List<Float> {
        return if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toFloat() }
    }

    @TypeConverter
    fun toFloatList(list: List<Float>): String {
        return list.joinToString(",")
    }
}
