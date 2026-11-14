package com.droid.googlebookfeaturechecker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.googlebookfeaturechecker.data.SystemDeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.model.DependencyActionType
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
import com.droid.googlebookfeaturechecker.model.DependencyId
import com.droid.googlebookfeaturechecker.util.SecurityGuard
import com.droid.googlebookfeaturechecker.util.SystemActionLauncher
import com.droid.googlebookfeaturechecker.util.VersionComparator
import com.droid.googlebookfeaturechecker.util.WifiIsolationAnalyzer
import com.droid.googlebookfeaturechecker.util.WifiNetworkTelemetry
import java.io.ByteArrayInputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class FuzzAndSecurityTest {

  private fun basePrivateWifiTelemetry() = WifiNetworkTelemetry(
    hasWifiTransport = true,
    hasEthernetTransport = false,
    hasCellularTransport = false,
    isCaptivePortal = false,
    isRestricted = false,
    isNotMetered = true,
    isTrusted = true,
    isVpnActive = false,
    isOpenUnencryptedWifi = false,
    isEnterpriseOrPasspointWifi = false,
    isEphemeralOrCarrierMerged = false,
    ssidOrNetworkHint = "\"Home_Fiber_5G\"",
    ipv4Address = "192.168.1.42",
    ipv4PrefixLength = 24,
    gatewayReachableOrMulticastOk = true
  )

  @Test
  fun wifiIsolationAnalyzer_acceptsGenuinePrivateHomeWifi() {
    val result = WifiIsolationAnalyzer.analyze(basePrivateWifiTelemetry())
    assertTrue(result.isPrivateHomeWifiMet)
    assertFalse(result.isIsolationOrPublicDetected)
    assertEquals("Home_Fiber_5G", result.connectedWifiName)
    assertEquals("Connected to Wi-Fi: Home_Fiber_5G", result.statusSummary)
    assertTrue(result.detailedExplanation.contains("private home Wi-Fi", ignoreCase = true))
  }

  @Test
  fun wifiIsolationAnalyzer_triggersOnAllNonPrivateAndHostIsolatedNetworks() {
    // 1. Disconnected / Cellular only
    val disconnected = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(hasWifiTransport = false, hasCellularTransport = true)
    )
    assertFalse(disconnected.isPrivateHomeWifiMet)

    // 2. Captive portal Wi-Fi
    val captive = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isCaptivePortal = true)
    )
    assertFalse(captive.isPrivateHomeWifiMet)
    assertTrue(captive.isIsolationOrPublicDetected)
    assertTrue(captive.detailedExplanation.contains("Captive portal", ignoreCase = true))

    // 3. Open / unencrypted Wi-Fi
    val openWifi = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isOpenUnencryptedWifi = true)
    )
    assertFalse(openWifi.isPrivateHomeWifiMet)
    assertTrue(openWifi.isIsolationOrPublicDetected)
    assertTrue(openWifi.detailedExplanation.contains("Open unencrypted", ignoreCase = true))

    // 4. Enterprise 802.1X / Passpoint Wi-Fi (client-to-client AP isolation)
    val enterpriseEap = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isEnterpriseOrPasspointWifi = true)
    )
    assertFalse(enterpriseEap.isPrivateHomeWifiMet)
    assertTrue(enterpriseEap.isIsolationOrPublicDetected)
    assertTrue(enterpriseEap.detailedExplanation.contains("Enterprise", ignoreCase = true))

    // 5. Untrusted / Restricted / Ephemeral / Carrier-merged Wi-Fi
    val untrusted = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isTrusted = false)
    )
    assertFalse(untrusted.isPrivateHomeWifiMet)
    assertTrue(untrusted.isIsolationOrPublicDetected)

    val ephemeral = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isEphemeralOrCarrierMerged = true)
    )
    assertFalse(ephemeral.isPrivateHomeWifiMet)
    assertTrue(ephemeral.isIsolationOrPublicDetected)

    // 6. Metered Wi-Fi emits a non-blocking warning instead of falsely failing private home Wi-Fi
    val metered = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isNotMetered = false)
    )
    assertTrue(metered.isPrivateHomeWifiMet)
    assertFalse(metered.isIsolationOrPublicDetected)
    assertTrue(metered.warnings.any { it.contains("metered", ignoreCase = true) })

    // 7. Public / guest / hotel / cafe / corporate SSID tokens MUST block, while benign substrings MUST NOT
    val publicSsids = listOf(
      "MyHome-Guest",
      "Starbucks WiFi",
      "Marriott_Hotel",
      "Airport_Free_WiFi",
      "CoffeeGuest",
      "eduroam",
      "xfinitywifi",
      "Acme-Corp-WiFi"
    )
    for (ssid in publicSsids) {
      val res = WifiIsolationAnalyzer.analyze(
        basePrivateWifiTelemetry().copy(ssidOrNetworkHint = ssid)
      )
      assertFalse("Expected SSID '$ssid' to be flagged as non-private", res.isPrivateHomeWifiMet)
      assertTrue(res.isIsolationOrPublicDetected)
    }

    val benignSsids = listOf("Freeman_Home", "Carefree_5G", "Corporal_LAN", "FreeportMesh")
    for (ssid in benignSsids) {
      val res = WifiIsolationAnalyzer.analyze(
        basePrivateWifiTelemetry().copy(ssidOrNetworkHint = ssid)
      )
      assertTrue("Expected benign SSID '$ssid' not to false-positive", res.isPrivateHomeWifiMet)
      assertFalse(res.isIsolationOrPublicDetected)
    }

    // 8. CGNAT (100.64.x.x) & < /20 private LAN emit warnings; VM NAT (10.0.2.15) & public IPs block
    val cgnat = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ipv4Address = "100.64.12.8")
    )
    assertTrue(cgnat.isPrivateHomeWifiMet)
    assertTrue(cgnat.warnings.any { it.contains("NAT", ignoreCase = true) })

    val publicIp = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ipv4Address = "8.8.8.8")
    )
    assertFalse(publicIp.isPrivateHomeWifiMet)
    assertTrue(publicIp.isIsolationOrPublicDetected)

    val vmNat = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ipv4Address = "10.0.2.15")
    )
    assertFalse(vmNat.isPrivateHomeWifiMet)
    assertTrue(vmNat.isIsolationOrPublicDetected)

    val hugePrivateSubnet = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ipv4Address = "10.12.0.45", ipv4PrefixLength = 16)
    )
    assertTrue(hugePrivateSubnet.isPrivateHomeWifiMet)
    assertTrue(hugePrivateSubnet.warnings.any { it.contains("/16") })

    // 9. Unverified single-device AP isolation adds advisory warning; confirmed isolation fails
    val unverifiedIsolation = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(apIsolationVerified = false)
    )
    assertTrue(unverifiedIsolation.isPrivateHomeWifiMet)
    assertTrue(unverifiedIsolation.requiresPeerIsolationVerification)
    assertTrue(unverifiedIsolation.warnings.any { it.contains("isolation", ignoreCase = true) })

    // 9. Multicast / AP Host Isolation
    val apIsolated = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(gatewayReachableOrMulticastOk = false)
    )
    assertFalse(apIsolated.isPrivateHomeWifiMet)
    assertTrue(apIsolated.isIsolationOrPublicDetected)
    assertTrue(apIsolated.detailedExplanation.contains("host isolation", ignoreCase = true))

    // 10. Active VPN
    val vpnActive = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(isVpnActive = true)
    )
    assertFalse(vpnActive.isPrivateHomeWifiMet)
    assertTrue(vpnActive.detailedExplanation.contains("VPN", ignoreCase = true))

    // 11. Wired Ethernet only must not show Wi-Fi Location-permission prompt
    val ethernetOnly = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(
        hasWifiTransport = false,
        hasEthernetTransport = true,
        ssidOrNetworkHint = null,
        hasLocationPermission = true
      )
    )
    assertTrue(ethernetOnly.isPrivateHomeWifiMet)
    assertTrue(ethernetOnly.isEthernetOnly)
    assertFalse(ethernetOnly.connectedWifiName.contains("Location", ignoreCase = true))
    assertEquals("Ethernet LAN", ethernetOnly.connectedWifiName)

    // 12. Hidden SSID when Location IS already granted must not ask for Location permission
    val hiddenWithPermission = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ssidOrNetworkHint = "<unknown ssid>", hasLocationPermission = true)
    )
    assertTrue(hiddenWithPermission.isPrivateHomeWifiMet)
    assertFalse(hiddenWithPermission.isKnownSsid)
    assertFalse(hiddenWithPermission.connectedWifiName.contains("grant Location", ignoreCase = true))

    // 13. APIPA Link-Local (169.254.x.x) DHCP failure is flagged clearly
    val apipa = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(ipv4Address = "169.254.44.10", ipv4PrefixLength = 16)
    )
    assertFalse(apipa.isPrivateHomeWifiMet)
    assertTrue(apipa.detailedExplanation.contains("DHCP", ignoreCase = true))

    // 14. Gateway ping timeout adds advisory warning without false-failing private home Wi-Fi
    val pingTimeout = WifiIsolationAnalyzer.analyze(
      basePrivateWifiTelemetry().copy(gatewayPingTimedOut = true)
    )
    assertTrue(pingTimeout.isPrivateHomeWifiMet)
    assertTrue(pingTimeout.warnings.any { it.contains("did not respond to ping", ignoreCase = true) })
  }

  @Test
  fun fuzzVersionComparator_handlesExtremeOverflowNegativeAndRandomStringsSafely() {
    // 1. >64-bit integer overflow segments must clamp to Long.MAX_VALUE instead of silently dropping
    assertTrue(
      VersionComparator.isAtLeast(
        "999999999999999999999999999999999999.1",
        "26.37"
      )
    )

    // 2. Negative versions must be rejected
    assertFalse(VersionComparator.isAtLeast("-26.37", "26.37"))
    assertFalse(VersionComparator.isAtLeast("26.37", "-26.37"))

    // 3. Extremely long input (50,000 chars) must execute rapidly without ReDoS or OOM
    val hugeVersion = "26.37." + "9".repeat(50_000)
    assertTrue(VersionComparator.isAtLeast(hugeVersion, "26.37"))

    // 4. Deep dot-separated segments (1,000 segments)
    val deepDots = (1..1000).joinToString(".") { "1" }
    assertFalse(VersionComparator.isAtLeast(deepDots, "26.37"))

    // 5. Randomized fuzzing with 1,000 arbitrary Unicode/control/punctuation strings
    val rng = Random(42)
    val alphabet = "0123456789.-+eE\u0000\u202E\uFFFF\n\r\t!@#$%^&*()_=[]{}|;:',<>?/\\abcXYZ"
    repeat(1000) {
      val len1 = rng.nextInt(0, 300)
      val len2 = rng.nextInt(0, 300)
      val s1 = buildString(len1) { repeat(len1) { append(alphabet[rng.nextInt(alphabet.length)]) } }
      val s2 = buildString(len2) { repeat(len2) { append(alphabet[rng.nextInt(alphabet.length)]) } }
      // Must never throw any exception
      VersionComparator.isAtLeast(s1, s2)
      VersionComparator.extractLeadingVersionParts(s1)
    }
  }

  @Test
  fun fuzzSecurityGuard_boundedStreamAndCompanionJsonParserResistDoSAndInjection() {
    // 1. Unbounded 100 KB stream without newline must cap at 4,096 bytes and never OOM
    val hugePayload = ByteArray(100_000) { 'A'.code.toByte() }
    val bounded = SecurityGuard.readBoundedLine(ByteArrayInputStream(hugePayload), maxBytes = 4096)
    assertNotNull(bounded)
    assertEquals(4096, bounded!!.length)

    // 2. Malicious BiDi override (\u202E) and control characters in JSON must be stripped
    val maliciousJson = """
      {
        "phoneModel": "Pixel 10\u202Egnas\u0000\n\r",
        "buildType": "user\u202E",
        "androidRelease": "17",
        "oneUiVersion": "9.0\u0007",
        "gmsVersionName": "26.38.00",
        "crossDeviceVersionName": "1.0.1350",
        "privateWifiConnected": true,
        "bluetoothEnabled": true
      }
    """.trimIndent()
    val parsed = SecurityGuard.parseAndValidateCompanionJson(maliciousJson)
    assertNotNull(parsed)
    assertFalse(parsed!!.phoneModel.contains('\u202E'))
    assertFalse(parsed.phoneModel.contains('\u0000'))
    assertEquals("user", parsed.buildType)

    // 3. Fuzz companion JSON parser with 500 random malformed inputs
    val rng = Random(1337)
    repeat(500) {
      val bytes = ByteArray(rng.nextInt(0, 2048))
      rng.nextBytes(bytes)
      val randomStr = String(bytes, Charsets.UTF_8)
      // Must return null or a sanitized snapshot, never throw an unhandled exception
      val res = SecurityGuard.parseAndValidateCompanionJson(randomStr)
      if (res != null) {
        assertTrue(res.phoneModel.length <= 120)
      }
    }
  }

  @Test
  fun securityGuard_blocksSsrfAndPackageAndUrlInjection() {
    // 1. SSRF Prevention: only loopback & non-privileged ports allowed
    assertTrue(SecurityGuard.isAllowedCompanionEndpoint("127.0.0.1", 47232))
    assertTrue(SecurityGuard.isAllowedCompanionEndpoint("localhost", 47232))
    assertFalse(SecurityGuard.isAllowedCompanionEndpoint("169.254.169.254", 80))
    assertFalse(SecurityGuard.isAllowedCompanionEndpoint("192.168.1.1", 47232))
    assertFalse(SecurityGuard.isAllowedCompanionEndpoint("evil.example.com", 47232))
    assertFalse(SecurityGuard.isAllowedCompanionEndpoint("127.0.0.1", 22))
    assertFalse(SecurityGuard.isAllowedCompanionEndpoint("127.0.0.1", 70000))

    val context: Context = ApplicationProvider.getApplicationContext()
    val ssrfAttemptProvider = SystemDeviceEnvironmentProvider(
      context = context,
      companionHostOverride = "169.254.169.254",
      companionPort = 80
    )
    assertNull(ssrfAttemptProvider.probeCompanionPhone())

    // 2. Package Name Validation: blocks URI query/path injection
    assertTrue(SecurityGuard.isValidAndroidPackageName("com.google.android.gms"))
    assertTrue(SecurityGuard.isValidAndroidPackageName("com.google.ambient.streaming"))
    assertFalse(SecurityGuard.isValidAndroidPackageName("com.google.android.gms&referrer=evil"))
    assertFalse(SecurityGuard.isValidAndroidPackageName("../../etc/passwd"))
    assertFalse(SecurityGuard.isValidAndroidPackageName("javascript:alert(1)"))

    val launcher = SystemActionLauncher(context)
    val injectedItem = DependencyCheckItem(
      id = DependencyId.GMS_APK,
      stepNumber = 3,
      title = "GMS",
      requirementSummary = "",
      detectedStatus = "",
      isMet = false,
      hasUpdateAvailable = true,
      isChecked = false,
      whyNotOrFixInstruction = "",
      phoneRequirementNote = "",
      actionLabel = "Update Apk",
      actionType = DependencyActionType.UPDATE_APK,
      targetPackage = "com.google.android.gms&referrer=utmsource%3Dattacker"
    )
    val actionResp = launcher.executeDependencyAction(injectedItem)
    assertTrue(actionResp.contains("Blocked invalid package identifier"))

    // 3. Trusted HTTPS URL Validation: blocks javascript:, file:, intent:, and non-allowlisted domains
    assertTrue(
      SecurityGuard.isTrustedHttpsUrl(
        "https://support.google.com/googlebook/thread/472325070/update-on-better-together-setup-samsung-galaxy-rollout-on-googlebook?hl=en-GB"
      )
    )
    assertFalse(SecurityGuard.isTrustedHttpsUrl("http://support.google.com/googlebook/thread/472325070"))
    assertFalse(SecurityGuard.isTrustedHttpsUrl("javascript:alert(document.cookie)"))
    assertFalse(SecurityGuard.isTrustedHttpsUrl("file:///data/data/com.droid.googlebookfeaturechecker/shared_prefs"))
    assertFalse(SecurityGuard.isTrustedHttpsUrl("https://attacker.com/support.google.com"))
    assertFalse(SecurityGuard.isTrustedHttpsUrl("https://user:pass@support.google.com/"))

    val urlResp = launcher.openReferenceSupportThread("javascript:alert(1)")
    assertTrue(urlResp.contains("Blocked untrusted support URL"))

    // 4. Reject unknown JSON keys, prototype pollution keys, and nested JSON objects/arrays
    assertNull(
      SecurityGuard.parseAndValidateCompanionJson(
        """{"phoneModel":"Pixel","unexpectedKey":"evil"}"""
      )
    )
    assertNull(
      SecurityGuard.parseAndValidateCompanionJson(
        """{"phoneModel":{"nested":"object"}}"""
      )
    )
    assertNull(
      SecurityGuard.parseAndValidateCompanionJson(
        """{"phoneModel":["array"]}"""
      )
    )

    // 5. Exercise all SystemActionLauncher action types safely
    for (actionType in DependencyActionType.entries) {
      val item = DependencyCheckItem(
        id = DependencyId.ANDROID_VERSION,
        stepNumber = 1,
        title = "Test",
        requirementSummary = "",
        detectedStatus = "",
        isMet = false,
        hasUpdateAvailable = false,
        isChecked = false,
        whyNotOrFixInstruction = "",
        phoneRequirementNote = "",
        actionLabel = "Action",
        actionType = actionType,
        targetPackage = "com.google.android.gms"
      )
      val res = launcher.executeDependencyAction(item)
      assertTrue(res.isNotBlank())
    }
    val validUrlResp = launcher.openReferenceSupportThread()
    assertTrue(validUrlResp.isNotBlank())
  }

  @Test
  fun fuzzWifiIsolationAnalyzer_andPermissionGuard_andViewModelSsidExtractionResistMalformedInputs() {
    // 1. Fuzz WifiIsolationAnalyzer.tokenizeSsid, IPv4 classifiers, and full analyze() across 1,000 randomized payloads
    val rng = Random(20261113)
    val fuzzChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-\" ()[]{}<>:/\\\u0000\u202E\uFFFD\n\r\t🔥🌐"
    repeat(1000) {
      val len = rng.nextInt(0, 260)
      val randomSsid = buildString(len) { repeat(len) { append(fuzzChars[rng.nextInt(fuzzChars.length)]) } }
      val ipLen = rng.nextInt(0, 64)
      val randomIp = buildString(ipLen) { repeat(ipLen) { append(fuzzChars[rng.nextInt(fuzzChars.length)]) } }
      val randomPrefix = if (rng.nextBoolean()) rng.nextInt(-128, 256) else null

      val tokens = WifiIsolationAnalyzer.tokenizeSsid(randomSsid)
      assertTrue(tokens.all { token -> token.isNotEmpty() && token.all { c -> c in 'a'..'z' || c in '0'..'9' } })

      // IPv4 classifiers must never throw on malformed/arbitrary inputs
      val isLinkLocal = WifiIsolationAnalyzer.isLinkLocalIpv4(randomIp)
      val isCgnat = WifiIsolationAnalyzer.isCgnatIpv4(randomIp)
      val isPrivate = WifiIsolationAnalyzer.isRfc1918PrivateIpv4(randomIp)
      // Link-local, CGNAT, and RFC 1918 ranges are mutually exclusive
      val trueCount = listOf(isLinkLocal, isCgnat, isPrivate).count { it }
      assertTrue(trueCount <= 1)

      val telemetry = WifiNetworkTelemetry(
        hasWifiTransport = rng.nextBoolean(),
        hasEthernetTransport = rng.nextBoolean(),
        hasCellularTransport = rng.nextBoolean(),
        isCaptivePortal = rng.nextBoolean(),
        isRestricted = rng.nextBoolean(),
        isNotMetered = rng.nextBoolean(),
        isTrusted = rng.nextBoolean(),
        isVpnActive = rng.nextBoolean(),
        isOpenUnencryptedWifi = rng.nextBoolean(),
        isEnterpriseOrPasspointWifi = rng.nextBoolean(),
        isEphemeralOrCarrierMerged = rng.nextBoolean(),
        ssidOrNetworkHint = if (rng.nextBoolean()) randomSsid else null,
        ipv4Address = if (rng.nextBoolean()) randomIp else null,
        ipv4PrefixLength = randomPrefix,
        gatewayReachableOrMulticastOk = rng.nextBoolean(),
        apIsolationVerified = rng.nextBoolean(),
        hasLocationPermission = rng.nextBoolean(),
        gatewayPingTimedOut = rng.nextBoolean()
      )
      val res = WifiIsolationAnalyzer.analyze(telemetry)
      assertFalse(res.connectedWifiName.contains('\u202E'))
      assertFalse(res.connectedWifiName.contains('\u0000'))
      assertTrue(res.statusSummary.isNotBlank())
      assertTrue(res.detailedExplanation.isNotBlank())
    }

    // 2. Fuzz isRequiredPermissionGranted across random permission maps, SDK levels, and action types
    val permNames = listOf(
      android.Manifest.permission.ACCESS_FINE_LOCATION,
      android.Manifest.permission.ACCESS_COARSE_LOCATION,
      android.Manifest.permission.BLUETOOTH_CONNECT,
      "com.example.UNKNOWN_PERMISSION",
      ""
    )
    val actionTypes: List<DependencyActionType?> = DependencyActionType.entries + listOf(null)
    repeat(500) {
      val map = buildMap {
        repeat(rng.nextInt(0, 6)) {
          put(permNames[rng.nextInt(permNames.size)], rng.nextBoolean())
        }
      }
      val sdk = rng.nextInt(21, 42)
      val action = actionTypes[rng.nextInt(actionTypes.size)]
      val granted = isRequiredPermissionGranted(action, map, sdk)
      if (action == DependencyActionType.REQUEST_LOCATION_PERMISSION &&
        map[android.Manifest.permission.ACCESS_FINE_LOCATION] != true
      ) {
        assertFalse("Approximate-only or denied fine location must never be accepted as granted", granted)
      }
    }

    // 3. Fuzz FeatureCheckerViewModel SSID & buildType evaluation with 300 randomized snapshots
    val provider = FakeDeviceEnvironmentProvider()
    val vm = com.droid.googlebookfeaturechecker.ui.FeatureCheckerViewModel(provider)
    repeat(300) {
      val len = rng.nextInt(0, 180)
      val randomNetDesc = buildString(len) { repeat(len) { append(fuzzChars[rng.nextInt(fuzzChars.length)]) } }
      val randomBuild = buildString(rng.nextInt(0, 40)) { repeat(rng.nextInt(0, 40)) { append(fuzzChars[rng.nextInt(fuzzChars.length)]) } }
      provider.snapshot = provider.snapshot.copy(
        buildType = randomBuild,
        connectedWifiName = if (rng.nextBoolean()) randomNetDesc else null,
        networkSsidOrDescription = randomNetDesc,
        wifiConnected = rng.nextBoolean(),
        wifiEthernetOnly = rng.nextBoolean(),
        wifiHasKnownSsid = rng.nextBoolean(),
        hasLocationPermission = rng.nextBoolean(),
        isPublicOrCaptiveWifi = rng.nextBoolean(),
        vpnActive = rng.nextBoolean()
      )
      vm.refreshChecks()
      assertEquals(7, vm.uiState.value.dependencies.size)
      assertNotNull(vm.buildDiagnosticFeedbackReport())
    }
  }
}
