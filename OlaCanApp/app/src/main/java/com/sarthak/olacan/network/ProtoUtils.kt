package com.sarthak.olacan.network

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

object ProtoUtils {

    fun encodeVarint(value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v != 0L) {
                out.write(b or 0x80)
            } else {
                out.write(b)
                break
            }
        }
        return out.toByteArray()
    }

    fun buildCommandPayload(command: String): ByteArray {
        val raw = command.toByteArray(StandardCharsets.UTF_8)
        val tag = encodeVarint(((1 shl 3) or 2).toLong())
        val len = encodeVarint(raw.size.toLong())
        return tag + len + raw
    }

    fun readVarint(buf: ByteArray, start: Int): Pair<Long, Int> {
        var value = 0L
        var shift = 0
        var i = start
        while (i < buf.size) {
            val b = buf[i++].toLong()
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80L) == 0L) {
                return Pair(value, i)
            }
            shift += 7
        }
        return Pair(value, i)
    }

    /**
     * Decodes a CANPacket: Field 1 is `bytes can_frame = 1`.
     */
    fun extractCanFrameBytes(buf: ByteArray): ByteArray? {
        var i = 0
        while (i < buf.size) {
            val (key, nextI) = readVarint(buf, i)
            i = nextI
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()

            if (wire == 2) {
                val (len, endI) = readVarint(buf, i)
                i = endI
                val length = len.toInt()
                if (field == 1 && i + length <= buf.size) {
                    val frameBytes = ByteArray(length)
                    System.arraycopy(buf, i, frameBytes, 0, length)
                    return frameBytes
                }
                i += length
            } else if (wire == 0) {
                val (_, endI) = readVarint(buf, i)
                i = endI
            } else {
                break
            }
        }
        return null
    }

    /**
     * Decodes Response { RespStatus status = 1; string status_msg = 2; }
     */
    fun decodeResponse(buf: ByteArray): Pair<Int, String> {
        var status = 0
        var message = ""
        var i = 0

        while (i < buf.size) {
            val (key, nextI) = readVarint(buf, i)
            i = nextI
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()

            when (wire) {
                0 -> {
                    val (valVarint, endI) = readVarint(buf, i)
                    i = endI
                    if (field == 1) status = valVarint.toInt()
                }
                2 -> {
                    val (len, endI) = readVarint(buf, i)
                    i = endI
                    val length = len.toInt()
                    if (i + length <= buf.size) {
                        val str = String(buf, i, length, StandardCharsets.UTF_8)
                        if (field == 2) message = str
                        i += length
                    } else {
                        break
                    }
                }
                else -> break
            }
        }
        return Pair(status, message)
    }
}
