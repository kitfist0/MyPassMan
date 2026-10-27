# Project-specific R8 keep rules. Most dependencies (Hilt, Room, WorkManager, Retrofit,
# OkHttp, kotlinx.serialization, SQLCipher) ship their own consumer rules.

# Keep source file names and line numbers so release stack traces stay readable,
# while still renaming the source file attribute itself.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google API Client (Drive): models are (de)serialized by reflecting over fields
# annotated with @Key, and generic field types are read from Signature attributes.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,EnclosingMethod,InnerClasses
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-keep class com.google.api.services.drive.** { *; }
