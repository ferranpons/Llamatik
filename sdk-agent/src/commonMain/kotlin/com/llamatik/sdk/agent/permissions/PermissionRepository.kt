package com.llamatik.sdk.agent.permissions

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val PERMISSION_KEY_PREFIX = "agent_perm_v1."

@Serializable
private data class StoredPermission(val state: String)

class PermissionRepository(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun getDecision(permissionId: String): PermissionDecision {
        val raw = settings.getString("$PERMISSION_KEY_PREFIX$permissionId", "")
        val state = if (raw.isBlank()) {
            PermissionState.NOT_SET
        } else {
            runCatching {
                PermissionState.valueOf(
                    json.decodeFromString(StoredPermission.serializer(), raw).state
                )
            }.getOrElse { PermissionState.NOT_SET }
        }
        return PermissionDecision(permissionId, state)
    }

    fun setDecision(permissionId: String, state: PermissionState) {
        settings.putString(
            "$PERMISSION_KEY_PREFIX$permissionId",
            json.encodeToString(StoredPermission.serializer(), StoredPermission(state.name))
        )
    }

    fun isGranted(permissionId: String): Boolean =
        getDecision(permissionId).state == PermissionState.GRANTED

    fun requiresConfirmation(permissionId: String): Boolean =
        getDecision(permissionId).state in setOf(
            PermissionState.ASK_EVERY_TIME,
            PermissionState.REQUIRE_CONFIRMATION,
            PermissionState.NOT_SET,
        )

    fun grant(permissionId: String) = setDecision(permissionId, PermissionState.GRANTED)
    fun deny(permissionId: String) = setDecision(permissionId, PermissionState.DENIED)
    fun askEveryTime(permissionId: String) = setDecision(permissionId, PermissionState.ASK_EVERY_TIME)

    fun getAllDecisions(): List<PermissionDecision> = listOf(
        KnownPermissions.CALENDAR, KnownPermissions.CONTACTS, KnownPermissions.REMINDERS,
        KnownPermissions.CLIPBOARD, KnownPermissions.FILES, KnownPermissions.NOTIFICATIONS,
        KnownPermissions.OPEN_APPS, KnownPermissions.LOCATION, KnownPermissions.SETTINGS,
        KnownPermissions.CAMERA, KnownPermissions.PHOTOS, KnownPermissions.NETWORK,
        KnownPermissions.SHARE, KnownPermissions.OPEN_URL, KnownPermissions.ACCESSIBILITY,
    ).map { getDecision(it) }
}

class PermissionManager(
    private val repository: PermissionRepository,
    private val policy: PermissionPolicy = PermissionPolicy(),
    private val platformPermissionCheck: suspend (permissionId: String) -> Boolean = { true },
) {
    suspend fun checkAll(permissionIds: Set<String>): Map<String, PermissionDecision> =
        permissionIds.associateWith { check(it) }

    suspend fun check(permissionId: String): PermissionDecision {
        val decision = repository.getDecision(permissionId)
        if (decision.state == PermissionState.NOT_SET) {
            val policyState = policy.overrides[permissionId] ?: policy.defaultState
            return PermissionDecision(permissionId, policyState)
        }
        if (decision.state == PermissionState.GRANTED) {
            val platformGranted = platformPermissionCheck(permissionId)
            if (!platformGranted) return PermissionDecision(permissionId, PermissionState.DENIED)
        }
        return decision
    }

    fun grant(permissionId: String) = repository.setDecision(permissionId, PermissionState.GRANTED)
    fun deny(permissionId: String) = repository.setDecision(permissionId, PermissionState.DENIED)
    fun askEveryTime(permissionId: String) = repository.setDecision(permissionId, PermissionState.ASK_EVERY_TIME)
}
