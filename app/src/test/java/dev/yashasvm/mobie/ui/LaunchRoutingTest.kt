package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.data.download.InstalledModelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRoutingTest {
    private fun entry(id: String) = InstalledModelEntry(
        model = AiModel(id = id, title = id, author = "litert-community", description = ""),
        localPath = "/data/models/$id.litertlm",
    )

    @Test
    fun firstRunShowsWelcomeEvenWhenModelsExist() {
        val destination = initialDestination(welcomeSeen = false, installed = listOf(entry("a")), lastUsedId = "a")

        assertEquals(LaunchDestination.Welcome, destination)
    }

    @Test
    fun noInstalledModelOpensCatalog() {
        assertEquals(LaunchDestination.Catalog, initialDestination(true, emptyList(), lastUsedId = null))
        assertEquals(LaunchDestination.Catalog, initialDestination(true, emptyList(), lastUsedId = "gone"))
    }

    @Test
    fun opensLastUsedInstalledModel() {
        val installed = listOf(entry("newest"), entry("last"), entry("older"))

        val destination = initialDestination(true, installed, lastUsedId = "last")

        assertEquals("last", (destination as LaunchDestination.Chat).entry.model.id)
    }

    @Test
    fun fallsBackToFirstInstalledWhenLastUsedIsMissing() {
        val installed = listOf(entry("newest"), entry("older"))

        val missing = initialDestination(true, installed, lastUsedId = "deleted")
        val never = initialDestination(true, installed, lastUsedId = null)

        assertEquals("newest", (missing as LaunchDestination.Chat).entry.model.id)
        assertEquals("newest", (never as LaunchDestination.Chat).entry.model.id)
    }

    @Test
    fun chatDestinationCarriesTheInstalledEntryPath() {
        val destination = initialDestination(true, listOf(entry("only")), lastUsedId = null)

        assertTrue(destination is LaunchDestination.Chat)
        assertEquals("/data/models/only.litertlm", (destination as LaunchDestination.Chat).entry.localPath)
    }
}
