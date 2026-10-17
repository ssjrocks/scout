# kotlinx.serialization: keep generated serializers for our @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ssjrocks.scout.** {
    *** Companion;
}
-keepclasseswithmembers class com.ssjrocks.scout.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ssjrocks.scout.**$$serializer { *; }
# jsoup's optional regex engine and OkHttp's optional TLS providers aren't on Android.
-dontwarn com.google.re2j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
