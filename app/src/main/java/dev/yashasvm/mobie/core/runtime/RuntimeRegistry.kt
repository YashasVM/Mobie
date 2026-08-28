package dev.yashasvm.mobie.core.runtime

import dev.yashasvm.mobie.core.model.ModelFormat

class RuntimeRegistry(adapters: Set<RuntimeAdapter>) {
    private val adaptersByFormat = adapters.associateBy(RuntimeAdapter::format)
    fun adapterFor(format: ModelFormat): RuntimeAdapter? = adaptersByFormat[format]
}
