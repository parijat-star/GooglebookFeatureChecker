package com.droid.googlebookfeaturechecker

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.provider.Settings
import org.robolectric.Robolectric
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.googlebookfeaturechecker.data.SystemDeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
import com.droid.googlebookfeaturechecker.model.DependencyId
import com.droid.googlebookfeaturechecker.model.ThemeMode
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerScreen
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerViewModel
import com.droid.googlebookfeaturechecker.ui.theme.GooglebookFeatureCheckerTheme
import com.droid.googlebookfeaturechecker.util.SystemActionLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, qualifiers = "w1024dp-h768dp")
class FeatureCheckerScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun systemDeviceEnvironmentProvider_readsLiveSystemSnapshotOnHostJvm() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val provider = SystemDeviceEnvironmentProvider(context)
    val snapshot = provider.readLocalSystemSnapshot()

    assertNotNull(snapshot.deviceModel)
    assertNotNull(snapshot.buildType)
    assertTrue(snapshot.sdkInt > 0)
  }

  @Test
  fun systemActionLauncher_executesUpdateApkAndFeedbackActionsCleanly() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val launcher = SystemActionLauncher(context)
    val viewModel = FeatureCheckerViewModel(FakeDeviceEnvironmentProvider())
    val gmsDep = viewModel.uiState.value.dependencies.first { it.id == DependencyId.GMS_APK }

    val updateResult = launcher.executeDependencyAction(gmsDep)
    assertTrue(updateResult.contains("Update Apk"))

    val feedbackResult = launcher.sendFeedbackReportToGoogle(viewModel.buildDiagnosticFeedbackReport())
    assertTrue(feedbackResult.contains("Diagnostic report copied to clipboard"))
  }

  @Test
  fun composeScreen_studioVisualStyle_showsGreenMetAndUpdateAvailableUnderMetWhenUpdateExists() {
    val fakeProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(
        buildType = "userdebug", // Met (#7 last), no update -> green Met badge only
        gmsVersionName = "26.30.00", // Outdated (!isMet) -> shows "Update Apk"
        crossDeviceVersionName = "1.0.1350", // Met (>= 1.0.1346)
        crossDeviceUpdateAvailable = true, // Explicit update available -> shows green Met AND "Update Available" button under Met!
        isPublicOrCaptiveWifi = true // Unmet -> shows "Fix Wi-Fi"
      )
    )
    val viewModel = FeatureCheckerViewModel(fakeProvider)
    var lastExecutedAction: DependencyCheckItem? = null
    var feedbackClicked = false

    composeTestRule.setContent {
      val state = viewModel.uiState.collectAsState().value
      GooglebookFeatureCheckerTheme(themeMode = state.themeMode) {
        FeatureCheckerScreen(
          uiState = state,
          onSelectFeature = viewModel::selectFeature,
          onToggleCheckMark = viewModel::toggleDependencyCheckMark,
          onFocusDependency = viewModel::setFocusedDependencyIndex,
          onMoveFocusNext = viewModel::moveFocusNext,
          onMoveFocusPrevious = viewModel::moveFocusPrevious,
          onToggleFocusedDependency = viewModel::toggleFocusedDependency,
          onExecuteAction = { lastExecutedAction = it },
          onRefreshChecks = viewModel::refreshChecks,
          onSetThemeMode = viewModel::setThemeMode,
          onCycleThemeMode = viewModel::cycleThemeMode,
          onSendFeedbackReport = { feedbackClicked = true },
          onOpenReferenceThread = {}
        )
      }
    }

    // Verify title & feature card, and confirm build type card is no longer in the left pane
    composeTestRule.onNodeWithText("Googlebook Feature Checker").assertIsDisplayed()
    composeTestRule.onNodeWithTag("feature_card_better_together").assertIsDisplayed()
    composeTestRule.onNodeWithTag("build_type_callout_card").assertDoesNotExist()

    // Scroll to GMS_APK dependency (step #2, outdated !isMet -> shows "Update Apk" and NO row checkmark)
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_GMS_APK"))
    composeTestRule.onNodeWithTag("click_mark_GMS_APK").assertDoesNotExist()
    composeTestRule.onNodeWithTag("action_button_GMS_APK").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_button_GMS_APK").performClick()
    assertNotNull(lastExecutedAction)
    assertEquals("Update Apk", lastExecutedAction?.actionLabel)
    assertEquals(DependencyId.GMS_APK, lastExecutedAction?.id)

    // Verify CROSS_DEVICE_APK (step #3, isMet == true && hasUpdateAvailable == true -> shows green "Met" AND "Update Available" button under Met)
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_CROSS_DEVICE_APK"))
    composeTestRule.onNodeWithTag("met_badge_CROSS_DEVICE_APK", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_available_button_CROSS_DEVICE_APK").assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_button_CROSS_DEVICE_APK").assertDoesNotExist()
    composeTestRule.onNodeWithTag("update_available_button_CROSS_DEVICE_APK").performClick()
    assertEquals(DependencyId.CROSS_DEVICE_APK, lastExecutedAction?.id)

    // Verify PRIVATE_WIFI (step #4, unmet public/captive -> shows "Switch Wi-Fi")
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_PRIVATE_WIFI"))
    composeTestRule.onNodeWithTag("action_button_PRIVATE_WIFI").assertIsDisplayed()
    composeTestRule.onNodeWithText("Switch Wi-Fi").assertIsDisplayed()

    // Scroll to BUILD_TYPE at the end of the checklist (step #7) -> listed as a dependency with NO click-mark and NO Met badge
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_BUILD_TYPE"))
    composeTestRule.onNodeWithTag("dependency_card_BUILD_TYPE").assertIsDisplayed()
    composeTestRule.onNodeWithTag("met_badge_BUILD_TYPE", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("click_mark_BUILD_TYPE").assertDoesNotExist()
    composeTestRule.onNodeWithTag("action_button_BUILD_TYPE").assertDoesNotExist()
    composeTestRule.onNodeWithTag("update_available_button_BUILD_TYPE").assertDoesNotExist()

    // Scroll to Companion Phone section -> verify "Check phone" button is removed, "What your phone also needs" highlight card is shown with connected Wi-Fi name
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("companion_phone_section"))
    composeTestRule.onNodeWithTag("companion_phone_section").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rescan_phone_button").assertDoesNotExist()
    composeTestRule.onNodeWithTag("phone_needs_highlight_card").assertIsDisplayed()
    composeTestRule.onNodeWithText("• Network: Same private Wi-Fi (\"Home_5G\"), No VPN & Bluetooth ON").assertIsDisplayed()

    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("google_feedback_card"))
    composeTestRule.onNodeWithTag("send_feedback_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("send_feedback_button").performClick()
    assertTrue(feedbackClicked)

    // Switch theme chip
    composeTestRule.onNodeWithTag("theme_chip_DARK").performClick()
    assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
  }

  @Test
  @Config(qualifiers = "w380dp-h840dp")
  fun composeScreen_phoneFormFactor_scalesCleanlyOnCompactPhoneScreen() {
    val phoneProvider = FakeDeviceEnvironmentProvider(
      snapshot = FakeDeviceEnvironmentProvider().snapshot.copy(
        deviceModel = "Pixel 10 Pro",
        isLaptopFormFactor = false,
        buildType = "user",
        crossDeviceVersionName = "1.0.1350",
        crossDeviceUpdateAvailable = false // Latest installed -> no Update Available button!
      )
    )
    val viewModel = FeatureCheckerViewModel(phoneProvider)

    composeTestRule.setContent {
      val state = viewModel.uiState.collectAsState().value
      GooglebookFeatureCheckerTheme(themeMode = state.themeMode) {
        FeatureCheckerScreen(
          uiState = state,
          onSelectFeature = viewModel::selectFeature,
          onToggleCheckMark = viewModel::toggleDependencyCheckMark,
          onFocusDependency = viewModel::setFocusedDependencyIndex,
          onMoveFocusNext = viewModel::moveFocusNext,
          onMoveFocusPrevious = viewModel::moveFocusPrevious,
          onToggleFocusedDependency = viewModel::toggleFocusedDependency,
          onExecuteAction = {},
          onRefreshChecks = viewModel::refreshChecks,
          onSetThemeMode = viewModel::setThemeMode,
          onCycleThemeMode = viewModel::cycleThemeMode,
          onSendFeedbackReport = {},
          onOpenReferenceThread = {}
        )
      }
    }

    composeTestRule.onNodeWithText("Googlebook Feature Checker").assertIsDisplayed()
    composeTestRule.onNodeWithTag("recheck_all_button").assertIsDisplayed()

    // Scroll to Cross-device services (#3) -> met with NO Update Available button
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_CROSS_DEVICE_APK"))
    composeTestRule.onNodeWithTag("met_badge_CROSS_DEVICE_APK", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("update_available_button_CROSS_DEVICE_APK").assertDoesNotExist()
    composeTestRule.onNodeWithTag("action_button_CROSS_DEVICE_APK").assertDoesNotExist()

    // Scroll to BUILD_TYPE (#7 at bottom of checklist) on phone -> displayed as dependency with no click-mark or Met badge
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_BUILD_TYPE"))
    composeTestRule.onNodeWithTag("dependency_card_BUILD_TYPE").assertIsDisplayed()
    composeTestRule.onNodeWithTag("met_badge_BUILD_TYPE", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("click_mark_BUILD_TYPE").assertDoesNotExist()
  }

  @Test
  fun systemSnapshot_marksCheck6AdvisoryInsteadOfFalseGreen() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val provider = SystemDeviceEnvironmentProvider(context)
    val snapshot = provider.readLocalSystemSnapshot()
    assertTrue(!snapshot.continueActivityVerifiedBySystem)

    val viewModel = FeatureCheckerViewModel(provider)
    composeTestRule.setContent {
      val state = viewModel.uiState.collectAsState().value
      GooglebookFeatureCheckerTheme(themeMode = state.themeMode) {
        FeatureCheckerScreen(
          uiState = state,
          onSelectFeature = viewModel::selectFeature,
          onToggleCheckMark = viewModel::toggleDependencyCheckMark,
          onFocusDependency = viewModel::setFocusedDependencyIndex,
          onMoveFocusNext = viewModel::moveFocusNext,
          onMoveFocusPrevious = viewModel::moveFocusPrevious,
          onToggleFocusedDependency = viewModel::toggleFocusedDependency,
          onExecuteAction = {},
          onRefreshChecks = viewModel::refreshChecks,
          onSetThemeMode = viewModel::setThemeMode,
          onCycleThemeMode = viewModel::cycleThemeMode,
          onSendFeedbackReport = {},
          onOpenReferenceThread = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_CONTINUE_ACTIVITY_SETTINGS"))
    composeTestRule.onNodeWithTag("advisory_badge_CONTINUE_ACTIVITY_SETTINGS", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("met_badge_CONTINUE_ACTIVITY_SETTINGS", useUnmergedTree = true).assertDoesNotExist()
    composeTestRule.onNodeWithTag("click_mark_CONTINUE_ACTIVITY_SETTINGS").assertDoesNotExist()
  }

  @Test
  fun systemActionLauncher_openCrossDeviceSettingsTargetsLinkDevicesActivity() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val shadowPm = Shadows.shadowOf(context.packageManager)
    val linkDevicesComponent = android.content.ComponentName(
      "com.google.android.gms",
      "com.google.android.gms.TestCrossDeviceSettingsActivity"
    )
    shadowPm.addActivityIfNotPresent(linkDevicesComponent)
    shadowPm.getInternalMutablePackageInfo("com.google.android.gms").activities
      ?.firstOrNull { it.name == linkDevicesComponent.className }
      ?.apply {
        exported = true
        enabled = true
      }
    shadowPm.addIntentFilterForActivity(
      linkDevicesComponent,
      android.content.IntentFilter("com.google.android.gms.multidevice.ACTION_LINK_DEVICES").apply {
        addCategory(android.content.Intent.CATEGORY_DEFAULT)
      }
    )

    val provider = SystemDeviceEnvironmentProvider(context)
    val snapshot = provider.readLocalSystemSnapshot()
    assertTrue(snapshot.crossDeviceSettingsResolvable)

    val viewModel = FeatureCheckerViewModel(provider)
    val continueDep = viewModel.uiState.value.dependencies.first {
      it.id == DependencyId.CONTINUE_ACTIVITY_SETTINGS
    }
    val launcher = SystemActionLauncher(context)
    val result = launcher.executeDependencyAction(continueDep)
    assertTrue(result.contains("Cross-device Services", ignoreCase = true))

    val launchedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
    assertNotNull(launchedIntent)
    assertEquals("com.google.android.gms.multidevice.ACTION_LINK_DEVICES", launchedIntent.action)
    assertEquals("com.google.android.gms", launchedIntent.`package`)
  }

  @Test
  fun systemDeviceEnvironmentProvider_checksOnlyAmbientStreamingForCrossDeviceServices() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val shadowPm = Shadows.shadowOf(context.packageManager)

    // Installing mediashell or Samsung Continuity without com.google.ambient.streaming must NOT satisfy Check #3
    shadowPm.installPackage(
      PackageInfo().apply {
        packageName = "com.google.android.apps.mediashell"
        versionName = "2.0.9999"
      }
    )
    shadowPm.installPackage(
      PackageInfo().apply {
        packageName = "com.samsung.android.mcfds"
        versionName = "1.5.0"
      }
    )

    val provider = SystemDeviceEnvironmentProvider(context)
    val withoutAmbient = provider.readLocalSystemSnapshot()
    assertFalse(withoutAmbient.crossDeviceInstalled)
    assertNull(withoutAmbient.crossDeviceVersionName)
    assertEquals("com.google.ambient.streaming", withoutAmbient.crossDevicePackageName)

    // Installing com.google.ambient.streaming satisfies Check #3 lookup directly
    shadowPm.installPackage(
      PackageInfo().apply {
        packageName = "com.google.ambient.streaming"
        versionName = "1.0.1346"
      }
    )
    val withAmbient = provider.readLocalSystemSnapshot()
    assertTrue(withAmbient.crossDeviceInstalled)
    assertEquals("1.0.1346", withAmbient.crossDeviceVersionName)
    assertEquals("com.google.ambient.streaming", withAmbient.crossDevicePackageName)
  }

  @Test
  @Config(qualifiers = "w380dp-h840dp")
  fun systemActionLauncher_sendFeedbackOnPhoneRoutesToAboutPhoneFeedback() {
    val activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val launcher = SystemActionLauncher(activity)

    val result = launcher.sendFeedbackReportToGoogle("Diagnostic sample for phone")
    assertEquals("Opened Settings -> About Phone -> Send Feedback.", result)

    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertNull(clipboard.primaryClip)

    val launchedIntent = Shadows.shadowOf(activity).nextStartedActivity
    assertNotNull(launchedIntent)
    assertEquals(Settings.ACTION_DEVICE_INFO_SETTINGS, launchedIntent.action)
    assertEquals(0, launchedIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    assertEquals("device_feedback", launchedIntent.getStringExtra(":settings:fragment_args_key"))
    assertEquals(
      "device_feedback",
      launchedIntent.getBundleExtra(":settings:show_fragment_args")?.getString(":settings:fragment_args_key")
    )
  }

  @Test
  fun systemActionLauncher_sendFeedbackOnGooglebookKeepsGmsFeedbackRoute() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val launcher = SystemActionLauncher(context)

    val result = launcher.sendFeedbackReportToGoogle("Diagnostic sample for Googlebook")
    assertTrue(result.contains("Feedback"))

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    assertEquals(
      "Diagnostic sample for Googlebook",
      clipboard.primaryClip?.getItemAt(0)?.text?.toString()
    )

    val launchedIntent = Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
    assertNotNull(launchedIntent)
    assertEquals("com.google.android.gms.feedback.LAUNCH", launchedIntent.action)
    assertEquals("com.google.android.gms", launchedIntent.`package`)
  }
}
