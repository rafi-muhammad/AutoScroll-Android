package com.autoscroll.app.data.repository

import android.content.Context
import com.autoscroll.app.data.local.AutoScrollDatabase
import com.autoscroll.app.data.local.MacroActionEntity
import com.autoscroll.app.data.local.MacroEntity
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MacroRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AutoScrollDatabase.getInstance(appContext).macroDao()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val allMacros: Flow<List<MacroRecord>> = dao.observeAllMacros().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getMacroById(macroId: Long): MacroRecord? {
        return dao.getMacroById(macroId)?.toDomain()
    }

    suspend fun saveNewMacro(
        name: String,
        targetPackageName: String,
        targetAppName: String,
        actions: List<MacroAction>,
        repeatCount: Int = 1
    ): Long {
        val now = System.currentTimeMillis()
        val macroEntity = MacroEntity(
            name = name.trim().ifBlank { "Macro Baru" },
            targetPackageName = targetPackageName,
            targetAppName = targetAppName,
            repeatCount = repeatCount.coerceIn(1, 999),
            createdAtMillis = now,
            updatedAtMillis = now
        )
        val actionEntities = actions.mapIndexed { idx, action ->
            MacroActionEntity(
                macroId = 0L,
                orderIndex = idx,
                actionType = action.type.name,
                startXPercent = action.startXPercent.coerceIn(0.01f, 0.99f),
                startYPercent = action.startYPercent.coerceIn(0.01f, 0.99f),
                endXPercent = action.endXPercent.coerceIn(0.01f, 0.99f),
                endYPercent = action.endYPercent.coerceIn(0.01f, 0.99f),
                durationMs = action.durationMs.coerceIn(0L, 5000L),
                delayAfterMs = action.delayAfterMs.coerceIn(0L, 300_000L),
                note = action.note
            )
        }
        return dao.insertMacroWithActions(macroEntity, actionEntities)
    }

    suspend fun renameMacro(macroId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotEmpty()) {
            dao.renameMacro(macroId, trimmed, System.currentTimeMillis())
        }
    }

    suspend fun updateRepeatCount(macroId: Long, repeatCount: Int) {
        dao.updateRepeatCount(macroId, repeatCount.coerceIn(1, 999), System.currentTimeMillis())
    }

    suspend fun updateMacroActions(macroId: Long, actions: List<MacroAction>) {
        val entities = actions.mapIndexed { idx, action ->
            MacroActionEntity(
                macroId = macroId,
                orderIndex = idx,
                actionType = action.type.name,
                startXPercent = action.startXPercent.coerceIn(0.01f, 0.99f),
                startYPercent = action.startYPercent.coerceIn(0.01f, 0.99f),
                endXPercent = action.endXPercent.coerceIn(0.01f, 0.99f),
                endYPercent = action.endYPercent.coerceIn(0.01f, 0.99f),
                durationMs = action.durationMs.coerceIn(0L, 5000L),
                delayAfterMs = action.delayAfterMs.coerceIn(0L, 300_000L),
                note = action.note
            )
        }
        dao.replaceMacroActions(macroId, entities, System.currentTimeMillis())
    }

    suspend fun deleteMacro(macroId: Long) {
        if (getPersistedActiveMacroId() == macroId) {
            setPersistedActiveMacroId(NO_ACTIVE_MACRO)
        }
        dao.deleteMacroById(macroId)
    }

    fun getPersistedActiveMacroId(): Long {
        return prefs.getLong(KEY_ACTIVE_MACRO_ID, NO_ACTIVE_MACRO)
    }

    fun setPersistedActiveMacroId(macroId: Long) {
        prefs.edit().putLong(KEY_ACTIVE_MACRO_ID, macroId).apply()
    }

    companion object {
        private const val PREFS_NAME = "auto_scroll_macro_prefs"
        private const val KEY_ACTIVE_MACRO_ID = "active_macro_id"
        const val NO_ACTIVE_MACRO = -1L
    }
}
