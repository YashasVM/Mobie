package dev.yashasvm.mobie.core.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Section facts from a LiteRT-LM container header: the flatbuffer that precedes every section's
 * data. A few kilobytes describe what the multi-gigabyte file contains, so Mobie can read them
 * with an HTTP range request before download and from the local file before load.
 *
 * Classification mirrors LiteRT-LM 0.18.0 `schema/model_info/model_info.cc`.
 */
data class LiteRtArtifactHeader(
    val majorVersion: Int,
    val minorVersion: Int,
    val sections: List<LiteRtHeaderSection>,
) {
    private val mainSections get() = sections.filter { it.isTfLite && it.modelType in MAIN_LLM_MODEL_TYPES }

    /** True only for a chat/text-generation container: LLM metadata plus a main decoder graph. */
    val isLlm: Boolean get() = sections.any { it.dataType == DATA_TYPE_LLM_METADATA } && mainSections.isNotEmpty()

    val hasVisionEncoder: Boolean get() = sections.any { it.isTfLite && it.modelType == "tf_lite_vision_encoder" }

    /** An MTP drafter section; LiteRT-LM can then run speculative decoding on CPU and GPU. */
    val hasSpeculativeDrafter: Boolean get() = sections.any { it.isTfLite && it.modelType == MTP_DRAFTER_MODEL_TYPE }

    val speculativeDrafterBytes: Long
        get() = sections.filter { it.isTfLite && it.modelType == MTP_DRAFTER_MODEL_TYPE }.sumOf { it.sizeBytes }

    /**
     * Why the main decoder needs a specific accelerator runtime, or null when it is portable.
     * Portable means LiteRT-LM's own default: no constraint (CPU and GPU), or only `cpu`/`gpu`.
     * Artisan graphs, NPU graphs, SoC-bound sections and NPU auxiliary graphs are not portable.
     */
    val acceleratorRequirement: String?
        get() {
            val main = mainSections
            main.firstOrNull { it.socName != null }?.let { return "built for ${it.socName}" }
            main.firstOrNull { it.modelType == "tf_lite_artisan_text_decoder" }?.let { return "GPU-only artisan build" }
            main.flatMap { it.backendConstraints }.firstOrNull { it !in PORTABLE_BACKENDS }?.let { return "requires $it" }
            if (sections.any { it.isTfLite && it.modelType == "tf_lite_aux" }) return "includes an NPU graph"
            return null
        }

    /** CPU must stay available as Auto's fallback; a GPU-only build cannot fall back. */
    val runsOnCpu: Boolean
        get() = mainSections.isNotEmpty() && mainSections.all { section ->
            section.modelType != "tf_lite_artisan_text_decoder" &&
                (section.backendConstraints.isEmpty() || "cpu" in section.backendConstraints)
        }

    companion object {
        const val PREAMBLE_BYTES = 32
        /** Real headers are under 4 KiB; refuse anything implausibly large instead of buffering it. */
        const val MAX_HEADER_BYTES = 256 * 1024
        internal const val DATA_TYPE_TFLITE_MODEL = 3
        internal const val DATA_TYPE_LLM_METADATA = 5
        internal const val MTP_DRAFTER_MODEL_TYPE = "tf_lite_mtp_drafter"
        private const val SUPPORTED_MAJOR_VERSION = 1
        private val MAGIC = "LITERTLM".toByteArray(Charsets.US_ASCII)
        private val MAIN_LLM_MODEL_TYPES = setOf(
            "tf_lite_prefill_decode", "tf_lite_prefill_decode_hw", "tf_lite_prefill", "tf_lite_decode",
            "tf_lite_artisan_text_decoder",
        )
        private val PORTABLE_BACKENDS = setOf("cpu", "gpu")
        private val SOC_KEYS = setOf("soc_name", "soc_model", "target_soc", "soc", "chipset", "target_device")

        /**
         * Returns the absolute offset where the header ends, from the 32-byte preamble.
         * Throws [IllegalArgumentException] for a non-LiteRT-LM file, an unsupported major
         * version, or an implausible header size.
         */
        fun headerEndOffset(preamble: ByteArray): Int {
            require(preamble.size >= PREAMBLE_BYTES) { "LiteRT-LM preamble is truncated." }
            require(preamble.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a LiteRT-LM file." }
            val buffer = ByteBuffer.wrap(preamble).order(ByteOrder.LITTLE_ENDIAN)
            val major = buffer.getInt(8)
            require(major == SUPPORTED_MAJOR_VERSION) { "Unsupported LiteRT-LM container version $major." }
            val end = buffer.getLong(24)
            require(end in PREAMBLE_BYTES.toLong()..MAX_HEADER_BYTES.toLong()) { "Implausible LiteRT-LM header size." }
            return end.toInt()
        }

        /** Parses a complete header (the file's first [headerEndOffset] bytes). Malformed input throws. */
        fun parse(bytes: ByteArray): LiteRtArtifactHeader {
            val end = headerEndOffset(bytes)
            require(bytes.size >= end) { "LiteRT-LM header is truncated." }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return try {
                FlatReader(buffer, PREAMBLE_BYTES, end).readHeader(
                    majorVersion = buffer.getInt(8),
                    minorVersion = buffer.getInt(12),
                )
            } catch (error: IndexOutOfBoundsException) {
                throw IllegalArgumentException("Malformed LiteRT-LM header.", error)
            }
        }
    }

    /**
     * Minimal bounds-checked reader for `litertlm_header_schema.fbs`. Every offset is checked
     * against the header region, so a corrupt or hostile header fails instead of reading past it.
     */
    private class FlatReader(private val buffer: ByteBuffer, private val start: Int, private val end: Int) {
        fun readHeader(majorVersion: Int, minorVersion: Int): LiteRtArtifactHeader {
            val root = indirect(start)
            val sectionMetadata = field(root, 1) ?: throw IllegalArgumentException("Missing section metadata.")
            val objects = field(indirect(sectionMetadata), 0) ?: throw IllegalArgumentException("Missing sections.")
            return LiteRtArtifactHeader(majorVersion, minorVersion, vector(objects).map { readSection(indirect(it)) })
        }

        private fun readSection(table: Int): LiteRtHeaderSection {
            val items = field(table, 0)?.let { pointer -> vector(pointer).map { readItem(indirect(it)) } }.orEmpty()
            val begin = field(table, 1)?.let { long(it) } ?: 0L
            val finish = field(table, 2)?.let { long(it) } ?: 0L
            require(begin >= 0 && finish >= begin) { "Section has invalid offsets." }
            val dataType = field(table, 3)?.let { byte(it) } ?: 0
            val strings = items.filter { it.second != null }.associate { (key, value) -> key.lowercase() to value!! }
            return LiteRtHeaderSection(
                dataType = dataType,
                modelType = strings["model_type"]?.lowercase(),
                backendConstraints = strings["backend_constraint"]?.lowercase()
                    ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty(),
                socName = SOC_KEYS.firstNotNullOfOrNull { strings[it] }?.takeIf(String::isNotBlank),
                sizeBytes = finish - begin,
            )
        }

        /** Key plus its string value; non-string values are irrelevant to classification. */
        private fun readItem(table: Int): Pair<String, String?> {
            val key = string(field(table, 0) ?: throw IllegalArgumentException("Header item without a key."))
            val valueType = field(table, 1)?.let { byte(it) } ?: 0
            val value = if (valueType == VDATA_STRING_VALUE) {
                field(table, 2)?.let { pointer -> field(indirect(pointer), 0)?.let(::string) }
            } else {
                null
            }
            return key to value
        }

        /** Absolute position of a table field, or null when absent from the vtable. */
        private fun field(table: Int, index: Int): Int? {
            val vtable = table - int(table)
            inRange(vtable)
            val vtableSize = short(vtable)
            val entry = 4 + index * 2
            if (entry + 2 > vtableSize) return null
            val offset = short(vtable + entry)
            return if (offset == 0) null else (table + offset).also(::inRange)
        }

        private fun indirect(position: Int): Int = (position + uint(position)).also(::inRange)

        private fun vector(pointer: Int): List<Int> {
            val vector = indirect(pointer)
            val count = uint(vector)
            require(count in 0..((end - vector) / 4)) { "Header vector is out of range." }
            return List(count) { vector + 4 + it * 4 }
        }

        private fun string(pointer: Int): String {
            val position = indirect(pointer)
            val length = uint(position)
            require(length in 0..(end - position - 4)) { "Header string is out of range." }
            val bytes = ByteArray(length)
            for (i in 0 until length) bytes[i] = buffer.get(position + 4 + i)
            return String(bytes, Charsets.UTF_8)
        }

        private fun inRange(position: Int) = require(position in start until end) { "Header offset is out of range." }
        private fun span(position: Int, size: Int) { inRange(position); inRange(position + size - 1) }
        private fun byte(position: Int): Int { span(position, 1); return buffer.get(position).toInt() and 0xff }
        private fun short(position: Int): Int { span(position, 2); return buffer.getShort(position).toInt() and 0xffff }
        private fun int(position: Int): Int { span(position, 4); return buffer.getInt(position) }
        private fun uint(position: Int): Int {
            val value = int(position)
            require(value >= 0) { "Header offset is out of range." }
            return value
        }
        private fun long(position: Int): Long { span(position, 8); return buffer.getLong(position) }

        private companion object {
            /** `VData` union tag for `StringValue` in the header schema. */
            const val VDATA_STRING_VALUE = 9
        }
    }
}

data class LiteRtHeaderSection(
    val dataType: Int,
    val modelType: String?,
    val backendConstraints: Set<String>,
    val socName: String?,
    val sizeBytes: Long,
) {
    val isTfLite: Boolean get() = dataType == LiteRtArtifactHeader.DATA_TYPE_TFLITE_MODEL
}
