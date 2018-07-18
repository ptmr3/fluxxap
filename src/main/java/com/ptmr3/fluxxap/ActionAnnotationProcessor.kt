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

    override fun getSupportedAnnotationTypes() = setOf(ACTION_ANNOTATION_CLASS)

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        mKaptKotlinGenerated = File(processingEnv.options["kapt.kotlin.generated"]?.replace("kaptKotlin", "kapt"))
    }

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        val annotation = annotations.firstOrNull { it.toString() == ACTION_ANNOTATION_CLASS }
                ?: return false
        val actionsClass = TypeSpec.classBuilder(ACTION_METHODS)
        var hasConstructor = false
        roundEnv.getElementsAnnotatedWith(annotation).map { element ->
            val constructor = ArrayList<String>()
            getConstructor(element.enclosingElement).parameters.map {
                constructor.add("mAny as ${processingEnv.elementUtils.getTypeElement(it.asType().toString())}")
            }
            val methodName = element.simpleName.toString()
            actionsClass.addFunction(FunSpec.builder("_$methodName")
                    .addStatement(processingEnv.elementUtils.getPackageOf(element).toString() +
                            ".${element.enclosingElement.simpleName}(${constructor.joinToString()})" +
                            ".$methodName($FLUXX_ACTION_CLASS.type(\"action\") as $FLUXX_ACTION_CLASS)")
                    .build())
            hasConstructor = constructor.isNotEmpty()
        }
        if (hasConstructor) {
            actionsClass.addProperty(PropertySpec.builder("mAny", Any::class, KModifier.PRIVATE).initializer("Any()").build())
        }
        FileSpec.builder(FLUXX_PACKAGE_NAME, ACTION_METHODS)
                .addType(actionsClass.build())
                .build()
                .writeTo(mKaptKotlinGenerated.apply { mkdirs() })
        return true
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
        const val FLUXX_ACTION_CLASS = "$FLUXX_PACKAGE_NAME.FluxxAction"
        const val ACTION_ANNOTATION_CLASS = "$FLUXX_PACKAGE_NAME.annotation.Action"
        const val ACTION_METHODS = "ActionMethods"
    }
}