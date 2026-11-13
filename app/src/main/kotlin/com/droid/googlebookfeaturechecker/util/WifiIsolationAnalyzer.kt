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
  val gatewayReachableOrMulticastOk: Boolean = true,
  val apIsolationVerified: Boolean = true,
  val hasLocationPermission: Boolean = true,
  val gatewayPingTimedOut: Boolean = false
)

data class WifiIsolationResult(
  val isPrivateHomeWifiMet: Boolean,
  val isIsolationOrPublicDetected: Boolean,
  val connectedWifiName: String,
  val isKnownSsid: Boolean = false,
  val isEthernetOnly: Boolean = false,
  val statusSummary: String,
  val detailedExplanation: String,
  val warnings: List<String> = emptyList(),
  val requiresPeerIsolationVerification: Boolean = false
)

/**
 * Evaluates whether the active network is a genuine private home Wi-Fi network
 * free of AP host/client isolation, public captive portals, guest networks,
 * enterprise 802.1X client isolation, or VPN tunnels.
 */
object WifiIsolationAnalyzer {

  private val PUBLIC_OR_GUEST_SSID_TOKENS = setOf(
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

  private val CAMEL_SPLIT_REGEX = Regex("""(?<=[a-z0-9])(?=[A-Z])""")
  private val WORD_TOKEN_REGEX = Regex("""[a-z0-9]+""")

  fun analyze(telemetry: WifiNetworkTelemetry): WifiIsolationResult {
    val issues = mutableListOf<String>()
    val warnings = mutableListOf<String>()

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
        isKnownSsid = false,
        isEthernetOnly = false,
        statusSummary = status,
        detailedExplanation = "Wi-Fi is not connected. Connect both your Googlebook and your phone to the same private home Wi-Fi network."
      )
    }

