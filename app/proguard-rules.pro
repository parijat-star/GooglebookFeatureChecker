# Googlebook Feature Checker R8 / ProGuard configuration
-keep class com.droid.googlebookfeaturechecker.data.CompanionPhoneSnapshot { *; }
-keep class com.droid.googlebookfeaturechecker.data.DeviceSystemSnapshot { *; }
-keep class com.droid.googlebookfeaturechecker.model.** { *; }
-dontwarn java.lang.invoke.StringConcatFactory
