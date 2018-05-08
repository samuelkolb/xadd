package diagram

import org.junit.Test
import org.testng.Assert

import xadd.XADD

/**
 * Tests the BoolXADD class
 *
 * @author Samuel Kolb
 */
class BoolXADDTest {

    private val DELTA = 0.000001;

    @Test
    fun testNot_Regular1() {
        val builder = XADDBuild.builder(XADD())
        val bounds = builder.test("x >= 0").and(builder.test("x <= 10"))
        val formula = builder.test("x <= 5").or(builder.bool("a"))

        compareVolumes(bounds, formula, listOf("a"), listOf("x"))
    }

    private fun compareVolumes(bounds: BoolXADD, formula: BoolXADD, boolVars: List<String>, realVars: List<String>) {
        val totalVolume = bounds.eliminateBoolVars(boolVars).eliminateRealVars(realVars).evaluate()
        val originalVolume = bounds.and(formula).eliminateBoolVars(boolVars).eliminateRealVars(realVars).evaluate()
        val negatedVolume = bounds.and(formula.not()).eliminateBoolVars(boolVars).eliminateRealVars(realVars).evaluate()

        Assert.assertEquals(negatedVolume, totalVolume - originalVolume, DELTA)
    }
}