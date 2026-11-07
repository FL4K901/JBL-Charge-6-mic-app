# ProGuard and R8 rules for Mic Karaoke App

# Flutter Engine
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.**  { *; }
-keep class io.flutter.util.**  { *; }
-keep class io.flutter.view.**  { *; }
-keep class io.flutter.**  { *; }
-keep class io.flutter.plugins.**  { *; }

# Preserve all native JNI method bindings
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Native Bridge & Service
-keep class com.mic.app.mic.OboeAudioEngineBridge { *; }
-keep class com.mic.app.mic.MicForegroundService { *; }
-keep class com.mic.app.mic.AudioEngine { *; }
-keep class com.mic.app.mic.AudioDspProcessor { *; }

# Google Oboe Library
-keep class com.google.oboe.** { *; }
