package cz.feldis.sdkandroidtests.utils

import org.junit.runner.Description
import org.junit.runner.notification.RunNotifier
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod


/**
 * Used with `@RunWith(AdvancedRunner::class)`. If you are using
 * [`MockitoJUnitRunner`][org.mockito.junit.MockitoJUnitRunner], put
 * `MockitoAnnotations.initMocks(this)` in your
 * [`@Before`][org.junit.Before] method.
 *
 * Example:
 *
 *     @RunWith(AdvancedRunner::class)
 *     class AdditionTest {
 *         @Before
 *         fun setUp() {
 *             MockitoAnnotations.initMocks(this)   // If you were using MockitoJUnitRunner
 *             ...
 *         }
 *
 *         @Test
 *         @Repeat(10)  // This test will run 10 times
 *         fun testAddition() {
 *             assertEquals(4, 2 + 2)
 *         }
 *     }
 */
@Suppress("unused")
class AdvancedRunner(klass: Class<*>) : BlockJUnit4ClassRunner(klass) {
    override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
        val repeatAnnotation = method.getAnnotation(Repeat::class.java)
        if (!isIgnored(method) && repeatAnnotation != null) {
            val times = repeatAnnotation.value
            repeat(times) {
                val description = Description.createTestDescription(
                    testClass.javaClass,
                    "${testName(method)} (Run ${it + 1} of $times)"
                )
                runLeaf(methodBlock(method), description, notifier)
            }
        } else {
            super.runChild(method, notifier)
        }
    }
}