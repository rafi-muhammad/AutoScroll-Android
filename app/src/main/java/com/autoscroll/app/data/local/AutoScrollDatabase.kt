package com.autoscroll.app.data.local

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MacroEntity::class, MacroActionEntity::class],
    version = 2,
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
                )
                    .addMigrations(migrationFromV1(context.applicationContext))
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun migrationFromV1(context: Context): Migration {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = windowManager.currentWindowMetrics.bounds
                bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
            }

            return object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE macro_actions_new (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            macroId INTEGER NOT NULL,
                            orderIndex INTEGER NOT NULL,
                            actionType TEXT NOT NULL,
                            startXPercent REAL NOT NULL,
                            startYPercent REAL NOT NULL,
                            endXPercent REAL NOT NULL,
                            endYPercent REAL NOT NULL,
                            durationMs INTEGER NOT NULL,
                            delayAfterMs INTEGER NOT NULL,
                            note TEXT NOT NULL,
                            FOREIGN KEY(macroId) REFERENCES macros(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        INSERT INTO macro_actions_new (
                            id, macroId, orderIndex, actionType,
                            startXPercent, startYPercent, endXPercent, endYPercent,
                            durationMs, delayAfterMs, note
                        )
                        SELECT
                            id, macroId, orderIndex,
                            CASE
                                WHEN actionType = 'DELAY' THEN 'WAIT'
                                WHEN actionType = 'SCROLL' AND endY < startY THEN 'SCROLL_DOWN'
                                WHEN actionType = 'SCROLL' THEN 'SCROLL_UP'
                                ELSE actionType
                            END,
                            MAX(0.01, MIN(0.99, startX / $screenWidth)),
                            MAX(0.01, MIN(0.99, startY / $screenHeight)),
                            MAX(0.01, MIN(0.99, endX / $screenWidth)),
                            MAX(0.01, MIN(0.99, endY / $screenHeight)),
                            durationMs, delayAfterMs, note
                        FROM macro_actions
                        """.trimIndent()
                    )
                    db.execSQL("DROP TABLE macro_actions")
                    db.execSQL("ALTER TABLE macro_actions_new RENAME TO macro_actions")
                    db.execSQL(
                        "CREATE INDEX index_macro_actions_macroId ON macro_actions (macroId)"
                    )
                }
            }
        }
    }
}
