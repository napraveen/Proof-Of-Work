# Keep Room schema and DAOs
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Compose
-keepclassmembers class * extends androidx.compose.runtime.State { *; }

# Keep Model classes for serialization
-keepclassmembers class com.proofofwork.core.model.** { *; }
