package org.eventhorizonlab.spi

import com.google.auto.service.AutoService
import java.io.Writer
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedSourceVersion
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

/**
 * Compile-time processor that:
 *  - Finds all @ServiceContract interfaces
 *  - Finds all @ServiceProvider classes
 *  - Generates META-INF/services entries
 *  - Validates provider/contract relationships
 */
@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_24)
class ServiceSchemeProcessor : AbstractProcessor() {
    override fun getSupportedAnnotationTypes() =
        mutableSetOf(
            ServiceProvider::class.java.canonicalName,
            ServiceContract::class.java.canonicalName
        )

    private val contracts = mutableSetOf<String>()
    private val providers = mutableMapOf<String, MutableList<String>>()

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        // 1️⃣ Collect contracts
        roundEnv.getElementsAnnotatedWith(ServiceContract::class.java)
            .filter { it.kind == ElementKind.INTERFACE }
            .map { (it as TypeElement).qualifiedName.toString() }
            .forEach { contracts += it }

        // 2️⃣ Collect providers
        roundEnv.getElementsAnnotatedWith(ServiceProvider::class.java)
            .forEach { element ->
                val ann = element.getAnnotation(ServiceProvider::class.java)
                val contractName = ann.value.qualifiedName!!
                providers.computeIfAbsent(contractName) { mutableListOf() }
                    .add((element as TypeElement).qualifiedName.toString())
            }

        // 3️⃣ On final round, generate files + validate
        if (roundEnv.processingOver()) {
            generateServiceFiles()
            validateProviders()
        }

        return true
    }

    private fun generateServiceFiles() {
        contracts.forEach { contract ->
            val impls = providers[contract].orEmpty()
            if (impls.isEmpty()) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "No @ServiceProvider found for contract $contract"
                )
            } else {
                writeServiceFile(contract, impls)
            }
        }
    }

    private fun validateProviders() {
        providers.keys
            .filterNot { contracts.contains(it) }
            .forEach {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@ServiceProvider target $it is not annotated with @ServiceContract"
                )
            }
    }

    private fun writeServiceFile(contract: String, impls: List<String>) {
        try {
            processingEnv.filer
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/$contract")
                .openWriter()
                .use { writer: Writer ->
                    impls.forEach { writer.write("$it\n") }
                }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}