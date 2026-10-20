package com.llamatik.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

actual object NodeDiscovery {
    private const val SERVICE_TYPE = "_llamatik._tcp"
    private var applicationContext: Context? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    actual fun startAdvertising(port: Int, serviceName: String) {
        val ctx = applicationContext ?: return
        val mgr = ctx.getSystemService(NsdManager::class.java) ?: return
        nsdManager = mgr

        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = SERVICE_TYPE
            this.port = port
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    actual fun stopAdvertising() {
        registrationListener?.let { nsdManager?.unregisterService(it) }
        registrationListener = null
    }

    actual fun startDiscovering(): Flow<DiscoveryEvent> {
        val ctx = applicationContext ?: return emptyFlow()
        val mgr = ctx.getSystemService(NsdManager::class.java) ?: return emptyFlow()

        return callbackFlow {
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    close(Exception("NSD discovery failed: $errorCode"))
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) { close() }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    mgr.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            trySend(
                                DiscoveryEvent.NodeFound(
                                    DiscoveredNode(
                                        id = info.serviceName,
                                        host = host,
                                        port = info.port,
                                        name = info.serviceName,
                                    )
                                )
                            )
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    trySend(DiscoveryEvent.NodeLost(serviceInfo.serviceName))
                }
            }

            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            awaitClose {
                try { mgr.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            }
        }
    }

    actual fun stopDiscovering() {}
}
