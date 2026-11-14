# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hmilyld.weeklyreport.**$$serializer { *; }
-keepclassmembers class com.hmilyld.weeklyreport.** {
    *** Companion;
}
-keepclasseswithmembers class com.hmilyld.weeklyreport.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.hmilyld.weeklyreport.data.model.** { *; }

# OkHttp platform hints
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
