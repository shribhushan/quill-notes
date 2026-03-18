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

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Tink
-keep class com.google.crypto.tink.** { *; }
