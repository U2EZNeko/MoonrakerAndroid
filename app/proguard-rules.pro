# Keep Moonraker serialization models
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlinx.serialization.**
-keep,includedescriptorclasses class com.moonraker.android.**$$serializer { *; }
-keepclassmembers class com.moonraker.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.moonraker.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
