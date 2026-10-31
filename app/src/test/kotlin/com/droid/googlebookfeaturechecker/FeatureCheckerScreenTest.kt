package com.droid.googlebookfeaturechecker

import android.app.Application
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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

    // Scroll to GMS_APK dependency (step #2, outdated !isMet -> shows "Update Apk")
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_GMS_APK"))
    composeTestRule.onNodeWithTag("click_mark_GMS_APK").assertIsOff()
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

    // Verify PRIVATE_WIFI (step #4, unmet -> shows "Wifi Not Supported" instead of "Fix Wi-Fi")
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_PRIVATE_WIFI"))
    composeTestRule.onNodeWithTag("action_button_PRIVATE_WIFI").assertIsDisplayed()
    composeTestRule.onNodeWithText("Wifi Not Supported").assertIsDisplayed()

    // Scroll to BUILD_TYPE at the end of the checklist (step #7) -> shows green Met badge, no action or update button
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_BUILD_TYPE"))
    composeTestRule.onNodeWithTag("dependency_card_BUILD_TYPE").assertIsDisplayed()
    composeTestRule.onNodeWithTag("met_badge_BUILD_TYPE", useUnmergedTree = true).assertIsDisplayed()
    composeTestRule.onNodeWithTag("action_button_BUILD_TYPE").assertDoesNotExist()
    composeTestRule.onNodeWithTag("update_available_button_BUILD_TYPE").assertDoesNotExist()
    composeTestRule.onNodeWithTag("click_mark_BUILD_TYPE").assertIsOn()
    composeTestRule.onNodeWithTag("click_mark_BUILD_TYPE").performClick()
    assertFalse(viewModel.uiState.value.dependencies.first { it.id == DependencyId.BUILD_TYPE }.isChecked)

    // Scroll to Companion Phone section and Google Feedback section
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("companion_phone_section"))
    composeTestRule.onNodeWithTag("companion_phone_section").assertIsDisplayed()

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

    // Scroll to BUILD_TYPE (#7 at bottom of checklist) on phone
    composeTestRule.onNodeWithTag("dependencies_lazy_column")
      .performScrollToNode(hasTestTag("dependency_card_BUILD_TYPE"))
    composeTestRule.onNodeWithTag("dependency_card_BUILD_TYPE").assertIsDisplayed()
    composeTestRule.onNodeWithTag("met_badge_BUILD_TYPE", useUnmergedTree = true).assertIsDisplayed()
  }
}
