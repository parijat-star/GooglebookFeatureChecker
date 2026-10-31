package com.droid.googlebookfeaturechecker.data

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.droid.googlebookfeaturechecker.util.SecurityGuard
import com.droid.googlebookfeaturechecker.util.WifiIsolationAnalyzer
import com.droid.googlebookfeaturechecker.util.WifiNetworkTelemetry
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.Socket

data class DeviceSystemSnapshot(
  val deviceModel: String,
  val deviceManufacturer: String,
  val isLaptopFormFactor: Boolean,
  val buildType: String,
  val buildFingerprint: String,
  val androidRelease: String,
  val sdkInt: Int,
  val oneUiVersion: String?,
  val gmsInstalled: Boolean,
  val gmsVersionName: String?,
  val gmsUpdateAvailable: Boolean = false,
  val crossDeviceInstalled: Boolean,
  val crossDevicePackageName: String,
  val crossDeviceVersionName: String?,
  val crossDeviceUpdateAvailable: Boolean = false,
  val wifiConnected: Boolean,
  val isPublicOrCaptiveWifi: Boolean,
  val vpnActive: Boolean,
  val networkSsidOrDescription: String,
  val connectedWifiName: String? = null,
  val wifiDetailedIssue: String? = null,
  val bluetoothSupported: Boolean,
  val bluetoothEnabled: Boolean,
  val hasGoogleAccountSignedIn: Boolean,
  val continueActivityConfigured: Boolean
)

data class CompanionPhoneSnapshot(
  val phoneModel: String,
  val buildType: String,
  val androidRelease: String,
  val oneUiVersion: String?,
  val gmsVersionName: String?,
  val crossDeviceVersionName: String?,
  val privateWifiConnected: Boolean,
  val bluetoothEnabled: Boolean
)

interface DeviceEnvironmentProvider {
  fun readLocalSystemSnapshot(): DeviceSystemSnapshot
  fun probeCompanionPhone(): CompanionPhoneSnapshot?
}

