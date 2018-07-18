package com.ptmr3.fluxxap
import javax.lang.model.element.Element

class ProcessingError(message: String, element: Element) : Throwable()