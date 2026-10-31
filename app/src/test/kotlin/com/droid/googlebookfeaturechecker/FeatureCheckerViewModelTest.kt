package com.droid.googlebookfeaturechecker

import com.droid.googlebookfeaturechecker.data.CompanionPhoneSnapshot
import com.droid.googlebookfeaturechecker.data.DeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.data.DeviceSystemSnapshot
import com.droid.googlebookfeaturechecker.model.DependencyActionType
import com.droid.googlebookfeaturechecker.model.DependencyId
import com.droid.googlebookfeaturechecker.model.PhoneReportState
import com.droid.googlebookfeaturechecker.model.ThemeMode
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerViewModel
import com.droid.googlebookfeaturechecker.util.VersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDeviceEnvironmentProvider(
  var snapshot: DeviceSystemSnapshot = DeviceSystemSnapshot(
    deviceModel = "Googlebook Pro 14",
    deviceManufacturer = "Google",
    isLaptopFormFactor = true,
    buildType = "userdebug",
    buildFingerprint = "google/googlebook/googlebook:17/BP1A.261005.004/123456:userdebug/dev-keys",
    androidRelease = "17",
    sdkInt = 37,
    oneUiVersion = null,
    gmsInstalled = true,
    gmsVersionName = "26.42.0 (190400-123456)",
    crossDeviceInstalled = true,
    crossDevicePackageName = "com.google.ambient.streaming",
    crossDeviceVersionName = "1.0.1420",
    wifiConnected = true,
    isPublicOrCaptiveWifi = false,
    vpnActive = false,
    networkSsidOrDescription = "Private Wi-Fi (Home_5G)",
    bluetoothSupported = true,
    bluetoothEnabled = true,
    hasGoogleAccountSignedIn = true,
    continueActivityConfigured = true
  ),
  var companionSnapshot: CompanionPhoneSnapshot? = null
) : DeviceEnvironmentProvider {
  override fun readLocalSystemSnapshot(): DeviceSystemSnapshot = snapshot
  override fun probeCompanionPhone(): CompanionPhoneSnapshot? = companionSnapshot
}

class FeatureCheckerViewModelTest {

  @Test
  fun versionComparator_comparesNumericVersionsCorrectly() {
    assertTrue(VersionComparator.isAtLeast("26.37", "26.37"))
    assertTrue(VersionComparator.isAtLeast("26.37.14 (190400-999)", "26.37"))
    assertTrue(VersionComparator.isAtLeast("26.40.0", "26.37"))
    assertFalse(VersionComparator.isAtLeast("26.36.99", "26.37"))
    assertFalse(VersionComparator.isAtLeast(null, "26.37"))

    assertTrue(VersionComparator.isAtLeast("1.0.1346", "1.0.1346"))
    assertTrue(VersionComparator.isAtLeast("1.0.1400-release", "1.0.1346"))
    assertFalse(VersionComparator.isAtLeast("1.0.1345", "1.0.1346"))
  }

