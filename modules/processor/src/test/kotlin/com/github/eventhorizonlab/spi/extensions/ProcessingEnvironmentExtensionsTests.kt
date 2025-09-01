package com.github.eventhorizonlab.spi.extensions

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

@OptIn(ExperimentalCompilerApi::class)
class ProcessingEnvironmentExtensionsTests : FunSpec({

    data class Case(
        val description: String,
        val source: SourceFile,
        val fqName: String,
        val expectedBinaryName: String
    )

    val cases = listOf(
        Case(
            "Top-level Kotlin class",
            SourceFile.kotlin("TopLevel.kt", """
                package com.test
                class TopLevel
            """.trimIndent()),
            fqName = "com.test.TopLevel",
            expectedBinaryName = "com.test.TopLevel"
        ),
        Case(
            "Nested Kotlin class",
            SourceFile.kotlin("Outer.kt", """
                package com.test
                class Outer {
                    class Inner
                }
            """.trimIndent()),
            fqName = "com.test.Outer.Inner",
            expectedBinaryName = $$"com.test.Outer$Inner"
        ),
        Case(
            "Inner (non-static) Java class",
            SourceFile.java("OuterJava.java", """
                package com.test;
                public class OuterJava {
                    public class InnerJava {}
                }
            """.trimIndent()),
            fqName = "com.test.OuterJava.InnerJava",
            expectedBinaryName = $$"com.test.OuterJava$InnerJava"
        )
    )

    withData(nameFn = { it.description }, cases) { case ->
        var actual: String? = null

        val processor = object : AbstractProcessor() {
            override fun init(processingEnv: ProcessingEnvironment) {
                super.init(processingEnv)
                val type = processingEnv.elementUtils.getTypeElement(case.fqName)
                if (type != null) {
                    actual = processingEnv.getStringifiedBinaryName(type)
                }
            }
            override fun process(
                annotations: MutableSet<out TypeElement>,
                roundEnv: RoundEnvironment
            ) = false
        }

        val result = KotlinCompilation().apply {
            sources = listOf(case.source)
            annotationProcessors = listOf(processor)
            inheritClassPath = true
        }.compile()

        result.exitCode shouldBe KotlinCompilation.ExitCode.OK
        actual shouldBe case.expectedBinaryName
    }
})