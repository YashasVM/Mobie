package dev.yashasvm.mobie.core.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeConversationReconfigurationPolicyTest {
    private val loaded = ComputeOptions(artifactIdentity = "repo/model:weights@revision")

    @Test
    fun identicalEngineSettingsKeepInitializedWeights() {
        assertTrue(RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded, loaded.copy()))
    }

    @Test
    fun eachEngineAffectingSettingRequiresReload() {
        val changes = listOf(
            loaded.copy(backend = ComputeBackend.CPU),
            loaded.copy(cpuThreads = 4),
            loaded.copy(contextTokens = 2_048),
            loaded.copy(activationType = ActivationType.FP16),
            loaded.copy(artifactIdentity = "repo/model:other-weights@revision"),
            loaded.copy(artifactIdentity = "repo/model:weights@new-revision"),
        )
        changes.forEach { requested ->
            assertFalse("Unsafe engine reuse for $requested", RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded, requested))
        }
    }

    @Test
    fun missingArtifactIdentityNeverProvesLoadedWeightsMatch() {
        assertFalse(RuntimeConversationReconfigurationPolicy.canRetainEngine(ComputeOptions(), ComputeOptions()))
        assertFalse(RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded, loaded.copy(artifactIdentity = "")))
    }

    @Test
    fun outputThinkingBudgetAndPrefillChangesOnlyReplaceConversation() {
        val requested = loaded.copy(
            maxOutputTokens = 128,
            thinkingEnabled = true,
            thinkingTokenBudget = 64,
            prefillPrefaceOnInit = true,
        )
        assertTrue(RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded, requested))
        assertTrue(RuntimeConversationReconfigurationPolicy.canRetainEngine(requested, loaded))
    }

    @Test
    fun speculativeDecodingIsCurrentlyOnlyPassedToConversationConfig() {
        for (requested in listOf(null, false, true)) {
            assertTrue(RuntimeConversationReconfigurationPolicy.canRetainEngine(loaded, loaded.copy(speculativeDecoding = requested)))
        }
        // If enableSpeculativeDecoding is added to EngineConfig/ExperimentalFlags, it must move
        // into the engine-affecting comparison and this test must change with that integration.
    }

    @Test
    fun automaticAndExplicitChoicesAreNotAssumedEquivalent() {
        val explicit = loaded.copy(backend = ComputeBackend.CPU, cpuThreads = 4, contextTokens = 2_048)
        assertFalse(RuntimeConversationReconfigurationPolicy.canRetainEngine(explicit, loaded))
    }
}
