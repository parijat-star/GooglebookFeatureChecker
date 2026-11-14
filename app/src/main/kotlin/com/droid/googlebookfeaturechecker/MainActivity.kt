package com.droid.googlebookfeaturechecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.droid.googlebookfeaturechecker.data.DeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.data.SystemDeviceEnvironmentProvider
import com.droid.googlebookfeaturechecker.model.DependencyActionType
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
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
        return FeatureCheckerViewModel(
          environmentProvider = environmentProvider,
          loadSynchronously = false
        ) as T
      }
    }

    setContent {
      val viewModel: FeatureCheckerViewModel = viewModel(factory = viewModelFactory)
      val uiState by viewModel.uiState.collectAsState()
      val lifecycleOwner = LocalLifecycleOwner.current
      var pendingPermissionAction by remember { mutableStateOf<DependencyCheckItem?>(null) }

      val runtimePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
      ) { permissions ->
        val pending = pendingPermissionAction
        pendingPermissionAction = null
        if (permissions.values.any { it }) {
          viewModel.refreshChecksAsync(bannerMessage = "Permissions updated; refreshed checks.")
        } else if (pending != null) {
          val msg = actionNavigator.executeDependencyAction(pending)
          viewModel.setStatusBannerMessage(msg)
        }
      }

      fun missingStartupPermissions(): Array<String> = buildList {
        if (ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          add(Manifest.permission.ACCESS_FINE_LOCATION)
          add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.BLUETOOTH_CONNECT
          ) != PackageManager.PERMISSION_GRANTED
        ) {
          add(Manifest.permission.BLUETOOTH_CONNECT)
        }
      }.toTypedArray()

      LaunchedEffect(Unit) {
        val missing = missingStartupPermissions()
        if (missing.isNotEmpty()) {
          runtimePermissionLauncher.launch(missing)
        }
      }

      DisposableEffect(lifecycleOwner) {
        var sawPause = false
        val observer = LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_PAUSE -> sawPause = true
            Lifecycle.Event.ON_RESUME -> if (sawPause) {
              sawPause = false
              viewModel.refreshChecksAsync(bannerMessage = null)
            }
            else -> {}
          }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            when (item.actionType) {
              DependencyActionType.REQUEST_LOCATION_PERMISSION -> {
                pendingPermissionAction = item
                runtimePermissionLauncher.launch(
                  arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                  )
                )
              }
              DependencyActionType.REQUEST_BLUETOOTH_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                  pendingPermissionAction = item
                  runtimePermissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                } else {
                  val msg = actionNavigator.executeDependencyAction(item)
                  viewModel.setStatusBannerMessage(msg)
                }
              }
              else -> {
                val msg = actionNavigator.executeDependencyAction(item)
                viewModel.setStatusBannerMessage(msg)
              }
            }
          },
          onRefreshChecks = { viewModel.refreshChecksAsync() },
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
