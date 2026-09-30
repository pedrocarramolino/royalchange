// Lobby del casino: saludo, avisos de cuenta y catálogo de juegos.
plugins {
    alias(libs.plugins.royalchance.kmp.feature)
}

compose.resources {
    packageOfResClass = "com.royalchance.feature.lobby.resources"
}
