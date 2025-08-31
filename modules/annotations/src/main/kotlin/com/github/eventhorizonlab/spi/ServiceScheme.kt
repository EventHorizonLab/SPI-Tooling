package com.github.eventhorizonlab.spi

import kotlin.reflect.KClass

/**
 * Marks a class as a ServiceLoader provider for the given contract.
 */
@Repeatable
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceProvider(vararg val value: KClass<*>)

/**
 * Marks an interface as a ServiceLoader contract.
 */
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceContract