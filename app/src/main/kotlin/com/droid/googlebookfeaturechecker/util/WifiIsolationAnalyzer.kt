package com.droid.googlebookfeaturechecker.util

data class WifiNetworkTelemetry(
  val hasWifiTransport: Boolean,
  val hasEthernetTransport: Boolean = false,
  val hasCellularTransport: Boolean = false,
  val isCaptivePortal: Boolean = false,
  val isRestricted: Boolean = false,
  val isNotMetered: Boolean = true,
  val isTrusted: Boolean = true,
  val isVpnActive: Boolean = false,
  val isOpenUnencryptedWifi: Boolean = false,
  val isEnterpriseOrPasspointWifi: Boolean = false,
  val isEphemeralOrCarrierMerged: Boolean = false,
  val ssidOrNetworkHint: String? = null,
  val ipv4Address: String? = null,
  val ipv4PrefixLength: Int? = null,
  val gatewayReachableOrMulticastOk: Boolean = true
)

data class WifiIsolationResult(
  val isPrivateHomeWifiMet: Boolean,
  val isIsolationOrPublicDetected: Boolean,
  val connectedWifiName: String,
  val statusSummary: String,
  val detailedExplanation: String
)

/**
 * Evaluates whether the active network is a genuine private home Wi-Fi network
 * free of AP host/client isolation, public captive portals, guest networks,
 * enterprise 802.1X client isolation, or VPN tunnels.
 */
object WifiIsolationAnalyzer {

  private val PUBLIC_OR_GUEST_SSID_KEYWORDS = listOf(
    "guest",
    "public",
    "hotel",
    "airport",
    "cafe",
    "coffee",
    "starbucks",
    "xfinitywifi",
    "attwifi",
    "eduroam",
    "visitor",
    "free",
    "hotspot",
    "lounge",
    "mall",
    "convention",
    "corp"
  )

