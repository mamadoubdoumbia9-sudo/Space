# --- kotlinx.serialization -------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.signalpro.app.data.remote.dto.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keep,includedescriptorclasses class com.signalpro.app.**$$serializer { *; }
-keepclasseswithmembers class com.signalpro.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit / OkHttp -----------------------------------------------------
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- Room ------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# --- ZXing ----------------------------------------------------------------
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# --- Sécurité : ne jamais journaliser les jetons ---------------------------
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# --- Dépendances optionnelles (absentes du classpath Android) --------------
# Ces classes ne sont jamais chargées sur Android : elles sont référencées par
# des bibliothèques (OkHttp, Tink/security-crypto, Coil, ZXing) dans des chemins
# de repli. Les déclarer absentes évite l'échec de la minification release,
# sans masquer d'erreur réelle dans notre propre code.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn kotlinx.coroutines.debug.**
-dontwarn androidx.camera.**
-dontwarn com.google.zxing.client.android.**

# --- androidx.work / Room --------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-dontwarn androidx.work.**
