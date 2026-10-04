package com.xiaomi.purifier.reset.nfc

import android.nfc.Tag
import android.nfc.tech.NfcA
import com.xiaomi.purifier.reset.data.FilterTagData
import com.xiaomi.purifier.reset.data.FilterType
import java.io.IOException

object XiaomiNfcManager {

    sealed class NfcResult<out T> {
        data class Success<out T>(val data: T, val message: String) : NfcResult<T>()
        data class Error(val message: String, val cause: Throwable? = null) : NfcResult<Nothing>()
    }

    /**
     * Read full filter information from an NFC tag
     */
    fun readFilterTag(tag: Tag): NfcResult<FilterTagData> {
        val nfca = NfcA.get(tag) ?: return NfcResult.Error("Tag does not support NfcA (ISO 14443-3A)")
        
        try {
            nfca.connect()
            nfca.timeout = 5000

            val uidBytes = tag.id
            val uidHex = PasswordCalculator.bytesToHex(uidBytes)
            val pwdBytes = PasswordCalculator.calculatePassword(uidBytes)
            val pwdHex = PasswordCalculator.bytesToHex(pwdBytes)

            // Step 1: Authenticate with calculated password
            authenticate(nfca, pwdBytes)

            // Step 2: Read blocks 04 to 07 (Factory, Product, Time, Serial)
            // Command 0x30 0x04 reads 16 bytes: page 4, 5, 6, 7
            val read04Resp = nfca.transceive(byteArrayOf(0x30.toByte(), 0x04.toByte()))
            if (read04Resp == null || read04Resp.size < 16) {
                return NfcResult.Error("Failed to read Blocks 04-07 from tag")
            }

            val b04 = read04Resp.copyOfRange(0, 4)
            val b05 = read04Resp.copyOfRange(4, 8)
            val b06 = read04Resp.copyOfRange(8, 12)
            val b07 = read04Resp.copyOfRange(12, 16)

            // Step 3: Read block 08 (Work time counter)
            // Command 0x30 0x08 reads 16 bytes: page 8, 9, 10, 11
            val read08Resp = nfca.transceive(byteArrayOf(0x30.toByte(), 0x08.toByte()))
            val b08 = if (read08Resp != null && read08Resp.size >= 4) {
                read08Resp.copyOfRange(0, 4)
            } else {
                byteArrayOf(0, 0, 0, 0)
            }

            val rawMap = mutableMapOf<Int, String>()
            rawMap[4] = PasswordCalculator.bytesToHex(b04)
            rawMap[5] = PasswordCalculator.bytesToHex(b05)
            rawMap[6] = PasswordCalculator.bytesToHex(b06)
            rawMap[7] = PasswordCalculator.bytesToHex(b07)
            rawMap[8] = PasswordCalculator.bytesToHex(b08)

            val productId = PasswordCalculator.bytesToHex(b05)
            val filterType = FilterType.fromProductId(productId)

            val data = FilterTagData(
                uid = uidHex,
                passwordHex = pwdHex,
                factoryIdHex = PasswordCalculator.bytesToHex(b04),
                productIdHex = productId,
                filterType = filterType,
                manufactureTimeHex = PasswordCalculator.bytesToHex(b06),
                serialNumberHex = PasswordCalculator.bytesToHex(b07),
                workTimeHex = PasswordCalculator.bytesToHex(b08),
                rawBlocks = rawMap
            )

            return NfcResult.Success(data, "Tag read successfully!")
        } catch (e: Exception) {
            return NfcResult.Error("Read error: ${e.localizedMessage ?: e.message}", e)
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }

    /**
     * Reset filter to 100% by writing 00 00 00 00 to Block 08
     */
    fun resetFilter(tag: Tag): NfcResult<FilterTagData> {
        return writeWorkTime(tag, byteArrayOf(0, 0, 0, 0), "Filter reset to 100% successfully!")
    }

    /**
     * Write custom usage value to Block 08
     */
    fun writeWorkTime(tag: Tag, workTimeBytes: ByteArray, successMsg: String): NfcResult<FilterTagData> {
        val nfca = NfcA.get(tag) ?: return NfcResult.Error("Tag does not support NfcA")
        
        try {
            nfca.connect()
            nfca.timeout = 5000

            val uidBytes = tag.id
            val pwdBytes = PasswordCalculator.calculatePassword(uidBytes)

            // Step 1: PWD_AUTH
            authenticate(nfca, pwdBytes)

            // Step 2: Write to block 08
            // Command 0xA2, page 0x08, data (4 bytes)
            val writeCmd = byteArrayOf(
                0xA2.toByte(),
                0x08.toByte(),
                workTimeBytes[0],
                workTimeBytes[1],
                workTimeBytes[2],
                workTimeBytes[3]
            )
            nfca.transceive(writeCmd)

            // Step 3: Verify write by reading block 08
            val verifyResp = nfca.transceive(byteArrayOf(0x30.toByte(), 0x08.toByte()))
            if (verifyResp != null && verifyResp.size >= 4) {
                val written = verifyResp.copyOfRange(0, 4)
                if (!written.contentEquals(workTimeBytes)) {
                    return NfcResult.Error("Verification failed: Written data does not match!")
                }
            }

            // Read full data again
            val read04Resp = nfca.transceive(byteArrayOf(0x30.toByte(), 0x04.toByte()))
            val b04 = read04Resp.copyOfRange(0, 4)
            val b05 = read04Resp.copyOfRange(4, 8)
            val b06 = read04Resp.copyOfRange(8, 12)
            val b07 = read04Resp.copyOfRange(12, 16)

            val updated = FilterTagData(
                uid = PasswordCalculator.bytesToHex(uidBytes),
                passwordHex = PasswordCalculator.bytesToHex(pwdBytes),
                factoryIdHex = PasswordCalculator.bytesToHex(b04),
                productIdHex = PasswordCalculator.bytesToHex(b05),
                filterType = FilterType.fromProductId(PasswordCalculator.bytesToHex(b05)),
                manufactureTimeHex = PasswordCalculator.bytesToHex(b06),
                serialNumberHex = PasswordCalculator.bytesToHex(b07),
                workTimeHex = PasswordCalculator.bytesToHex(workTimeBytes)
            )

            return NfcResult.Success(updated, successMsg)
        } catch (e: Exception) {
            return NfcResult.Error("Write error: ${e.localizedMessage ?: e.message}", e)
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }

    /**
     * Create / format a blank NTAG (NTAG213/215/216) into a valid Xiaomi Filter tag
     */
    fun createBlankTag(tag: Tag, selectedType: FilterType): NfcResult<FilterTagData> {
        val nfca = NfcA.get(tag) ?: return NfcResult.Error("Tag does not support NfcA")

        try {
            nfca.connect()
            nfca.timeout = 5000

            val uidBytes = tag.id
            val uidHex = PasswordCalculator.bytesToHex(uidBytes)
            val pwdBytes = PasswordCalculator.calculatePassword(uidBytes)

            // Try to authenticate if already protected, or continue if brand new tag
            try {
                authenticate(nfca, pwdBytes)
            } catch (_: Exception) {
                // Tag might be new/unprotected
            }

            // Block 04: Factory ID (Default genuine Xiaomi: 00004150)
            val b04 = byteArrayOf(0x00, 0x00, 0x41, 0x50)
            writePage(nfca, 0x04, b04)

            // Block 05: Product ID
            val prodBytes = PasswordCalculator.hexToBytes(selectedType.productIdHex)
            writePage(nfca, 0x05, prodBytes)

            // Block 06: Time/Date (e.g. 00200422)
            val b06 = byteArrayOf(0x00, 0x20, 0x04, 0x22)
            writePage(nfca, 0x06, b06)

            // Block 07: Serial Number
            val b07 = byteArrayOf(0xC7.toByte(), 0x83.toByte(), 0xD7.toByte(), 0xB7.toByte())
            writePage(nfca, 0x07, b07)

            // Block 08: Work time = 00000000 (100% life)
            val b08 = byteArrayOf(0x00, 0x00, 0x00, 0x00)
            writePage(nfca, 0x08, b08)

            // Determine tag type (NTAG213 vs 215 vs 216) by trying to read configuration page
            val (auth0Page, pwdPage, packPage) = detectAuthPages(nfca)

            // Write Password & PACK
            writePage(nfca, pwdPage, pwdBytes)
            writePage(nfca, packPage, byteArrayOf(0x00, 0x00, 0x00, 0x00))

            // Protect from page 0x04 onward (AUTH0 = 0x04)
            writePage(nfca, auth0Page, byteArrayOf(0x04, 0x00, 0x00, 0x00))

            val created = FilterTagData(
                uid = uidHex,
                passwordHex = PasswordCalculator.bytesToHex(pwdBytes),
                factoryIdHex = PasswordCalculator.bytesToHex(b04),
                productIdHex = selectedType.productIdHex,
                filterType = selectedType,
                manufactureTimeHex = PasswordCalculator.bytesToHex(b06),
                serialNumberHex = PasswordCalculator.bytesToHex(b07),
                workTimeHex = "00000000"
            )

            return NfcResult.Success(created, "Initialized new Xiaomi filter tag successfully!")
        } catch (e: Exception) {
            return NfcResult.Error("Tag creation error: ${e.localizedMessage ?: e.message}", e)
        } finally {
            try { nfca.close() } catch (_: Exception) {}
        }
    }

    private fun authenticate(nfca: NfcA, pwdBytes: ByteArray) {
        val authCmd = byteArrayOf(
            0x1B.toByte(),
            pwdBytes[0],
            pwdBytes[1],
            pwdBytes[2],
            pwdBytes[3]
        )
        val resp = nfca.transceive(authCmd)
        if (resp == null || resp.isEmpty()) {
            throw IOException("Password authentication received no response")
        }
    }

    private fun writePage(nfca: NfcA, page: Int, data: ByteArray) {
        require(data.size == 4) { "Each page must be exactly 4 bytes" }
        val cmd = byteArrayOf(0xA2.toByte(), page.toByte(), data[0], data[1], data[2], data[3])
        nfca.transceive(cmd)
    }

    private fun detectAuthPages(nfca: NfcA): Triple<Int, Int, Int> {
        return try {
            val resp = nfca.transceive(byteArrayOf(0x30.toByte(), 41.toByte()))
            if (resp != null && resp.size >= 4) {
                Triple(41, 43, 44) // NTAG213
            } else {
                Triple(131, 133, 134) // NTAG215
            }
        } catch (_: Exception) {
            try {
                val resp = nfca.transceive(byteArrayOf(0x30.toByte(), 131.toByte()))
                if (resp != null && resp.size >= 4) {
                    Triple(131, 133, 134) // NTAG215
                } else {
                    Triple(227, 229, 230) // NTAG216
                }
            } catch (_: Exception) {
                Triple(41, 43, 44)
            }
        }
    }
}
