package com.sarthak.olacan.model

/**
 * Represents a parsed raw CAN frame from vcan0.
 *
 * Wire layout of CANPacket.can_frame is 16 bytes:
 * [0:4]  CAN ID (Big-Endian)
 * [4]    DLC (Data Length Code 0..8)
 * [5:8]  Padding
 * [8:16] Data payload
 */
data class CanFrame(
    val id: Int,
    val dlc: Int,
    val data: ByteArray,
    val timestampMs: Long = System.currentTimeMillis()
) {
    val idHex: String
        get() = if (id in 0..0x7FF) ID_HEX_CACHE[id] else String.format("0x%03X", id)

    val dataHex: String
        get() {
            val len = dlc.coerceIn(0, data.size)
            if (len == 0) return ""
            val chars = CharArray(len * 3 - 1)
            var cIdx = 0
            for (i in 0 until len) {
                val b = data[i].toInt() and 0xFF
                chars[cIdx++] = HEX_CHARS[b ushr 4]
                chars[cIdx++] = HEX_CHARS[b and 0x0F]
                if (i < len - 1) chars[cIdx++] = ' '
            }
            return String(chars)
        }

    val compactDataHex: String
        get() {
            val len = dlc.coerceIn(0, data.size)
            if (len == 0) return ""
            val chars = CharArray(len * 2)
            var cIdx = 0
            for (i in 0 until len) {
                val b = data[i].toInt() and 0xFF
                chars[cIdx++] = HEX_CHARS[b ushr 4]
                chars[cIdx++] = HEX_CHARS[b and 0x0F]
            }
            return String(chars)
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as CanFrame
        return id == other.id && dlc == other.dlc && data.contentEquals(other.data) && timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + dlc
        result = 31 * result + data.contentHashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }

    companion object {
        private val HEX_CHARS = "0123456789ABCDEF".toCharArray()
        private val ID_HEX_CACHE = Array(0x800) { "0x" + it.toString(16).uppercase().padStart(3, '0') }

        fun parse(raw: ByteArray, timestampMs: Long = System.currentTimeMillis()): CanFrame? {
            return try {
                if (raw.size < 16) return null

                // Support both big-endian and little-endian ID decoding as seen on MoveOS
                val be = ((raw[0].toInt() and 0xFF) shl 24) or
                        ((raw[1].toInt() and 0xFF) shl 16) or
                        ((raw[2].toInt() and 0xFF) shl 8) or
                        (raw[3].toInt() and 0xFF)

                val le = ((raw[3].toInt() and 0xFF) shl 24) or
                        ((raw[2].toInt() and 0xFF) shl 16) or
                        ((raw[1].toInt() and 0xFF) shl 8) or
                        (raw[0].toInt() and 0xFF)

                val id = if (be in 0..0x1FFFFFFF) be else le
                var dlc = raw[4].toInt() and 0xFF
                if (dlc !in 0..8) dlc = 8

                val data = ByteArray(8)
                val available = (raw.size - 8).coerceAtLeast(0).coerceAtMost(8)
                System.arraycopy(raw, 8, data, 0, available)

                CanFrame(id, dlc, data, timestampMs)
            } catch (_: Throwable) {
                null
            }
        }
    }
}
