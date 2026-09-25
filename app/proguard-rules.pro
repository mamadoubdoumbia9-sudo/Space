# WhAlert ProGuard / R8 Rules

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Moshi rules
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}

# AndroidX Security Crypto / Tink
-keepclassmembers class * extends androidx.security.crypto.MasterKey { *; }

# Libphonenumber Android
-keepresourcefiles assets/io/michaelrocks/libphonenumber/android/**

# Data models
-keepclassmembers class com.whalert.app.data.model.** { *; }
