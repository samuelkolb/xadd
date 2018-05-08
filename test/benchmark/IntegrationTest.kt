package benchmark

import org.testng.annotations.Test

private const val timeOut = 60L

/**
 * Tests various integration methods for XADDs
 */
class IntegrationTest {

    @Test(timeOut = timeOut)
    fun test() {

    }

    @Test(dependsOnMethods = arrayOf("test"))
    fun test2() {

    }
}