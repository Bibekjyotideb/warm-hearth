# Firestore maps documents to these classes by reflection.
-keep class com.lusanetech.warmhearth.family.model.** { *; }
-keepclassmembers class * { @com.google.firebase.firestore.PropertyName *; }
