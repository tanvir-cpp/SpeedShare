# SpeedShare Proguard Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep models for JSON serialization
-keep class com.example.speedshareandroid.models.** { *; }

# Keep Coroutines internals
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }
