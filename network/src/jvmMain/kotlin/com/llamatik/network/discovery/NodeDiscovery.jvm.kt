package com.llamatik.network.discovery

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

actual object NodeDiscovery {
    private const val SERVICE_TYPE = "_llamatik._tcp.local."
    private var jmdns: javax.jmdns.JmDNS? = null
    private var registeredInfo: javax.jmdns.ServiceInfo? = null

    actual fun startAdvertising(port: Int, serviceName: String) {
        val mdns = javax.jmdns.JmDNS.create()
        jmdns = mdns
        val info = javax.jmdns.ServiceInfo.create(SERVICE_TYPE, serviceName, port, "Llamatik peer")
        registeredInfo = info
        mdns.registerService(info)
    }

    actual fun stopAdvertising() {
        registeredInfo?.let { jmdns?.unregisterService(it) }
        jmdns?.close()
        jmdns = null
        registeredInfo = null
    }

    actual fun startDiscovering(): Flow<DiscoveryEvent> = callbackFlow {
        val mdns = javax.jmdns.JmDNS.create()

        val listener = object : javax.jmdns.ServiceListener {
            override fun serviceAdded(event: javax.jmdns.ServiceEvent) {
                mdns.requestServiceInfo(event.type, event.name)
            }
            override fun serviceRemoved(event: javax.jmdns.ServiceEvent) {
                trySend(DiscoveryEvent.NodeLost(event.name))
            }
            override fun serviceResolved(event: javax.jmdns.ServiceEvent) {
                val info = event.info
                val address = info.inetAddresses.firstOrNull()?.hostAddress ?: return
                trySend(
                    DiscoveryEvent.NodeFound(
                        DiscoveredNode(
                            id = info.name,
                            host = address,
                            port = info.port,
                            name = info.name,
                        )
                    )
                )
            }
        }

        mdns.addServiceListener(SERVICE_TYPE, listener)

        awaitClose {
            mdns.removeServiceListener(SERVICE_TYPE, listener)
            mdns.close()
        }
    }

    actual fun stopDiscovering() {}
}
