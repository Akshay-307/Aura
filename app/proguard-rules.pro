# Add project specific ProGuard rules here.
-keep class com.aura.data.model.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Keep JavascriptInterface for WebView bridge
-keepattributes JavascriptInterface
-keep class android.webkit.JavascriptInterface { *; }

# Keep all classes in Unity Ads packages
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }


