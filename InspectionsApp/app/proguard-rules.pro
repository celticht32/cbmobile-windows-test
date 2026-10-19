# Couchbase Lite ships consumer ProGuard rules; release builds need no manual keeps.
# Only if you enable aggressive shrinking and hit ClassNotFound/reflection issues, uncomment:
# -keep class com.couchbase.lite.** { *; }
# -keep class com.couchbase.lite.internal.** { *; }
