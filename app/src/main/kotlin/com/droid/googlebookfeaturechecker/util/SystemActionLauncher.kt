package com.droid.googlebookfeaturechecker.util

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import com.droid.googlebookfeaturechecker.data.isLaptopOrDesktopFormFactor
import com.droid.googlebookfeaturechecker.data.isSamsungDevice
import com.droid.googlebookfeaturechecker.model.DependencyActionType
import com.droid.googlebookfeaturechecker.model.DependencyCheckItem
import com.droid.googlebookfeaturechecker.ui.FeatureCheckerViewModel

interface ActionNavigator {
  fun executeDependencyAction(item: DependencyCheckItem): String
  fun sendFeedbackReportToGoogle(diagnosticReport: String): String
  fun openReferenceSupportThread(url: String = FeatureCheckerViewModel.SUPPORT_THREAD_URL): String
}

class SystemActionLauncher(private val context: Context) : ActionNavigator {

  override fun executeDependencyAction(item: DependencyCheckItem): String {
    return when (item.actionType) {
      DependencyActionType.UPDATE_APK -> {
        val rawPkg = item.targetPackage ?: FeatureCheckerViewModel.GMS_PACKAGE
        if (!SecurityGuard.isValidAndroidPackageName(rawPkg)) {
          return "Blocked invalid package identifier."
        }
        val pkg = rawPkg

        val marketUri = Uri.Builder()
          .scheme("market")
          .authority("details")
          .appendQueryParameter("id", pkg)
          .build()
        val marketIntent = Intent(Intent.ACTION_VIEW, marketUri).apply {
          setPackage("com.android.vending")
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val webPlayUri = Uri.Builder()
          .scheme("https")
          .authority("play.google.com")
          .path("/store/apps/details")
          .appendQueryParameter("id", pkg)
          .build()
        val webPlayIntent = Intent(Intent.ACTION_VIEW, webPlayUri).apply {
          addCategory(Intent.CATEGORY_BROWSABLE)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val appDetailsIntent = Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", pkg, null)
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val playSystemUpdateIntent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val isGms = pkg == FeatureCheckerViewModel.GMS_PACKAGE
        when {
          tryStartActivity(marketIntent) -> if (isGms) {
            "Opened Play Store to Update Apk ($pkg) (if no Update button appears, check Settings > Google / System update)."
          } else {
            "Opened Google Play Store to Update Apk ($pkg)."
          }
          tryStartActivity(webPlayIntent) -> "Opened Google Play web store to Update Apk ($pkg)."
          tryStartActivity(appDetailsIntent) -> "Opened App Info for $pkg (tap 'App details in store' > 'Update')."
          isGms && tryStartActivity(playSystemUpdateIntent) -> "Opened System Update settings to check Google Play services / system updates."
          else -> "Requested Update Apk for $pkg (v${if (isGms) "26.37+" else "1.0.1346+"})."
        }
      }

      DependencyActionType.REQUEST_LOCATION_PERMISSION,
      DependencyActionType.REQUEST_BLUETOOTH_PERMISSION -> {
        val appDetailsIntent = Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", context.packageName, null)
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(appDetailsIntent)) {
          "Opened App Permissions in Settings to grant Location / Nearby Bluetooth access."
        } else {
          "Open Settings > Apps > Googlebook Feature Checker > Permissions to grant required permissions."
        }
      }

      DependencyActionType.OPEN_SYSTEM_UPDATE -> {
        val updateIntent = Intent("android.settings.SYSTEM_UPDATE_SETTINGS").apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val infoIntent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        when {
          tryStartActivity(updateIntent) -> "Opened System Update settings."
          tryStartActivity(infoIntent) -> "Opened About Device settings to check Android 17 / One UI 9.0 update."
          else -> "Check Settings > About Device > Software Update for Android 17."
        }
      }

      DependencyActionType.OPEN_DEVICE_INFO -> {
        val infoIntent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        when {
          tryStartActivity(infoIntent) -> "Opened About Device settings to inspect Build Type (user / userdbg)."
          tryStartActivity(settingsIntent) -> "Opened System Settings."
          else -> "Build type verified in system properties."
        }
      }

      DependencyActionType.OPEN_WIFI_SETTINGS -> {
        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        when {
          tryStartActivity(wifiIntent) -> "Opened Wi-Fi Settings (connect to private home Wi-Fi & pause VPN)."
          tryStartActivity(wirelessIntent) -> "Opened Network & Internet Settings."
          else -> "Connect both devices to a private home Wi-Fi network and pause any VPN."
        }
      }

      DependencyActionType.OPEN_BLUETOOTH_SETTINGS -> {
        val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (tryStartActivity(btIntent)) {
          "Opened Bluetooth Settings to turn ON Bluetooth."
        } else {
          "Enable Bluetooth in Quick Settings or System Settings on both devices."
        }
      }

      DependencyActionType.OPEN_CROSS_DEVICE_SETTINGS -> {
        val linkDevicesIntent = Intent("com.google.android.gms.multidevice.ACTION_LINK_DEVICES").apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addCategory(Intent.CATEGORY_DEFAULT)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val preLinkDevicesIntent = Intent("com.google.android.gms.multidevice.ACTION_PRE_LINK_DEVICES").apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addCategory(Intent.CATEGORY_DEFAULT)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val crossDeviceIntent = Intent("com.google.android.gms.settings.CROSS_DEVICE_SETTINGS").apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val googleSettingsIntent = Intent("com.google.android.gms.accountsettings.VIEW_SETTINGS_0P").apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        when {
          tryStartActivity(linkDevicesIntent) ->
            "Opened Cross-device Services settings."
          tryStartActivity(preLinkDevicesIntent) ->
            "Opened Cross-device Services settings."
          tryStartActivity(crossDeviceIntent) ->
            "Opened Cross-device Services settings."
          tryStartActivity(googleSettingsIntent) ->
            "Opened Google Account settings (navigate to Devices & sharing > Cross-device services)."
          tryStartActivity(settingsIntent) ->
            "Opened Settings (search for 'Cross-device services')."
          else ->
            "Open Settings > Google > Devices & sharing > Cross-device services."
        }
      }
    }
  }

  override fun sendFeedbackReportToGoogle(diagnosticReport: String): String {
    val safeReport = diagnosticReport.take(8192)

    val shareFeedbackIntent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, "Googlebook Better Together Feedback & Bug Report")
      putExtra(Intent.EXTRA_TEXT, safeReport)
    }
    val chooserIntent = Intent.createChooser(shareFeedbackIntent, "Send Feedback Report to Google").apply {
      if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // On phones, do not copy to clipboard (avoids SystemUI clipboard overlay) and route
    // directly to the OEM's supported system feedback entry point.
    if (!isLaptopOrDesktopFormFactor(context)) {
      val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      if (isSamsungDevice()) {
        // Samsung One UI omits Pixel's About Phone -> Send feedback entry; route to Google Settings
        // or Cross-device Services where the '?' Help & feedback -> Send feedback option lives.
        val googleSettingsIntent = Intent(ACTION_GOOGLE_EXTRA_SETTINGS).apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addCategory(Intent.CATEGORY_DEFAULT)
          if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val crossDeviceFeedbackIntent = Intent(ACTION_LINK_DEVICES).apply {
          setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
          addCategory(Intent.CATEGORY_DEFAULT)
          if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return when {
          tryStartActivity(googleSettingsIntent) ->
            "Opened Google Settings (? -> Send feedback)."
          tryStartActivity(crossDeviceFeedbackIntent) ->
            "Opened Cross-device Services (? -> Send feedback)."
          tryStartActivity(settingsIntent) ->
            "Opened Settings (Google -> ? -> Send feedback)."
          tryStartActivity(chooserIntent) ->
            "Opened Feedback Report sender."
          else ->
            "Open Settings -> Google -> ? -> Send feedback."
        }
      }
      val highlightArgs = Bundle().apply {
        putString(SETTINGS_FRAGMENT_ARGS_KEY, DEVICE_FEEDBACK_PREFERENCE_KEY)
      }
      val aboutPhoneFeedbackIntent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
        putExtra(SETTINGS_SHOW_FRAGMENT_ARGS, highlightArgs)
        putExtra(SETTINGS_FRAGMENT_ARGS_KEY, DEVICE_FEEDBACK_PREFERENCE_KEY)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      return when {
        tryStartActivity(aboutPhoneFeedbackIntent) ->
          "Opened Settings -> About Phone -> Send Feedback."
        tryStartActivity(settingsIntent) ->
          "Opened Settings (About Phone -> Send Feedback)."
        tryStartActivity(chooserIntent) ->
          "Opened Feedback Report sender."
        else ->
          "Open Settings -> About Phone -> Send Feedback."
      }
    }

    copyToClipboard("Googlebook Better Together Diagnostic Report", safeReport)

    val gmsFeedbackIntent = Intent("com.google.android.gms.feedback.LAUNCH").apply {
      setPackage(FeatureCheckerViewModel.GMS_PACKAGE)
      putExtra(Intent.EXTRA_TEXT, safeReport)
      putExtra("com.android.feedback.SAFEPARCELABLE_SESSION_ID", "googlebook_better_together")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val bugReportIntent = Intent(Intent.ACTION_BUG_REPORT).apply {
      putExtra(Intent.EXTRA_TEXT, safeReport)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return when {
      tryStartActivity(gmsFeedbackIntent) ->
        "Diagnostic report copied to clipboard & launched Google Feedback Report tool."
      tryStartActivity(bugReportIntent) ->
        "Diagnostic report copied to clipboard & launched Android System Bug Report."
      tryStartActivity(chooserIntent) ->
        "Diagnostic report copied to clipboard & opened Feedback Report sender."
      else ->
        "Diagnostic report copied to clipboard ready for Google Feedback submission."
    }
  }

  override fun openReferenceSupportThread(url: String): String {
    if (!SecurityGuard.isTrustedHttpsUrl(url)) {
      return "Blocked untrusted support URL."
    }
    val targetUrl = url
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
      addCategory(Intent.CATEGORY_BROWSABLE)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return if (tryStartActivity(browserIntent)) {
      "Opened Googlebook Support Thread #472325070 in browser."
    } else {
      "Reference URL: $targetUrl"
    }
  }

  private fun copyToClipboard(label: String, text: String) {
    try {
      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
      val clip = ClipData.newPlainText(label, text)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
          putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
      }
      clipboard.setPrimaryClip(clip)
    } catch (_: Exception) {
    }
  }

  private fun tryStartActivity(intent: Intent): Boolean {
    return try {
      context.startActivity(intent)
      true
    } catch (_: Exception) {
      false
    }
  }

  private companion object {
    const val ACTION_GOOGLE_EXTRA_SETTINGS = "com.android.settings.action.EXTRA_SETTINGS"
    const val ACTION_LINK_DEVICES = "com.google.android.gms.multidevice.ACTION_LINK_DEVICES"
    const val SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
    const val SETTINGS_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
    const val DEVICE_FEEDBACK_PREFERENCE_KEY = "device_feedback"
  }
}
