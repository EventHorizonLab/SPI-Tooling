package com.github.eventhorizonlab.spi

import com.google.auto.service.AutoService
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.MirroredTypeException
import javax.tools.Diagnostic
import javax.tools.StandardLocation

private data class ProviderInfo(
    val contractCanonical: String,
    val contractBinary: String,
    val providerBinary: String
)

@SupportedOptions("org.gradle.annotation.processing.aggregating")
@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_24)
class ServiceSchemeProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes() = setOf(
        ServiceProvider::class.java.canonicalName,
        ServiceContract::class.java.canonicalName
    )

    private val contracts = mutableSetOf<String>() // canonical names
    private val providers = mutableListOf<ProviderInfo>()

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        // 1) Collect contracts defined in this compilation
        roundEnv.getElementsAnnotatedWith(ServiceContract::class.java)
            .filter { it.kind == ElementKind.INTERFACE }
            .map { (it as TypeElement).qualifiedName.toString() }
            .forEach { contracts += it }

        // 2) Collect providers
        roundEnv.getElementsAnnotatedWith(ServiceProvider::class.java).forEach { element ->
            val contractElement = try {
                (element.getAnnotation(ServiceProvider::class.java).value as? TypeElement)
                    ?: throw IllegalStateException()
            } catch (mte: MirroredTypeException) {
                (mte.typeMirror as DeclaredType).asElement() as TypeElement
            }

            val contractCanonical = contractElement.qualifiedName.toString()
            val contractBinary = processingEnv.elementUtils.getBinaryName(contractElement).toString()
            val providerBinary = processingEnv.elementUtils.getBinaryName(element as TypeElement).toString()

            providers += ProviderInfo(contractCanonical, contractBinary, providerBinary)
        }

        // 3) On final round, generate files + validate
        if (roundEnv.processingOver()) {
            generateServiceFiles()
            validateProviders()
        }

        return true
    }

    private fun generateServiceFiles() {
        providers.groupBy { it.contractBinary }
            .forEach { (contractBinary, infos) ->
                writeServiceFile(contractBinary, infos.map { it.providerBinary }.distinct().sorted())
            }

        // Flag contracts declared here with no providers
        contracts.filter { canonical ->
            providers.none { it.contractCanonical == canonical }
        }.forEach { contract ->
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                missingServiceProviderErrorMessage(contract)
            )
        }
    }

    private fun validateProviders() {
        providers.map { it.contractCanonical }.distinct().forEach { canonicalName ->
            val contractElement = processingEnv.elementUtils.getTypeElement(canonicalName)
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
                    "@ServiceProvider target $canonicalName is not annotated with @ServiceContract. $hint"
                )
            }
        }
    }

    private fun writeServiceFile(contractBinary: String, impls: List<String>) {
        processingEnv.messager.printMessage(
            Diagnostic.Kind.NOTE,
            "Writing META-INF/services/$contractBinary with ${impls.size} implementation(s): ${impls.joinToString()}"
        )
        processingEnv.filer
            .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/$contractBinary")
            .openWriter()
            .use { writer ->
                impls.forEach { writer.write("$it\n") }
            }
    }
}

internal fun missingServiceProviderErrorMessage(contract: String): String = "No @ServiceProvider found for contract $contract"