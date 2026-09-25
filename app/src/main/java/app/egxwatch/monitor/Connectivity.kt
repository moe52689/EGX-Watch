package app.egxwatch.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

fun connectivity(context: Context) = callbackFlow {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val current = java.util.concurrent.atomic.AtomicReference(manager.activeNetwork)
    fun publish() {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
    }
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { current.set(network) }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (current.get() == network) trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        override fun onLost(network: Network) {
            val active = current.get()
            if (active == network && current.compareAndSet(active, null)) trySend(false)
        }
    }
    manager.registerDefaultNetworkCallback(callback)
    publish()
    awaitClose { manager.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
