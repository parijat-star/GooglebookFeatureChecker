package com.droid.googlebookfeaturechecker.util

import android.net.Uri
import com.droid.googlebookfeaturechecker.data.CompanionPhoneSnapshot
import org.json.JSONObject
import java.io.InputStream

/**
 * Security validation, input sanitization, and anti-exploitation guardrails for
 * Googlebook Feature Checker.
 */
object SecurityGuard {

  private const val MAX_SANITISED_LENGTH = 120
  private const val MAX_JSON_PAYLOAD_BYTES = 4096
  private val PACKAGE_NAME_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$""")

  private val TRUSTED_HTTPS_HOSTS = setOf(
    "support.google.com",
    "play.google.com"
  )

  /**
   * Strips control characters, null bytes, and Unicode BiDi override characters
   * (U+202A..U+202E, U+2066..U+2069) that could spoof UI text or corrupt diagnostic logs,
   * and truncates to [maxLength].
   */
  fun sanitizeUntrustedText(raw: String?, maxLength: Int = MAX_SANITISED_LENGTH): String {
    if (raw.isNullOrEmpty()) return ""
    val sb = StringBuilder(minOf(raw.length, maxLength))
    for (i in 0 until raw.length) {
      if (sb.length >= maxLength) break
      val ch = raw[i]
      val code = ch.code
      val isBidiOverride = (code in 0x202A..0x202E) || (code in 0x2066..0x2069) || code == 0x200E || code == 0x200F
      val isControl = code < 0x20 || code == 0x7F || (code in 0x80..0x9F)
      if (!isControl && !isBidiOverride) {
        sb.append(ch)
      }
    }
    return sb.toString().trim()
  }

  /**
   * Validates that [packageName] is a well-formed Android application ID free of
   * URI query/fragment/path traversal characters.
   */
  fun isValidAndroidPackageName(packageName: String?): Boolean {
    if (packageName.isNullOrBlank() || packageName.length > 128) return false
    return PACKAGE_NAME_REGEX.matches(packageName)
  }

  /**
   * Validates that an external URL uses strict `https` scheme and targets an allowlisted Google host.
   * Rejects `javascript:`, `file:`, `content:`, `intent:`, `http:`, and untrusted domains.
   */
  fun isTrustedHttpsUrl(url: String?): Boolean {
    if (url.isNullOrBlank() || url.length > 512) return false
    return try {
      val uri = Uri.parse(url)
      val scheme = uri.scheme?.lowercase()
      val host = uri.host?.lowercase()
      val userInfo = uri.userInfo
      scheme == "https" && userInfo == null && host != null && host in TRUSTED_HTTPS_HOSTS
    } catch (_: Exception) {
      false
    }
  }

  /**
   * Prevents SSRF by ensuring companion socket probes only target loopback (`127.0.0.1` or `localhost`)
   * on an unprivileged user port (`1024..65535`).
   */
  fun isAllowedCompanionEndpoint(host: String?, port: Int): Boolean {
    if (host.isNullOrBlank()) return false
    if (port !in 1024..65535) return false
    val normalized = host.trim().lowercase()
    return normalized == "127.0.0.1" || normalized == "localhost" || normalized == "::1"
  }

  /**
   * Reads at most [maxBytes] from [inputStream] up to the first newline, preventing
   * OutOfMemoryError DoS from unbounded streams without newline terminators.
   */
  fun readBoundedLine(inputStream: InputStream, maxBytes: Int = MAX_JSON_PAYLOAD_BYTES): String? {
    val buffer = ByteArray(maxBytes)
    var count = 0
    while (count < maxBytes) {
      val b = inputStream.read()
      if (b == -1) break
      if (b == '\n'.code) break
      if (b != '\r'.code) {
        buffer[count++] = b.toByte()
      }
    }
    if (count == 0) return null
    return String(buffer, 0, count, Charsets.UTF_8)
  }

  private val ALLOWED_COMPANION_KEYS = setOf(
    "phoneModel",
    "buildType",
    "androidRelease",
    "oneUiVersion",
    "gmsVersionName",
    "crossDeviceVersionName",
    "privateWifiConnected",
    "bluetoothEnabled"
  )

  /**
   * Safely parses and sanitizes a JSON companion phone payload, rejecting malformed, nested,
   * or unexpected-key payloads.
   */
  fun parseAndValidateCompanionJson(rawLine: String?): CompanionPhoneSnapshot? {
    if (rawLine.isNullOrBlank() || rawLine.length > MAX_JSON_PAYLOAD_BYTES) return null
    return try {
      val trimmed = rawLine.trim()
      if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
      val json = JSONObject(trimmed)
      val keys = json.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        if (k !in ALLOWED_COMPANION_KEYS) return null
        val v = json.opt(k)
        if (v is JSONObject || v is org.json.JSONArray) return null
      }
      val model = sanitizeUntrustedText(json.optString("phoneModel", ""), 64)
        .ifBlank { "Connected Android Phone" }
      val buildType = sanitizeUntrustedText(json.optString("buildType", ""), 32)
        .ifBlank { "unknown" }
      val androidRelease = sanitizeUntrustedText(json.optString("androidRelease", ""), 24)
        .ifBlank { "unknown" }
      val oneUi = sanitizeUntrustedText(json.optString("oneUiVersion", ""), 24)
        .takeIf { it.isNotBlank() }
      val gmsVer = sanitizeUntrustedText(json.optString("gmsVersionName", ""), 48)
        .takeIf { it.isNotBlank() }
      val crossVer = sanitizeUntrustedText(json.optString("crossDeviceVersionName", ""), 48)
        .takeIf { it.isNotBlank() }
      val privateWifi = json.optBoolean("privateWifiConnected", false)
      val btEnabled = json.optBoolean("bluetoothEnabled", false)

      CompanionPhoneSnapshot(
        phoneModel = model,
        buildType = buildType,
        androidRelease = androidRelease,
        oneUiVersion = oneUi,
        gmsVersionName = gmsVer,
        crossDeviceVersionName = crossVer,
        privateWifiConnected = privateWifi,
        bluetoothEnabled = btEnabled
      )
    } catch (_: Exception) {
      null
    }
  }
}
