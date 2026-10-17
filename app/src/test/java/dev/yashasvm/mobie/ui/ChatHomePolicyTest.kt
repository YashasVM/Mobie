package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHomePolicyTest {
    private fun installed(id: String) = InstalledModelEntry(AiModel(id, id, "litert-community", ""), "/models/$id")

    private val first = installed("litert-community/first")
    private val second = installed("litert-community/second")

    @Test
    fun opensTheLastUsedModelWhenItIsStillInstalled() {
        assertEquals(second, chooseLaunchModel(listOf(first, second), second.model.id))
    }

    @Test
    fun fallsBackToTheFirstInstalledModelWhenTheLastOneWasRemovedOrUnknown() {
        assertEquals(first, chooseLaunchModel(listOf(first, second), "litert-community/deleted"))
        assertEquals(first, chooseLaunchModel(listOf(first, second), null))
    }

    @Test
    fun opensNoChatWhenNothingIsInstalled() {
        assertNull(chooseLaunchModel(emptyList(), first.model.id))
    }
}
