# ============================================================
# R8 / ProGuard Rules for Mimiral
# ============================================================
# R8 full mode is enabled via isMinifyEnabled = true in build.gradle.kts.
# R8 performs whole-program optimization: inlining, class merging,
# vertical/horizontal class merging, and unboxing — all of which
# reduce the DEX method/class count and thus the verification cost
# at cold start time.
# ============================================================

# ---------- Keep Application class ----------
-keep class com.mimiral.app.MimiralApp { *; }

# ---------- Keep Hilt generated components ----------
# Hilt's generated classes use reflection — must be kept.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class **_HiltModules* { *; }
-keep class **_HiltComponents* { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ---------- Keep Compose (targeted) ----------
# Do NOT use a blanket keep for androidx.compose.** — keeps too much.
# Specifically exclude material.icons.extended (~5000 unused icon classes)
# to allow R8 to strip them and reduce DEX verification time.
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.animation.** { *; }
-keep class androidx.compose.navigation.** { *; }
# Keep material.core Scaffold, TopAppBar, etc. but NOT material.icons.extended
-keep class androidx.compose.material.AppBarKt { *; }
-keep class androidx.compose.material.ScaffoldKt { *; }
-keep class androidx.compose.material.SnackbarKt { *; }
-keep class androidx.compose.material.SurfaceKt { *; }
-keep class androidx.compose.material.TopAppBarKt { *; }
-keep class androidx.compose.material.AlertDialogKt { *; }
-keep class androidx.compose.material.ModalNavigationDrawerKt { *; }
-keep class androidx.compose.material.DrawerKt { *; }
-keep class androidx.compose.material.BottomAppBarKt { *; }
-keep class androidx.compose.material.FloatingActionButtonKt { *; }
-keep class androidx.compose.material.icons.Icons { *; }
-dontwarn androidx.compose.**

# ---------- Keep Room generated ----------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# ---------- Keep Readium ----------
# Readium uses Kotlin reflection and sealed classes extensively.
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# ---------- Keep Retrofit/OkHttp ----------
# Retrofit uses dynamic proxies — keep service interfaces.
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ---------- Keep Gson ----------
# Gson uses reflection for serialization.
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ---------- Keep Kavita models ----------
# Two KavitaServerInfo classes in different packages — keep both to prevent R8 merging
-keep class com.mimiral.app.data.remote.kavita.KavitaModels { *; }
-keep class com.mimiral.app.data.remote.kavita.KavitaModels$* { *; }
-keep class com.mimiral.app.data.remote.KavitaSyncModels { *; }
-keep class com.mimiral.app.data.remote.KavitaSyncModels$* { *; }
# Keep all top-level data classes in KavitaSyncModels.kt (each compiles to its own class)
-keep class com.mimiral.app.data.remote.KavitaServerInfo { *; }
-keep class com.mimiral.app.data.remote.KavitaLoginRequest { *; }
-keep class com.mimiral.app.data.remote.KavitaLoginResponse { *; }
-keep class com.mimiral.app.data.remote.KavitaProgressRequest { *; }
-keep class com.mimiral.app.data.remote.KavitaProgressResponse { *; }
-keep class com.mimiral.app.data.remote.KavitaProgressData { *; }
-keep class com.mimiral.app.data.remote.ConnectionStatus { *; }
-keep class com.mimiral.app.data.remote.SyncStatus { *; }
-keep class com.mimiral.app.data.remote.SeriesDetailDto { *; }
-keep class com.mimiral.app.data.remote.VolumeDto { *; }
-keep class com.mimiral.app.data.remote.ChapterDto { *; }
-keep class com.mimiral.app.data.remote.SeriesDto { *; }
-keep class com.mimiral.app.data.remote.kavita.KavitaBookmarkModels { *; }
-keep class com.mimiral.app.data.remote.kavita.KavitaBookmarkModels$* { *; }
# Keep all @SerializedName fields in Kavita models for Gson
-keepclassmembers class com.mimiral.app.data.remote.kavita.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class com.mimiral.app.data.remote.KavitaSyncModels$* {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Keep top-level model classes in data.remote (from KavitaSyncModels.kt)
-keepclassmembers class com.mimiral.app.data.remote.KavitaServerInfo {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class com.mimiral.app.data.remote.KavitaLoginRequest {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class com.mimiral.app.data.remote.KavitaLoginResponse {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---------- Keep Kotlin Coroutines ----------
-keepclassmembers class kotlinx.coroutines.** {
    ** <methods>;
}
-dontwarn kotlinx.coroutines.**

# ---------- General Android ----------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- Startup optimization ----------
# Aggressively optimize the startup path — allow R8 to inline and
# merge classes in the app's critical startup path.
-allowaccessmodification
-dontwarn java.lang.instrument.Instrumentation
