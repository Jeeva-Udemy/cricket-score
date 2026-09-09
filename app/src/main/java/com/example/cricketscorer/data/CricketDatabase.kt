package com.example.cricketscorer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MatchEntity::class,
        InningsEntity::class,
        BallEventEntity::class,
        SquadEntity::class,
        PlayerEntity::class
    ],
    version = 7, // v7: req #3 Super Over — MatchEntity.wasSuperOver, InningsEntity.isSuperOver
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CricketDatabase : RoomDatabase() {

    abstract fun cricketDao(): CricketDao

    companion object {
        @Volatile private var INSTANCE: CricketDatabase? = null

        /** v6 -> v7 (req #3, Super Over): two new columns, both with safe defaults, so every
         *  existing match/innings row already on a device is left exactly as it was — nothing
         *  here can lose or alter existing scores/history. Written as a real Migration (rather
         *  than left to fallbackToDestructiveMigration below) specifically so upgrading the
         *  app never wipes the local database. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE innings ADD COLUMN isSuperOver INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE matches ADD COLUMN wasSuperOver INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): CricketDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    "cricket_scorer_db"
                )
                    .addMigrations(MIGRATION_6_7)
                    // Safety net for any OTHER schema drift this migration doesn't cover — the
                    // explicit migration above means a normal v6 -> v7 upgrade never hits this
                    // destructive path.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
