package com.github.eventhorizonlab.spi.extensions

import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.TypeElement

internal fun ProcessingEnvironment.getStringifiedBinaryName(type: TypeElement) =
    elementUtils.getBinaryName(type).toString()