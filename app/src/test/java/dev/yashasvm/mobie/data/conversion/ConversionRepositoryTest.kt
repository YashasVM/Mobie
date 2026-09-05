package dev.yashasvm.mobie.data.conversion

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionRepositoryTest {
    @Test
    fun `does not report a conversion request when no service is configured`() = runBlocking {
        val result = ConversionRepository(OkHttpClient()).request("owner/model")

        assertTrue(result.isFailure)
    }
}
