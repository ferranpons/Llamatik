package com.llamatik.network.discovery

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual object NodeDiscovery {
    actual fun startAdvertising(port: Int, serviceName: String) {}
    actual fun stopAdvertising() {}
    actual fun startDiscovering(): Flow<DiscoveryEvent> = emptyFlow()
    actual fun stopDiscovering() {}
}
