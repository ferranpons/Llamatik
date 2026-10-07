package com.llamatik.sdk.agent.registry

import com.llamatik.sdk.agent.capability.Capability

class CapabilityRegistry {
    private val capabilities = mutableMapOf<String, Capability>()

    fun registerCapability(capability: Capability) {
        capabilities[capability.id] = capability
    }

    fun unregisterCapability(id: String) {
        capabilities.remove(id)
    }

    fun get(id: String): Capability? = capabilities[id]

    fun all(): Set<Capability> = capabilities.values.toSet()

    fun available(): Set<Capability> = capabilities.values.filter { it.isAvailable }.toSet()
}
