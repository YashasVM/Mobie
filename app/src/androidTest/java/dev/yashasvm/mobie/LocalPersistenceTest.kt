package dev.yashasvm.mobie

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.yashasvm.mobie.data.history.ChatHistoryStore
import dev.yashasvm.mobie.data.history.HistoryMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    @Test
    fun chatHistorySurvivesStoreRecreationAndCanBeCleared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelId = "test/history-model"
        val messages = listOf(HistoryMessage(true, "Hello"), HistoryMessage(false, "Hi"))

        ChatHistoryStore(context).write(modelId, messages)
        assertEquals(messages, ChatHistoryStore(context).read(modelId))

        ChatHistoryStore(context).clear(modelId)
        assertEquals(emptyList<HistoryMessage>(), ChatHistoryStore(context).read(modelId))
    }
}