    val ethernetOnly = telemetry.hasEthernetTransport && !telemetry.hasWifiTransport
    if (ethernetOnly) {
      warnings += "Wired Ethernet connection active—ensure your phone is connected to the same private home router/Wi-Fi subnet"
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

    // 6. Metered Wi-Fi is a non-blocking warning (users often mark home Wi-Fi or test hotspots metered)
    if (!telemetry.isNotMetered) {
      warnings += "Network is marked as metered—ensure metered restrictions do not pause Cross-device discovery or background updates"
    }

    // 7. Extract connected Wi-Fi name (SSID) and match word/token boundaries for Guest/Public networks
    val cleanSsid = SecurityGuard.sanitizeUntrustedText(telemetry.ssidOrNetworkHint, 64)
      .removeSurrounding("\"")
      .trim()
    val hasKnownSsid = cleanSsid.isNotBlank() &&
      !cleanSsid.equals("<unknown ssid>", ignoreCase = true) &&
      !cleanSsid.equals("0x", ignoreCase = true)

    if (hasKnownSsid) {
      val tokens = tokenizeSsid(cleanSsid)
      val matchedToken = tokens.firstOrNull { it in PUBLIC_OR_GUEST_SSID_TOKENS }
      if (matchedToken != null) {
        issues += "Public or guest Wi-Fi SSID ('$cleanSsid') detected—guest networks enable AP host isolation"
      }
    }

    // 8. Check IPv4 subnet for VM SLIRP NAT (10.0.2.x), APIPA link-local (169.254.x.x), CGNAT (100.64.0.0/10), non-private IP, or large private subnet (< /20)
    val ip = telemetry.ipv4Address?.trim()
    val prefix = telemetry.ipv4PrefixLength
    if (!ip.isNullOrBlank()) {
      if (ip.startsWith("10.0.2.")) {
        issues += "Isolated virtual NAT network detected (host isolation prevents direct phone discovery)"
      } else if (isLinkLocalIpv4(ip)) {
        issues += "No valid router DHCP IPv4 address ($ip link-local) detected—verify router DHCP connection"
      } else if (isCgnatIpv4(ip)) {
        warnings += "Carrier-grade NAT / overlay IPv4 subnet ($ip) detected—verify direct local peer reachability"
      } else if (!isRfc1918PrivateIpv4(ip)) {
        issues += "Public non-private network subnet detected (not a private home LAN)"
      } else if (prefix != null && prefix < 20) {
        warnings += "Large private subnet (/$prefix) detected—ensure router AP/client isolation is turned off"
      }
    }

    val wifiDisplayName = when {
      ethernetOnly -> "Ethernet LAN"
      hasKnownSsid -> cleanSsid
      !telemetry.hasLocationPermission -> "Wi-Fi (Location permission needed for SSID)"
      else -> "Wi-Fi (SSID hidden or unavailable)"
    }

    // 9. Check local LAN gateway / peer reachability
    if (!telemetry.gatewayReachableOrMulticastOk) {
      issues += "Wi-Fi AP Host Isolation or gateway unreachability detected (local LAN peer traffic appears blocked)"
    } else {
      if (telemetry.gatewayPingTimedOut) {
        warnings += "Router gateway did not respond to ping—if phone discovery fails, verify AP/client isolation is off"
      }
      if (!telemetry.apIsolationVerified) {
        warnings += "Router AP/client isolation cannot be confirmed from a single device alone—if phone discovery fails, ensure AP isolation is off on your router"
      }
    }

    // 10. Check active VPN tunnel
    if (telemetry.isVpnActive) {
      issues += "Active VPN or DNS blocker tunnel is intercepting local network traffic"
    }

    val requiresPeerCheck = !telemetry.apIsolationVerified && telemetry.gatewayReachableOrMulticastOk
    val transportLabel = if (ethernetOnly) "Ethernet LAN" else "Wi-Fi: $wifiDisplayName"

    if (issues.isNotEmpty()) {
      val primaryIssue = issues.first()
      val allIssuesText = issues.joinToString("; ")
      return WifiIsolationResult(
        isPrivateHomeWifiMet = false,
        isIsolationOrPublicDetected = true,
        connectedWifiName = wifiDisplayName,
        isKnownSsid = hasKnownSsid && !ethernetOnly,
        isEthernetOnly = ethernetOnly,
        statusSummary = "Connected to $transportLabel · $primaryIssue",
        detailedExplanation = "$allIssuesText. Hotel, cafe, office, guest networks, AP client isolation, and VPNs stop your phone and Googlebook from talking to each other. Connect both devices to a private home Wi-Fi network and pause any VPN.",
        warnings = warnings,
        requiresPeerIsolationVerification = requiresPeerCheck
      )
    }

    val warningSuffix = if (warnings.isNotEmpty()) " (${warnings.size} warning${if (warnings.size > 1) "s" else ""})" else ""
    return WifiIsolationResult(
      isPrivateHomeWifiMet = true,
      isIsolationOrPublicDetected = false,
      connectedWifiName = wifiDisplayName,
      isKnownSsid = hasKnownSsid && !ethernetOnly,
      isEthernetOnly = ethernetOnly,
      statusSummary = "Connected to $transportLabel$warningSuffix",
      detailedExplanation = if (ethernetOnly) {
        "Connected via local Ethernet LAN with no captive portal or active VPN."
      } else {
        "Connected to private home Wi-Fi \"$wifiDisplayName\" with no captive portal, guest SSID, or active VPN."
      },
      warnings = warnings,
      requiresPeerIsolationVerification = requiresPeerCheck
    )
  }

  /**
   * Returns true if [ipv4] is an APIPA / link-local address in 169.254.0.0/16 (DHCP failure).
   */
  fun isLinkLocalIpv4(ipv4: String): Boolean {
    val parts = ipv4.trim().split(".")
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 169 && octets[1] == 254
  }

  /**
   * Splits an SSID into lowercase alphanumeric word tokens across delimiters and camelCase boundaries
   * so "Freeman_Home" -> ["freeman", "home"] without false-matching "free", while
   * "CoffeeGuest" -> ["coffee", "guest"] and "Airport_Free_WiFi" -> ["airport", "free", "wi", "fi"].
   */
  internal fun tokenizeSsid(ssid: String): List<String> {
    val camelSplit = CAMEL_SPLIT_REGEX.replace(ssid, " ").lowercase()
    return WORD_TOKEN_REGEX.findAll(camelSplit).map { it.value }.toList()
  }

  /**
   * Returns true if [ipv4] is in RFC 6598 CGNAT range 100.64.0.0/10 (100.64.0.0 – 100.127.255.255).
   */
  fun isCgnatIpv4(ipv4: String): Boolean {
    val parts = ipv4.trim().split(".")
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 100 && octets[1] in 64..127
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
