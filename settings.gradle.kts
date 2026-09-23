// Projet Gradle (Android Studio) — équivalent de la chaîne tools/build_apk.py.
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "LaCartographieDesAbsents"
include(":app")
