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

# ---------- Keep Compose ----------
# Compose runtime uses reflection for Composable function discovery.
-keep class androidx.compose.** { *; }
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
