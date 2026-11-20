package com.autoscroll.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.autoscroll.app.data.model.MacroAction
import com.autoscroll.app.data.model.MacroActionType
import com.autoscroll.app.data.model.MacroRecord

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val targetPackageName: String,
    val targetAppName: String,
    val repeatCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "macro_actions",
    foreignKeys = [
        ForeignKey(
            entity = MacroEntity::class,
            parentColumns = ["id"],
            childColumns = ["macroId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["macroId"])]
)
data class MacroActionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val macroId: Long,
    val orderIndex: Int,
    val actionType: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val durationMs: Long,
    val delayAfterMs: Long,
    val note: String
)

data class MacroWithActions(
    @Embedded val macro: MacroEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "macroId"
    )
    val actions: List<MacroActionEntity>
) {
    fun toDomain(): MacroRecord {
        return MacroRecord(
            id = macro.id,
            name = macro.name,
            targetPackageName = macro.targetPackageName,
            targetAppName = macro.targetAppName,
            repeatCount = macro.repeatCount.coerceAtLeast(1),
            createdAtMillis = macro.createdAtMillis,
            updatedAtMillis = macro.updatedAtMillis,
            actions = actions.sortedBy { it.orderIndex }.map { entity ->
                val parsedType = runCatching { MacroActionType.valueOf(entity.actionType) }
                    .getOrDefault(MacroActionType.TAP)
                MacroAction(
                    id = entity.id,
                    orderIndex = entity.orderIndex,
                    type = parsedType,
                    startX = entity.startX,
                    startY = entity.startY,
                    endX = entity.endX,
                    endY = entity.endY,
                    durationMs = entity.durationMs,
                    delayAfterMs = entity.delayAfterMs,
                    note = entity.note
                )
            }
        )
    }
}
