package jp.juggler.subwaytooter.testutil

private fun formatClass(value: Class<*>) =
    value.canonicalName ?: value.name

private fun buildPrefix(message: String?) =
    if (message.isNullOrEmpty()) "" else "$message: "

private fun isEquals(expected: Any, actual: Any) =
    expected == actual

private fun equalsRegardingNull(expected: Any?, actual: Any?) =
    expected == actual

private fun formatClassAndValue(value: Any?, valueString: String) =
    "${value?.javaClass?.name ?: "null"}<$valueString>"

fun format(message: String?, expected: Any, actual: Any): String {
    val formatted = when {
        message.isNullOrEmpty() -> ""
        else -> "$message "
    }
    val expectedString = expected.toString()
    val actualString = actual.toString()
    return when {
        expectedString != actualString ->
            ("${formatted}expected:<$expectedString> but was:<$actualString>")
        else ->
            ("${formatted}expected: ${
                formatClassAndValue(expected, expectedString)
            } but was: ${
                formatClassAndValue(actual, actualString)
            }")
    }
}

suspend fun <T : Throwable?> assertThrowsSuspend(
    message: String?,
    expectedThrowable: Class<T>,
    runnable: suspend () -> Unit,
): T {
    try {
        runnable()
    } catch (actualThrown: Throwable) {
        return if (expectedThrowable.isInstance(actualThrown)) {
            @Suppress("UNCHECKED_CAST")
            actualThrown as T
        } else {
            var expected = formatClass(expectedThrowable)
            val actualThrowable: Class<out Throwable> = actualThrown.javaClass
            var actual = formatClass(actualThrowable)
            if (expected == actual) {
                // There must be multiple class loaders. Add the identity hash code so the message
                // doesn't say "expected: java.lang.String<my.package.MyException> ..."
                expected += "@" + Integer.toHexString(System.identityHashCode(expectedThrowable))
                actual += "@" + Integer.toHexString(System.identityHashCode(actualThrowable))
            }
            val mismatchMessage = (buildPrefix(message) +
                    format("unexpected exception type thrown;", expected, actual))

            // The AssertionError(String, Throwable) ctor is only available on JDK7.
            val assertionError = AssertionError(mismatchMessage)
            assertionError.initCause(actualThrown)
            throw assertionError
        }
    }
    val notThrownMessage = "${
        buildPrefix(message)
    }expected ${
        formatClass(expectedThrowable)
    } to be thrown, but nothing was thrown"
    throw AssertionError(notThrownMessage)
}
