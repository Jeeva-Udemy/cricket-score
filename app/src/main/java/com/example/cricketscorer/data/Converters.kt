package com.example.cricketscorer.data

import androidx.room.TypeConverter
import com.example.cricketscorer.model.ExtraType
import com.example.cricketscorer.model.TossDecision
import com.example.cricketscorer.model.WicketType

class Converters {

    @TypeConverter
    fun fromTossDecision(value: TossDecision): String = value.name

    @TypeConverter
    fun toTossDecision(value: String): TossDecision = TossDecision.valueOf(value)

    @TypeConverter
    fun fromExtraType(value: ExtraType): String = value.name

    @TypeConverter
    fun toExtraType(value: String): ExtraType = ExtraType.valueOf(value)

    @TypeConverter
    fun fromWicketType(value: WicketType): String = value.name

    @TypeConverter
    fun toWicketType(value: String): WicketType = WicketType.valueOf(value)
}
