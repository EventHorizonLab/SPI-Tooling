package com.github.eventhorizonlab.spi

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
class ServiceSchemeProcessorSpec : FunSpec({

    // --- Common compile helpers ---
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

    fun compileApi(vararg sources: SourceFile) =
        compile(sources.toList(), runProcessor = false)

    fun compileImpl(apiResult: JvmCompilationResult, vararg sources: SourceFile) =
        compile(sources.toList(), listOf(apiResult.outputDirectory))

    fun assertServiceFile(result: JvmCompilationResult, contractFqcn: List<String>, vararg expectedImpls: String) {
        contractFqcn.forEach { fqcn ->
            val lines = result.classLoader
                .readServiceFile(fqcn)
                ?.lines()
                ?.filter { it.isNotBlank() }
                ?.sorted()
            lines shouldBe expectedImpls.toList().sorted()
        }
    }

    // --- Happy path scenarios ---
    data class ServiceCase(
        val description: String,
        val api: List<SourceFile>,
        val impls: List<SourceFile>,
        val contractFqcn: List<String>,
        val expectedImpls: List<String>
    )

    context("service generation happy paths") {
        withData(
            nameFn = { it.description },
            // Kotlin API + Kotlin impl (nested)
            ServiceCase(
                "nested provider (Kotlin API + Kotlin impl)",
                api = listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    interface Outer {
                        @ServiceContract
                        interface Inner
                    }
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl.kt", """
                    package my.impl
                    import my.api.Outer.Inner
                    import com.github.eventhorizonlab.spi.ServiceProvider
                    class Impl {
                        @ServiceProvider(Inner::class)
                        class ImplInner : Inner
                    }
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Outer\$Inner"),
                expectedImpls = listOf("my.impl.Impl\$ImplInner")
            ),
            // Kotlin API + Kotlin impl (cross-module)
            ServiceCase(
                "cross-module provider (Kotlin API + Kotlin impl)",
                api = listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    @ServiceContract
                    interface Contract
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl.kt", """
                    package my.impl
                    import my.api.Contract
                    import com.github.eventhorizonlab.spi.ServiceProvider
                    @ServiceProvider(Contract::class)
                    class Impl : Contract
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Contract"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            // Kotlin API + Java impl
            ServiceCase(
                "Kotlin API + Java impl",
                api = listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    @ServiceContract
                    interface Api
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.java(
                        "Impl.java", """
                    package my.impl;
                    import my.api.Api;
                    import com.github.eventhorizonlab.spi.ServiceProvider;
                    @ServiceProvider(Api.class)
                    public class Impl implements Api {}
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            // Java API + Kotlin impl
            ServiceCase(
                "Java API + Kotlin impl",
                api = listOf(
                    SourceFile.java(
                        "Api.java", """
                    package my.api;
                    import com.github.eventhorizonlab.spi.ServiceContract;
                    @ServiceContract
                    public interface Api {}
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl.kt", """
                    package my.impl
                    import my.api.Api
                    import com.github.eventhorizonlab.spi.ServiceProvider
                    @ServiceProvider(Api::class)
                    class Impl : Api
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            // Java API + Java impl
            ServiceCase(
                "Java API + Java impl",
                api = listOf(
                    SourceFile.java(
                        "Contract.java", """
                    package my.api;
                    import com.github.eventhorizonlab.spi.ServiceContract;
                    @ServiceContract
                    public interface Contract {}
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.java(
                        "Impl.java", """
                    package my.impl;
                    import my.api.Contract;
                    import com.github.eventhorizonlab.spi.ServiceProvider;
                    @ServiceProvider(Contract.class)
                    public class Impl implements Contract {}
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Contract"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            // Multiple providers
            ServiceCase(
                "multiple providers for same contract",
                api = listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    @ServiceContract
                    interface Contract
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl1.kt", """
                        package my.impl
                        import my.api.Contract
                        import com.github.eventhorizonlab.spi.ServiceProvider
                        @ServiceProvider(Contract::class)
                        class Impl1 : Contract
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Impl2.kt", """
                        package my.impl
                        import my.api.Contract
                        import com.github.eventhorizonlab.spi.ServiceProvider
                        @ServiceProvider(Contract::class)
                        class Impl2 : Contract
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Impl3.java", """
                        package my.impl;
                        import my.api.Contract;
                        import com.github.eventhorizonlab.spi.ServiceProvider;
                        @ServiceProvider(Contract.class)
                        public class Impl3 implements Contract {}
                    """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Contract"),
                expectedImpls = listOf("my.impl.Impl1", "my.impl.Impl2", "my.impl.Impl3")
            ),
            ServiceCase(
                "provider implements multiple contracts (Kotlin)",
                api = listOf(
                    SourceFile.kotlin(
                        "Apis.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    
                    @ServiceContract
                    interface Api
                    
                    @ServiceContract
                    interface Api2
                """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl.kt", """
                    package my.impl
                    import my.api.Api
                    import my.api.Api2
                    import com.github.eventhorizonlab.spi.ServiceProvider
                    @ServiceProvider(Api::class, Api2::class)
                    class Impl : Api, Api2
                """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api", "my.api.Api2"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            ServiceCase(
                "provider implements multiple contracts (Java)",
                api = listOf(
                    SourceFile.java(
                        "Api.java", """
                        package my.api;
                        import com.github.eventhorizonlab.spi.ServiceContract;
                        @ServiceContract
                        public interface Api {}
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Api2.java", """
                        package my.api;
                        import com.github.eventhorizonlab.spi.ServiceContract;
                        @ServiceContract
                        public interface Api2 {}
                        """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.java(
                        "Impl.java", """
                        package my.impl;
                        import my.api.Api;
                        import my.api.Api2;
                        import com.github.eventhorizonlab.spi.ServiceProvider;
                        @ServiceProvider({Api.class, Api2.class})
                        public class Impl implements Api, Api2 {}
                        """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api", "my.api.Api2"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            ServiceCase(
                "provider implements multiple contracts (Kotlin+Java)",
                api = listOf(
                    SourceFile.kotlin(
                        "Apis.kt", """
                        package my.api
                        import com.github.eventhorizonlab.spi.ServiceContract
                        @ServiceContract
                        interface Api
                        @ServiceContract
                        interface Api2
                    """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.java(
                        "Impl.java", """
                            package my.impl;
                            import my.api.Api;
                            import my.api.Api2;
                            import com.github.eventhorizonlab.spi.ServiceProvider;
                            @ServiceProvider({Api.class, Api2.class})
                            public class Impl implements Api, Api2 {}
                        """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api", "my.api.Api2"),
                expectedImpls = listOf("my.impl.Impl")
            ),
            ServiceCase(
                "provider implements multiple contracts (Java+Kotlin)",
                api = listOf(
                    SourceFile.java(
                        "Api.java", """
                        package my.api;
                        import com.github.eventhorizonlab.spi.ServiceContract;
                        @ServiceContract
                        public interface Api {}
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Api2.kt", """
                        package my.api
                        import com.github.eventhorizonlab.spi.ServiceContract
                        @ServiceContract
                        interface Api2
                        """.trimIndent()
                    )
                ),
                impls = listOf(
                    SourceFile.kotlin(
                        "Impl.kt", """
                        package my.impl
                        import my.api.Api
                        import my.api.Api2
                        import com.github.eventhorizonlab.spi.ServiceProvider
                        @ServiceProvider(Api::class, Api2::class)
                        class Impl : Api, Api2
                        """.trimIndent()
                    )
                ),
                contractFqcn = listOf("my.api.Api", "my.api.Api2"),
                expectedImpls = listOf("my.impl.Impl")
            )
        ) { case ->
            val apiResult = compileApi(*case.api.toTypedArray())
            apiResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

            val implResult = compileImpl(apiResult, *case.impls.toTypedArray())
            implResult.exitCode shouldBe KotlinCompilation.ExitCode.OK

            assertServiceFile(implResult, case.contractFqcn, *case.expectedImpls.toTypedArray())
        }
    }

    // --- Error scenarios ---
    data class ErrorCase(
        val description: String,
        val sources: List<SourceFile>,
        val expectedMessage: String
    )

    context("error cases") {
        withData(
            nameFn = { it.description },
            ErrorCase(
                "no provider for contract (Kotlin)",
                listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                    package my.api
                    import com.github.eventhorizonlab.spi.ServiceContract
                    @ServiceContract
                    interface LonelyContract
                """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.LonelyContract")
            ),
            ErrorCase(
                "no provider for contract (Java)",
                listOf(
                    SourceFile.java(
                        "LonelyContract.java", """
                    package my.api;
                    import com.github.eventhorizonlab.spi.ServiceContract;
                    @ServiceContract
                    public interface LonelyContract {}
                """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.LonelyContract")
            ),
            ErrorCase(
                "implementation not annotated with @ServiceProvider (Kotlin)",
                listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                        package my.api
                        import com.github.eventhorizonlab.spi.ServiceContract
                        @ServiceContract
                        interface Contract
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Impl.kt", """
                        package my.impl
                        import my.api.Contract
                        class Impl : Contract
                    """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.Contract")
            ),
            ErrorCase(
                "implementation not annotated with @ServiceProvider (Java)",
                listOf(
                    SourceFile.java(
                        "Contract.java", """
                        package my.api;
                        import com.github.eventhorizonlab.spi.ServiceContract;
                        @ServiceContract
                        public interface Contract {}
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Impl.java", """
                        package my.impl;
                        import my.api.Contract;
                        public class Impl implements Contract {}
                    """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.Contract")
            ),
            ErrorCase(
                "implementation not annotated with @ServiceProvider (Java+Kotlin)",
                listOf(
                    SourceFile.java(
                        "Contract.java", """
                        package my.api;
                        import com.github.eventhorizonlab.spi.ServiceContract;
                        @ServiceContract
                        public interface Contract {}
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Impl.kt", """
                        package my.impl
                        import my.api.Contract
                        class Impl : Contract
                    """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.Contract")
            ),
            ErrorCase(
                "implementation not annotated with @ServiceProvider (Kotlin+Java)",
                listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                        package my.api
                        import com.github.eventhorizonlab.spi.ServiceContract
                        @ServiceContract
                        interface Contract
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Impl.java", """
                        package my.impl;
                        import my.api.Contract;
                        public class Impl implements Contract {}
                    """.trimIndent()
                    )
                ),
                missingServiceProviderErrorMessage("my.api.Contract")
            ),
            ErrorCase(
                "provider target not annotated with @ServiceContract (Kotlin)",
                listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                        package my.api
                        interface NotAContract
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Impl.kt", """
                        package my.impl
                        import my.api.NotAContract
                        import com.github.eventhorizonlab.spi.ServiceProvider
                        @ServiceProvider(NotAContract::class)
                        class Impl : NotAContract
                    """.trimIndent()
                    )
                ),
                "@ServiceProvider target my.api.NotAContract is not annotated with @ServiceContract"
            ),
            ErrorCase(
                "provider target not annotated with @ServiceContract (Java)",
                listOf(
                    SourceFile.java(
                        "NotAContract.java", """
                        package my.api;
                        public interface NotAContract {}
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Impl.java", """
                        package my.impl;
                        import my.api.NotAContract;
                        import com.github.eventhorizonlab.spi.ServiceProvider;
                        @ServiceProvider(NotAContract.class)
                        public class Impl implements NotAContract {}
                    """.trimIndent()
                    )
                ),
                "@ServiceProvider target my.api.NotAContract is not annotated with @ServiceContract"
            ),
            ErrorCase(
                "provider target not annotated with @ServiceContract (kotlin+java)",
                listOf(
                    SourceFile.kotlin(
                        "Api.kt", """
                        package my.api
                        interface NotAContract
                    """.trimIndent()
                    ),
                    SourceFile.java(
                        "Impl.java", """
                        package my.impl;
                        import my.api.NotAContract;
                        import com.github.eventhorizonlab.spi.ServiceProvider;
                        @ServiceProvider(NotAContract.class)
                        public class Impl implements NotAContract {}
                    """.trimIndent()
                    )
                ),
                "@ServiceProvider target my.api.NotAContract is not annotated with @ServiceContract"
            ),
            ErrorCase(
                "provider target not annotated with @ServiceContract (java+kotlin)",
                listOf(
                    SourceFile.java(
                        "NotAContract.java", """
                        package my.api;
                        public interface NotAContract {}
                    """.trimIndent()
                    ),
                    SourceFile.kotlin(
                        "Impl.kt", """
                        package my.impl
                        import my.api.NotAContract
                        import com.github.eventhorizonlab.spi.ServiceProvider
                        @ServiceProvider(NotAContract::class)
                        class Impl : NotAContract
                    """.trimIndent()
                    )
                ),
                "@ServiceProvider target my.api.NotAContract is not annotated with @ServiceContract"
            )
        ) { case ->
            val result = compile(case.sources)
            result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
            result.messages shouldContain case.expectedMessage
        }
    }
})