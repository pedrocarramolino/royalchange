package com.royalchance.core.common.random

import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

// Android consume esta misma variante JVM: en Android, SecureRandom usa el proveedor del sistema (BoringSSL).
internal actual fun platformSecureRandom(): Random = SecureRandom().asKotlinRandom()
