package dev.yashasvm.mobie.core.model

/** A remote chat offering, never a downloadable artifact or a local runtime format. */
data class CloudModel(
    val id: String,
    val title: String,
    val supportsImages: Boolean,
    val privacyNotice: String,
) {
    fun asAiModel() = AiModel(
        id = "opencode/$id",
        title = title,
        author = "OpenCode",
        description = privacyNotice,
        type = if (supportsImages) ModelType.VISION else ModelType.TEXT_GENERATION,
        cloudModel = this,
    )
}

const val CLOUD_DISCLOSURE = "Your Linux box forwards this cloud conversation's messages, history and attached photos to OpenCode and its model providers. " +
    "Server sessions may temporarily retain conversation context. " +
    "OpenCode lists hosting in the US and EU. Retention and training policies vary by model; some free models use data for improvement. " +
    "Avoid sensitive information. Internet is required. Free models and limits can change."
const val CLOUD_PRIVACY_URL = "https://opencode.ai/v2/docs/console/models/#privacy"

const val DEFAULT_CLOUD_GATEWAY_URL = "https://mobie.yvmx.dpdns.org"
