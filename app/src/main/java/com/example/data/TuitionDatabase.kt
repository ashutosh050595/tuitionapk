package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Student::class, Attendance::class, FeeTransaction::class, AppConfig::class, AppNotification::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TuitionDatabase : RoomDatabase() {
    abstract fun tuitionDao(): TuitionDao

    companion object {
        @Volatile
        private var INSTANCE: TuitionDatabase? = null

        fun getDatabase(context: Context): TuitionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TuitionDatabase::class.java,
                    "tuition_manager_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
