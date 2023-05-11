package cz.feldis.sdkandroidtests.utils

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Repeat(val value: Int = 2)