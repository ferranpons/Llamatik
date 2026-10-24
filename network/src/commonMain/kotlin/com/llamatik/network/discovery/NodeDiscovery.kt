package com.llamatik.network.discovery

import kotlinx.coroutines.flow.Flow

expect object NodeDiscovery {
    fun startAdvertising(port: Int, serviceName: String = "Llamatik")
    fun stopAdvertising()
    fun startDiscovering(): Flow<DiscoveryEvent>
    fun stopDiscovering()
}