class SystemDeviceEnvironmentProvider(
  private val context: Context,
  private val companionHostOverride: String? = null,
  private val companionPort: Int = 47232
) : DeviceEnvironmentProvider {

  override fun readLocalSystemSnapshot(): DeviceSystemSnapshot {
    val pm = context.packageManager
    val config = context.resources.configuration

    val isDesktopFeature = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && pm.hasSystemFeature(PackageManager.FEATURE_PC)) ||
      pm.hasSystemFeature("org.chromium.arc") ||
      (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_DESK ||
      config.smallestScreenWidthDp >= 600

    val buildType = SecurityGuard.sanitizeUntrustedText(Build.TYPE, 32).ifBlank { "unknown" }
    val buildFingerprint = SecurityGuard.sanitizeUntrustedText(Build.FINGERPRINT, 120).ifBlank { "unknown" }
    val androidRelease = SecurityGuard.sanitizeUntrustedText(Build.VERSION.RELEASE, 24)
      .ifBlank { Build.VERSION.SDK_INT.toString() }
    val sdkInt = Build.VERSION.SDK_INT

    val oneUiVersion = readOneUiVersion()

    val gmsInfo = getPackageVersion(pm, "com.google.android.gms")
    val ambientInfo = getPackageVersion(pm, "com.google.ambient.streaming")
    val mediaShellInfo = getPackageVersion(pm, "com.google.android.apps.mediashell")

    val crossDevicePkg = if (ambientInfo != null || mediaShellInfo == null) {
      "com.google.ambient.streaming"
    } else {
      "com.google.android.apps.mediashell"
    }
    val crossDeviceVersion = ambientInfo ?: mediaShellInfo

    // Comprehensive Private Home Wi-Fi & Host Isolation Inspection
    val wifiTelemetry = collectWifiNetworkTelemetry()
    val wifiAnalysis = WifiIsolationAnalyzer.analyze(wifiTelemetry)

    // Check Bluetooth
    val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val btAdapter = btManager?.adapter
    val btSupported = btAdapter != null
    val btEnabled = try {
      btAdapter?.isEnabled == true
    } catch (_: SecurityException) {
      false
    }

    // Check if Cross-device / Google Settings activity resolves
    val crossDeviceSettingsIntent = Intent("com.google.android.gms.settings.CROSS_DEVICE_SETTINGS")
    val gmsAccountSettingsIntent = Intent("com.google.android.gms.accountsettings.VIEW_SETTINGS_0P")
    val canResolveCrossDevice = pm.resolveActivity(crossDeviceSettingsIntent, 0) != null ||
      pm.resolveActivity(gmsAccountSettingsIntent, 0) != null ||
      gmsInfo != null

    return DeviceSystemSnapshot(
      deviceModel = SecurityGuard.sanitizeUntrustedText(Build.MODEL, 64).ifBlank { "Googlebook Device" },
      deviceManufacturer = SecurityGuard.sanitizeUntrustedText(Build.MANUFACTURER, 64).ifBlank { "Google" },
      isLaptopFormFactor = isDesktopFeature,
      buildType = buildType,
      buildFingerprint = buildFingerprint,
      androidRelease = androidRelease,
      sdkInt = sdkInt,
      oneUiVersion = oneUiVersion,
      gmsInstalled = gmsInfo != null,
      gmsVersionName = gmsInfo,
      crossDeviceInstalled = crossDeviceVersion != null,
      crossDevicePackageName = crossDevicePkg,
      crossDeviceVersionName = crossDeviceVersion,
      wifiConnected = wifiTelemetry.hasWifiTransport || wifiTelemetry.hasEthernetTransport,
      isPublicOrCaptiveWifi = wifiAnalysis.isIsolationOrPublicDetected && !wifiTelemetry.isVpnActive,
      vpnActive = wifiTelemetry.isVpnActive,
      networkSsidOrDescription = wifiAnalysis.statusSummary,
      connectedWifiName = wifiAnalysis.connectedWifiName,
      wifiDetailedIssue = wifiAnalysis.detailedExplanation,
      bluetoothSupported = btSupported,
      bluetoothEnabled = btEnabled,
      hasGoogleAccountSignedIn = canResolveCrossDevice,
      continueActivityConfigured = canResolveCrossDevice && crossDeviceVersion != null
    )
  }

  private fun collectWifiNetworkTelemetry(): WifiNetworkTelemetry {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val activeNetwork = cm?.activeNetwork
    val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null
    if (caps == null) {
      return WifiNetworkTelemetry(hasWifiTransport = false)
    }

    val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    val hasEth = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    val isCaptive = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    val isRestricted = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
    val isNotMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val isTrusted = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
    val isVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
      !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)

    // Inspect WifiInfo for Open/Unencrypted, Enterprise 802.1X/Passpoint, Ephemeral, and SSID
    var isOpenWifi = false
    var isEnterpriseOrPasspoint = false
    var isEphemeral = false
    var ssidHint: String? = null

    val wm = runCatching {
      context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }.getOrNull()

    // 1. On Android 12+ (API 31+), NetworkCapabilities.transportInfo only includes the SSID when
    // requested via NetworkCallback(FLAG_INCLUDE_LOCATION_INFO). Dispatch on a background HandlerThread
    // so it never deadlocks when called from the UI thread.
    var locationAwareWifiInfo: WifiInfo? = null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && cm != null) {
      runCatching {
        val latch = java.util.concurrent.CountDownLatch(1)
        var captured: WifiInfo? = null
        val handlerThread = android.os.HandlerThread("WifiSsidReader").apply { start() }
        val cb = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
          override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) {
            val info = networkCapabilities.transportInfo as? WifiInfo
            if (info != null) {
              captured = info
              latch.countDown()
            }
          }
        }
        val req = android.net.NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .build()
        try {
          cm.registerNetworkCallback(req, cb, android.os.Handler(handlerThread.looper))
          latch.await(220, java.util.concurrent.TimeUnit.MILLISECONDS)
        } finally {
          runCatching { cm.unregisterNetworkCallback(cb) }
          runCatching { handlerThread.quitSafely() }
        }
        locationAwareWifiInfo = captured
      }
    }

    // 2. Also query WifiManager.connectionInfo directly (do NOT skip it when caps.transportInfo is non-null,
    // because caps.transportInfo without FLAG_INCLUDE_LOCATION_INFO always masks SSID as "<unknown ssid>").
    @Suppress("DEPRECATION")
    val legacyWifiInfo: WifiInfo? = runCatching { wm?.connectionInfo }.getOrNull()
    val transportWifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      caps.transportInfo as? WifiInfo
    } else {
      null
    }
    val wifiInfo: WifiInfo? = locationAwareWifiInfo ?: legacyWifiInfo ?: transportWifiInfo

    fun isValidSsid(candidate: String?): Boolean {
      if (candidate.isNullOrBlank()) return false
      val cleaned = candidate.trim().removeSurrounding("\"").trim()
      return cleaned.isNotEmpty() &&
        !cleaned.equals("<unknown ssid>", ignoreCase = true) &&
        !cleaned.equals("0x", ignoreCase = true)
    }

    val candidateSsids = listOfNotNull(
      runCatching { locationAwareWifiInfo?.ssid }.getOrNull(),
      runCatching { legacyWifiInfo?.ssid }.getOrNull(),
      runCatching { transportWifiInfo?.ssid }.getOrNull()
    )
    ssidHint = candidateSsids.firstOrNull { isValidSsid(it) }

    // 3. If SSID is still masked, match current BSSID against WifiManager.scanResults
    if (!isValidSsid(ssidHint)) {
      val bssid = listOfNotNull(
        runCatching { locationAwareWifiInfo?.bssid }.getOrNull(),
        runCatching { legacyWifiInfo?.bssid }.getOrNull(),
        runCatching { transportWifiInfo?.bssid }.getOrNull()
      ).firstOrNull { !it.isNullOrBlank() && it != "02:00:00:00:00:00" && it != "00:00:00:00:00:00" }

      val hasLocationPerm = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
      if (bssid != null && hasLocationPerm) {
        @Suppress("DEPRECATION")
        val scanSsid = try {
          wm?.scanResults
            ?.firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
            ?.SSID
        } catch (_: SecurityException) {
          null
        } catch (_: Exception) {
          null
        }
        if (isValidSsid(scanSsid)) {
          ssidHint = scanSsid
        }
      }
    }

    if (wifiInfo != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (wifiInfo.currentSecurityType) {
          WifiInfo.SECURITY_TYPE_OPEN,
          WifiInfo.SECURITY_TYPE_OWE -> isOpenWifi = true
          WifiInfo.SECURITY_TYPE_EAP,
          WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
          WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
          WifiInfo.SECURITY_TYPE_PASSPOINT_R1_R2,
          WifiInfo.SECURITY_TYPE_PASSPOINT_R3 -> isEnterpriseOrPasspoint = true
          else -> {}
        }
        if (!wifiInfo.passpointFqdn.isNullOrBlank() || !wifiInfo.passpointProviderFriendlyName.isNullOrBlank()) {
          isEnterpriseOrPasspoint = true
          if (!isValidSsid(ssidHint)) {
            ssidHint = wifiInfo.passpointProviderFriendlyName ?: wifiInfo.passpointFqdn
          }
        }
        val carrierOrRestricted = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && wifiInfo.isRestricted) ||
          wifiInfo.subscriptionId != android.telephony.SubscriptionManager.INVALID_SUBSCRIPTION_ID
        if (carrierOrRestricted) {
          isEphemeral = true
        }
      }
    }

    if (!isValidSsid(ssidHint)) {
      @Suppress("DEPRECATION")
      val extra = runCatching { cm?.activeNetworkInfo?.extraInfo }.getOrNull()
      if (isValidSsid(extra)) {
        ssidHint = extra
      }
    }

    // Inspect LinkProperties for IPv4 subnet analysis only (do NOT use IP address as Wi-Fi name)
    var ipv4Addr: String? = null
    var ipv4Prefix: Int? = null
    runCatching {
      val linkProps = cm?.getLinkProperties(activeNetwork)
      val v4Link = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
      if (v4Link != null) {
        ipv4Addr = v4Link.address.hostAddress
        ipv4Prefix = v4Link.prefixLength
      }
    }

    // Check if local LAN multicast socket creation works (AP / OS multicast isolation check)
    val multicastOk = runCatching {
      MulticastSocket().use { ms ->
        ms.timeToLive = 1
        true
      }
    }.getOrDefault(true)

    return WifiNetworkTelemetry(
      hasWifiTransport = hasWifi,
      hasEthernetTransport = hasEth,
      hasCellularTransport = hasCellular,
      isCaptivePortal = isCaptive,
      isRestricted = isRestricted,
      isNotMetered = isNotMetered,
      isTrusted = isTrusted,
      isVpnActive = isVpn,
      isOpenUnencryptedWifi = isOpenWifi,
      isEnterpriseOrPasspointWifi = isEnterpriseOrPasspoint,
      isEphemeralOrCarrierMerged = isEphemeral,
      ssidOrNetworkHint = ssidHint,
      ipv4Address = ipv4Addr,
      ipv4PrefixLength = ipv4Prefix,
      gatewayReachableOrMulticastOk = multicastOk
    )
  }

  override fun probeCompanionPhone(): CompanionPhoneSnapshot? {
    val host = companionHostOverride ?: return null
    if (!SecurityGuard.isAllowedCompanionEndpoint(host, companionPort)) {
      return null
    }
    return try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host.trim(), companionPort), 500)
        socket.soTimeout = 500
        val line = SecurityGuard.readBoundedLine(socket.getInputStream(), 4096) ?: return null
        SecurityGuard.parseAndValidateCompanionJson(line)
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun getPackageVersion(pm: PackageManager, packageName: String): String? {
    return try {
      val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
      }
      SecurityGuard.sanitizeUntrustedText(info.versionName, 48).takeIf { it.isNotBlank() }
    } catch (_: PackageManager.NameNotFoundException) {
      null
    } catch (_: Exception) {
      null
    }
  }

  private fun readOneUiVersion(): String? {
    if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
      return null
    }
    return try {
      // Samsung devices expose the public Build.VERSION.SEM_PLATFORM_INT field (e.g., 180000 -> One UI 9.0)
      val semPlatformInt = Build.VERSION::class.java.getField("SEM_PLATFORM_INT").getInt(null)
      if (semPlatformInt >= 100000) {
        val oneUiCode = semPlatformInt - 90000
        val major = oneUiCode / 10000
        val minor = (oneUiCode % 10000) / 100
        "$major.$minor"
      } else {
        null
      }
    } catch (_: Exception) {
      null
    }
  }
}
