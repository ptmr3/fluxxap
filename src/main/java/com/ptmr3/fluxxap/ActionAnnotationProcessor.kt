package com.ptmr3.fluxxap

import com.google.auto.service.AutoService
import com.squareup.kotlinpoet.*
import java.io.File
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.util.ElementFilter

@AutoService(Processor::class)
class ActionAnnotationProcessor : AbstractProcessor() {
    private lateinit var mKaptKotlinGenerated: File

    override fun getSupportedAnnotationTypes() = setOf(ACTION_ANNOTATION_CLASS, REACTION_ANNOTATION_CLASS)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        mKaptKotlinGenerated = File(processingEnv.options[KOTLIN_GENERATED]?.replace(KAPT_KOTLIN, KAPT))
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val actionAnnotation = annotations.firstOrNull { it.toString() == ACTION_ANNOTATION_CLASS }
                ?: return false
        val reactionAnnotation = annotations.firstOrNull { it.toString() == REACTION_ANNOTATION_CLASS }
                ?: return false
        val failureReactionAnnotation = annotations.firstOrNull { it.toString() == FAILURE_REACTION_ANNOTATION_CLASS }
            ?: return false
        generateClass(roundEnv.getElementsAnnotatedWith(actionAnnotation), ACTION_METHODS, FLUXX_ACTION_CLASS)
        generateClass(roundEnv.getElementsAnnotatedWith(reactionAnnotation), REACTION_METHODS, FLUXX_REACTION_CLASS)
        generateClass(roundEnv.getElementsAnnotatedWith(failureReactionAnnotation), FAILURE_REACTION_METHODS, FLUXX_FAILURE_REACTION_CLASS)
        return true
    }

    private fun generateClass(elementsFromAnnotation: MutableSet<out Element>, generatedClassName: String, reqFluxxClass: String) {
        val typeClass = TypeSpec.classBuilder(generatedClassName)
        var hasConstructor = false
        elementsFromAnnotation.map { element ->
            val constructor = ArrayList<String>()
            getConstructor(element.enclosingElement).parameters.map {
                constructor.add("mAny as ${processingEnv.elementUtils.getTypeElement(it.asType().toString())}")
            }
            val className = element.enclosingElement.simpleName.toString()
            val methodName = element.simpleName.toString()
            var methodParam: String? = null
            if ((element as ExecutableElement).parameters.isNotEmpty()) {
                methodParam = "$reqFluxxClass::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()"
            }
            typeClass.addFunction(FunSpec.builder("${className}_$methodName")
                    .addStatement(processingEnv.elementUtils.getPackageOf(element).toString() +
                            ".$className(${constructor.joinToString()})" +
                            ".$methodName(${methodParam ?: kotlin.run { "" }})")
                    .build())
            hasConstructor = constructor.isNotEmpty()
        }
        if (hasConstructor) {
            typeClass.addProperty(PropertySpec.builder("mAny", Any::class, KModifier.PRIVATE).initializer("Any()").build())
        }
        FileSpec.builder(FLUXX_PACKAGE_NAME, generatedClassName)
                .addType(typeClass.build())
                .build()
                .writeTo(mKaptKotlinGenerated.apply { mkdirs() })
    }

    private fun getConstructor(element: Element): ExecutableElement {
        val constructors = ElementFilter.constructorsIn(element.enclosedElements)
        if (constructors.isEmpty()) {
            throw ProcessingError("No constructors found", element)
        }
        fun ExecutableElement.validate(): ExecutableElement {
            if (Modifier.PRIVATE in modifiers) {
                throw ProcessingError("Constructor is private", this)
            }
            return this
        }

        val constructor = when {
            constructors.filter { it.parameters.isNotEmpty() }.size == 1 -> constructors.first()
            constructors.size == 1 -> constructors.first()
            else -> throw ProcessingError("Multiple constructors found, please align your project with the Fluxx standard", element)
        }
        return constructor.validate()
    }

    companion object {
        const val FLUXX_PACKAGE_NAME = "com.ptmr3.fluxx"
        const val ACTION_ANNOTATION_CLASS = "$FLUXX_PACKAGE_NAME.annotation.Action"
        const val ACTION_METHODS = "ActionMethods"
        const val FAILURE_REACTION_ANNOTATION_CLASS = "$FLUXX_PACKAGE_NAME.annotation.FailureReaction"
        const val FLUXX_ACTION_CLASS = "$FLUXX_PACKAGE_NAME.Action"
        const val FLUXX_REACTION_CLASS = "$FLUXX_PACKAGE_NAME.Reaction"
        const val FLUXX_FAILURE_REACTION_CLASS = "$FLUXX_PACKAGE_NAME.FailureReaction"
        const val KAPT = "kapt"
        const val KAPT_KOTLIN = "kaptKotlin"
        const val KOTLIN_GENERATED = "kapt.kotlin.generated"
        const val REACTION_ANNOTATION_CLASS = "$FLUXX_PACKAGE_NAME.annotation.Reaction"
        const val REACTION_METHODS = "ReactionMethods"
        const val FAILURE_REACTION_METHODS = "FailureReactionMethods"
    }
}