import com.natpryce.hamkrest.*
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.function.ThrowingSupplier
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

// This should be standard.
val isTrue = equalTo(true)
val isFalse = equalTo(false)

fun <T> containsElementsInOrder(vararg elements: T): Matcher<Collection<T>> {
    val perElementMatcher = object : Matcher.Primitive<Collection<T>>() {
        override fun invoke(actual: Collection<T>): MatchResult {
            elements.zip(actual).forEach {
                if (it.first != it.second)
                    return MatchResult.Mismatch("${it.first} does not equal ${it.second}")
            }
            return MatchResult.Match
        }

        override val description = "is ${describe(elements)}"
        override val negatedDescription = "is not ${describe(elements)}"
    }
    return has(Collection<T>::size, equalTo(elements.size)) and perElementMatcher
}

/**
 * Perform [CompletableFuture.join], and if an exception is thrown, unwrap the [CompletionException] and throw the
 * causing exception.
 */
fun <T> CompletableFuture<T>.joinException(): T {
    try {
        return this.join()
    } catch (e: CompletionException) {
        throw e.cause!!
    }
}

// This is a tiny wrapper over assertTimeoutPreemptively which makes the syntax slightly nicer.
fun <T> runWithTimeout(timeout: Duration, executable: () -> T): T =
        assertTimeoutPreemptively(timeout, ThrowingSupplier(executable))