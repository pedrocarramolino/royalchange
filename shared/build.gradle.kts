// Raíz de composición compartida: App(), navegación y DI. La consumen los tres hosts.
plugins {
    alias(libs.plugins.royalchance.kmp.compose)
}

compose.resources {
    packageOfResClass = "com.royalchance.shared.resources"
}
