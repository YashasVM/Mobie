package dev.yashasvm.mobie.ui

import dev.yashasvm.mobie.core.model.AiModel
import dev.yashasvm.mobie.core.model.ModelArtifact
import dev.yashasvm.mobie.core.model.ModelFormat
import dev.yashasvm.mobie.data.history.ChatHistorySession
import dev.yashasvm.mobie.data.history.ChatThread
import dev.yashasvm.mobie.data.history.HistoryMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ChatHomePolicyTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.of(2026, 10, 17, 9, 30).atZone(zone).toInstant().toEpochMilli()

    private fun at(daysAgo: Long, hour: Int = 12): Long =
        LocalDateTime.of(2026, 10, 17, hour, 0).minusDays(daysAgo).atZone(zone).toInstant().toEpochMilli()

    private fun thread(id: String, updatedAt: Long, model: String = "litert-community/A", vararg text: String) =
        ChatThread(model, ChatHistorySession(id, "Chat $id", updatedAt, text.map { HistoryMessage(true, it) }))

    @Test
    fun `only a ready visible chat can send to the runtime`() {
        val artifact = ModelArtifact("model.litertlm", "https://example.com/model", 1_000, format = ModelFormat.LITERT_LM)
        val ready = MobieUiState(
            selected = AiModel("litert-community/A", "A", "litert-community", "", artifacts = listOf(artifact)),
            selectedArtifact = artifact,
            downloadedPath = "/private/model.litertlm",
            chatting = true,
            runtimeState = RuntimeState.READY,
        )
        assertTrue(ready.canSendToRuntime)
        assertFalse(ready.copy(chatPending = true).canSendToRuntime)
        assertFalse(ready.copy(chatting = false).canSendToRuntime)
        assertFalse(ready.copy(screens = listOf(AppScreen.CHAT, AppScreen.CATALOG)).canSendToRuntime)
        assertFalse(ready.copy(readOnlyChat = ReadOnlyChat("deleted", "Removed model")).canSendToRuntime)
        assertFalse(ready.copy(downloadedPath = null).canSendToRuntime)
        assertFalse(ready.copy(selectedArtifact = null).canSendToRuntime)
        RuntimeState.entries.filterNot { it == RuntimeState.READY }.forEach { runtime ->
            assertFalse(ready.copy(runtimeState = runtime).canSendToRuntime)
        }
    }

    @Test
    fun `launch prefers the last used model while it is installed`() {
        assertEquals("b", chooseLaunchModel(listOf("a", "b"), lastUsedModelId = "b", chatModelIdsNewestFirst = listOf("a")))
    }

    @Test
    fun `launch falls back to the newest chat model that is still installed`() {
        assertEquals(
            "a",
            chooseLaunchModel(listOf("a", "b"), lastUsedModelId = "deleted", chatModelIdsNewestFirst = listOf("gone", "a", "b")),
        )
    }

    @Test
    fun `launch falls back to any installed model without history`() {
        assertEquals("a", chooseLaunchModel(listOf("a", "b"), lastUsedModelId = null, chatModelIdsNewestFirst = emptyList()))
    }

    @Test
    fun `launch has no model when nothing is installed`() {
        assertNull(chooseLaunchModel(emptyList(), lastUsedModelId = "a", chatModelIdsNewestFirst = listOf("a")))
    }

    @Test
    fun `age buckets follow calendar days`() {
        assertEquals(ChatAge.TODAY, chatAge(at(0, hour = 0), now, zone))
        assertEquals(ChatAge.TODAY, chatAge(now + 60_000, now, zone))
        assertEquals(ChatAge.YESTERDAY, chatAge(at(1, hour = 23), now, zone))
        assertEquals(ChatAge.PREVIOUS_7_DAYS, chatAge(at(2), now, zone))
        assertEquals(ChatAge.PREVIOUS_7_DAYS, chatAge(at(7), now, zone))
        assertEquals(ChatAge.PREVIOUS_30_DAYS, chatAge(at(8), now, zone))
        assertEquals(ChatAge.PREVIOUS_30_DAYS, chatAge(at(30), now, zone))
        assertEquals(ChatAge.OLDER, chatAge(at(31), now, zone))
        assertEquals(ChatAge.OLDER, chatAge(0L, now, zone))
    }

    @Test
    fun `chats are grouped newest first and empty groups are skipped`() {
        val chats = listOf(
            thread("old", at(90)),
            thread("today-early", at(0, hour = 1)),
            thread("week", at(3)),
            thread("today-late", at(0, hour = 9)),
        )
        val groups = groupChatsByAge(chats, now, zone)
        assertEquals(listOf(ChatAge.TODAY, ChatAge.PREVIOUS_7_DAYS, ChatAge.OLDER), groups.map { it.age })
        assertEquals(listOf("today-late", "today-early"), groups.first().chats.map { it.session.id })
    }

    @Test
    fun `search matches titles and message text without case`() {
        val chats = listOf(
            thread("1", at(0), text = arrayOf("Plan a trip to Lisbon")),
            thread("2", at(0), text = arrayOf("Write a poem")),
        )
        assertEquals(listOf("1"), filterChats(chats, "  lisbon ").map { it.session.id })
        assertEquals(listOf("2"), filterChats(chats, "chat 2").map { it.session.id })
        assertEquals(chats, filterChats(chats, " "))
        assertEquals(emptyList<ChatThread>(), filterChats(chats, "nothing"))
    }

    @Test
    fun `short model name prefers a title and drops the owner otherwise`() {
        assertEquals("Gemma 3 1B", shortModelName("litert-community/Gemma3-1B-IT", "Gemma 3 1B"))
        assertEquals("Gemma3-1B-IT", shortModelName("litert-community/Gemma3-1B-IT", " "))
        assertEquals("plain", shortModelName("plain"))
    }

    @Test
    fun `back stack keeps the chat home at the root`() {
        val home = listOf(AppScreen.CHAT)
        val model = home.pushScreen(AppScreen.CATALOG).pushScreen(AppScreen.MODEL)
        assertEquals(listOf(AppScreen.CHAT, AppScreen.CATALOG, AppScreen.MODEL), model)
        assertEquals(model, model.pushScreen(AppScreen.MODEL))
        assertEquals(listOf(AppScreen.CHAT, AppScreen.CATALOG), model.popScreen())
        assertEquals(home, model.popScreen().popScreen().popScreen())
        assertEquals(home, model.pushScreen(AppScreen.CHAT))
        assertEquals(listOf(AppScreen.CHAT, AppScreen.MODEL, AppScreen.CATALOG), model.pushScreen(AppScreen.CATALOG))
    }
}
