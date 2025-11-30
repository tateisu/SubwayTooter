package jp.juggler.util.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

/**
 * BinPack format
 *
 * アプリサーバからプッシュサービス経由で受け取るデータを包む。
 * - 構造的にはJSONに似ている。階層を持つデータをエンコードできる。
 *
 * Pros:
 * - バイト配列をエンコードできる。
 * - 整数、浮動少数をエンコードできる。
 * - エンコードされたデータは外部ライブラリの仕様変更に影響されない。
 * - 内部実装のenumの名前やorderに依存しない。
 *
 * Cons:
 * - ORMをサポートしない。
 * - デコード結果の解読は生jsonと同程度に面倒。
 * - ByteArray以外のプリミティブ配列をサポートしない。
 *
 * Usage:
 * ListやMap をエンコード/デコードしたら BinPackMap や BinPackList になる。
 * 利便性のために string(k), bytes(k), map(k) などのメソッドがある。
 *
 * Background:
 * WebPushで暗号化されたバイト配列とHTTPヘッダ情報と宛先ハッシュを送るため
 * バイナリの汎用データフォーマットが欲しかった。
 * 既存のものをいくつか検討した結果、300行程度で済むなら自前実装
 *
 *
 */

fun Any?.encodeBinPack(): ByteArray =
    ByteArrayOutputStream().use {
        BinPackWriter(it).writeValue(this)
        it.flush()
        it.toByteArray()
    }

fun ByteArray.decodeBinPack(): Any? =
    ByteArrayInputStream(this).use {
        BinPackReader(it).readValue()
    }

fun ByteArray.decodeBinPackMap() = decodeBinPack() as? BinPackMap

@Suppress("unused")
fun ByteArray.decodeBinPackList() = decodeBinPack() as? BinPackList

class BinPackMap() : HashMap<Any?, Any?>() {
    constructor(vararg pairs: Pair<Any?, Any?>) : this() {
        putAll(pairs)
    }

    fun string(k: Any?) = get(k) as? String
    fun bytes(k: Any?) = get(k) as? ByteArray
    fun map(k: Any?) = get(k) as? BinPackMap
}

class BinPackList() : ArrayList<Any?>() {
    constructor(vararg args: Any?) : this() {
        addAll(args)
    }

    fun string(k: Int) = elementAtOrNull(k) as? String
    @Suppress("unused")
    fun bytes(k: Int) = elementAtOrNull(k) as? ByteArray
    fun map(k: Int) = elementAtOrNull(k) as? BinPackMap
}

private enum class ValueType(val id: Int) {
    VNull(0),
    VTrue(1),
    VFalse(2),
    VBytes(3),
    VString(4),
    VList(5),
    VMap(6),
    VDouble(7),
    VFloat(8),

    @Suppress("unused")
    VInt8(10), // Byte, signed
    VInt16(11),
    VInt32(12),
    VInt64(13),
    VUInt16(15), // Char, unsigned
    ;

    companion object {
        val valuesCache = values()
        val idMap = valuesCache.associateBy { it.id }
    }
}

