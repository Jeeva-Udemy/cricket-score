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
        PlayerEntity::class,
        PlayerMergeEntity::class
    ],
    // v7: req #3 Super Over — MatchEntity.wasSuperOver, InningsEntity.isSuperOver
    // v8: "Merge players" — new player_merges table
    // v9: overthrows — BallEventEntity.isOverthrow
    version = 9,
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

        /** v7 -> v8: adds the player_merges table only. No existing table is touched, so
         *  upgrading never loses matches, squads or history. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `player_merges` (" +
                        "`mergeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`fromName` TEXT NOT NULL, " +
                        "`fromTeam` TEXT NOT NULL, " +
                        "`toName` TEXT NOT NULL, " +
                        "`toTeam` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v8 -> v9: one new column with a safe default; existing balls are untouched. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ball_events ADD COLUMN isOverthrow INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): CricketDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CricketDatabase::class.java,
                    "cricket_scorer_db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    // Safety net for any OTHER schema drift this migration doesn't cover — the
                    // explicit migration above means a normal v6 -> v7 upgrade never hits this
                    // destructive path.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
