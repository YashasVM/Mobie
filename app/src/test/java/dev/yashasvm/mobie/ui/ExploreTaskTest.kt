package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.CloudModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.core.model.ModelType
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ExploreTaskTest {
    @Test fun `freeform preserves input without adding chat context`() {
        assertEquals("What is a rainbow?", taskPrompt(PromptTask.FREEFORM, "  What is a rainbow?  ", ""))
    }

    @Test fun `templates include task and user text`() {
        assertEquals("Summarize the following text. Preserve key facts and avoid inventing details.\n\nNotes",
            taskPrompt(PromptTask.SUMMARIZE, "Notes", ""))
        assertEquals("Rewrite the following text in this tone: Friendly. Preserve its meaning.\n\nDraft",
            taskPrompt(PromptTask.REWRITE, "Draft", " Friendly "))
        assertEquals("Write code in Kotlin for the following request. Explain how to use it.\n\nSort a list",
            taskPrompt(PromptTask.CODE, "Sort a list", "Kotlin"))
        assertEquals("Translate the following text into Spanish. Return only the translation.\n\nHello",
            taskPrompt(PromptTask.TRANSLATE, "Hello", "Spanish"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank inputs cannot start a task`() { taskPrompt(PromptTask.FREEFORM, " ", "") }

    @Test(expected = IllegalArgumentException::class)
    fun `blank required option cannot start a task`() { taskPrompt(PromptTask.TRANSLATE, "Hello", " ") }

    @Test fun `photo tasks require vision and all tasks exclude unsupported and cloud entries`() {
        val text = entry("text", ModelType.TEXT_GENERATION)
        val vision = entry("vision", ModelType.VISION)
        val unsupported = entry("gguf", ModelType.TEXT_GENERATION, ModelFormat.GGUF)
        val cloud = InstalledModelEntry(CloudModel("cloud", "Cloud", false, "Privacy").asAiModel(), "/fake/cloud")
        val entries = listOf(text, vision, unsupported, cloud)
        assertEquals(listOf(vision), localTaskModels(entries, ExploreTask.IMAGE))
        assertEquals(listOf(text, vision), localTaskModels(entries, ExploreTask.LAB))
    }

    private fun entry(id: String, type: ModelType, format: ModelFormat = ModelFormat.LITERT_LM) =
        InstalledModelEntry(AiModel(id, id, "fixture", "", type = type,
            artifacts = listOf(ModelArtifact("$id.litertlm", "https://fixture.invalid/$id", 1024, format = format))), "/fake/$id")
}
