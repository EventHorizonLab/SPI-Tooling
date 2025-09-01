package com.github.eventhorizonlab.spi

import com.github.eventhorizonlab.spi.extensions.getStringifiedBinaryName
import com.google.auto.service.AutoService
import java.lang.annotation.Repeatable
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation

private data class ProviderInfo(
    val contractCanonical: String, val contractBinary: String, val providerBinary: String
)

@SupportedOptions("org.gradle.annotation.processing.aggregating")
@AutoService(Processor::class)
@SupportedSourceVersion(SourceVersion.RELEASE_24)
class ServiceSchemeProcessor : AbstractProcessor() {

    override fun getSupportedAnnotationTypes() = setOf(
        ServiceProvider::class.java.canonicalName, ServiceContract::class.java.canonicalName
    )

    private val contracts = mutableSetOf<String>() // canonical names
    private val providers = mutableListOf<ProviderInfo>()

    override fun process(
        annotations: MutableSet<out TypeElement>, roundEnv: RoundEnvironment
    ): Boolean {
        // 1) Collect contracts defined in this compilation
        roundEnv.getElementsAnnotatedWith(ServiceContract::class.java).filter { it.kind == ElementKind.INTERFACE }
            .map { (it as TypeElement).qualifiedName.toString() }.forEach { contracts += it }

        // 2) Collect providers
        roundEnv.getElementsAnnotatedWith(ServiceProvider::class.java).forEach { element ->
            // 1) Collect all ServiceProvider mirrors (direct + from container if repeatable)
            val spMirrors = collectServiceProviderMirrors(element, processingEnv)

            // 2) For each ServiceProvider mirror, read its "value" (array of class literals)
            spMirrors.forEach { spMirror ->
                val valuesWithDefaults = processingEnv.elementUtils.getElementValuesWithDefaults(spMirror)
                val valueAv =
                    valuesWithDefaults.entries.firstOrNull { it.key.simpleName.contentEquals("value") }?.value ?: error(
                        "@ServiceProvider missing 'value' on ${element.simpleName}"
                    )

                val typeMirrors = classArrayAnnotationValues(valueAv, processingEnv)
                typeMirrors.forEach { tm ->
                    val contractElement = (tm as DeclaredType).asElement() as TypeElement
                    addProvider(element as TypeElement, contractElement)
                }
            }
        }

        // 3) On final round, generate files + validate
        if (roundEnv.processingOver()) {
            generateServiceFiles()
            validateProviders()
        }

        return true
    }

    private fun generateServiceFiles() {
        providers.groupBy { it.contractBinary }.forEach { (contractBinary, infos) ->
            writeServiceFile(contractBinary, infos.map { it.providerBinary }.distinct().sorted())
        }

        // Flag contracts declared here with no providers
        contracts.filter { canonical ->
            providers.none { it.contractCanonical == canonical }
        }.forEach { contract ->
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR, missingServiceProviderErrorMessage(contract)
            )
        }
    }

    private fun validateProviders() {
        providers.map { it.contractCanonical }.distinct().forEach { canonicalName ->
            val contractElement = processingEnv.elementUtils.getTypeElement(canonicalName)
            val hasAnnotation = contractElement?.annotationMirrors?.any {
                val annType = (it.annotationType.asElement() as TypeElement).qualifiedName.toString()
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
        processingEnv.filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/$contractBinary")
            .openWriter().use { writer ->
                impls.forEach { writer.write("$it\n") }
            }
    }

    private fun addProvider(providerElement: TypeElement, contractElement: TypeElement) {
        val contractCanonical = contractElement.qualifiedName.toString()
        val contractBinary = processingEnv.getStringifiedBinaryName(contractElement)
        val providerBinary = processingEnv.getStringifiedBinaryName(providerElement)

        providers += ProviderInfo(contractCanonical, contractBinary, providerBinary)
    }


    /**
     * Returns all @ServiceProvider annotation mirrors on the element, expanding a repeatable
     * container if present, using only javax.lang.model APIs.
     */
    private fun collectServiceProviderMirrors(
        element: javax.lang.model.element.Element, processingEnv: ProcessingEnvironment
    ): List<AnnotationMirror> {
        val spName = ServiceProvider::class.java.canonicalName
        val spType = processingEnv.elementUtils.getTypeElement(spName) ?: return emptyList()

        // Find container type from @Repeatable, if any
        val containerName = spType.annotationMirrors.firstNotNullOfOrNull { am ->
            val annType = (am.annotationType.asElement() as TypeElement).qualifiedName.toString()
            if (annType == Repeatable::class.java.canonicalName) {
                // @Repeatable(value = Container.class)
                val values = processingEnv.elementUtils.getElementValuesWithDefaults(am)
                val repeatableValueAv = values.entries.first { it.key.simpleName.contentEquals("value") }.value
                val containerTm = repeatableValueAv.value as TypeMirror
                ((containerTm as DeclaredType).asElement() as TypeElement).qualifiedName.toString()
            } else null
        }

        val direct = element.annotationMirrors.filter { m ->
            ((m.annotationType.asElement() as TypeElement).qualifiedName.toString() == spName)
        }

        val expandedFromContainer = if (containerName != null) {
            element.annotationMirrors.filter { (it.annotationType.asElement() as TypeElement).qualifiedName.toString() == containerName }
                .flatMap { containerMirror ->
                    // Container has a "value" which is an array of nested @ServiceProvider annotations
                    val values = processingEnv.elementUtils.getElementValuesWithDefaults(containerMirror)
                    val valueAv = values.entries.firstOrNull { it.key.simpleName.contentEquals("value") }?.value
                        ?: return@flatMap emptyList()
                    val arr = valueAv.value as List<*>
                    arr.filterIsInstance<AnnotationValue>().mapNotNull { it.value as? AnnotationMirror }
                }
        } else emptyList()

        return direct + expandedFromContainer
    }

    private fun toTypeMirror(raw: Any?, processingEnv: ProcessingEnvironment): TypeMirror? = when (raw) {
        null -> null
        is TypeMirror -> raw
        is AnnotationValue -> toTypeMirror(raw.value, processingEnv)
        is String -> processingEnv.elementUtils.getTypeElement(raw)?.asType()
        else -> null
    }

    /**
     * Converts the "value" of a class[] annotation member into a List<TypeMirror>,
     * handling both array and single-class forms.
     */
    private fun classArrayAnnotationValues(
        valueAv: AnnotationValue,
        processingEnv: ProcessingEnvironment
    ): List<TypeMirror> {
        return when (val raw = valueAv.value) {
            is List<*> -> raw.mapNotNull { toTypeMirror(it, processingEnv) }
            else -> listOfNotNull(toTypeMirror(raw, processingEnv))
        }
    }

}

internal fun missingServiceProviderErrorMessage(contract: String) = "No @ServiceProvider found for contract $contract"