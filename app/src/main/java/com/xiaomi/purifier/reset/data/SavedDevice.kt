package com.xiaomi.purifier.reset.data

data class SavedDevice(
    val uid: String,
    var alias: String = "",
    val filterType: FilterType = FilterType.REGULAR,
    var lastWorkTimeHex: String = "00000000",
    var lastPercent: Int = 100,
    var resetCount: Int = 1,
    var lastResetTimestamp: Long = System.currentTimeMillis(),
    val firstSeenTimestamp: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() = if (alias.isNotBlank()) alias else "Filter ${uid.takeLast(6)}"
}