private class BinPackWriter(
    private val out: OutputStream,
) {
    private fun writeInt16(n: Int) {
        out.write(n)
        out.write(n.ushr(8))
    }

    private fun writeInt32(n: Int) {
        out.write(n)
        out.write(n.ushr(8))
        out.write(n.ushr(16))
        out.write(n.ushr(24))
    }

    private fun writeInt64(n: Long) {
        writeInt32(n.toInt())
        writeInt32(n.ushr(32).toInt())
    }

    private fun writeDouble(n: Double) = writeInt64(n.toRawBits())
    private fun writeFloat(n: Float) = writeInt32(n.toRawBits())

    private fun writeVType(t: ValueType) = out.write(t.id)
    private fun writeSize(n: Int) = writeInt32(n)

    fun writeValue(value: Any?) {
        when (value) {
            null -> writeVType(ValueType.VNull)
            true -> writeVType(ValueType.VTrue)
            false -> writeVType(ValueType.VFalse)
            is ByteArray -> {
                writeVType(ValueType.VBytes)
                writeSize(value.size)
                out.write(value)
            }

            is String -> {
                writeVType(ValueType.VString)
                val encoded = value.toByteArray(StandardCharsets.UTF_8)
                writeSize(encoded.size)
                out.write(encoded)
            }

            is Collection<*> -> {
                writeVType(ValueType.VList)
                writeSize(value.size)
                for (x in value) {
                    writeValue(x)
                }
            }

            is Array<*> -> {
                writeVType(ValueType.VList)
                writeSize(value.size)
                for (x in value) {
                    writeValue(x)
                }
            }

            is Map<*, *> -> {
                writeVType(ValueType.VMap)
                val entries = value.entries
                writeSize(entries.size)
                for (entry in value.entries) {
                    writeValue(entry.key)
                    writeValue(entry.value)
                }
            }

            is Double -> {
                writeVType(ValueType.VDouble)
                writeDouble(value)
            }

            is Float -> {
                writeVType(ValueType.VFloat)
                writeFloat(value)
            }

            is Long -> {
                writeVType(ValueType.VInt64)
                writeInt64(value)
            }

            is Int -> {
                writeVType(ValueType.VInt32)
                writeInt32(value)
            }

            is Short -> {
                writeVType(ValueType.VInt16)
                writeInt16(value.toInt())
            }

            is Char -> {
                writeVType(ValueType.VUInt16)
                writeInt16(value.code)
            }

            is Byte -> {
                writeVType(ValueType.VInt8)
                out.write(value.toInt())
            }

            else -> error("unsupported type ${value.javaClass.simpleName}")
        }
    }
}

private class BinPackReader(
    private val ins: InputStream,
) {
    private fun readUInt8(): Int {
        val n = ins.read().and(255)
        if (n == -1) error("unexpected end")
        return n
    }

    private fun readUInt16(): Int {
        val b0 = readUInt8()
        val b1 = readUInt8().shl(8)
        return b0.or(b1)
    }

    private fun readInt32(): Int {
        val b0 = readUInt8()
        val b1 = readUInt8().shl(8)
        val b2 = readUInt8().shl(16)
        val b3 = readUInt8().shl(24)
        return b0.or(b1).or(b2).or(b3)
    }

    private fun readInt64(): Long {
        val low = readInt32().toLong().and(0xFFFFFFFFL)
        val high = readInt32().toLong().shl(32)
        return low.or(high)
    }

    private fun readFloat() = Float.fromBits(readInt32())

    private fun readDouble() = Double.fromBits(readInt64())

    private fun readBytes(size: Int) = ByteArray(size).also { dst ->
        var nRead = 0
        while (nRead < size) {
            val delta = ins.read(dst, nRead, size - nRead)
            if (delta < 0) error("unexpected end.")
            if (delta > 0) nRead += delta
        }
    }

    private fun readSize() = readInt32()

    fun readValue(): Any? {
        val id = readUInt8()
        return when (ValueType.idMap[id]) {
            null -> error("unknown type id=$id")
            ValueType.VNull -> null
            ValueType.VTrue -> true
            ValueType.VFalse -> false
            ValueType.VBytes -> readBytes(readSize())
            ValueType.VString -> readBytes(readSize()).toString(StandardCharsets.UTF_8)
            ValueType.VList -> BinPackList().apply {
                val size = readSize()
                ensureCapacity(size)
                repeat(size) { add(readValue()) }
            }
            ValueType.VMap -> BinPackMap().apply {
                repeat(readSize()) {
                    val k = readValue()
                    val v = readValue()
                    put(k, v)
                }
            }
            ValueType.VDouble -> readDouble()
            ValueType.VFloat -> readFloat()
            ValueType.VInt8 -> readUInt8().toByte()
            ValueType.VInt16 -> readUInt16().toShort()
            ValueType.VInt32 -> readInt32()
            ValueType.VInt64 -> readInt64()
            ValueType.VUInt16 -> readUInt16().toChar()
        }
    }
}
