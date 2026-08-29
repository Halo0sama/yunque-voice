package com.halo.yunquevoice.ui

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/** 极简 Tavern PNG 角色卡读写：PNG tEXt chunk 中嵌入角色 JSON（keyword: chara / ccv3）。 */
object TavernPngIO {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    fun readPnghJson(bytes: ByteArray): String? {
        if (bytes.size < 8 || !bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return null
        var offset = 8
        while (offset + 8 <= bytes.size) {
            val len = readInt(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            val dataEnd = dataStart + len
            if (dataEnd > bytes.size) break
            if (type == "tEXt") {
                val data = bytes.copyOfRange(dataStart, dataEnd)
                val nul = data.indexOf(0)
                if (nul >= 0) {
                    val keyword = String(data, 0, nul, Charsets.US_ASCII)
                    if (keyword == "chara" || keyword == "ccv3") {
                        return String(data, nul + 1, data.size - nul - 1, Charsets.UTF_8)
                    }
                }
            }
            offset = dataEnd + 4
        }
        return null
    }

    fun writePngWithJson(json: String): ByteArray {
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
        bmp.recycle()
        val base = baos.toByteArray()
        return insertTextChunk(base, "chara", json)
    }

    private fun insertTextChunk(png: ByteArray, keyword: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(png.copyOfRange(0, 33)) // signature + IHDR chunk
        val data = keyword.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + text.toByteArray(Charsets.UTF_8)
        out.write(chunk("tEXt", data))
        out.write(png.copyOfRange(33, png.size))
        return out.toByteArray()
    }

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intBytes(data.size))
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        out.write(intBytes(crc.value.toInt()))
        return out.toByteArray()
    }

    private fun intBytes(v: Int): ByteArray = byteArrayOf(
        ((v ushr 24) and 0xff).toByte(),
        ((v ushr 16) and 0xff).toByte(),
        ((v ushr 8) and 0xff).toByte(),
        (v and 0xff).toByte()
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