  fun analyze(telemetry: WifiNetworkTelemetry): WifiIsolationResult {
    val issues = mutableListOf<String>()

    // 1. Must have Wi-Fi (or local private LAN) active
    if (!telemetry.hasWifiTransport && !telemetry.hasEthernetTransport) {
      val status = if (telemetry.hasCellularTransport) {
        "Not connected to Wi-Fi (Cellular data only)"
      } else {
        "Not connected to Wi-Fi"
      }
      return WifiIsolationResult(
        isPrivateHomeWifiMet = false,
        isIsolationOrPublicDetected = false,
        connectedWifiName = "Not connected",
        statusSummary = status,
        detailedExplanation = "Wi-Fi is not connected. Connect both your Googlebook and your phone to the same private home Wi-Fi network."
      )
    }

    // 2. Check Captive Portal
    if (telemetry.isCaptivePortal) {
      issues += "Captive portal login network detected (hotel/cafe/airport public Wi-Fi)"
    }

    // 3. Check Open / Unencrypted Wi-Fi
    if (telemetry.isOpenUnencryptedWifi) {
      issues += "Open unencrypted Wi-Fi network detected (public networks isolate clients)"
    }

    // 4. Check Enterprise 802.1X / Passpoint roaming Wi-Fi
    if (telemetry.isEnterpriseOrPasspointWifi) {
      issues += "Enterprise 802.1X / Passpoint network detected (corporate & campus networks enforce AP client isolation)"
    }

    // 5. Check Ephemeral / Carrier-merged / Untrusted / Restricted flags
    if (telemetry.isEphemeralOrCarrierMerged || !telemetry.isTrusted || telemetry.isRestricted) {
      issues += "Untrusted, ephemeral, or restricted Wi-Fi network detected"
    }

    // 6. Check Metered Wi-Fi / Hotspot flag
    if (!telemetry.isNotMetered) {
      issues += "Metered Wi-Fi or mobile hotspot detected (background sync & peer discovery restricted)"
    }

    // 7. Extract connected Wi-Fi name (SSID) and check keywords for Guest / Public / Hotel / Cafe networks
    val cleanSsid = SecurityGuard.sanitizeUntrustedText(telemetry.ssidOrNetworkHint, 64)
      .removeSurrounding("\"")
      .trim()
    val hasKnownSsid = cleanSsid.isNotBlank() &&
      !cleanSsid.equals("<unknown ssid>", ignoreCase = true) &&
      !cleanSsid.equals("0x", ignoreCase = true)

    if (hasKnownSsid) {
      val lowerSsid = cleanSsid.lowercase()
      val matchedKeyword = PUBLIC_OR_GUEST_SSID_KEYWORDS.firstOrNull { kw ->
        lowerSsid.contains(kw)
      }
      if (matchedKeyword != null) {
        issues += "Public or guest Wi-Fi SSID ('$cleanSsid') detected—guest networks enable AP host isolation"
      }
    }

    // 8. Check IPv4 subnet for non-private IP, CGNAT (100.64.0.0/10), VM SLIRP NAT (10.0.2.x), or oversized public subnet (< /20)
    val ip = telemetry.ipv4Address?.trim()
    val prefix = telemetry.ipv4PrefixLength
    if (!ip.isNullOrBlank()) {
      if (!isRfc1918PrivateIpv4(ip)) {
        issues += "Public non-private network subnet detected (not a private home LAN)"
      } else if (ip.startsWith("10.0.2.")) {
        issues += "Isolated virtual NAT network detected (host isolation prevents direct phone discovery)"
      } else if (prefix != null && prefix < 20) {
        issues += "Large-scale public/campus/enterprise Wi-Fi subnet (/$prefix) with host isolation"
      }
    }

    val wifiDisplayName = if (hasKnownSsid) {
      cleanSsid
    } else {
      "Wi-Fi (grant Location permission to view Wi-Fi name)"
    }

    // 9. Check local LAN gateway / multicast peer reachability (AP Host Isolation check)
    if (!telemetry.gatewayReachableOrMulticastOk) {
      issues += "Wi-Fi AP Host Isolation detected (local LAN multicast / peer traffic is blocked by router)"
    }

    // 10. Check active VPN tunnel
    if (telemetry.isVpnActive) {
      issues += "Active VPN or DNS blocker tunnel is intercepting local network traffic"
    }

    if (issues.isNotEmpty()) {
      val primaryIssue = issues.first()
      val allIssuesText = issues.joinToString("; ")
      return WifiIsolationResult(
        isPrivateHomeWifiMet = false,
        isIsolationOrPublicDetected = true,
        connectedWifiName = wifiDisplayName,
        statusSummary = "Connected to Wi-Fi: $wifiDisplayName · $primaryIssue",
        detailedExplanation = "$allIssuesText. Hotel, cafe, office, guest networks, AP client isolation, and VPNs stop your phone and Googlebook from talking to each other. Connect both devices to a private home Wi-Fi network and pause any VPN."
      )
    }

    return WifiIsolationResult(
      isPrivateHomeWifiMet = true,
      isIsolationOrPublicDetected = false,
      connectedWifiName = wifiDisplayName,
      statusSummary = "Connected to Wi-Fi: $wifiDisplayName",
      detailedExplanation = "Connected to private home Wi-Fi \"$wifiDisplayName\" with local peer discovery available and no active VPN."
    )
  }

  /**
   * Returns true if [ipv4] is a standard RFC 1918 private LAN address:
   * - 10.0.0.0/8 (10.0.0.0 – 10.255.255.255)
   * - 172.16.0.0/12 (172.16.0.0 – 172.31.255.255)
   * - 192.168.0.0/16 (192.168.0.0 – 192.168.255.255)
   */
  fun isRfc1918PrivateIpv4(ipv4: String): Boolean {
    val parts = ipv4.trim().split(".")
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    val a = octets[0]
    val b = octets[1]
    return when {
      a == 10 -> true
      a == 172 && b in 16..31 -> true
      a == 192 && b == 168 -> true
      else -> false
    }
  }
}
