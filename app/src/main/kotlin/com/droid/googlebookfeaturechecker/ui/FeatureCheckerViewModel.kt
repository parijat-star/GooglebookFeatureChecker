package com.droid.googlebookfeaturechecker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droid.googlebookfeaturechecker.data.CompanionPhoneSnapshot
import com.droid.googlebookfeaturechecker.data.DeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.data.DeviceSystemSnapshot
import com.droid.googlebookfeaturechecker.model.CheckStatus
import com.droid.googlebookfeaturechecker.model.DependencyActionType
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
import com.droid.googlebookfeaturechecker.model.DependencyId
import com.droid.googlebookfeaturechecker.model.FeatureCheckerUiState
import com.droid.googlebookfeaturechecker.model.FeatureItem
import com.droid.googlebookfeaturechecker.model.PhoneDependencyStatusItem
import com.droid.googlebookfeaturechecker.model.PhoneReportState
import com.droid.googlebookfeaturechecker.model.ThemeMode
import com.droid.googlebookfeaturechecker.util.VersionComparator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeatureCheckerViewModel(
  private val environmentProvider: DeviceEnvironmentProvider,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  loadSynchronously: Boolean = true
) : ViewModel() {

  companion object {
    const val SUPPORT_THREAD_URL =
      "https://support.google.com/googlebook/thread/472325070/update-on-better-together-setup-samsung-galaxy-rollout-on-googlebook?hl=en-GB"
    const val REQUIRED_GMS_VERSION = "26.37"
    const val REQUIRED_CROSS_DEVICE_VERSION = "1.0.1346"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val CROSS_DEVICE_PACKAGE = "com.google.ambient.streaming"

    val AVAILABLE_FEATURES = listOf(
      FeatureItem(
        id = "better_together",
        title = "Googlebook Better Together",
        subtitle = "Cross-device sync for Android 17 & Samsung Galaxy (One UI 9.0)",
        subFeatures = listOf(
          "Continue On · Pick up phone tasks on your Googlebook",
          "Cast My Apps · Open phone apps in desktop windows",
          "Quick Access · Browse phone files & media in Files",
          "Notification Sync · Keep alerts in step across both devices"
        ),
        referenceThreadUrl = SUPPORT_THREAD_URL
      )
    )
  }

  private val manualCheckOverrides = mutableMapOf<DependencyId, Boolean>()

  private val _uiState = MutableStateFlow(
    if (loadSynchronously) buildInitialState() else buildPlaceholderState()
  )
  val uiState: StateFlow<FeatureCheckerUiState> = _uiState.asStateFlow()

  private var lastSnapshot: DeviceSystemSnapshot? = null

  init {
    if (!loadSynchronously) {
      refreshChecksAsync(bannerMessage = null)
    }
  }

  private fun formatTimestamp(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
  }

  private fun buildPlaceholderState(): FeatureCheckerUiState {
    val selectedFeature = AVAILABLE_FEATURES.first()
    return FeatureCheckerUiState(
      themeMode = ThemeMode.SYSTEM,
      features = AVAILABLE_FEATURES,
      selectedFeature = selectedFeature,
      localDeviceName = "Checking device…",
      localDeviceRoleLabel = "Googlebook",
      localBuildType = "checking",
      isLocalBuildTypeValid = false,
      dependencies = emptyList(),
      focusedDependencyIndex = 0,
      phoneReportState = PhoneReportState.Unreachable(
        reason = "Inspecting local and companion status…",
        tipTitle = "Tip: Install Googlebook Feature Checker on the other device",
        tipMessage = "Install and run Googlebook Feature Checker directly on the other device to inspect its Android 17 / One UI 9.0 status and matching build type.",
        phoneRequirementsChecklist = emptyList()
      ),
      isRefreshing = true,
      lastCheckedTimestampText = "Checking…"
    )
  }

  private fun buildInitialState(): FeatureCheckerUiState {
    val snapshot = environmentProvider.readLocalSystemSnapshot()
    val companion = environmentProvider.probeCompanionPhone()
    lastSnapshot = snapshot
    val selectedFeature = AVAILABLE_FEATURES.first()
    val dependencies = evaluateDependencies(snapshot, companion)
    val buildValid = isValidBuildType(snapshot.buildType) &&
      (companion == null || areBuildTypesMatching(snapshot.buildType, companion.buildType))
    return FeatureCheckerUiState(
      themeMode = ThemeMode.SYSTEM,
      features = AVAILABLE_FEATURES,
      selectedFeature = selectedFeature,
      localDeviceName = "${snapshot.deviceManufacturer} ${snapshot.deviceModel}".trim(),
      localDeviceRoleLabel = if (snapshot.isLaptopFormFactor) {
        "Googlebook"
      } else {
        "Phone"
      },
      localBuildType = snapshot.buildType,
      isLocalBuildTypeValid = buildValid,
      dependencies = dependencies,
      focusedDependencyIndex = 0,
      phoneReportState = evaluatePhoneReportState(companion, snapshot),
      isRefreshing = false,
      lastCheckedTimestampText = formatTimestamp()
    )
  }

  fun refreshChecks() {
    val snapshot = environmentProvider.readLocalSystemSnapshot()
    val companion = environmentProvider.probeCompanionPhone()
    applySnapshotState(snapshot, companion, "Refreshed all 7 dependency checks.")
  }

  fun refreshChecksAsync(bannerMessage: String? = "Refreshed all 7 dependency checks.") {
    _uiState.update { it.copy(isRefreshing = true) }
    viewModelScope.launch {
      val (snapshot, companion) = withContext(ioDispatcher) {
        environmentProvider.readLocalSystemSnapshot() to environmentProvider.probeCompanionPhone()
      }
      applySnapshotState(snapshot, companion, bannerMessage)
    }
  }

  private fun applySnapshotState(
    snapshot: DeviceSystemSnapshot,
    companion: CompanionPhoneSnapshot?,
    bannerMessage: String?
  ) {
    lastSnapshot = snapshot
    val buildValid = isValidBuildType(snapshot.buildType) &&
      (companion == null || areBuildTypesMatching(snapshot.buildType, companion.buildType))
    val dependencies = evaluateDependencies(snapshot, companion)
    _uiState.update { current ->
      current.copy(
        localDeviceName = "${snapshot.deviceManufacturer} ${snapshot.deviceModel}".trim(),
        localDeviceRoleLabel = if (snapshot.isLaptopFormFactor) {
          "Googlebook"
        } else {
          "Phone"
        },
        localBuildType = snapshot.buildType,
        isLocalBuildTypeValid = buildValid,
        dependencies = dependencies,
        phoneReportState = evaluatePhoneReportState(companion, snapshot),
        isRefreshing = false,
        lastCheckedTimestampText = formatTimestamp(),
        statusBannerMessage = bannerMessage
      )
    }
  }

  fun selectFeature(featureId: String) {
    val found = AVAILABLE_FEATURES.find { it.id == featureId } ?: return
    _uiState.update { it.copy(selectedFeature = found, focusedDependencyIndex = 0) }
  }

  fun toggleDependencyCheckMark(id: DependencyId) {
    _uiState.update { current ->
      val updated = current.dependencies.map { item ->
        if (item.id == id) {
          val newChecked = !item.isChecked
          manualCheckOverrides[id] = newChecked
          item.copy(isChecked = newChecked)
        } else {
          item
        }
      }
      current.copy(dependencies = updated)
    }
  }

  fun setFocusedDependencyIndex(index: Int) {
    _uiState.update { current ->
      if (current.dependencies.isEmpty()) current
      else current.copy(focusedDependencyIndex = index.coerceIn(0, current.dependencies.lastIndex))
    }
  }

  fun moveFocusNext() {
    _uiState.update { current ->
      if (current.dependencies.isEmpty()) current
      else {
        val next = (current.focusedDependencyIndex + 1) % current.dependencies.size
        current.copy(focusedDependencyIndex = next)
      }
    }
  }

  fun moveFocusPrevious() {
    _uiState.update { current ->
      if (current.dependencies.isEmpty()) current
      else {
        val prev = (current.focusedDependencyIndex - 1 + current.dependencies.size) % current.dependencies.size
        current.copy(focusedDependencyIndex = prev)
      }
    }
  }

  fun toggleFocusedDependency() {
    val current = _uiState.value
    val focusedItem = current.dependencies.getOrNull(current.focusedDependencyIndex) ?: return
    toggleDependencyCheckMark(focusedItem.id)
  }

  fun setThemeMode(mode: ThemeMode) {
    _uiState.update { it.copy(themeMode = mode) }
  }

  fun cycleThemeMode() {
    _uiState.update { current ->
      val next = when (current.themeMode) {
        ThemeMode.SYSTEM -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.SYSTEM
      }
      current.copy(themeMode = next)
    }
  }

  fun setStatusBannerMessage(message: String?) {
    _uiState.update { it.copy(statusBannerMessage = message) }
  }

  fun buildDiagnosticFeedbackReport(): String {
    val state = _uiState.value
    val metCount = state.dependencies.count { it.isMet }
    val checkedCount = state.dependencies.count { it.isChecked }
    return buildString {
      appendLine("=== Googlebook Feature Checker Diagnostic Report ===")
      appendLine("Feature: ${state.selectedFeature.title}")
      appendLine("Reference: ${state.selectedFeature.referenceThreadUrl}")
      appendLine("Local Device: ${state.localDeviceName} (${state.localDeviceRoleLabel})")
      appendLine("Detected Build Type: ${state.localBuildType} (Both phone and Googlebook must have the same build type—'user' with 'user', or 'userdbg' with 'userdbg'; most production phone builds are 'user')")
      lastSnapshot?.let { snap ->
        appendLine("Permissions: FINE_LOCATION=${snap.hasLocationPermission}, BLUETOOTH_CONNECT=${snap.bluetoothPermissionGranted}")
      }
      appendLine("Dependencies Met: $metCount / ${state.dependencies.size} (Click-marked: $checkedCount / ${state.dependencies.size})")
      appendLine("Last Checked: ${state.lastCheckedTimestampText}")
      appendLine()
      appendLine("--- Dependency Breakdown ---")
      state.dependencies.forEach { dep ->
        val mark = if (dep.isChecked) "[x]" else "[ ]"
        val statusTag = when (dep.status) {
          CheckStatus.MET -> "MET"
          CheckStatus.WARNING -> "MET-WITH-WARNING"
          CheckStatus.ADVISORY -> "3P-ADVISORY"
          CheckStatus.PERMISSION_REQUIRED -> "PERMISSION-REQUIRED"
          CheckStatus.NOT_MET -> "NOT MET"
        }
        appendLine("$mark ${dep.stepNumber}. ${dep.title}: [$statusTag] (${dep.detectedStatus})")
        dep.warningOrAdvisoryNote?.let { note ->
          appendLine("    Note: $note")
        }
      }
      appendLine()
      appendLine("--- Companion Phone Status ---")
      when (val phone = state.phoneReportState) {
        is PhoneReportState.Connected -> {
          appendLine("Phone Connected: ${phone.snapshot.phoneModel} (Build: ${phone.snapshot.buildType}, Android ${phone.snapshot.androidRelease})")
          phone.items.forEach { item ->
            appendLine("- ${item.title}: ${item.statusText} (${if (item.isMet) "MET" else "NOT MET"})")
          }
        }
        is PhoneReportState.Unreachable -> {
          appendLine("Other Device Direct Probe: Unreachable (${phone.reason})")
          appendLine("Tip: Install and run Googlebook Feature Checker on the other device as well to verify its dependencies.")
        }
      }
    }
  }

  private fun normalizeBuildType(buildType: String): String {
    return when (val lowered = buildType.trim().lowercase()) {
      "userdebug", "userdbg" -> "userdbg"
      else -> lowered
    }
  }

  private fun isValidBuildType(buildType: String): Boolean {
    val normalized = normalizeBuildType(buildType)
    return normalized == "user" || normalized == "userdbg"
  }

  private fun areBuildTypesMatching(buildA: String, buildB: String): Boolean {
    return isValidBuildType(buildA) &&
      isValidBuildType(buildB) &&
      normalizeBuildType(buildA) == normalizeBuildType(buildB)
  }

  private fun evaluateDependencies(
    snapshot: DeviceSystemSnapshot,
    companion: CompanionPhoneSnapshot? = null
  ): List<DependencyCheckItem> {
    // 1. Android Version (Android 17 / One UI 9.0 for Samsung Galaxy)
    val releaseParts = VersionComparator.extractLeadingVersionParts(snapshot.androidRelease)
    val majorRelease = releaseParts.firstOrNull() ?: 0L
    val isSamsung = snapshot.deviceManufacturer.contains("samsung", ignoreCase = true)
    val oneUiMet = if (isSamsung && snapshot.oneUiVersion != null) {
      VersionComparator.isAtLeast(snapshot.oneUiVersion, "9.0")
    } else {
      true
    }
    val androidVersionMet = (majorRelease >= 17L || snapshot.sdkInt >= 37) && oneUiMet
    val oneUiSuffix = snapshot.oneUiVersion?.let { " · One UI $it" } ?: ""
    val androidItem = DependencyCheckItem(
      id = DependencyId.ANDROID_VERSION,
      stepNumber = 1,
      title = "Android 17 / One UI 9.0",
      requirementSummary = "Requires Android 17 (API 37+) on both devices, and One UI 9.0+ on Samsung Galaxy phones.",
      detectedStatus = "Android ${snapshot.androidRelease} (API ${snapshot.sdkInt}$oneUiSuffix)",
      isMet = androidVersionMet,
      hasUpdateAvailable = !androidVersionMet,
      isChecked = manualCheckOverrides[DependencyId.ANDROID_VERSION] ?: androidVersionMet,
      whyNotOrFixInstruction = if (androidVersionMet) {
        "Running Android ${snapshot.androidRelease}$oneUiSuffix."
      } else {
        "Update to Android 17 (One UI 9.0 on Galaxy phones) in Settings > System > Software update."
      },
      phoneRequirementNote = "Phone must also run Android 17 (Settings > About phone > Software information).",
      actionLabel = "Check System Update",
      actionType = DependencyActionType.OPEN_SYSTEM_UPDATE
    )

    // 2. Google Play Services APK (26.37 or newer)
    val gmsMet = snapshot.gmsInstalled && VersionComparator.isAtLeast(snapshot.gmsVersionName, REQUIRED_GMS_VERSION)
    val gmsUpdateAvailable = !gmsMet || snapshot.gmsUpdateAvailable
    val gmsDetected = if (snapshot.gmsInstalled) {
      "v${snapshot.gmsVersionName ?: "unknown"} installed · requires v$REQUIRED_GMS_VERSION+"
    } else {
      "Not installed · requires v$REQUIRED_GMS_VERSION+"
    }
    val gmsItem = DependencyCheckItem(
      id = DependencyId.GMS_APK,
      stepNumber = 2,
      title = "Google Play services (v26.37+)",
      requirementSummary = "Streamlines first-time Cross-device setup and fixes the stuck setup QR code issue.",
      detectedStatus = gmsDetected,
      isMet = gmsMet,
      hasUpdateAvailable = gmsUpdateAvailable,
      isChecked = manualCheckOverrides[DependencyId.GMS_APK] ?: gmsMet,
      whyNotOrFixInstruction = if (gmsMet) {
        "Google Play services meets the v$REQUIRED_GMS_VERSION+ requirement."
      } else {
        "An APK update to v26.37 or newer is needed. Tap 'Update Apk' to update Google Play services in the Play Store."
      },
      phoneRequirementNote = "Phone also needs Google Play services v26.37 or newer.",
      actionLabel = "Update Apk",
      actionType = DependencyActionType.UPDATE_APK,
      targetPackage = GMS_PACKAGE
    )

    // 3. Cross-Device Services APK (1.0.1346 or newer)
    val crossDeviceMet = snapshot.crossDeviceInstalled &&
      VersionComparator.isAtLeast(snapshot.crossDeviceVersionName, REQUIRED_CROSS_DEVICE_VERSION)
    val crossDeviceUpdateAvailable = !crossDeviceMet || snapshot.crossDeviceUpdateAvailable
    val crossDetected = if (snapshot.crossDeviceInstalled) {
      "v${snapshot.crossDeviceVersionName ?: "unknown"} installed · requires v$REQUIRED_CROSS_DEVICE_VERSION+"
    } else {
      "Not installed · requires v$REQUIRED_CROSS_DEVICE_VERSION+"
    }
    val crossDeviceItem = DependencyCheckItem(
      id = DependencyId.CROSS_DEVICE_APK,
      stepNumber = 3,
      title = "Cross-device services (v1.0.1346+)",
      requirementSummary = "Required for Continue On, Cast My Apps, Quick Access, and Notification Sync.",
      detectedStatus = crossDetected,
      isMet = crossDeviceMet,
      status = if (crossDeviceMet) CheckStatus.MET else CheckStatus.NOT_MET,
      hasUpdateAvailable = crossDeviceUpdateAvailable,
      isChecked = manualCheckOverrides[DependencyId.CROSS_DEVICE_APK] ?: crossDeviceMet,
      whyNotOrFixInstruction = if (crossDeviceMet) {
        "Cross-device services meets the v$REQUIRED_CROSS_DEVICE_VERSION+ requirement."
      } else {
        "An APK update to v1.0.1346 or newer is needed. Tap 'Update Apk' to update Cross-device services."
      },
      phoneRequirementNote = "Phone also needs Cross-device services v1.0.1346 or newer.",
      actionLabel = "Update Apk",
      actionType = DependencyActionType.UPDATE_APK,
      targetPackage = CROSS_DEVICE_PACKAGE
    )

    // 4. Private Home Wi-Fi & No Public/Captive Network, Host Isolation, or VPN
    val wifiNeedsLocation = !snapshot.hasLocationPermission && snapshot.wifiConnected && !snapshot.wifiEthernetOnly
    val wifiMet = snapshot.wifiConnected &&
      !snapshot.isPublicOrCaptiveWifi &&
      !snapshot.vpnActive &&
      !wifiNeedsLocation
    val connectedWifiLabel = resolveConnectedWifiLabel(snapshot)
    val usableSsid = hasUsableWifiSsid(snapshot, connectedWifiLabel)
    val wifiDetectedStatus = when {
      !snapshot.wifiConnected -> "Not connected to Wi-Fi"
      snapshot.wifiEthernetOnly -> "Connected via Ethernet LAN"
      else -> "Connected to Wi-Fi: $connectedWifiLabel"
    }
    val wifiPhoneNote = if (usableSsid) {
      "Phone should be connected to \"$connectedWifiLabel\" Wi-Fi (and charging to expedite background updates)."
    } else {
      "Phone should be connected to the same private home Wi-Fi network as this device (and charging to expedite background updates)."
    }
    val wifiWhyNot = when {
      !snapshot.wifiConnected ->
        "Not connected to Wi-Fi. Connect both your Googlebook and phone to the same private home Wi-Fi network."
      snapshot.vpnActive && snapshot.isPublicOrCaptiveWifi ->
        snapshot.wifiDetailedIssue?.takeIf { it.isNotBlank() }
          ?: "Public/guest Wi-Fi (\"$connectedWifiLabel\") and an active VPN were detected. Switch to a private home Wi-Fi network and pause any VPN during setup."
      snapshot.vpnActive ->
        if (usableSsid) {
          "An active VPN or DNS blocker was detected on \"$connectedWifiLabel\". Pause any VPN or custom DNS blocker during Better Together setup."
        } else {
          "An active VPN or DNS blocker was detected. Pause any VPN or custom DNS blocker during Better Together setup."
        }
      !wifiMet && !snapshot.isPublicOrCaptiveWifi && wifiNeedsLocation ->
        "Location permission is required on Android to read the Wi-Fi SSID and confirm you are not on a guest or public network."
      !wifiMet && !snapshot.wifiDetailedIssue.isNullOrBlank() ->
        snapshot.wifiDetailedIssue
      snapshot.isPublicOrCaptiveWifi ->
        "Public, hotel, cafe, office, or guest Wi-Fi (\"$connectedWifiLabel\") detected—these networks enforce AP host isolation so devices cannot talk to each other. Switch to a private home Wi-Fi."
      snapshot.wifiEthernetOnly ->
        "Connected via wired Ethernet LAN with no captive portal or active VPN."
      else ->
        if (usableSsid) {
          "Connected to private Wi-Fi \"$connectedWifiLabel\" with no captive portal or active VPN."
        } else {
          "Connected to private Wi-Fi with no captive portal or active VPN."
        }
    }
    val wifiWarningList = buildList {
      if (wifiNeedsLocation) {
        add("Location permission is required on Android to view the Wi-Fi SSID and detect guest/public network names.")
      }
      addAll(snapshot.wifiWarnings)
    }.distinct()
    val wifiStatus = when {
      wifiNeedsLocation && !snapshot.isPublicOrCaptiveWifi && !snapshot.vpnActive -> CheckStatus.PERMISSION_REQUIRED
      !wifiMet -> CheckStatus.NOT_MET
      wifiWarningList.isNotEmpty() -> CheckStatus.WARNING
      else -> CheckStatus.MET
    }
    val wifiActionLabel = when {
      snapshot.vpnActive -> "Pause VPN"
      !snapshot.wifiConnected -> "Connect Wi-Fi"
      snapshot.isPublicOrCaptiveWifi -> "Switch Wi-Fi"
      wifiNeedsLocation -> "Grant Location"
      else -> "Fix Wi-Fi"
    }
    val wifiActionType = if (wifiNeedsLocation && !snapshot.vpnActive && !snapshot.isPublicOrCaptiveWifi && snapshot.wifiConnected) {
      DependencyActionType.REQUEST_LOCATION_PERMISSION
    } else {
      DependencyActionType.OPEN_WIFI_SETTINGS
    }
    val wifiItem = DependencyCheckItem(
      id = DependencyId.PRIVATE_WIFI,
      stepNumber = 4,
      title = "Private home Wi-Fi & no active VPN",
      requirementSummary = "Public/guest networks and VPNs block local device-to-device discovery.",
      detectedStatus = wifiDetectedStatus,
      isMet = wifiMet,
      status = wifiStatus,
      hasUpdateAvailable = wifiNeedsLocation,
      isChecked = manualCheckOverrides[DependencyId.PRIVATE_WIFI] ?: wifiMet,
      whyNotOrFixInstruction = wifiWhyNot,
      warningOrAdvisoryNote = wifiWarningList.takeIf { it.isNotEmpty() }?.joinToString(" • "),
      phoneRequirementNote = wifiPhoneNote,
      actionLabel = wifiActionLabel,
      actionType = wifiActionType
    )

    // 5. Bluetooth Enabled
    val btPermissionMissing = snapshot.bluetoothSupported && !snapshot.bluetoothPermissionGranted
    val btMet = snapshot.bluetoothSupported && snapshot.bluetoothPermissionGranted && snapshot.bluetoothEnabled
    val btStatusText = when {
      !snapshot.bluetoothSupported -> "Bluetooth adapter unavailable"
      btPermissionMissing -> "Bluetooth permission required to verify adapter state"
      snapshot.bluetoothEnabled -> "Bluetooth is on"
      else -> "Bluetooth is off"
    }
    val btStatusEnum = when {
      btPermissionMissing -> CheckStatus.PERMISSION_REQUIRED
      btMet -> CheckStatus.MET
      else -> CheckStatus.NOT_MET
    }
    val btItem = DependencyCheckItem(
      id = DependencyId.BLUETOOTH,
      stepNumber = 5,
      title = "Bluetooth turned on",
      requirementSummary = "Used alongside Wi-Fi to discover your nearby phone and complete pairing.",
      detectedStatus = btStatusText,
      isMet = btMet,
      status = btStatusEnum,
      hasUpdateAvailable = false,
      isChecked = manualCheckOverrides[DependencyId.BLUETOOTH] ?: btMet,
      whyNotOrFixInstruction = when {
        btMet -> "Bluetooth is active for proximity discovery."
        btPermissionMissing -> "Grant Nearby devices (Bluetooth) permission so this app can verify whether Bluetooth is enabled."
        else -> "Bluetooth is turned off. Turn Bluetooth on for both your Googlebook and your phone."
      },
      phoneRequirementNote = "Ensure Bluetooth is turned on for your phone as well.",
      actionLabel = if (btPermissionMissing) "Grant Permission" else "Enable Bluetooth",
      actionType = if (btPermissionMissing) {
        DependencyActionType.REQUEST_BLUETOOTH_PERMISSION
      } else {
        DependencyActionType.OPEN_BLUETOOTH_SETTINGS
      }
    )

    // 6. Same Personal Google Account & "Continue Activity" Options
    val continueVerified = snapshot.continueActivityVerifiedBySystem
    val continueMet = continueVerified && snapshot.hasGoogleAccountSignedIn && snapshot.continueActivityConfigured
    val continueCheckStatus = when {
      !continueVerified -> CheckStatus.ADVISORY
      continueMet -> CheckStatus.MET
      else -> CheckStatus.NOT_MET
    }
    val continueStatusText = when {
      !continueVerified && snapshot.crossDeviceSettingsResolvable ->
        "Cross-device settings available · Manual verification required"
      !continueVerified ->
        "Cannot verify account parity or toggles from a third-party app"
      continueMet ->
        "Cross-device services available on this device"
      else ->
        "Needs setup in Settings > Cross-device services > Continue activity"
    }
    val continueAdvisoryNote = if (!continueVerified) {
      "Android blocks third-party apps from inspecting Google account parity or private Continue activity toggles. Tap 'Open Cross-Device Settings' to confirm manually."
    } else {
      null
    }
    val continueItem = DependencyCheckItem(
      id = DependencyId.CONTINUE_ACTIVITY_SETTINGS,
      stepNumber = 6,
      title = "Same Google account & 'Continue activity' options",
      requirementSummary = "Sign into the same personal Google account and enable Tasks, Apps, Notifications, and Files & media sharing, then restart both devices.",
      detectedStatus = continueStatusText,
      isMet = continueMet,
      status = continueCheckStatus,
      hasUpdateAvailable = false,
      isChecked = manualCheckOverrides[DependencyId.CONTINUE_ACTIVITY_SETTINGS] ?: continueMet,
      whyNotOrFixInstruction = "In Settings > Cross-device services > Continue activity, turn on Tasks, Apps, Notifications, and Files & media sharing, then restart your phone and Googlebook.",
      warningOrAdvisoryNote = continueAdvisoryNote,
      phoneRequirementNote = "Phone must be signed into the same personal Google account with all Continue activity toggles on.",
      actionLabel = "Open Cross-Device Settings",
      actionType = DependencyActionType.OPEN_CROSS_DEVICE_SETTINGS
    )

    // 7. Device Build Type (LAST in checklist: user or userdbg, and BOTH devices must match!)
    val localBuildValid = isValidBuildType(snapshot.buildType)
    val companionMatches = companion == null || areBuildTypesMatching(snapshot.buildType, companion.buildType)
    val buildMet = localBuildValid && companionMatches
    val normalizedLocal = normalizeBuildType(snapshot.buildType)
    val expectedPairLabel = if (normalizedLocal == "user") "user" else "userdbg"
    val buildDetectedStatus = if (companion != null) {
      "This device (${snapshot.deviceModel}): ${snapshot.buildType} · Phone (${companion.phoneModel}): ${companion.buildType}"
    } else {
      "This device (${snapshot.deviceModel}) build type: ${snapshot.buildType} (paired device must also be '$expectedPairLabel')"
    }
    val buildWhyNot = when {
      !localBuildValid ->
        "This device is on an '${snapshot.buildType}' build. Engineering ('eng') builds cannot pass Cross-device attestation—install a matching 'user' or 'userdbg' build."
      companion != null && !companionMatches ->
        "Build type mismatch: this device is '${snapshot.buildType}' but your connected phone (${companion.phoneModel}) is '${companion.buildType}'. If a Googlebook is on a 'user' build, the phone must be 'user'; if a Googlebook is on 'userdbg', the connected phone must be 'userdbg' too."
      else ->
        "This device is on a '${snapshot.buildType}' build (paired device must also be '$expectedPairLabel'; most production phone builds are expected to be 'user' builds)."
    }
    val buildItem = DependencyCheckItem(
      id = DependencyId.BUILD_TYPE,
      stepNumber = 7,
      title = "Matching device build type (user or userdbg)",
      requirementSummary = "",
      detectedStatus = buildDetectedStatus,
      isMet = buildMet,
      hasUpdateAvailable = false,
      isChecked = manualCheckOverrides[DependencyId.BUILD_TYPE] ?: buildMet,
      whyNotOrFixInstruction = buildWhyNot,
      phoneRequirementNote = "If Googlebook is a 'user' build, phone must be a 'user' build; if Googlebook is 'userdbg', connected phone must be 'userdbg' too. Most production phone builds are expected to be 'user' builds.",
      actionLabel = "View Build Info",
      actionType = DependencyActionType.OPEN_DEVICE_INFO
    )

    return listOf(
      androidItem,
      gmsItem,
      crossDeviceItem,
      wifiItem,
      btItem,
      continueItem,
      buildItem
    )
  }

  private fun resolveConnectedWifiLabel(snapshot: DeviceSystemSnapshot): String {
    val extractedQuotedSsid = Regex("\"([^\"]+)\"").find(snapshot.networkSsidOrDescription)?.groupValues?.getOrNull(1)
    val extractedParenSsid = Regex("\\(([^)]+)\\)").find(snapshot.networkSsidOrDescription)?.groupValues?.getOrNull(1)
      ?.takeUnless { it.matches(Regex("""\d+\s+warnings?""", RegexOption.IGNORE_CASE)) }
    return snapshot.connectedWifiName?.takeIf {
      it.isNotBlank() && !it.equals("Not connected", ignoreCase = true)
    } ?: extractedQuotedSsid
      ?: extractedParenSsid
      ?: if (snapshot.wifiConnected && snapshot.networkSsidOrDescription.isNotBlank()) {
        snapshot.networkSsidOrDescription
          .substringBefore(" · ")
          .removePrefix("Connected to Wi-Fi:")
          .replace(Regex("""\s*\(\d+\s+warnings?\)$""", RegexOption.IGNORE_CASE), "")
          .trim()
          .removeSurrounding("\"")
      } else {
        "Not connected"
      }
  }

  private fun hasUsableWifiSsid(snapshot: DeviceSystemSnapshot, resolvedLabel: String): Boolean {
    return snapshot.wifiConnected &&
      !snapshot.wifiEthernetOnly &&
      snapshot.wifiHasKnownSsid &&
      resolvedLabel.isNotBlank() &&
      !resolvedLabel.equals("Not connected", ignoreCase = true) &&
      !resolvedLabel.equals("Ethernet LAN", ignoreCase = true) &&
      !resolvedLabel.startsWith("Wi-Fi (", ignoreCase = true)
  }

  private fun evaluatePhoneReportState(
    companion: CompanionPhoneSnapshot?,
    snapshot: DeviceSystemSnapshot
  ): PhoneReportState {
    val localBuildType = snapshot.buildType
    val expectedPairLabel = if (normalizeBuildType(localBuildType) == "user") "user" else "userdbg"
    val connectedWifiLabel = resolveConnectedWifiLabel(snapshot)
    val networkRequirementLine = if (hasUsableWifiSsid(snapshot, connectedWifiLabel)) {
      "Network: Same private Wi-Fi (\"$connectedWifiLabel\"), No VPN & Bluetooth ON"
    } else {
      "Network: Same private Wi-Fi, No VPN & Bluetooth ON"
    }
    if (companion == null) {
      return PhoneReportState.Unreachable(
        reason = "No active companion bridge detected (ADB loopback probe on 127.0.0.1:47232 is disabled by default and Android isolates cross-device inspection).",
        tipTitle = "Tip: Install Googlebook Feature Checker on the other device",
        tipMessage = "Install and run Googlebook Feature Checker directly on the other device to check its Android 17 / One UI 9.0 status, verify that its build type matches this device ('$expectedPairLabel'), and update Google Play services or Cross-device services directly on that device.",
        phoneRequirementsChecklist = listOf(
          "OS version: Android 17 (One UI 9.0 on Samsung Galaxy devices)",
          "Google Play services APK: v26.37 or newer",
          "Cross-device services APK: v1.0.1346 or newer",
          networkRequirementLine,
          "Continue activity: Tasks, Apps, Notifications, and Files & media sharing on, then restart both devices",
          "Matching build type: Must match this device ('$expectedPairLabel' — both devices must be 'user', or both 'userdbg'; most production builds are 'user')"
        )
      )
    }

    val buildMet = areBuildTypesMatching(localBuildType, companion.buildType)
    val androidMet = VersionComparator.isAtLeast(companion.androidRelease, "17")
    val gmsMet = VersionComparator.isAtLeast(companion.gmsVersionName, REQUIRED_GMS_VERSION)
    val crossMet = VersionComparator.isAtLeast(companion.crossDeviceVersionName, REQUIRED_CROSS_DEVICE_VERSION)
    val netMet = companion.privateWifiConnected && companion.bluetoothEnabled

    val items = listOf(
      PhoneDependencyStatusItem(
        title = "Phone Android 17 / One UI 9.0",
        statusText = "Android ${companion.androidRelease}" + (companion.oneUiVersion?.let { " · One UI $it" } ?: ""),
        isMet = androidMet
      ),
      PhoneDependencyStatusItem(
        title = "Phone Google Play services (26.37+)",
        statusText = "v${companion.gmsVersionName ?: "Not found"}",
        isMet = gmsMet
      ),
      PhoneDependencyStatusItem(
        title = "Phone Cross-device services (1.0.1346+)",
        statusText = "v${companion.crossDeviceVersionName ?: "Not found"}",
        isMet = crossMet
      ),
      PhoneDependencyStatusItem(
        title = "Phone private Wi-Fi & Bluetooth",
        statusText = "Private Wi-Fi: ${if (companion.privateWifiConnected) "Yes" else "No"} · BT: ${if (companion.bluetoothEnabled) "On" else "Off"}",
        isMet = netMet
      ),
      PhoneDependencyStatusItem(
        title = "Phone matching build type ($expectedPairLabel)",
        statusText = "Phone: ${companion.buildType} · Local: $localBuildType",
        isMet = buildMet
      )
    )

    return PhoneReportState.Connected(
      snapshot = companion,
      items = items,
      allPhoneDependenciesMet = items.all { it.isMet }
    )
  }
}
