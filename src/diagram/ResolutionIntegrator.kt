package diagram

import xadd.ExprLib
import xadd.ExprLib.ArithExpr as Expression
import xadd.XADD

/**
 * Implements integration through Symbolic Bound Resolution.
 *
 * @author Samuel Kolb
 */
class ResolutionIntegrator(val context: XADD, val verbose: Boolean=false) : SingleVariableIntegrator {

    private inner class ResolveKey constructor(val rootId: Int, val variable: String, val ub: Expression,
                                               val lb: Expression) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResolveKey

            if (rootId != other.rootId) return false
            if (variable != other.variable) return false
            if (ub != other.ub) return false
            if (lb != other.lb) return false

            return true
        }

        override fun hashCode(): Int {
            var result = rootId
            result = 31 * result + variable.hashCode()
            result = 31 * result + ub.hashCode()
            result = 31 * result + lb.hashCode()
            return result
        }
    }

    override fun integrate(diagram: XADDiagram, variable: Variable): XADDiagram {
        return diagram.xadd(integrate(diagram.number, variable, HashMap(), ExprLib.POS_INF, ExprLib.NEG_INF, 1))
    }

    private fun integrate(rootId: Int, variable: Variable, cache: MutableMap<ResolveKey, Int>,
                          ub: Expression, lb: Expression, prefix: Int): Int {

        fun XADD.Decision.variables(): HashSet<String> {
            val variables = HashSet<String>()
            this.collectVars(variables)
            return variables
        }

        fun XADD.Decision.getBound(variable: String): Pair<Double, ExprLib.ArithExpr> {
            this as XADD.ExprDec
            val pair = _expr._lhs.removeVarFromExpr(variable)
            val coefficient = pair._coef
            val scaling = ExprLib.DoubleExpr(1 / Math.abs(coefficient))
            val normalized = ExprLib.OperExpr(ExprLib.ArithOperation.PROD, pair._expr, scaling).makeCanonical()
            return Pair(coefficient, normalized as ExprLib.ArithExpr)
        }

        fun ExprLib.ArithExpr.negate(): ExprLib.ArithExpr {
            val operation = ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, this)
            return operation.makeCanonical() as ExprLib.ArithExpr
        }

        fun getDecision(exp1: ExprLib.ArithExpr, op: String, exp2: ExprLib.ArithExpr): XADD.Decision {
            val cmp: ExprLib.CompOperation = when(op) {
                ">=" -> ExprLib.CompOperation.GT_EQ
                ">" -> ExprLib.CompOperation.GT
                "<=" -> ExprLib.CompOperation.LT_EQ
                "<" -> ExprLib.CompOperation.LT
                else -> throw IllegalArgumentException("Invalid operator $op")
            }
            return context.ExprDec(ExprLib.CompExpr(cmp, exp1, exp2)).makeCanonical()
        }

        fun XADD.Decision.ite(idTrue: Int, idFalse: Int): Int {
            return context.getINodeCanon(context.getVarIndex(this, true), idFalse, idTrue)
        }

        fun XADD.Decision.ite(idTrue: () -> Int, idFalse: () -> Int): Int {
            return when {
                this is XADD.TautDec -> if(this._bTautology) idTrue() else idFalse ()
                else -> this.ite(idTrue(), idFalse())
            }
        }

        fun XADD.Decision.ifThen(id: Int): Int {
            return this.ite(id, context.ZERO)
        }

        fun XADD.Decision.ifThen(id: () -> Int): Int {
            return this.ite(id, { context.ZERO })
        }

        if(rootId == context.ZERO) {
            log(prefix, "integrate($rootId, $variable) -> 0")
            return rootId
        }

        val key = ResolveKey(rootId, variable.name, ub, lb)
        if(cache.containsKey(key)) {
            log(prefix, "integrate($rootId, $variable) -> cached")
            return cache[key]!!
        }

        fun cache(key: ResolveKey, id: Int): Int {
            cache[key] = id
            log(prefix, "cache integrate($rootId, $variable) -> $id")
            return id
        }

        val node = context.getNode(rootId)
        val nodeString = (node as? XADD.XADDINode)?.decision?.toString() ?: (node as? XADD.XADDTNode)?._expr?.toString()
        log(prefix, "integrate($rootId = ($nodeString), $variable):")

        when (node) {
            is XADD.XADDINode -> {
                if(variable.isReal) {
                    val variables = node.decision.variables()
                    if (variable.name in variables) {
                        val bound = node.decision.getBound(variable.name)

                        val ubId: Int
                        val lbId: Int
                        val newBound: ExprLib.ArithExpr

                        when {
                            bound.first < 0 -> {
                                ubId = node._high
                                lbId = node._low
                                newBound = bound.second
                            }
                            bound.first > 0 -> {
                                ubId = node._low
                                lbId = node._high
                                newBound = bound.second.negate()
                            }
                            else -> {
                                val message = "Coefficient ${bound.first} from ${node.decision} was zero"
                                throw IllegalStateException(message)
                            }
                        }

                        log(prefix, "calculate ub-branch ($ubId)")
                        val ubOldBranchF = { integrate(ubId, variable, cache, ub, lb, prefix + 1) }
                        val ubNewBranchF = {
                            val ubRec = { integrate(ubId, variable, cache, newBound, lb, prefix + 1) }
                            getDecision(newBound, ">=", lb).ifThen(ubRec)
                        }
                        log(prefix, "merge ub-branch")
                        val ubBranch = getDecision(newBound, ">=", ub).ite(ubOldBranchF, ubNewBranchF)

                        log(prefix, "calculate lb-branch ($lbId)")
                        val lbOldBranchF = { integrate(lbId, variable, cache, ub, lb, prefix + 1) }
                        val lbNewBranchF = {
                            val lbRec = { integrate(lbId, variable, cache, ub, newBound, prefix + 1) }
                            getDecision(ub, ">=", newBound).ifThen(lbRec)
                        }
                        log(prefix, "merge lb-branch")
                        val lbBranch = getDecision(newBound, "<=", lb).ite(lbOldBranchF, lbNewBranchF)

                        return cache(key, context.apply(ubBranch, lbBranch, XADD.SUM))
                    } else {
                        log(prefix, "recur on both sides")
                        val idTrue = integrate(node._high, variable, cache, ub, lb, prefix + 1)
                        val idFalse = integrate(node._low, variable, cache, ub, lb, prefix + 1)
                        return cache(key, node.decision.ite(idTrue, idFalse))
                    }
                } else if(variable.isBool) {
                    log(prefix, "sum out boolean")
                    return cache(key, context.apply(node._high, node._low, XADD.SUM))
                } else {
                    throw IllegalArgumentException("Variable $variable of unknown type")
                }
            }
            is XADD.XADDTNode -> {
                log(prefix, "integrate leaf")
                val result = when (rootId) {
                    context.ZERO -> context.ZERO
                    else -> computeIntegral(node._expr, variable, ub, lb)
                }
                return cache(key, result)
            }
            else -> throw IllegalStateException("Unexpected node $node")
        }
    }

    private fun computeIntegral(expression: ExprLib.ArithExpr, variable: Variable, ub: Expression,
                                lb: Expression): Int {
        if(expression == ExprLib.ZERO) {
            return context.ZERO
        }

        return when {
            variable.isReal -> {
                val integrated = expression.integrateExpr(variable.name)
                val upper = integrated.substitute(hashMapOf(Pair(variable.name, ub)))
                val lower = integrated.substitute(hashMapOf(Pair(variable.name, lb)))
                val result = ExprLib.ArithExpr.op(upper, lower, ExprLib.ArithOperation.MINUS)
                context.getTermNode(result.makeCanonical() as ExprLib.ArithExpr)
            }
            variable.isBool -> {
                val result = ExprLib.ArithExpr.op(expression, 2.0, ExprLib.ArithOperation.PROD)
                context.getTermNode(result.makeCanonical() as ExprLib.ArithExpr)
            }
            else -> throw IllegalArgumentException("Variable $variable of unknown type")
        }
    }

    private fun log(prefix: Int, message: String) {
        if(verbose) {
            println((0..prefix).joinToString("") { "  " } + message)
        }
    }
}