  @Test
  fun initialState_defaultsToBetterTogetherAndSystemTheme_allDependenciesMet_buildTypeIsLast() {
    val provider = FakeDeviceEnvironmentProvider()
    val viewModel = FeatureCheckerViewModel(provider)
    val state = viewModel.uiState.value

    assertEquals(ThemeMode.SYSTEM, state.themeMode)
    assertEquals("better_together", state.selectedFeature.id)
    assertEquals("Googlebook Better Together", state.selectedFeature.title)
    assertEquals(7, state.dependencies.size)
    assertTrue(state.dependencies.all { it.isMet })
    assertTrue(state.dependencies.all { it.isChecked })
    // When all dependencies are met and no update is available, action buttons are hidden
    assertTrue(state.dependencies.none { it.shouldShowActionButton })

    // Verify BUILD_TYPE is the LAST item (#7) in the checklist, not the first, and has no "Both your Googlebook and phone..." requirementSummary line
    assertEquals(DependencyId.ANDROID_VERSION, state.dependencies.first().id)
    assertEquals(1, state.dependencies.first().stepNumber)
    val buildDep = state.dependencies.last()
    assertEquals(DependencyId.BUILD_TYPE, buildDep.id)
    assertEquals(7, buildDep.stepNumber)
    assertTrue(buildDep.detectedStatus.contains("userdebug"))
    assertEquals("", buildDep.requirementSummary)
    assertFalse(buildDep.phoneRequirementNote.contains("Both your Googlebook and phone"))
    assertTrue(buildDep.phoneRequirementNote.contains("production phone builds are expected to be 'user' builds"))

    // Verify Wi-Fi item shows connected Wi-Fi name and phone requirement note for that named Wi-Fi
    val wifiDep = state.dependencies.first { it.id == DependencyId.PRIVATE_WIFI }
    assertTrue(wifiDep.detectedStatus.contains("Home_5G"))
    assertTrue(wifiDep.phoneRequirementNote.contains("Phone should be connected to \"Home_5G\" Wi-Fi"))
    assertEquals("Wifi Not Supported", wifiDep.actionLabel)
  }

  @Test
  fun crossDeviceServicesAtBaseline_doesNotShowUpdateAvailableUnlessFlagged() {
    val provider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(
        gmsVersionName = "26.38.12",
        gmsUpdateAvailable = true, // Explicit update available while met
        crossDeviceVersionName = "1.0.1346", // Meets 1.0.1346+ and is latest -> no update available!
        crossDeviceUpdateAvailable = false
      )
    )
    val viewModel = FeatureCheckerViewModel(provider)
    val state = viewModel.uiState.value

    val gmsDep = state.dependencies.first { it.id == DependencyId.GMS_APK }
    assertTrue(gmsDep.isMet)
    assertTrue(gmsDep.hasUpdateAvailable)
    assertTrue(gmsDep.shouldShowActionButton)

