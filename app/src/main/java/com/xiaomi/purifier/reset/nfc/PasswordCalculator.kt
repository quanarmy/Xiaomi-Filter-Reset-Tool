package com.xiaomi.purifier.reset.nfc

import java.security.MessageDigest
import java.util.Locale

object PasswordCalculator {

    /**
     * Cleans up input UID string: removes colons, spaces, dashes and converts to uppercase
     */
    fun cleanUid(input: String): String {
        return input.replace(":", "")
            .replace(" ", "")
            .replace("-", "")
            .trim()
            .uppercase(Locale.US)
    }

    /**
     * Calculates the 4-byte password for a 7-byte (14 hex chars) UID.
     * Algorithm from Xiaomi RFID reverse engineering:
     * 1. SHA-1 of UID byte array -> 20 bytes hash
     * 2. First byte of hash b0 (converted to unsigned 0..255)
     * 3. 4 indices:
     *      i1 = b0 % 20
     *      i2 = (b0 + 5) % 20
     *      i3 = (b0 + 13) % 20
     *      i4 = (b0 + 17) % 20
     * 4. Password bytes = [hash[i1], hash[i2], hash[i3], hash[i4]]
     */
    fun calculatePassword(uidBytes: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(uidBytes)

        val b0 = hash[0].toInt() and 0xFF
        val idx1 = b0 % 20
        val idx2 = (b0 + 5) % 20
        val idx3 = (b0 + 13) % 20
        val idx4 = (b0 + 17) % 20

        return byteArrayOf(
            hash[idx1],
            hash[idx2],
            hash[idx3],
            hash[idx4]
        )
    }

    fun calculatePasswordHex(uidHex: String): String {
        val clean = cleanUid(uidHex)
        require(clean.length == 14) { "UID must be 7 bytes (14 Hex characters)" }
        val bytes = hexToBytes(clean)
        val pwdBytes = calculatePassword(bytes)
        return bytesToHex(pwdBytes)
    }

    fun getNfcToolsCommand(pwdHex: String): String {
        return "1B${pwdHex},3008,A20800000000"
    }

    fun getFlipperZeroCommand(pwdHex: String): String {
        return "nfc apdu 1B${pwdHex} 3008 A20800000000 3008"
    }

    fun getProxmarkCommand(pwdHex: String): String {
        return "hf 14a raw -c 1B${pwdHex} ; hf 14a raw -c 3008 ; hf 14a raw -c A20800000000"
    }

    fun getChameleonCommand(pwdHex: String): String {
        return "hw raw 1B${pwdHex} ; hw raw 3008 ; hw raw A20800000000"
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format(Locale.US, "%02X", b))
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val clean = cleanUid(hex)
        val len = clean.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                    Character.digit(clean[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
