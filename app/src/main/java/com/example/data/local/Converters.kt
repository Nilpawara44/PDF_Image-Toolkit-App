package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.ConversionType

class Converters {
    @TypeConverter
    fun fromConversionType(value: ConversionType): String = value.name

    @TypeConverter
    fun toConversionType(value: String): ConversionType = try {
        enumValueOf<ConversionType>(value)
    } catch (e: Exception) {
        ConversionType.PDF_TO_IMAGE
    }
}
