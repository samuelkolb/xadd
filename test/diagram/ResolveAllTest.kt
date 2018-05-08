package diagram

import junit.framework.Assert
import org.junit.Test
import xadd.XADD

/**
 * Created by samuelkolb on 05/07/2017.
 * @author Samuel Kolb
 */

class ResolveAllTest() {
    @Test
    fun test_TwoRealVar() {
        val order = listOf("x", "y")
        val context = OrderedXADD(order, false)
        val builder = XADDBuild.builder(context);
        val bounds = builder.test("y >= 1").and(builder.test("y <= 3")).and(builder.test("x >= 0"))
                .and(builder.test("x <= 1"))
        val both = builder.test("x >= y")
        val diagram = bounds.assignWeights(both.assignWeights(builder.`val`("x"), builder.`val`(10)), builder.`val`(0))
        diagram.show("")
        val types = listOf("real", "real")
        val integrated = XADDiagram(context, ResolveAllIntegration(context, types, true).integrate(diagram.number))
        integrated.show("Integrated")
        Thread.sleep(100000)
    }

    @Test
    fun test_TwoRealVar_TwoSteps() {
        val order = listOf("x", "y")
        val context = OrderedXADD(order, false)
        val builder = XADDBuild.builder(context);
        val bounds = builder.test("y >= 1").and(builder.test("y <= 3")).and(builder.test("x >= 0"))
                .and(builder.test("x <= 1"))
        val both = builder.test("x >= y")
        val diagram = bounds.assignWeights(both.assignWeights(builder.`val`("x"), builder.`val`(10)), builder.`val`(0))
        diagram.show("")
        val types = listOf("real", "real")
        var integrated = XADDiagram(context, ResolveAllIntegration(context, types, true).integrate(diagram.number, hashSetOf("y")))
        integrated = XADDiagram(context, ResolveAllIntegration(context, types, true).integrate(integrated.number, hashSetOf("x")))
        integrated.show("Integrated")
        Thread.sleep(100000)
    }

    @Test
    fun test_SmallSynthetic() {
        val order = listOf("a", "b", "x")
        val context = OrderedXADD(order, false)

        val wmi = "(* (& (| (var bool A) (var bool B)) (~ (& (var bool A) (var bool B))) (| (~ (var bool A)) (< (var real x) (const real 3.0))) (| (~ (var bool B)) (< (var real x) (const real 7.0))) (<= (const real 0.0) (var real x)) (<= (var real x) (const real 10.0))) (ite (var bool A) (var real x) (const real 2.0)) (ite (var bool B) (* (var real x) (const real 3.0)) (* (var real x) (const real 4.0))))".toLowerCase()
        val diagram = XADDParser(context).parseXadd(wmi)
        diagram.show("Diagram")
        val resolved = XADDiagram(context, ResolveAllIntegration(context, listOf("bool", "bool", "real"), true).integrate(diagram.number))
        resolved.show("Resolved")
        Thread.sleep(100000)
        // Assert.assertEquals(183.0, compareResults(diagram, asList<String>("a", "b"), listOf("x")), DELTA)
    }
}