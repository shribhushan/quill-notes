# Quill Notes ProGuard Rules

# Keep encryption classes
-keep class com.quillnotes.data.encryption.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Google API Client
-keep class com.google.api.** { *; }
-keep class com.google.http.** { *; }
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**

# MSAL
-keep class com.microsoft.identity.** { *; }
-keep class com.nimbusds.** { *; }
# Optional classes MSAL's `common` module references for features this app
# doesn't use (Surface Duo dual-screen, OpenTelemetry tracing, FindBugs
# annotations) — never present on the Android classpath, never executed.
-dontwarn com.microsoft.device.display.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn io.opentelemetry.**

# Apache HttpClient (transitive via MSAL) references optional Kerberos/LDAP
# support (javax.naming, org.ietf.jgss) not present on Android and not used
# by this app's HTTP calls.
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Tink
-keep class com.google.crypto.tink.** { *; }

# kotlinx.serialization: payload serializers are looked up through the companion at runtime
-keepclassmembers class com.quillnotes.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.quillnotes.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Glance widgets and their tap callbacks are instantiated by name by the platform
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { <init>(); }
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
