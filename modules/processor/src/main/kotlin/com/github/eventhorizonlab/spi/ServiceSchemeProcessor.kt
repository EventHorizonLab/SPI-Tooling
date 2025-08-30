package com.github.eventhorizonlab.spi

import com.google.auto.service.AutoService
import java.io.Writer
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedOptions("org.gradle.annotation.processing.aggregating")
@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_24)
class ServiceSchemeProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes() = setOf(
        ServiceProvider::class.java.canonicalName,
        ServiceContract::class.java.canonicalName
    )

    private val contracts = mutableSetOf<String>()
    private val providers = mutableMapOf<String, MutableList<String>>()

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "ServiceSchemeProcessor running")

        // 1) Collect contracts defined in this compilation
        roundEnv.getElementsAnnotatedWith(ServiceContract::class.java)
            .filter { it.kind == ElementKind.INTERFACE }
            .map { (it as TypeElement).qualifiedName.toString() }
            .forEach { contracts += it }

        // 2) Collect providers (KClass-safe)
        roundEnv.getElementsAnnotatedWith(ServiceProvider::class.java)
            .forEach { element ->
                val ann = element.getAnnotation(ServiceProvider::class.java)
                val contractName = try {
                    ann.value.qualifiedName ?: error("No qualified name for ${ann.value}")
                } catch (mte: MirroredTypeException) {
                    val typeMirror = mte.typeMirror
                    val typeElement = (typeMirror as DeclaredType).asElement() as TypeElement
                    typeElement.qualifiedName.toString()
                }
                providers.computeIfAbsent(contractName) { mutableListOf() }
                    .add((element as TypeElement).qualifiedName.toString())
            }

        // 3) On final round, generate files + validate
        if (roundEnv.processingOver()) {
            generateServiceFiles()
            validateProviders()
        }

        return true
    }

    private fun generateServiceFiles() {
        // Write service files for all contracts that actually have providers (cross-module safe)
        providers.forEach { (contract, impls) ->
            if (impls.isNotEmpty()) {
                writeServiceFile(contract, impls.distinct().sorted())
            }
        }

        // Also flag any contracts declared here that have no providers
        contracts.filter { providers[it].isNullOrEmpty() }
            .forEach { contract ->
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    missingServiceProviderErrorMessage(contract)
                )
            }
    }

    private fun validateProviders() {
        providers.keys.forEach { contractName ->
            val contractElement = processingEnv.elementUtils.getTypeElement(contractName)
            val hasAnnotation = contractElement?.annotationMirrors?.any {
                val annType = (it.annotationType.asElement() as TypeElement)
                    .qualifiedName.toString()
                annType == ServiceContract::class.java.canonicalName
            } ?: false

            if (!hasAnnotation) {
                val hint = if (contractElement == null) {
                    "Contract type not found — is the API module on the processor's compile classpath?"
                } else {
                    "Type is present but not annotated with @ServiceContract."
                }
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@ServiceProvider target $contractName is not annotated with @ServiceContract. $hint"
                )
            }
        }
    }

    private fun writeServiceFile(contract: String, impls: List<String>) {
        try {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.NOTE,
                "Writing META-INF/services/$contract with ${impls.size} implementation(s): ${impls.joinToString()}"
            )
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

internal fun missingServiceProviderErrorMessage(contract: String): String = "No @ServiceProvider found for contract $contract"