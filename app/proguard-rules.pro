# REAL: application-specific runtime entry points only.
# Android entry points, Parcelable/JNI support and dependency consumer rules are
# merged by AGP with proguard-android-optimize.txt. Do not duplicate whole SDKs.

# WebView invokes Android.saveAvatar(...) by name from the embedded HTML in
# AvatarEditorActivity. The bridge is instantiated directly, so only this member
# needs its name and reachability retained; the class itself may be renamed.
# This intentionally documents the app contract even if an AGP default rule also
# protects @JavascriptInterface methods.
-keepclassmembers class com.cagri.real.AvatarEditorActivity$AvatarBridge {
    @android.webkit.JavascriptInterface public void saveAvatar(java.lang.String, java.lang.String);
}

# WebView requires the runtime annotation on the retained bridge method.
# Attribute preservation does not keep unrelated classes or methods alive.
-keepattributes RuntimeVisibleAnnotations

# Keep source/line information for retrace with the matching per-build mapping.
# Do not rename all source files to one token or disable obfuscation/optimization.
-keepattributes SourceFile,LineNumberTable

# PdfBox-Android: optional JPEG2000 decoder is not bundled; text extraction uses reflection-free paths.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
