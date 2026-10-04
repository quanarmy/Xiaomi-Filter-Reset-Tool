package com.xiaomi.purifier.reset.data

data class FilterTagData(
    val uid: String,
    val passwordHex: String,
    val factoryIdHex: String = "",
    val productIdHex: String = "",
    val filterType: FilterType = FilterType.UNKNOWN,
    val manufactureTimeHex: String = "",
    val serialNumberHex: String = "",
    val workTimeHex: String = "00000000",
    val rawBlocks: Map<Int, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Estimated filter remaining life percentage:
     * 00000000 means 100% remaining.
     * Non-zero indicates elapsed running time (little-endian seconds or hours).
     */
    val remainingPercent: Int
        get() {
            if (workTimeHex.replace("0", "").isEmpty()) {
                return 100
            }
            return try {
                val clean = workTimeHex.replace(" ", "")
                if (clean.length != 8) return 0
                // parse little-endian 32-bit integer
                val b0 = clean.substring(0, 2).toLong(16)
                val b1 = clean.substring(2, 4).toLong(16)
                val b2 = clean.substring(4, 6).toLong(16)
                val b3 = clean.substring(6, 8).toLong(16)
                val elapsedSeconds = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
                
                // Average filter lifespan is ~365 days (31,536,000 seconds)
                val maxLifespanSeconds = 365L * 24 * 3600
                val usedRatio = elapsedSeconds.toDouble() / maxLifespanSeconds
                val percent = (100.0 * (1.0 - usedRatio)).toInt()
                percent.coerceIn(0, 100)
            } catch (e: Exception) {
                0
            }
        }

    val isFullLife: Boolean
        get() = workTimeHex.replace("0", "").isEmpty()
}
