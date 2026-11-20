package com.autoscroll.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {

    @Transaction
    @Query("SELECT * FROM macros ORDER BY updatedAtMillis DESC")
    fun observeAllMacros(): Flow<List<MacroWithActions>>

    @Transaction
    @Query("SELECT * FROM macros WHERE id = :macroId LIMIT 1")
    suspend fun getMacroById(macroId: Long): MacroWithActions?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMacro(macro: MacroEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<MacroActionEntity>)

    @Query("UPDATE macros SET name = :newName, updatedAtMillis = :updatedAt WHERE id = :macroId")
    suspend fun renameMacro(macroId: Long, newName: String, updatedAt: Long)

    @Query("UPDATE macros SET repeatCount = :repeatCount, updatedAtMillis = :updatedAt WHERE id = :macroId")
    suspend fun updateRepeatCount(macroId: Long, repeatCount: Int, updatedAt: Long)

    @Query("UPDATE macros SET updatedAtMillis = :updatedAt WHERE id = :macroId")
    suspend fun touchMacro(macroId: Long, updatedAt: Long)

    @Query("DELETE FROM macro_actions WHERE macroId = :macroId")
    suspend fun deleteActionsForMacro(macroId: Long)

    @Query("DELETE FROM macros WHERE id = :macroId")
    suspend fun deleteMacroById(macroId: Long)

    @Transaction
    suspend fun insertMacroWithActions(
        macro: MacroEntity,
        actions: List<MacroActionEntity>
    ): Long {
        val insertedId = insertMacro(macro)
        val mapped = actions.mapIndexed { index, action ->
            action.copy(id = 0L, macroId = insertedId, orderIndex = index)
        }
        if (mapped.isNotEmpty()) {
            insertActions(mapped)
        }
        return insertedId
    }

    @Transaction
    suspend fun replaceMacroActions(
        macroId: Long,
        actions: List<MacroActionEntity>,
        updatedAt: Long
    ) {
        deleteActionsForMacro(macroId)
        val mapped = actions.mapIndexed { index, action ->
            action.copy(id = 0L, macroId = macroId, orderIndex = index)
        }
        if (mapped.isNotEmpty()) {
            insertActions(mapped)
        }
        touchMacro(macroId, updatedAt)
    }
}
