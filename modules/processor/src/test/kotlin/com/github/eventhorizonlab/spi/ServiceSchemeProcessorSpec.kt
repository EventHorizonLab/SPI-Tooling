package com.github.eventhorizonlab.spi

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

    test("generates correct META-INF/services for nested provider") {
        val api = SourceFile.kotlin(
            "Api.kt",
            """
            package my.api
            import com.github.eventhorizonlab.spi.ServiceContract
            interface Outer {
                @ServiceContract
                interface Inner
            }
            """.trimIndent()
        )

        val impl = SourceFile.kotlin(
            "Impl.kt",
            """
            package my.impl
            import my.api.Outer
            import my.api.Outer.Inner
            import com.github.eventhorizonlab.spi.ServiceProvider
            class Impl {
              @ServiceProvider(Inner::class)
              class ImplInner : Inner
            }
            """.trimIndent()
        )

        val apiResult = compile(listOf(api), runProcessor = false)
        apiResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val implResult = compile(listOf(impl), listOf(apiResult.outputDirectory))
        implResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        implResult.classLoader.readServiceFile("my.api.Outer\$Inner")?.trim() shouldBe "my.impl.Impl\$ImplInner"
    }

    test("generates META-INF/services for cross-module provider") {
        val api = SourceFile.kotlin(
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

    test("emits error when contract has no provider") {
        val api = SourceFile.kotlin(
            "Api.kt",
            """
            package my.api
            import com.github.eventhorizonlab.spi.ServiceContract
            @ServiceContract
            interface LonelyContract
            """.trimIndent()
        )

        val result = compile(listOf(api))
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages.contains(missingServiceProviderErrorMessage("my.api.LonelyContract")) shouldBe true
    }

    test("emits error when provider target is not annotated with @ServiceContract") {
        val api = SourceFile.kotlin(
            "Api.kt",
            """
        package my.api
        interface NotAContract
        """.trimIndent()
        )

        val impl = SourceFile.kotlin(
            "Impl.kt",
            """
        package my.impl
        import my.api.NotAContract
        import com.github.eventhorizonlab.spi.ServiceProvider
        @ServiceProvider(NotAContract::class)
        class Impl : NotAContract
        """.trimIndent()
        )

        val result = compile(listOf(api, impl))
        result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
        result.messages shouldContain "@ServiceProvider target my.api.NotAContract is not annotated with @ServiceContract"
    }


    test("writes all providers for a contract into META-INF/services") {
        val api = SourceFile.kotlin(
            "Api.kt",
            """
        package my.api
        import com.github.eventhorizonlab.spi.ServiceContract
        @ServiceContract
        interface Contract
        """.trimIndent()
        )

        val impl1 = SourceFile.kotlin(
            "Impl1.kt",
            """
        package my.impl
        import my.api.Contract
        import com.github.eventhorizonlab.spi.ServiceProvider
        @ServiceProvider(Contract::class)
        class Impl1 : Contract
        """.trimIndent()
        )

        val impl2 = SourceFile.kotlin(
            "Impl2.kt",
            """
        package my.impl
        import my.api.Contract
        import com.github.eventhorizonlab.spi.ServiceProvider
        @ServiceProvider(Contract::class)
        class Impl2 : Contract
        """.trimIndent()
        )

        val apiResult = compile(listOf(api), runProcessor = false)
        val implResult = compile(listOf(impl1, impl2), listOf(apiResult.outputDirectory))
        implResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

        val lines = implResult.classLoader
            .readServiceFile("my.api.Contract")
            ?.lines()
            ?.filter { it.isNotBlank() }
            ?.sorted()

        lines shouldBe listOf("my.impl.Impl1", "my.impl.Impl2")
    }

})