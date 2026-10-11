package dev.yashasvm.mobie.core.runtime

import android.content.Context

/** User-facing sampling values. Defaults match the original LiteRT conversation configuration. */
data class SamplerSettings(
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val seed: Int = 0,
) {
    fun validated(): SamplerSettings {
        require(temperature.isFinite() && temperature in 0.0..2.0) { "Temperature must be between 0 and 2." }
        require(topK in 1..200) { "Top-k must be between 1 and 200." }
        require(topP.isFinite() && topP > 0.0 && topP <= 1.0) { "Top-p must be above 0 and at most 1." }
        return this
    }
}

/** Keeps the persisted format independent of the LiteRT SDK. Invalid older values use defaults. */
internal object SamplerSettingsCodec {
    fun decode(values: Map<String, String?>): SamplerSettings = runCatching {
        SamplerSettings(
            temperature = values["temperature"]?.toDouble() ?: 0.7,
            topK = values["topK"]?.toInt() ?: 40,
            topP = values["topP"]?.toDouble() ?: 0.95,
            seed = values["seed"]?.toInt() ?: 0,
        ).validated()
    }.getOrDefault(SamplerSettings())

    fun encode(settings: SamplerSettings): Map<String, String> = settings.validated().let {
        mapOf(
            "temperature" to it.temperature.toString(),
            "topK" to it.topK.toString(),
            "topP" to it.topP.toString(),
            "seed" to it.seed.toString(),
        )
    }
}

class SamplerSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("sampler_settings", Context.MODE_PRIVATE)

    fun read(): SamplerSettings = SamplerSettingsCodec.decode(
        listOf("temperature", "topK", "topP", "seed").associateWith(::getStringOrNull),
    )

    fun write(settings: SamplerSettings) {
        val editor = preferences.edit()
        SamplerSettingsCodec.encode(settings).forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    private fun getStringOrNull(key: String): String? = preferences.getString(key, null)
}
