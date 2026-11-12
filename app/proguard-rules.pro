# Room, WorkManager, Navigation and kotlinx.serialization ship their own consumer
# R8 rules. App-specific keeps go here.

# Keep @Serializable backup/navigation classes' generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.spendplan.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
