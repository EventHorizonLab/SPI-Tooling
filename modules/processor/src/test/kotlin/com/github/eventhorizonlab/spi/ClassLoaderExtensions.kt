package com.github.eventhorizonlab.spi

import java.nio.charset.StandardCharsets

fun ClassLoader.readServiceFile(serviceFqName: String): String? {
    val path = "META-INF/services/$serviceFqName"
    return getResourceAsStream(path)?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }
}
