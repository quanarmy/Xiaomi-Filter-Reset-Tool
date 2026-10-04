package com.xiaomi.purifier.reset

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.xiaomi.purifier.reset.data.ChangeLogEntry
import com.xiaomi.purifier.reset.data.FilterTagData
import com.xiaomi.purifier.reset.data.FilterType
import com.xiaomi.purifier.reset.data.SavedDevice
import com.xiaomi.purifier.reset.data.TagHistoryManager
import com.xiaomi.purifier.reset.databinding.ActivityMainBinding
import com.xiaomi.purifier.reset.databinding.DialogCustomUsageBinding
import com.xiaomi.purifier.reset.nfc.PasswordCalculator
import com.xiaomi.purifier.reset.nfc.XiaomiNfcManager
import com.xiaomi.purifier.reset.ui.ChangeLogAdapter
import com.xiaomi.purifier.reset.ui.SavedDeviceAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var historyManager: TagHistoryManager

    private lateinit var savedDeviceAdapter: SavedDeviceAdapter
    private lateinit var changeLogAdapter: ChangeLogAdapter

    private var currentTagData: FilterTagData? = null
    private var lastScannedTag: Tag? = null

    // Target device tracking
    private var targetDeviceUid: String? = null
    private var filterLogsByUid: String? = null

    // Operational mode
    private enum class AppNfcMode {
        READ_OR_AUTO_RESET,
        ARMED_RESET,
        ARMED_CUSTOM_WRITE,
        ARMED_BLANK_TAG_CLONE
    }

    private var currentMode = AppNfcMode.READ_OR_AUTO_RESET
    private var customWriteHex = "00000000"
    private var blankTagTargetType = FilterType.REGULAR

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        historyManager = TagHistoryManager(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupBottomNavigation()
        setupResetTab()
        setupCloneTab()
        setupCalcTab()
        setupHistoryTab()
        checkNfcStatus()
    }

    override fun onResume() {
        super.onResume()
        checkNfcStatus()
        enableNfcReaderMode()
        loadDevicesAndLogs()
    }

    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            }
            if (tag != null) {
                onTagDiscovered(tag)
            }
        }
    }

    // ==========================================
    // NFC READER HANDLING
    // ==========================================

    private fun enableNfcReaderMode() {
        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) return
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
        try {
            nfcAdapter?.enableReaderMode(this, this, flags, null)
        } catch (_: Exception) {
            enableForegroundDispatchFallback()
        }
    }

    private fun disableNfcReaderMode() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Exception) {}
        try {
            nfcAdapter?.disableForegroundDispatch(this)
        } catch (_: Exception) {}
    }

    private fun enableForegroundDispatchFallback() {
        if (nfcAdapter == null || !nfcAdapter!!.isEnabled) return
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        val techList = arrayOf(arrayOf("android.nfc.tech.NfcA"))
        try {
            nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
        } catch (_: Exception) {}
    }

    override fun onTagDiscovered(tag: Tag) {
        lastScannedTag = tag
        val tagUidHex = PasswordCalculator.bytesToHex(tag.id)

        // Check if targeted to a specific device
        if (targetDeviceUid != null && !tagUidHex.equals(targetDeviceUid, ignoreCase = true)) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "⚠️ Scanned tag ($tagUidHex) does not match target device ($targetDeviceUid)!",
                    Toast.LENGTH_LONG
                ).show()
                updateStatusLog("⚠️ Tag UID does not match target: $tagUidHex != $targetDeviceUid")
            }
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            when (currentMode) {
                AppNfcMode.READ_OR_AUTO_RESET -> {
                    if (binding.switchAutoReset.isChecked) {
                        performReset(tag)
                    } else {
                        performRead(tag)
                    }
                }
                AppNfcMode.ARMED_RESET -> {
                    performReset(tag)
                    currentMode = AppNfcMode.READ_OR_AUTO_RESET
                    targetDeviceUid = null
                }
                AppNfcMode.ARMED_CUSTOM_WRITE -> {
                    performCustomWrite(tag, customWriteHex)
                    currentMode = AppNfcMode.READ_OR_AUTO_RESET
                }
                AppNfcMode.ARMED_BLANK_TAG_CLONE -> {
                    performBlankClone(tag, blankTagTargetType)
                    currentMode = AppNfcMode.READ_OR_AUTO_RESET
                }
            }
        }
    }

    // ==========================================
    // NFC ACTIONS (BACKGROUND THREAD)
    // ==========================================

    private suspend fun performRead(tag: Tag) {
        updateStatusLog("Reading NFC chip data...")
        val result = XiaomiNfcManager.readFilterTag(tag)
        withContext(Dispatchers.Main) {
            when (result) {
                is XiaomiNfcManager.NfcResult.Success -> {
                    vibrateFeedback(success = true)
                    displayTagData(result.data)
                    historyManager.saveTag(result.data)
                    updateStatusLog("✅ ${result.message}\nUID: ${result.data.uid} | PWD: ${result.data.passwordHex} | Block 08: ${result.data.workTimeHex}")
                    Toast.makeText(this@MainActivity, "Filter tag read successfully!", Toast.LENGTH_SHORT).show()
                }
                is XiaomiNfcManager.NfcResult.Error -> {
                    vibrateFeedback(success = false)
                    updateStatusLog("❌ ${result.message}")
                    Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun performReset(tag: Tag) {
        updateStatusLog("Reading current state before Reset...")
        // First read previous state so we can record exact transition in change history
        var prevHex = "00000000"
        var prevPercent = 0
        try {
            val preRead = XiaomiNfcManager.readFilterTag(tag)
            if (preRead is XiaomiNfcManager.NfcResult.Success) {
                prevHex = preRead.data.workTimeHex
                prevPercent = preRead.data.remainingPercent
            }
        } catch (_: Exception) {}

        updateStatusLog("Sending unlock command and resetting Block 08...")
        val result = XiaomiNfcManager.resetFilter(tag)
        withContext(Dispatchers.Main) {
            when (result) {
                is XiaomiNfcManager.NfcResult.Success -> {
                    vibrateFeedback(success = true)
                    displayTagData(result.data)

                    // Record to device history & change log
                    val savedDevice = historyManager.recordDeviceReset(result.data, 100, "00000000")
                    historyManager.recordChangeLog(
                        ChangeLogEntry(
                            uid = result.data.uid,
                            deviceAlias = savedDevice.alias,
                            actionType = ChangeLogEntry.ActionType.RESET_100,
                            previousWorkTimeHex = prevHex,
                            previousPercent = prevPercent,
                            newWorkTimeHex = "00000000",
                            newPercent = 100,
                            filterType = result.data.filterType,
                            note = "Reset filter to 100% successfully"
                        )
                    )
                    loadDevicesAndLogs()

                    updateStatusLog("🎉 ${result.message}\nDevice ${savedDevice.displayTitle} restored to 100% life!")
                    showSuccessDialog(
                        "Reset Successful!",
                        "Device [${savedDevice.displayTitle}] has been reset to 100% life (Block 08 = 00000000).\n\nChange history has been saved so you can track or reset it again anytime."
                    )
                }
                is XiaomiNfcManager.NfcResult.Error -> {
                    vibrateFeedback(success = false)
                    updateStatusLog("❌ Reset error: ${result.message}")
                    Toast.makeText(this@MainActivity, "Reset failed: ${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun performCustomWrite(tag: Tag, hexValue: String) {
        var prevHex = "00000000"
        var prevPercent = 0
        try {
            val preRead = XiaomiNfcManager.readFilterTag(tag)
            if (preRead is XiaomiNfcManager.NfcResult.Success) {
                prevHex = preRead.data.workTimeHex
                prevPercent = preRead.data.remainingPercent
            }
        } catch (_: Exception) {}

        updateStatusLog("Writing custom value $hexValue to Block 08...")
        val bytes = PasswordCalculator.hexToBytes(hexValue)
        val result = XiaomiNfcManager.writeWorkTime(tag, bytes, "Block 08 value written successfully!")
        withContext(Dispatchers.Main) {
            when (result) {
                is XiaomiNfcManager.NfcResult.Success -> {
                    vibrateFeedback(success = true)
                    displayTagData(result.data)

                    val savedDevice = historyManager.recordDeviceReset(result.data, result.data.remainingPercent, hexValue)
                    historyManager.recordChangeLog(
                        ChangeLogEntry(
                            uid = result.data.uid,
                            deviceAlias = savedDevice.alias,
                            actionType = ChangeLogEntry.ActionType.CUSTOM_WRITE,
                            previousWorkTimeHex = prevHex,
                            previousPercent = prevPercent,
                            newWorkTimeHex = hexValue,
                            newPercent = result.data.remainingPercent,
                            filterType = result.data.filterType,
                            note = "Wrote custom value to Block 08"
                        )
                    )
                    loadDevicesAndLogs()

                    updateStatusLog("✅ ${result.message}")
                    Toast.makeText(this@MainActivity, "Write successful!", Toast.LENGTH_SHORT).show()
                }
                is XiaomiNfcManager.NfcResult.Error -> {
                    vibrateFeedback(success = false)
                    updateStatusLog("❌ Write error: ${result.message}")
                }
            }
        }
    }

    private suspend fun performBlankClone(tag: Tag, filterType: FilterType) {
        updateStatusLog("Writing data and configuring password protection on blank tag...")
        val result = XiaomiNfcManager.createBlankTag(tag, filterType)
        withContext(Dispatchers.Main) {
            when (result) {
                is XiaomiNfcManager.NfcResult.Success -> {
                    vibrateFeedback(success = true)
                    binding.tvBlankTagStatus.text = "🎉 ${result.message}\nUID: ${result.data.uid}\nType: ${result.data.filterType.getDisplayName(this@MainActivity)}"

                    val savedDevice = historyManager.recordDeviceReset(result.data, 100, "00000000")
                    historyManager.recordChangeLog(
                        ChangeLogEntry(
                            uid = result.data.uid,
                            deviceAlias = savedDevice.alias,
                            actionType = ChangeLogEntry.ActionType.CLONE_BLANK,
                            previousWorkTimeHex = "BLANK",
                            previousPercent = 0,
                            newWorkTimeHex = "00000000",
                            newPercent = 100,
                            filterType = result.data.filterType,
                            note = "Initialized blank OEM tag successfully"
                        )
                    )
                    loadDevicesAndLogs()

                    showSuccessDialog("Tag Created Successfully!", "Blank tag has been written with Xiaomi filter identification and secured with PWD_AUTH.\n\nYour air purifier will now recognize it as a brand new 100% genuine filter!")
                }
                is XiaomiNfcManager.NfcResult.Error -> {
                    vibrateFeedback(success = false)
                    binding.tvBlankTagStatus.text = "❌ Failed: ${result.message}"
                }
            }
        }
    }

    // ==========================================
    // UI SETUP & NAVIGATION
    // ==========================================

    private fun setupBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            binding.tabResetView.visibility = if (item.itemId == R.id.nav_reset) View.VISIBLE else View.GONE
            binding.tabCloneView.visibility = if (item.itemId == R.id.nav_clone) View.VISIBLE else View.GONE
            binding.tabCalcView.visibility = if (item.itemId == R.id.nav_calc) View.VISIBLE else View.GONE
            binding.tabHistoryView.visibility = if (item.itemId == R.id.nav_history) View.VISIBLE else View.GONE
            binding.tabGuideView.visibility = if (item.itemId == R.id.nav_guide) View.VISIBLE else View.GONE

            if (item.itemId == R.id.nav_history) {
                loadDevicesAndLogs()
            }
            true
        }
    }

    private fun setupResetTab() {
        binding.btnResetNow.setOnClickListener {
            val tag = lastScannedTag
            if (tag != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    performReset(tag)
                }
            } else {
                currentMode = AppNfcMode.ARMED_RESET
                updateStatusLog("⚡ Armed mode: Hold NFC tag against the back of the phone to reset now!")
                Toast.makeText(this, getString(R.string.toast_touch_tag_to_reset), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnManualRead.setOnClickListener {
            val tag = lastScannedTag
            if (tag != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    performRead(tag)
                }
            } else {
                currentMode = AppNfcMode.READ_OR_AUTO_RESET
                updateStatusLog("Waiting for NFC tag to read...")
                Toast.makeText(this, getString(R.string.toast_touch_tag_to_read), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCustomValue.setOnClickListener {
            showCustomUsageDialog()
        }

        binding.btnCopyUid.setOnClickListener {
            currentTagData?.let { copyToClipboard("UID", it.uid) }
        }

        binding.btnCopyPwd.setOnClickListener {
            currentTagData?.let { copyToClipboard("Password", it.passwordHex) }
        }

        binding.btnEditCardAlias.setOnClickListener {
            currentTagData?.let { promptEditDeviceAlias(it.uid) }
        }

        binding.btnNfcStatus.setOnClickListener {
            if (nfcAdapter != null && !nfcAdapter!!.isEnabled) {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        }
    }

    private fun setupCloneTab() {
        binding.btnArmBlankTagWrite.setOnClickListener {
            blankTagTargetType = when (binding.rgFilterTypes.checkedRadioButtonId) {
                R.id.rbTypeFormaldehyde -> FilterType.FORMALDEHYDE
                R.id.rbTypeAntibacterial -> FilterType.ANTI_BACTERIAL
                else -> FilterType.REGULAR
            }

            currentMode = AppNfcMode.ARMED_BLANK_TAG_CLONE
            binding.tvBlankTagStatus.text = "⚡ READY: Hold blank NTAG sticker against back of phone..."
            Toast.makeText(this, getString(R.string.toast_touch_blank_tag), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupCalcTab() {
        calculateAndDisplayCommands(binding.etInputUid.text.toString())

        binding.btnCalcSubmit.setOnClickListener {
            calculateAndDisplayCommands(binding.etInputUid.text.toString())
        }

        binding.btnPasteUid.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).text.toString()
                binding.etInputUid.setText(pasteText)
                calculateAndDisplayCommands(pasteText)
            }
        }

        binding.btnCopyResultPwd.setOnClickListener {
            copyToClipboard("Password", binding.tvResultPwd.text.toString())
        }
        binding.btnCopyNfcTools.setOnClickListener {
            copyToClipboard("NFC Tools Command", binding.tvCmdNfcTools.text.toString())
        }
        binding.btnCopyFlipper.setOnClickListener {
            copyToClipboard("Flipper Zero Command", binding.tvCmdFlipper.text.toString())
        }
        binding.btnCopyProxmark.setOnClickListener {
            copyToClipboard("Proxmark Command", binding.tvCmdProxmark.text.toString())
        }
    }

    private fun calculateAndDisplayCommands(rawUid: String) {
        val clean = PasswordCalculator.cleanUid(rawUid)
        if (clean.length != 14) {
            Toast.makeText(this, "UID must be 14 hex characters (7 bytes). Current: ${clean.length}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pwd = PasswordCalculator.calculatePasswordHex(clean)
            binding.tvResultPwd.text = pwd
            binding.tvCmdNfcTools.text = PasswordCalculator.getNfcToolsCommand(pwd)
            binding.tvCmdFlipper.text = PasswordCalculator.getFlipperZeroCommand(pwd)
            binding.tvCmdProxmark.text = PasswordCalculator.getProxmarkCommand(pwd)
        } catch (e: Exception) {
            Toast.makeText(this, "Calculation error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHistoryTab() {
        // Saved Devices Adapter
        savedDeviceAdapter = SavedDeviceAdapter(
            items = emptyList(),
            onResetClick = { device ->
                // Quick 1-click targeted re-reset for this device!
                targetDeviceUid = device.uid
                currentMode = AppNfcMode.ARMED_RESET
                binding.bottomNav.selectedItemId = R.id.nav_reset
                binding.tvScanPromptTitle.text = "⚡ Waiting for tag: ${device.displayTitle}"
                binding.tvScanPromptSubtitle.text = "Hold filter tag with UID ${device.uid} against phone to reset back to 100%"
                updateStatusLog("⚡ Activated Reset for: ${device.displayTitle} (${device.uid})")
                Toast.makeText(this, "Hold tag for [${device.displayTitle}] to reset!", Toast.LENGTH_LONG).show()
            },
            onEditAliasClick = { device ->
                promptEditDeviceAlias(device.uid)
            },
            onViewLogsClick = { device ->
                // Switch to Change Logs tab and filter by this device
                filterLogsByUid = device.uid
                binding.chipTabChangeLogs.isChecked = true
                loadDevicesAndLogs()
            }
        )

        binding.rvSavedDevices.layoutManager = LinearLayoutManager(this)
        binding.rvSavedDevices.adapter = savedDeviceAdapter

        // Change Logs Adapter
        changeLogAdapter = ChangeLogAdapter(
            items = emptyList(),
            onRestoreClick = { logEntry ->
                customWriteHex = logEntry.previousWorkTimeHex
                currentMode = AppNfcMode.ARMED_CUSTOM_WRITE
                binding.bottomNav.selectedItemId = R.id.nav_reset
                updateStatusLog("⚡ Restore state: Hold tag ${logEntry.uid} to restore value ${logEntry.previousWorkTimeHex}")
                Toast.makeText(this, "Hold tag to restore previous state!", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvChangeLogs.layoutManager = LinearLayoutManager(this)
        binding.rvChangeLogs.adapter = changeLogAdapter

        // Chip switcher between Saved Devices and Change Logs
        binding.chipGroupHistoryMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val isLogs = checkedIds.contains(R.id.chipTabChangeLogs)
            binding.containerDevices.visibility = if (isLogs) View.GONE else View.VISIBLE
            binding.containerChangeLogs.visibility = if (isLogs) View.VISIBLE else View.GONE
            loadDevicesAndLogs()
        }

        binding.btnClearDeviceFilter.setOnClickListener {
            filterLogsByUid = null
            loadDevicesAndLogs()
        }

        binding.btnClearHistory.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_clear_title)
                .setMessage(R.string.dialog_clear_desc)
                .setPositiveButton(R.string.btn_clear_all) { _, _ ->
                    historyManager.clearAll()
                    loadDevicesAndLogs()
                    Toast.makeText(this, "All history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun loadDevicesAndLogs() {
        val devices = historyManager.getDevices()
        savedDeviceAdapter.updateData(devices)
        binding.tvDevicesEmpty.visibility = if (devices.isEmpty()) View.VISIBLE else View.GONE
        binding.rvSavedDevices.visibility = if (devices.isEmpty()) View.GONE else View.VISIBLE
        binding.chipTabDevices.text = getString(R.string.chip_devices, devices.size)

        val allLogs = historyManager.getChangeLogs()
        val filteredLogs = if (filterLogsByUid != null) {
            allLogs.filter { it.uid.equals(filterLogsByUid, ignoreCase = true) }
        } else {
            allLogs
        }

        if (filterLogsByUid != null) {
            val dev = historyManager.getDevice(filterLogsByUid!!)
            val name = dev?.displayTitle ?: filterLogsByUid
            binding.layoutFilterBanner.visibility = View.VISIBLE
            binding.tvFilterBannerText.text = getString(R.string.filter_banner_prefix, name)
        } else {
            binding.layoutFilterBanner.visibility = View.GONE
        }

        changeLogAdapter.updateData(filteredLogs)
        binding.tvLogsEmpty.visibility = if (filteredLogs.isEmpty()) View.VISIBLE else View.GONE
        binding.rvChangeLogs.visibility = if (filteredLogs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun promptEditDeviceAlias(uid: String) {
        val currentDevice = historyManager.getDevice(uid)
        val currentAlias = currentDevice?.alias ?: ""

        val input = EditText(this).apply {
            hint = getString(R.string.dialog_alias_hint)
            setText(currentAlias)
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_alias_title)
            .setMessage(getString(R.string.dialog_alias_desc, uid))
            .setView(input)
            .setPositiveButton(R.string.btn_save_name) { _, _ ->
                val newName = input.text.toString().trim()
                historyManager.setDeviceAlias(uid, newName)
                if (currentTagData?.uid.equals(uid, ignoreCase = true)) {
                    binding.tvCardDeviceAlias.text = if (newName.isNotBlank()) newName else getString(R.string.device_unnamed)
                }
                loadDevicesAndLogs()
                Toast.makeText(this, "Device name saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==========================================
    // UI HELPERS
    // ==========================================

    private fun displayTagData(data: FilterTagData) {
        currentTagData = data
        val device = historyManager.getDevice(data.uid)
        binding.tvCardDeviceAlias.text = device?.displayTitle ?: "Filter ${data.uid.takeLast(6)}"
        binding.tvCardFilterType.text = data.filterType.getDisplayName(this)
        binding.tvCardLifePercent.text = "${data.remainingPercent}%"
        binding.progressFilterLife.progress = data.remainingPercent
        binding.tvCardUid.text = data.uid
        binding.tvCardPwd.text = data.passwordHex
        binding.tvCardWorkTime.text = "${data.workTimeHex} (${if (data.isFullLife) getString(R.string.filter_life_new) else getString(R.string.filter_life_used)})"
        binding.tvCardProductId.text = "${data.productIdHex} (${data.filterType.name})"
    }

    private fun updateStatusLog(msg: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.tvStatusLog.text = msg
        }
    }

    private fun checkNfcStatus() {
        when {
            nfcAdapter == null -> {
                binding.tvNfcStatus.text = getString(R.string.nfc_unsupported)
                binding.viewNfcIndicator.setBackgroundResource(R.drawable.bg_status_badge)
            }
            !nfcAdapter!!.isEnabled -> {
                binding.tvNfcStatus.text = getString(R.string.nfc_disabled)
                binding.viewNfcIndicator.setBackgroundColor(getColor(R.color.status_red))
            }
            else -> {
                binding.tvNfcStatus.text = getString(R.string.nfc_ready)
                binding.viewNfcIndicator.setBackgroundColor(getColor(R.color.status_green))
            }
        }
    }

    private fun showCustomUsageDialog() {
        val dialogBinding = DialogCustomUsageBinding.inflate(layoutInflater)
        
        dialogBinding.sliderPercent.addOnChangeListener { _, value, _ ->
            val percent = value.toInt()
            dialogBinding.tvSliderPercent.text = "$percent%"
            
            val maxSeconds = 365L * 24 * 3600
            val usedRatio = (100 - percent) / 100.0
            val seconds = (maxSeconds * usedRatio).toLong()
            
            val b0 = (seconds and 0xFF).toInt()
            val b1 = ((seconds shr 8) and 0xFF).toInt()
            val b2 = ((seconds shr 16) and 0xFF).toInt()
            val b3 = ((seconds shr 24) and 0xFF).toInt()
            
            val hex = String.format(Locale.US, "%02X%02X%02X%02X", b0, b1, b2, b3)
            dialogBinding.etCustomHex.setText(hex)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_custom_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.btn_write_value) { _, _ ->
                val targetHex = dialogBinding.etCustomHex.text.toString().trim()
                if (targetHex.length != 8) {
                    Toast.makeText(this, "Hex value must be 8 characters (4 bytes)", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                customWriteHex = targetHex
                val tag = lastScannedTag
                if (tag != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        performCustomWrite(tag, customWriteHex)
                    }
                } else {
                    currentMode = AppNfcMode.ARMED_CUSTOM_WRITE
                    updateStatusLog("⚡ Ready: Hold NFC tag against phone to write value $customWriteHex")
                    Toast.makeText(this, "Hold NFC tag against phone to write", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSuccessDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setIcon(R.drawable.ic_check_circle)
            .setPositiveButton(R.string.btn_great, null)
            .show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.toast_copied, label), Toast.LENGTH_SHORT).show()
    }

    private fun vibrateFeedback(success: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (success) {
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 100), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (success) 150 else 300)
            }
        } catch (_: Exception) {}
    }
}
