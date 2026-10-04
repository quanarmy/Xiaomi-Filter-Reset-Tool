package com.xiaomi.purifier.reset.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class TagHistoryManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("xiaomi_filter_history", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HISTORY = "tag_history_json"
        private const val KEY_DEVICES = "saved_devices_json"
        private const val KEY_CHANGE_LOGS = "change_logs_json"
    }

    // ==========================================
    // DEVICE MANAGEMENT
    // ==========================================

    fun getDevices(): List<SavedDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedDevice>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedDevice(
                        uid = obj.getString("uid"),
                        alias = obj.optString("alias", ""),
                        filterType = FilterType.fromProductId(obj.optString("productId")),
                        lastWorkTimeHex = obj.optString("lastWorkTimeHex", "00000000"),
                        lastPercent = obj.optInt("lastPercent", 100),
                        resetCount = obj.optInt("resetCount", 1),
                        lastResetTimestamp = obj.optLong("lastResetTimestamp", System.currentTimeMillis()),
                        firstSeenTimestamp = obj.optLong("firstSeenTimestamp", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.lastResetTimestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDevice(uid: String): SavedDevice? {
        return getDevices().find { it.uid.equals(uid, ignoreCase = true) }
    }

    fun setDeviceAlias(uid: String, alias: String) {
        val list = getDevices().toMutableList()
        val index = list.indexOfFirst { it.uid.equals(uid, ignoreCase = true) }
        if (index != -1) {
            list[index].alias = alias
            saveDevices(list)
        }
    }

    fun recordDeviceReset(
        tagData: FilterTagData,
        newPercent: Int = 100,
        newHex: String = "00000000"
    ): SavedDevice {
        val list = getDevices().toMutableList()
        val existingIndex = list.indexOfFirst { it.uid.equals(tagData.uid, ignoreCase = true) }
        val now = System.currentTimeMillis()

        val device: SavedDevice
        if (existingIndex != -1) {
            val existing = list[existingIndex]
            device = existing.copy(
                lastWorkTimeHex = newHex,
                lastPercent = newPercent,
                resetCount = existing.resetCount + 1,
                lastResetTimestamp = now
            )
            list[existingIndex] = device
        } else {
            device = SavedDevice(
                uid = tagData.uid,
                alias = "",
                filterType = tagData.filterType,
                lastWorkTimeHex = newHex,
                lastPercent = newPercent,
                resetCount = 1,
                lastResetTimestamp = now,
                firstSeenTimestamp = now
            )
            list.add(0, device)
        }
        saveDevices(list)
        return device
    }

    private fun saveDevices(list: List<SavedDevice>) {
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("uid", item.uid)
                put("alias", item.alias)
                put("productId", item.filterType.productIdHex)
                put("lastWorkTimeHex", item.lastWorkTimeHex)
                put("lastPercent", item.lastPercent)
                put("resetCount", item.resetCount)
                put("lastResetTimestamp", item.lastResetTimestamp)
                put("firstSeenTimestamp", item.firstSeenTimestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DEVICES, arr.toString()).apply()
    }

    // ==========================================
    // CHANGE LOGS (AUDIT TRAIL)
    // ==========================================

    fun recordChangeLog(entry: ChangeLogEntry) {
        val logs = getChangeLogs().toMutableList()
        logs.add(0, entry)
        // Keep up to 100 history entries
        val trimmed = if (logs.size > 100) logs.subList(0, 100) else logs
        saveChangeLogs(trimmed)
    }

    fun getChangeLogs(): List<ChangeLogEntry> {
        val raw = prefs.getString(KEY_CHANGE_LOGS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ChangeLogEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    ChangeLogEntry(
                        id = obj.optString("id"),
                        uid = obj.getString("uid"),
                        deviceAlias = obj.optString("deviceAlias", ""),
                        actionType = try {
                            ChangeLogEntry.ActionType.valueOf(obj.optString("actionType", "RESET_100"))
                        } catch (_: Exception) {
                            ChangeLogEntry.ActionType.RESET_100
                        },
                        previousWorkTimeHex = obj.optString("prevHex", "00000000"),
                        previousPercent = obj.optInt("prevPercent", 0),
                        newWorkTimeHex = obj.optString("newHex", "00000000"),
                        newPercent = obj.optInt("newPercent", 100),
                        filterType = FilterType.fromProductId(obj.optString("productId")),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        note = obj.optString("note", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLogsForDevice(uid: String): List<ChangeLogEntry> {
        return getChangeLogs().filter { it.uid.equals(uid, ignoreCase = true) }
    }

    private fun saveChangeLogs(logs: List<ChangeLogEntry>) {
        val arr = JSONArray()
        for (item in logs) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("uid", item.uid)
                put("deviceAlias", item.deviceAlias)
                put("actionType", item.actionType.name)
                put("prevHex", item.previousWorkTimeHex)
                put("prevPercent", item.previousPercent)
                put("newHex", item.newWorkTimeHex)
                put("newPercent", item.newPercent)
                put("productId", item.filterType.productIdHex)
                put("timestamp", item.timestamp)
                put("note", item.note)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CHANGE_LOGS, arr.toString()).apply()
    }

    // ==========================================
    // BACKWARD COMPATIBILITY
    // ==========================================

    fun saveTag(tag: FilterTagData) {
        val list = getHistory().toMutableList()
        list.removeAll { it.uid.equals(tag.uid, ignoreCase = true) }
        list.add(0, tag)
        val trimmed = if (list.size > 50) list.subList(0, 50) else list

        val arr = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject().apply {
                put("uid", item.uid)
                put("pwd", item.passwordHex)
                put("factoryId", item.factoryIdHex)
                put("productId", item.productIdHex)
                put("timeHex", item.manufactureTimeHex)
                put("serialHex", item.serialNumberHex)
                put("workTimeHex", item.workTimeHex)
                put("timestamp", item.timestamp)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    fun getHistory(): List<FilterTagData> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<FilterTagData>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    FilterTagData(
                        uid = obj.optString("uid"),
                        passwordHex = obj.optString("pwd"),
                        factoryIdHex = obj.optString("factoryId"),
                        productIdHex = obj.optString("productId"),
                        filterType = FilterType.fromProductId(obj.optString("productId")),
                        manufactureTimeHex = obj.optString("timeHex"),
                        serialNumberHex = obj.optString("serialHex"),
                        workTimeHex = obj.optString("workTimeHex", "00000000"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
