# Le jeu n'utilise ni réflexion ni sérialisation par noms de classes : R8 peut tout optimiser.
-keep class com.ateliermareebasse.cartographie.MainActivity { *; }
-keepclassmembers class * extends android.view.View { public <init>(android.content.Context); }
-dontwarn kotlin.**
-assumenosideeffects class android.util.Log { public static int i(...); public static int d(...); public static int v(...); }
