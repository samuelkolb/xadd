package diagram

import xadd.ExprLib
import xadd.ExprLib.ArithExpr as Expression
import xadd.XADD

/**
 * Implements integration through Symbolic Bound Resolution.
 *
 * @author Samuel Kolb
 */
class SymbolicResolutionIntegrator(val context: XADD, val verbose: Boolean=false) : SingleVariableIntegrator {

    private val ubSym = ExprLib.VarExpr("_ub")
    private val lbSym = ExprLib.VarExpr("_lb")

    private inner class ResolveKey constructor(val rootId: Int, val variable: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ResolveKey

            if (rootId != other.rootId) return false
            if (variable != other.variable) return false

            return true
        }

        override fun hashCode(): Int {
            var result = rootId
            result = 31 * result + variable.hashCode()
            return result
        }
    }

    override fun integrate(diagram: XADDiagram, variable: Variable): XADDiagram {
        val cache = HashMap<ResolveKey, Int>()
        val result = integrate(diagram.number, variable, cache, 1)
        val substitution = hashMapOf(Pair(ubSym._sVarName, ExprLib.POS_INF), Pair(lbSym._sVarName, ExprLib.NEG_INF))
        return diagram.xadd(context.substitute(result, substitution))
    }

    private fun integrate(rootId: Int, variable: Variable, cache: MutableMap<ResolveKey, Int>, prefix: Int): Int {

        fun XADD.Decision.variables(): HashSet<String> {
            val variables = HashSet<String>()
            this.collectVars(variables)
            return variables
        }

        fun XADD.Decision.getBound(variable: String): Pair<Double, Expression> {
            this as XADD.ExprDec
            val pair = _expr._lhs.removeVarFromExpr(variable)
            val coefficient = pair._coef
            val scaling = ExprLib.DoubleExpr(1 / Math.abs(coefficient))
            val normalized = ExprLib.OperExpr(ExprLib.ArithOperation.PROD, pair._expr, scaling).makeCanonical()
            return Pair(coefficient, normalized as Expression)
        }

        fun Expression.negate(): Expression {
            val operation = ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, this)
            return operation.makeCanonical() as Expression
        }

        fun getDecision(exp1: Expression, op: String, exp2: Expression): XADD.Decision {
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

        fun XADD.Decision.ifThen(id: Int): Int {
            log(prefix, "get var-index for $this")
            val varIndex = context.getVarIndex(this, true)
            log(prefix, "calculate if $varIndex = ($this) then $id else 0")
            return context.getINodeCanon(varIndex, context.ZERO, id)
        }

        if(rootId == context.ZERO) {
            log(prefix, "integrate($rootId, $variable) -> 0")
            return rootId
        }

        val key = ResolveKey(rootId, variable.name)
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
                        val newBound: Expression

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
                        val ubComp = getDecision(newBound, ">=", ubSym)
                        val ubConsistency = getDecision(newBound, ">=", lbSym)
                        val ubRec = integrate(ubId, variable, cache, prefix + 1)
                        val ubSub = hashMapOf(Pair(ubSym._sVarName, newBound))
                        val ubNewBranch = ubConsistency.ifThen(context.substitute(ubRec, ubSub))
                        log(prefix, "merge ub-branch")
                        val ubBranch = ubComp.ite(ubRec, ubNewBranch)

                        log(prefix, "calculate lb-branch ($lbId)")
                        val lbComp = getDecision(newBound, "<", lbSym)
                        val lbConsistency = getDecision(ubSym, ">=", newBound)
                        val lbRec = integrate(lbId, variable, cache, prefix + 1)
                        val lbSub = hashMapOf(Pair(lbSym._sVarName, newBound))
                        val lbNewBranch = lbConsistency.ifThen(context.substitute(lbRec, lbSub))
                        log(prefix, "merge lb-branch")
                        val lbBranch = lbComp.ite(lbRec, lbNewBranch)

                        return cache(key, context.apply(ubBranch, lbBranch, XADD.SUM))
                    } else {
                        log(prefix, "recur on both sides")
                        val idTrue = integrate(node._high, variable, cache, prefix + 1)
                        val idFalse = integrate(node._low, variable, cache, prefix + 1)
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
                return cache(key, if(rootId == context.ZERO) context.ZERO  else computeIntegral(node._expr, variable))
            }
            else -> throw IllegalStateException("Unexpected node $node")
        }
    }

    private fun computeIntegral(expression: Expression, variable: Variable): Int {
        if(expression == ExprLib.ZERO) {
            return context.ZERO
        }

        return when {
            variable.isReal -> {
                val integrated = expression.integrateExpr(variable.name)
                val ub = integrated.substitute(hashMapOf(Pair(variable.name, ubSym)))
                val lb = integrated.substitute(hashMapOf(Pair(variable.name, lbSym)))
                val result = Expression.op(ub, lb, ExprLib.ArithOperation.MINUS)
                context.getTermNode(result.makeCanonical() as Expression)
            }
            variable.isBool -> {
                val result = Expression.op(expression, 2.0, ExprLib.ArithOperation.PROD)
                context.getTermNode(result.makeCanonical() as Expression)
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
