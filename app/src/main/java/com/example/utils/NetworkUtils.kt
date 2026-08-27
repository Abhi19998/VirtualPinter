package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    data class NetworkInfo(
        val ipAddress: String,
        val isConnected: Boolean,
        val connectionType: String,
        val port: Int = 9100
    )

    fun getNetworkInfo(context: Context): NetworkInfo {
        val ip = getLocalIpAddress()
        val isConnected = ip != "127.0.0.1" && ip != "0.0.0.0" && ip.isNotEmpty()
        
        var connectionType = "Disconnected"
        if (isConnected) {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(network)
            
            connectionType = when {
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi Connected"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet Connected"
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Hotspot / Cellular"
                else -> "Local Network"
            }
        }

        return NetworkInfo(
            ipAddress = if (isConnected) ip else "Not connected to Wi-Fi",
            isConnected = isConnected,
            connectionType = connectionType,
            port = 9100
        )
    }

    fun getLocalIpAddress(): String {
        try {
            val enumeration = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            val interfaces = Collections.list(enumeration)
            // First look for wlan, ap, or eth interfaces
            for (intf in interfaces) {
                if (intf == null) continue
                if (intf.name.contains("wlan", ignoreCase = true) ||
                    intf.name.contains("ap", ignoreCase = true) ||
                    intf.name.contains("eth", ignoreCase = true)) {
                    val addrs = Collections.list(intf.inetAddresses ?: continue)
                    for (addr in addrs) {
                        if (addr != null && !addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: ""
                        }
                    }
                }
            }
            // Fallback to any non-loopback IPv4
            for (intf in interfaces) {
                if (intf == null) continue
                val addrs = Collections.list(intf.inetAddresses ?: continue)
                for (addr in addrs) {
                    if (addr != null && !addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotEmpty()) return host
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
