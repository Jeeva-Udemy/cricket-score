package com.example.cricketscorer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        MatchEntity::class,
        InningsEntity::class,
        BallEventEntity::class,
        SquadEntity::class,
        PlayerEntity::class
    ],
    version = 6, // v6: added MatchEntity.shareCode for Cloud Sync (Firestore)
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CricketDatabase : RoomDatabase() {

    abstract fun cricketDao(): CricketDao

    companion object {
        @Volatile private var INSTANCE: CricketDatabase? = null

        fun getInstance(context: Context): CricketDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    "cricket_scorer_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
