package com.droid.googlebookfeaturechecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droid.googlebookfeaturechecker.data.DeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.data.SystemDeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerScreen
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerViewModel
import com.droid.googlebookfeaturechecker.ui.theme.GooglebookFeatureCheckerTheme
import com.droid.googlebookfeaturechecker.util.ActionNavigator
import com.droid.googlebookfeaturechecker.util.SystemActionLauncher

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val environmentProvider: DeviceEnvironmentProvider = SystemDeviceEnvironmentProvider(applicationContext)
    val actionNavigator: ActionNavigator = SystemActionLauncher(this)

    val viewModelFactory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FeatureCheckerViewModel(environmentProvider) as T
      }
    }

    setContent {
      val viewModel: FeatureCheckerViewModel = viewModel(factory = viewModelFactory)
      val uiState by viewModel.uiState.collectAsState()

      val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { permissions ->
        if (permissions.values.any { it }) {
          viewModel.refreshChecks()
        }
      }

      LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
          this@MainActivity,
          Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) {
          wifiPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION
            )
          )
        }
      }

      GooglebookFeatureCheckerTheme(themeMode = uiState.themeMode) {
        FeatureCheckerScreen(
          uiState = uiState,
          onSelectFeature = viewModel::selectFeature,
          onToggleCheckMark = viewModel::toggleDependencyCheckMark,
          onFocusDependency = viewModel::setFocusedDependencyIndex,
          onMoveFocusNext = viewModel::moveFocusNext,
          onMoveFocusPrevious = viewModel::moveFocusPrevious,
          onToggleFocusedDependency = viewModel::toggleFocusedDependency,
          onExecuteAction = { item ->
            val msg = actionNavigator.executeDependencyAction(item)
            viewModel.setStatusBannerMessage(msg)
          },
          onRefreshChecks = viewModel::refreshChecks,
          onSetThemeMode = viewModel::setThemeMode,
          onCycleThemeMode = viewModel::cycleThemeMode,
          onSendFeedbackReport = {
            val report = viewModel.buildDiagnosticFeedbackReport()
            val msg = actionNavigator.sendFeedbackReportToGoogle(report)
            viewModel.setStatusBannerMessage(msg)
          },
          onOpenReferenceThread = {
            val msg = actionNavigator.openReferenceSupportThread()
            viewModel.setStatusBannerMessage(msg)
          }
        )
      }
    }
  }
}
