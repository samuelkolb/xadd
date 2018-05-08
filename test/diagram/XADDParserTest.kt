package diagram

import junit.framework.Assert.assertEquals
import junit.framework.Assert.fail
import org.junit.Test
import xadd.XADD

/**
 * Created by samuelkolb on 20/06/2017.
 * @author Samuel Kolb
 */

class XADDParserTest() {
    // val wmi = "(* (& (| (var bool A) (var bool B)) (~ (& (var bool A) (var bool B))) (| (~ (var bool A)) (< (var real x) (const real 3.0))) (| (~ (var bool B)) (< (var real x) (const real 7.0))) (<= (const real 0.0) (var real x)) (<= (var real x) (const real 10.0))) (ite (var bool A) (var real x) (const real 2.0)) (ite (var bool B) (* (var real x) (const real 3.0)) (* (var real x) (const real 4.0))))".toLowerCase()

    // Operators: "ite", "^", "~", "&", "|", "*", "+", "<=", "<", "const", "var"

    val testBoolVar = "a"
    val testRealVar = "x"

    fun compile(wmi: String): XADDiagram {
        return XADDParser(XADD()).parseXadd(wmi)
    }

    fun testBoolValues(wmi: String, val1: Number, val2: Number) {
        val diagram = compile(wmi)
        assertEquals(val1, diagram.evaluate(Assignment().setBool(testBoolVar, true)))
        assertEquals(val2, diagram.evaluate(Assignment().setBool(testBoolVar, false)))
    }

    fun testRealValues(wmi: String, testingValues: Collection<Double>, resultValues: Collection<Double>) {
        val diagram = compile(wmi)
        for((test, result) in testingValues.zip(resultValues)) {
            assertEquals(result, diagram.evaluate(Assignment().setReal(testRealVar, test)))
        }
    }

    fun testConstant(wmi: String, value: Number) {
        assertEquals(value, compile(wmi).evaluate(Assignment()))
    }

    @Test
    fun testSimpleIte() {
        val val1 = 13.17
        val val2 = 19.23
        val wmi = "(ite (var bool %s) (const real %f) (const real %f))".format(testBoolVar, val1, val2)
        testBoolValues(wmi,val1, val2)
    }

    @Test
    fun testSimplePower() {
        val power = 2.0
        val values = listOf(1.0, 5.0)
        val wmi = "(^ (var real %s) (const real %f)".format(testRealVar, power)
        testRealValues(wmi, values, values.map { it * it })
    }

    @Test
    fun testSimpleNot() {
        testConstant("(~ (const bool true))", 0.0)
    }

    @Test
    fun testSimpleAnd() {
        testBoolValues("(& (const bool true) (var bool %s)".format(testBoolVar), 1.0, 0.0)
    }

    @Test
    fun testSimpleOr() {
        testBoolValues("(| (const bool false) (var bool %s)".format(testBoolVar), 1.0, 0.0)
    }

    @Test
    fun testSimpleTimes() {
        val constValue = 17.0
        val values = listOf(1.0, 5.0)
        val wmi = "(* (var real %s) (const real %f)".format(testRealVar, constValue)
        testRealValues(wmi, values, values.map { it * constValue })
    }

    @Test
    fun testSimplePlus() {
        val constValue = 17.0
        val values = listOf(1.0, 5.0)
        val wmi = "(+ (var real %s) (const real %f)".format(testRealVar, constValue)
        testRealValues(wmi, values, values.map { it + constValue })
    }

    @Test
    fun testSimpleLE() {
        val values = listOf(1.0, 5.0)
        val wmi = "(<= (var real %s) (const real 2.5))".format(testRealVar)
        testRealValues(wmi, values, listOf(1.0, 0.0))
    }

    @Test
    fun testSimpleLT() {
        val values = listOf(1.0, 5.0)
        val wmi = "(<= (var real %s) (const real 2.5))".format(testRealVar)
        testRealValues(wmi, values, listOf(1.0, 0.0))
    }

    @Test
    fun testSimpleConstReal() {
        val number = 2.0
        testConstant("(const real %f)".format(number), number)
    }

    @Test
    fun testSimpleConstBool() {
        testConstant("(const bool true)", 1.0)
    }

    @Test
    fun testSimpleVar() {
        val values = listOf(1.0, 5.0)
        val wmi = "(var real %s)".format(testRealVar)
        testRealValues(wmi, values, values)
    }

    @Test
    fun testTautology() {
        val wmi = "(| (const bool true) (var bool %s))".format(testBoolVar)
        testConstant(wmi, 1.0)
    }
}