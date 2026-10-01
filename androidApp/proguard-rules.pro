# Reglas de R8 para Royal Chance.
#
# kotlinx.serialization, Compose, Firebase y AndroidX traen sus propias reglas (consumer rules).
# Aquí solo se protege lo que el proyecto serializa por su nombre: rutas de navegación, sesiones
# de mesa guardadas y documentos de Firestore (mapas ↔ clases @Serializable).

# Serializadores generados de las clases del proyecto (incluidos objetos y clases selladas).
-keepclassmembers @kotlinx.serialization.Serializable class com.royalchance.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.royalchance.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Los nombres de los enums se guardan (tipos de asiento, juegos, logros): no deben cambiar.
-keepclassmembers enum com.royalchance.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Trazas legibles en los informes de errores.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
