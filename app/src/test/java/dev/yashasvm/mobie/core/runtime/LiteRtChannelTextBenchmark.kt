package dev.yashasvm.mobie.core.runtime


/** Opt-in host JVM benchmark, not an Android inference benchmark or a timing-based unit test. */
object LiteRtChannelTextBenchmark {
    @Volatile private var sink: Any? = null
    private val reasoning = setOf("analysis", "thinking", "cot")

    private fun baseline(channels: Map<String, String>): LiteRtChannelText.Split {
        if (channels.isEmpty()) return LiteRtChannelText.Split("", "")
        val thought = StringBuilder()
        val visible = StringBuilder()
        for ((name, value) in channels) {
            if (name.lowercase() in reasoning) thought.append(value) else visible.append(value)
        }
        return LiteRtChannelText.Split(
            if (thought.isEmpty()) "" else thought.toString(),
            if (visible.isEmpty()) "" else visible.toString(),
        )
    }

    @JvmStatic fun main(args: Array<String>) {
        val iterations = args.firstOrNull()?.toInt() ?: 1_000_000
        // Reflection keeps host-only management APIs out of Android's compile classpath.
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val type = Class.forName("com.sun.management.ThreadMXBean")
        type.getMethod("setThreadAllocatedMemoryEnabled", Boolean::class.javaPrimitiveType).invoke(bean, true)
        val allocated = type.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        val id = Thread.currentThread().id
        fun allocatedBytes() = allocated.invoke(bean, id) as Long
        for (length in listOf(8, 128, 2048)) {
            val cases = listOf(mapOf("text" to "a".repeat(length)), mapOf("analysis" to "b".repeat(length)))
            repeat(100_000) { sink = baseline(cases[it % 2]); sink = LiteRtChannelText.split(cases[it % 2], reasoning) }
            fun run(fixed: Boolean): Pair<Double, Double> {
                val bytes = allocatedBytes()
                val start = System.nanoTime()
                repeat(iterations) {
                    val input = cases[it % 2]
                    sink = if (fixed) LiteRtChannelText.split(input, reasoning) else baseline(input)
                }
                return (System.nanoTime() - start).toDouble() / iterations to
                    (allocatedBytes() - bytes).toDouble() / iterations
            }
            val old = mutableListOf<Pair<Double, Double>>()
            val current = mutableListOf<Pair<Double, Double>>()
            repeat(7) { round ->
                if (round % 2 == 0) { old += run(false); current += run(true) }
                else { current += run(true); old += run(false) }
            }
            fun median(values: List<Double>) = values.sorted()[values.size / 2]
            println("chunkChars=$length calls=$iterations rounds=7 baselineNs=${median(old.map { it.first })} " +
                "fixedNs=${median(current.map { it.first })} baselineBytes=${median(old.map { it.second })} " +
                "fixedBytes=${median(current.map { it.second })}")
        }
    }
}
