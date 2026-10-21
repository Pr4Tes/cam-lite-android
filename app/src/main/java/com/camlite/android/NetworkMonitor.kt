package com.camlite.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.Build
import java.net.InetAddress

data class LocalNetwork(val network: Network, val capabilities: NetworkCapabilities, val links: LinkProperties) {
    fun score(host: String): Int {
        val clean = host.removePrefix("[").removeSuffix("]")
        val numeric = clean.matches(Regex("[0-9.]+")) || clean.matches(Regex("[0-9a-fA-F:]+")) && ':' in clean
        val address = if (numeric) try { InetAddress.getByName(clean) } catch (_: Exception) { null } else null
        val prefix = address?.let { ip -> links.routes.filter { it.matches(ip) }.maxOfOrNull { it.destination.prefixLength } } ?: 0
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> 40
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 30
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 20
            else -> 10
        }
        return prefix * 100 + transport
    }
}

/** One callback per visible Activity; no internet probes and no connectivity polling. */
class NetworkMonitor(context: Context, private val changed: (List<LocalNetwork>) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private data class Entry(var caps: NetworkCapabilities? = null, var links: LinkProperties? = null, var settled: Boolean = false, var settleTask: Runnable? = null)
    private val entries = linkedMapOf<Network, Entry>()
    private var callback: ConnectivityManager.NetworkCallback? = null
    var networks: List<LocalNetwork> = emptyList()
        private set
    fun start() {
        if (callback != null) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (callback === this) entries.getOrPut(network) { Entry() } }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (callback !== this) return
                entries.getOrPut(network) { Entry() }.caps = caps
                update(network)
            }
            override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) {
                if (callback !== this) return
                entries.getOrPut(network) { Entry() }.links = links
                update(network)
            }
            override fun onLost(network: Network) {
                if (callback !== this) return
                entries.remove(network)?.settleTask?.let { handler.removeCallbacks(it) }
                publish()
            }
        }
        callback = cb
        try {
            // Do not require INTERNET or VALIDATED: a camera LAN can have neither.
            val request = NetworkRequest.Builder()
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .build()
            manager.registerNetworkCallback(request, cb, handler)
            manager.allNetworks.forEach { n ->
                entries[n] = Entry(manager.getNetworkCapabilities(n), manager.getLinkProperties(n), settled = true)
            }
            publish()
        } catch (_: Exception) { stop(); changed(emptyList()) }
    }
    private fun usable(entry: Entry): Boolean {
        val caps = entry.caps ?: return false
        val links = entry.links ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED) &&
            (Build.VERSION.SDK_INT < 28 || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND)) &&
            links.linkAddresses.isNotEmpty() && links.interfaceName != null &&
            listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_ETHERNET, NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_CELLULAR).any { caps.hasTransport(it) }
    }
    private fun update(network: Network) {
        val entry = entries[network] ?: return
        if (!usable(entry)) {
            entry.settleTask?.let { handler.removeCallbacks(it) }; entry.settleTask = null; entry.settled = false
        } else if (!entry.settled && entry.settleTask == null) {
            val task = Runnable {
                if (entries[network] === entry && callback != null) { entry.settleTask = null; entry.settled = true; publish() }
            }
            entry.settleTask = task
            handler.postDelayed(task, 1500)
        }
        publish()
    }
    private fun publish() {
        networks = entries.filterValues { it.settled && usable(it) }.map { (n, e) -> LocalNetwork(n, e.caps!!, e.links!!) }
        changed(networks)
    }
    fun stop() {
        val old = callback
        callback = null
        if (old != null) try { manager.unregisterNetworkCallback(old) } catch (_: IllegalArgumentException) { }
        handler.removeCallbacksAndMessages(null)
        entries.clear(); networks = emptyList()
    }
}
