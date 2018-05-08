package diagram

import xadd.ExprLib
import xadd.ExprLib.ArithExpr
import xadd.XADD
import java.util.*

/**
 * Created by samuelkolb on 21/06/2017.
 * @author Samuel Kolb
 */

class EnumerationIntegration(val xadd: XADD) {

    private class Bounds(val upper: Boolean, val numericBound: Double, val arithmeticBounds: List<ArithExpr>) {
        constructor(upper: Boolean) :
            this(upper, if(upper) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY, ArrayList<ArithExpr>())

        fun addNumericBound(bound: Double): Bounds {
            val tighter: (Double, Double) -> Boolean = if(upper) { d1, d2 -> d1 < d2 } else { d1, d2 -> d1 > d2 }
            if(tighter(bound, numericBound)) {
                return Bounds(upper, bound, arithmeticBounds)
            } else {
                return this
            }
        }

        fun addArithmeticBound(bound: ArithExpr): Bounds {
            val copy = ArrayList<ArithExpr>(arithmeticBounds.size + 1)
            copy.addAll(arithmeticBounds)
            copy.add(bound)
            return Bounds(upper, numericBound, copy)
        }
    }

    fun integrate(rootId: Int, variable: String, type: String): Int {
        return resolve(rootId, variable, type, Bounds(true), Bounds(false))
    }

    private fun resolve(rootId: Int, variable: String, type: String, ubs: Bounds, lbs: Bounds): Int {
        throw NotImplementedError()
        /*val node = xadd.getNode(rootId)
        if(node is XADD.XADDINode) {
            /*for(ub in ubs) {
                for(lb in lbs) {

                }
            }*/
        } else if(node is XADD.XADDTNode) {

        } else {
            throw IllegalArgumentException("Unexpected node $node")
        }*/
    }
}