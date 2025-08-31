package com.github.eventhorizonlab.spi

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
class ServiceSchemeProcessorSpec : FunSpec({
    fun compile(
        sources: List<SourceFile>,
        classpaths: List<File> = emptyList(),
        runProcessor: Boolean = true
    ) = KotlinCompilation().apply {
        this.sources = sources
        if (runProcessor) {
            this.annotationProcessors = listOf(ServiceSchemeProcessor())
        }
        this.inheritClassPath = true
        this.classpaths = classpaths
    }.compile()

    test("generates META-INF/services for cross-module provider") {
        var api = SourceFile.kotlin(
            "Api.kt",
            """
            package my.api
            import com.github.eventhorizonlab.spi.ServiceContract
            @ServiceContract
            interface Contract
            """.trimIndent()
        )

        val impl = SourceFile.kotlin(
            "Impl.kt",
            """
            package my.impl
            import my.api.Contract
            import com.github.eventhorizonlab.spi.ServiceProvider
            @ServiceProvider(Contract::class)
            class Impl : Contract
            """.trimIndent()
        )

        val apiResult = compile(listOf(api), runProcessor = false)
        apiResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val implResult = compile(listOf(impl), listOf(apiResult.outputDirectory))
        implResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        implResult.classLoader.readServiceFile("my.api.Contract")?.trim() shouldBe "my.impl.Impl"
    }
})