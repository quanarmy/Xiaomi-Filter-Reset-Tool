package com.xiaomi.purifier.reset.data

import java.util.UUID

data class ChangeLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val uid: String,
    val deviceAlias: String = "",
    val actionType: ActionType = ActionType.RESET_100,
    val previousWorkTimeHex: String = "00000000",
    val previousPercent: Int = 0,
    val newWorkTimeHex: String = "00000000",
    val newPercent: Int = 100,
    val filterType: FilterType = FilterType.REGULAR,
    val timestamp: Long = System.currentTimeMillis(),
    val note: String = ""
) {
    enum class ActionType(val labelEn: String, val labelVi: String) {
        RESET_100("Reset to 100%", "Reset về 100%"),
        CUSTOM_WRITE("Custom Usage %", "Ghi % tuỳ chọn"),
        CLONE_BLANK("Initialize Blank Tag", "Tạo thẻ mới (OEM)"),
        RESTORE("Restore State", "Khôi phục bản ghi cũ");

        val label: String
            get() = labelEn
    }
}
