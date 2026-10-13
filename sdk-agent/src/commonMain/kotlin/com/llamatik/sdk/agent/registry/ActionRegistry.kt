package com.llamatik.sdk.agent.registry

import com.llamatik.sdk.agent.action.Action

class ActionRegistry {
    private val actions = mutableMapOf<String, Action>()

    fun registerAction(action: Action) {
        actions[action.id] = action
    }

    fun unregisterAction(id: String) {
        actions.remove(id)
    }

    fun get(id: String): Action? = actions[id]

    fun allActions(): List<Action> = actions.values.toList()

    fun supportedActions(): List<Action> = actions.values.filter { it.isSupported() }
}