    val crossDep = state.dependencies.first { it.id == DependencyId.CROSS_DEVICE_APK }
    assertTrue(crossDep.isMet)
    assertFalse(crossDep.hasUpdateAvailable)
    assertFalse(crossDep.shouldShowActionButton)
  }

  @Test
  fun outdatedApks_reportsUnmetAndShowsUpdateApkActionButton() {
    val provider = FakeDeviceEnvironmentProvider(
      snapshot = DeviceSystemSnapshot(
        deviceModel = "Galaxy S26 Ultra",
        deviceManufacturer = "Samsung",
        isLaptopFormFactor = false,
        buildType = "user",
        buildFingerprint = "samsung/s26/s26:17/UP1A/123:user/release-keys",
        androidRelease = "17",
        sdkInt = 37,
        oneUiVersion = "9.0",
        gmsInstalled = true,
        gmsVersionName = "26.31.05",
        crossDeviceInstalled = true,
        crossDevicePackageName = "com.google.ambient.streaming",
        crossDeviceVersionName = "1.0.1200",
        wifiConnected = true,
        isPublicOrCaptiveWifi = false,
        vpnActive = false,
        networkSsidOrDescription = "Private Wi-Fi",
        bluetoothSupported = true,
        bluetoothEnabled = true,
        hasGoogleAccountSignedIn = true,
        continueActivityConfigured = true
      )
    )
    val viewModel = FeatureCheckerViewModel(provider)
    val state = viewModel.uiState.value

    val gmsDep = state.dependencies.first { it.id == DependencyId.GMS_APK }
    assertFalse(gmsDep.isMet)
    assertTrue(gmsDep.hasUpdateAvailable)
    assertTrue(gmsDep.shouldShowActionButton)
    assertEquals("Update Apk", gmsDep.actionLabel)
    assertEquals(DependencyActionType.UPDATE_APK, gmsDep.actionType)
    assertEquals("com.google.android.gms", gmsDep.targetPackage)

    val crossDeviceDep = state.dependencies.first { it.id == DependencyId.CROSS_DEVICE_APK }
    assertFalse(crossDeviceDep.isMet)
    assertTrue(crossDeviceDep.hasUpdateAvailable)
    assertTrue(crossDeviceDep.shouldShowActionButton)
    assertEquals("Update Apk", crossDeviceDep.actionLabel)
    assertEquals(DependencyActionType.UPDATE_APK, crossDeviceDep.actionType)
    assertEquals("com.google.ambient.streaming", crossDeviceDep.targetPackage)

    // Met dependencies on the same device do NOT show action buttons
    val wifiDep = state.dependencies.first { it.id == DependencyId.PRIVATE_WIFI }
    assertTrue(wifiDep.isMet)
    assertFalse(wifiDep.shouldShowActionButton)
  }

  @Test
  fun buildType_enforcesSameBuildTypeBetweenGooglebookAndPhone_andRejectsEng() {
    // 1. Local is userdbg, connected phone is userdbg -> Met!
    val matchingUserdbgProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(buildType = "userdbg"),
      companionSnapshot = CompanionPhoneSnapshot(
        phoneModel = "Pixel 10 Pro",
        buildType = "userdebug",
        androidRelease = "17",
        oneUiVersion = null,
        gmsVersionName = "26.38.00",
        crossDeviceVersionName = "1.0.1350",
        privateWifiConnected = true,
        bluetoothEnabled = true
      )
    )
    val vmMatching = FeatureCheckerViewModel(matchingUserdbgProvider)
    assertTrue(
      vmMatching.uiState.value.dependencies.last().isMet
    )

    // 2. Local is userdebug, connected phone is user -> MISMATCH (Not Met!)
    val mismatchProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(buildType = "userdebug"),
      companionSnapshot = CompanionPhoneSnapshot(
        phoneModel = "Galaxy S26",
        buildType = "user",
        androidRelease = "17",
        oneUiVersion = "9.0",
        gmsVersionName = "26.38.00",
        crossDeviceVersionName = "1.0.1350",
        privateWifiConnected = true,
        bluetoothEnabled = true
      )
    )
    val vmMismatch = FeatureCheckerViewModel(mismatchProvider)
    val mismatchDep = vmMismatch.uiState.value.dependencies.last()
    assertEquals(DependencyId.BUILD_TYPE, mismatchDep.id)
    assertFalse(mismatchDep.isMet)
    assertTrue(mismatchDep.whyNotOrFixInstruction.contains("mismatch", ignoreCase = true))

    // 3. Local is eng -> Rejected
    val engProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(buildType = "eng")
    )
    val vmEng = FeatureCheckerViewModel(engProvider)
    val buildDep = vmEng.uiState.value.dependencies.last()
    assertFalse(buildDep.isMet)
    assertTrue(buildDep.shouldShowActionButton)
    assertTrue(buildDep.detectedStatus.contains("eng"))
    assertTrue(buildDep.whyNotOrFixInstruction.contains("user") && buildDep.whyNotOrFixInstruction.contains("userdbg"))
  }

  @Test
  fun publicWifiOrVpn_marksWifiDependencyUnmetWithWifiNotSupportedLabelAndConnectedName() {
    val publicWifiProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(
        connectedWifiName = "Airport_Free_WiFi",
        networkSsidOrDescription = "Connected to Wi-Fi: \"Airport_Free_WiFi\" · Public/Guest SSID (\"Airport_Free_WiFi\")",
        isPublicOrCaptiveWifi = true,
        vpnActive = true
      )
    )
    val viewModel = FeatureCheckerViewModel(publicWifiProvider)
    val wifiDep = viewModel.uiState.value.dependencies.first { it.id == DependencyId.PRIVATE_WIFI }

    assertFalse(wifiDep.isMet)
    assertTrue(wifiDep.shouldShowActionButton)
    assertEquals("Wifi Not Supported", wifiDep.actionLabel)
    assertTrue(wifiDep.detectedStatus.contains("Airport_Free_WiFi"))
    assertTrue(wifiDep.phoneRequirementNote.contains("Phone should be connected to \"Airport_Free_WiFi\" Wi-Fi"))
    assertFalse(wifiDep.isChecked)
    assertTrue(wifiDep.whyNotOrFixInstruction.contains("private", ignoreCase = true))
    assertEquals(DependencyActionType.OPEN_WIFI_SETTINGS, wifiDep.actionType)
  }

  @Test
  fun clickMarkAndKeyboardNavigation_togglesDependencyCheckMarks() {
    val provider = FakeDeviceEnvironmentProvider()
    val viewModel = FeatureCheckerViewModel(provider)

    val firstId = viewModel.uiState.value.dependencies.first().id
    assertTrue(viewModel.uiState.value.dependencies.first().isChecked)

    viewModel.toggleDependencyCheckMark(firstId)
    assertFalse(viewModel.uiState.value.dependencies.first().isChecked)

    viewModel.toggleDependencyCheckMark(firstId)
    assertTrue(viewModel.uiState.value.dependencies.first().isChecked)

    assertEquals(0, viewModel.uiState.value.focusedDependencyIndex)
    viewModel.moveFocusNext()
    assertEquals(1, viewModel.uiState.value.focusedDependencyIndex)
    viewModel.toggleFocusedDependency()
    assertFalse(viewModel.uiState.value.dependencies[1].isChecked)
    viewModel.moveFocusPrevious()
    assertEquals(0, viewModel.uiState.value.focusedDependencyIndex)
  }

  @Test
  fun companionPhoneCheck_reportsUnreachableTipOrConnectedPhoneStatusWithMatchingBuildType() {
    val provider = FakeDeviceEnvironmentProvider(companionSnapshot = null)
    val viewModel = FeatureCheckerViewModel(provider)

    val unreachableState = viewModel.uiState.value.phoneReportState
    assertTrue(unreachableState is PhoneReportState.Unreachable)
    val tipText = (unreachableState as PhoneReportState.Unreachable).tipMessage
    assertTrue(tipText.contains("phone", ignoreCase = true))
    assertTrue(tipText.contains("Googlebook Feature Checker", ignoreCase = true))

    // Local snapshot in FakeDeviceEnvironmentProvider is "userdebug" -> phone with "userdbg" matches!
    provider.companionSnapshot = CompanionPhoneSnapshot(
      phoneModel = "Samsung Galaxy S25",
      buildType = "userdbg",
      androidRelease = "17",
      oneUiVersion = "9.0",
      gmsVersionName = "26.37.02",
      crossDeviceVersionName = "1.0.1348",
      privateWifiConnected = true,
      bluetoothEnabled = true
    )
    viewModel.refreshChecks()

    val connectedState = viewModel.uiState.value.phoneReportState
    assertTrue(connectedState is PhoneReportState.Connected)
    val connected = connectedState as PhoneReportState.Connected
    assertEquals("Samsung Galaxy S25", connected.snapshot.phoneModel)
    assertTrue(connected.allPhoneDependenciesMet)
  }

  @Test
  fun themeAndFeedbackReport_workCorrectly() {
    val provider = FakeDeviceEnvironmentProvider()
    val viewModel = FeatureCheckerViewModel(provider)

    viewModel.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    viewModel.cycleThemeMode()
    assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
    viewModel.cycleThemeMode()
    assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)

    val report = viewModel.buildDiagnosticFeedbackReport()
    assertNotNull(report)
    assertTrue(report.contains("Googlebook Better Together"))
    assertTrue(report.contains("userdebug"))
    assertTrue(report.contains("support.google.com/googlebook/thread/472325070"))
  }
}
