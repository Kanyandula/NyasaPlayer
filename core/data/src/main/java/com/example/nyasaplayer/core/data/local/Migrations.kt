package com.example.nyasaplayer.core.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `albums` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `artist_id` TEXT NOT NULL,
                `artist_name` TEXT NOT NULL,
                `image_url` TEXT NOT NULL,
                `song_ids` TEXT NOT NULL,
                `popularity` INTEGER NOT NULL,
                `release_date` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
    }
}
