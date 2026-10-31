package com.droid.googlebookfeaturechecker.util

object VersionComparator {
  private const val MAX_VERSION_STRING_LENGTH = 128
  private const val MAX_SEGMENTS = 8
  private val LEADING_VERSION_REGEX = Regex("""^\D*?(\d+(?:\.\d+){0,7})""")

  /**
   * Compares an installed version string (such as "26.37.14 (190400-123456)" or "1.0.1346-release")
   * against a required minimum dotted numeric prefix (such as "26.37" or "1.0.1346").
   * Hardened against ReDoS, integer/long overflow, negative numbers, and oversized inputs.
   */
  fun isAtLeast(installedVersion: String?, requiredMinimum: String): Boolean {
    if (installedVersion.isNullOrBlank() || requiredMinimum.isBlank()) return false
    val boundedInstalled = installedVersion.take(MAX_VERSION_STRING_LENGTH).trim()
    if (boundedInstalled.startsWith("-")) return false

    val installedParts = extractLeadingVersionParts(boundedInstalled)
    val requiredParts = extractLeadingVersionParts(requiredMinimum.take(MAX_VERSION_STRING_LENGTH))
    if (installedParts.isEmpty() || requiredParts.isEmpty()) return false

    val maxLen = maxOf(installedParts.size, requiredParts.size).coerceAtMost(MAX_SEGMENTS)
    for (i in 0 until maxLen) {
      val inst = installedParts.getOrElse(i) { 0L }
      val req = requiredParts.getOrElse(i) { 0L }
      if (inst > req) return true
      if (inst < req) return false
    }
    return true
  }

  fun extractLeadingVersionParts(rawVersion: String): List<Long> {
    if (rawVersion.isBlank()) return emptyList()
    val bounded = rawVersion.take(MAX_VERSION_STRING_LENGTH).trim()
    if (bounded.startsWith("-")) return emptyList()
    val match = LEADING_VERSION_REGEX.find(bounded) ?: return emptyList()
    return match.groupValues[1]
      .split(".")
      .take(MAX_SEGMENTS)
      .mapNotNull { seg ->
        if (seg.isEmpty()) null
        else if (seg.length > 18) Long.MAX_VALUE
        else seg.toLongOrNull() ?: Long.MAX_VALUE
      }
  }
}
