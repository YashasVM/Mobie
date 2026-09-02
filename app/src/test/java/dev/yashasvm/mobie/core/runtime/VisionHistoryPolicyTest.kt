package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionHistoryPolicyTest {
    @Test
    fun textOnlyRuntimeRestoresNoImages() {
        val history = listOf(
            RuntimeMessage(fromUser = true, text = "describe", imagePath = "/tmp/a.jpg"),
            RuntimeMessage(fromUser = false, text = "answer"),
        )

        val index = VisionHistoryPolicy.latestUsableImageIndex(history, visionReady = false) { true }

        assertEquals(-1, index)
    }

    @Test
    fun restoresOnlyNewestUsableUserImage() {
        val history = listOf(
            RuntimeMessage(fromUser = true, text = "first", imagePath = "/tmp/first.jpg"),
            RuntimeMessage(fromUser = false, text = "first answer"),
            RuntimeMessage(fromUser = true, text = "second", imagePath = "/tmp/second.jpg"),
            RuntimeMessage(fromUser = false, text = "second answer"),
        )

        val index = VisionHistoryPolicy.latestUsableImageIndex(history, visionReady = true) { true }

        assertEquals(2, index)
    }

    @Test
    fun missingNewestImageFallsBackToOlderUsableImage() {
        val history = listOf(
            RuntimeMessage(fromUser = true, text = "first", imagePath = "/tmp/first.jpg"),
            RuntimeMessage(fromUser = false, text = "first answer"),
            RuntimeMessage(fromUser = true, text = "second", imagePath = "/tmp/missing.jpg"),
            RuntimeMessage(fromUser = false, text = "second answer"),
        )

        val index = VisionHistoryPolicy.latestUsableImageIndex(history, visionReady = true) { path ->
            path != "/tmp/missing.jpg"
        }

        assertEquals(0, index)
    }
}
