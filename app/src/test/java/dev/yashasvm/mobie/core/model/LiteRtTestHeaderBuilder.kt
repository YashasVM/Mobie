package dev.yashasvm.mobie.core.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Forward-built flatbuffer tables, including aliased references that ordinary writers avoid. */
internal class LiteRtTestHeaderBuilder {
    private val buffer = ByteBuffer.allocate(LiteRtArtifactHeader.MAX_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    private var cursor = 36
    private data class Table(val position: Int, val vtable: Int)

    private fun reserve(size: Int, alignment: Int = 4): Int {
        cursor = (cursor + alignment - 1) / alignment * alignment
        return cursor.also { cursor += size }
    }

    private fun table(fields: Int): Table {
        val vtable = reserve(4 + fields * 2, 2)
        val position = reserve(8 + fields * 8, 8)
        buffer.putShort(vtable, (4 + fields * 2).toShort())
        buffer.putShort(vtable + 2, (8 + fields * 8).toShort())
        buffer.putInt(position, position - vtable)
        return Table(position, vtable)
    }

    private fun field(table: Table, index: Int): Int {
        val offset = 8 + index * 8
        buffer.putShort(table.vtable + 4 + index * 2, offset.toShort())
        return table.position + offset
    }

    private fun point(pointer: Int, target: Int) {
        require(target > pointer)
        buffer.putInt(pointer, target - pointer)
    }

    private fun vector(count: Int): Int = reserve(4 + count * 4).also { buffer.putInt(it, count) }

    private fun string(value: String): Int {
        val bytes = value.toByteArray()
        val position = reserve(4 + bytes.size + 1)
        buffer.putInt(position, bytes.size)
        bytes.forEachIndexed { index, byte -> buffer.put(position + 4 + index, byte) }
        return position
    }

    private fun writeItems(
        pointer: Int,
        entries: List<Pair<String, String?>>,
        aliases: Int = 1,
        shareKey: Boolean = false,
    ) {
        val vector = vector(entries.size * aliases)
        point(pointer, vector)
        val sharedKeyPointers = mutableListOf<Int>()
        entries.forEachIndexed { index, (key, value) ->
            val item = table(3)
            repeat(aliases) { point(vector + 4 + (index * aliases + it) * 4, item.position) }
            val keyPointer = field(item, 0)
            if (shareKey) sharedKeyPointers += keyPointer else point(keyPointer, string(key))
            if (value != null) {
                buffer.put(field(item, 1), 9)
                val valueTable = table(1)
                point(field(item, 2), valueTable.position)
                point(field(valueTable, 0), string(value))
            }
        }
        if (shareKey && entries.isNotEmpty()) {
            val key = string(entries.first().first)
            sharedKeyPointers.forEach { point(it, key) }
        }
    }

    fun build(
        sectionReferences: Int = 1,
        itemAliases: Int = 1,
        shareKey: Boolean = false,
        entries: List<Pair<String, String?>> = listOf("model_type" to "tf_lite_prefill_decode"),
        systemEntries: List<Pair<String, String?>> = emptyList(),
    ): ByteArray {
        val root = table(2)
        point(32, root.position)
        if (systemEntries.isNotEmpty()) {
            val system = table(1)
            point(field(root, 0), system.position)
            writeItems(field(system, 0), systemEntries)
        }
        val metadata = table(1)
        point(field(root, 1), metadata.position)
        val sections = vector(sectionReferences + 1)
        point(field(metadata, 0), sections)
        val llmMetadata = table(4)
        point(sections + 4, llmMetadata.position)
        buffer.put(field(llmMetadata, 3), 5)
        val main = table(4)
        repeat(sectionReferences) { point(sections + 8 + it * 4, main.position) }
        buffer.put(field(main, 3), 3)
        writeItems(field(main, 0), entries, itemAliases, shareKey)
        "LITERTLM".toByteArray().forEachIndexed { index, byte -> buffer.put(index, byte) }
        buffer.putInt(8, 1)
        buffer.putLong(24, cursor.toLong())
        return buffer.array().copyOf(cursor)
    }
}
