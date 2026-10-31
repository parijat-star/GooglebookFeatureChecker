package com.droid.googlebookfeaturechecker.model

enum class ThemeMode(val displayName: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark")
}

enum class DependencyId {
  BUILD_TYPE,
  ANDROID_VERSION,
  GMS_APK,
  CROSS_DEVICE_APK,
  PRIVATE_WIFI,
  BLUETOOTH,
  CONTINUE_ACTIVITY_SETTINGS
}

enum class DependencyActionType {
  UPDATE_APK,
  OPEN_SYSTEM_UPDATE,
  OPEN_DEVICE_INFO,
  OPEN_WIFI_SETTINGS,
  OPEN_BLUETOOTH_SETTINGS,
  OPEN_CROSS_DEVICE_SETTINGS
}

data class FeatureItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val subFeatures: List<String>,
  val referenceThreadUrl: String
)

data class DependencyCheckItem(
  val id: DependencyId,
  val stepNumber: Int,
  val title: String,
  val requirementSummary: String,
  val detectedStatus: String,
  val isMet: Boolean,
  val hasUpdateAvailable: Boolean = false,
  val isChecked: Boolean,
  val whyNotOrFixInstruction: String,
  val phoneRequirementNote: String,
  val actionLabel: String,
  val actionType: DependencyActionType,
  val targetPackage: String? = null
) {
  /** Only show "Update Apk", "Fix Wi-Fi", etc. when the dependency is not met OR an update is available. */
  val shouldShowActionButton: Boolean
    get() = !isMet || hasUpdateAvailable
}

data class PhoneDependencyStatusItem(
  val title: String,
  val statusText: String,
  val isMet: Boolean
)

sealed class PhoneReportState {
  data class Connected(
    val snapshot: com.droid.googlebookfeaturechecker.data.CompanionPhoneSnapshot,
    val items: List<PhoneDependencyStatusItem>,
    val allPhoneDependenciesMet: Boolean
  ) : PhoneReportState()

  data class Unreachable(
    val reason: String,
    val tipTitle: String,
    val tipMessage: String,
    val phoneRequirementsChecklist: List<String>
  ) : PhoneReportState()
}

data class FeatureCheckerUiState(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val features: List<FeatureItem> = emptyList(),
  val selectedFeature: FeatureItem,
  val localDeviceName: String = "",
  val localDeviceRoleLabel: String = "",
  val localBuildType: String = "",
  val isLocalBuildTypeValid: Boolean = false,
  val dependencies: List<DependencyCheckItem> = emptyList(),
  val focusedDependencyIndex: Int = 0,
  val phoneReportState: PhoneReportState,
  val lastCheckedTimestampText: String = "Just now",
  val statusBannerMessage: String? = null
)
