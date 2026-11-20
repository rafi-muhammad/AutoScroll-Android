package com.autoscroll.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MacroEntity::class, MacroActionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AutoScrollDatabase : RoomDatabase() {

    abstract fun macroDao(): MacroDao

    companion object {
        @Volatile
        private var INSTANCE: AutoScrollDatabase? = null

        fun getInstance(context: Context): AutoScrollDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoScrollDatabase::class.java,
                    "autoscroll_macros.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
