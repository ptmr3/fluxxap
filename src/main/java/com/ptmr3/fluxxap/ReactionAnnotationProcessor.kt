package com.ptmr3.fluxxap

import com.google.auto.service.AutoService
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

@AutoService(Processor::class)
class ReactionAnnotationProcessor : AbstractProcessor() {
    private lateinit var mKaptKotlinGenerated: File
    private lateinit var mPackagePath: String
    override fun getSupportedAnnotationTypes() = setOf(REACTION_ANNOTATION_CLASS)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        mKaptKotlinGenerated = File(processingEnv.options[KAPT_KOTLIN_GENERATED_OPTION_NAME]?.replace("kaptKotlin", "kapt"))
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val annotation = annotations.firstOrNull { it.toString() == REACTION_ANNOTATION_CLASS }
                ?: return false
        for (element in roundEnv.getElementsAnnotatedWith(annotation)) {
            val methodName = element.simpleName.toString()
            val className = element.enclosingElement.simpleName.toString()
            val `package` = processingEnv.elementUtils.getPackageOf(element).toString()
            mPackagePath = `package`.replace(".", "\\")
            generateClass(methodName, className, `package`)
        }
        return true
    }

    private fun generateClass(methodName: String, className: String, `package`: String) {
        val source = generateSourceSource(`package`, className, methodName)
        val relativePath = `package`.replace('.', File.separatorChar)
        val folder = File(mKaptKotlinGenerated, relativePath).apply { mkdirs() }
        File(folder, "Generated$className.kt").writeText(source)
    }

    private fun generateMethodCode(`package`: String, className: String) =
            """
        package $`package`
        import javax.inject.Inject
        class Generated$className @Inject constructor() {
            inline fun test(log: (message: String) -> Unit) {
                log("The annotated class was $`package`.$className")
            }
        }
        """.trimIndent()

    private fun generateSourceSource(`package`: String, className: String, methodName: String) =
            """
        package $`package`

        class Generated$className() {
            fun _$methodName() {
            $`package`.$className().$methodName(com.ptmr3.fluxx.FluxxReaction.type("reaction"))
            }
        }
        """.trimIndent()

    companion object {
        const val KAPT_KOTLIN_GENERATED_OPTION_NAME = "kapt.kotlin.generated"
        const val REACTION_ANNOTATION_CLASS = "com.ptmr3.fluxx.annotation.Reaction"
    }
}