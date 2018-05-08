package diagram;

import xadd.ExprLib;
import xadd.ExprLib.ArithExpr;
import xadd.XADD;

import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Supplier;

import static java.lang.String.format;

/**
 * Created by samuelkolb on 07/06/2017.
 *
 * @author Samuel Kolb
 */
public class ResolveMaximization {

	private class ResolveKey {
		final int rootId;
		final String variable;
		final ArithExpr optUb;
		final ArithExpr optLb;

		ResolveKey(int rootId, Variable variable, ArithExpr optUb, ArithExpr optLb) {
			this.rootId = rootId;
			this.variable = variable.getName();
			this.optUb = optUb;
			this.optLb = optLb;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			ResolveKey that = (ResolveKey) o;

			if(rootId != that.rootId) return false;
			if(variable != null ? !variable.equals(that.variable) : that.variable != null) return false;
			if(optUb != null ? !optUb.equals(that.optUb) : that.optUb != null) return false;
			return optLb != null ? optLb.equals(that.optLb) : that.optLb == null;
		}

		@Override
		public int hashCode() {
			int result = rootId;
			result = 31 * result + (variable != null ? variable.hashCode() : 0);
			result = 31 * result + (optUb != null ? optUb.hashCode() : 0);
			result = 31 * result + (optLb != null ? optLb.hashCode() : 0);
			return result;
		}
	}

	//region Variables
	private HashMap<ResolveKey, Integer> resolveCache;
	private final boolean verbose;
	private final boolean reduce = false;
	private XADD context;
	//endregion

	//region Construction

	/**
	 * @param context	The XADD pool / context
	 */
	public ResolveMaximization(XADD context) {
		this(context, false);
	}

	/**
	 * @param context	The XADD pool / context
	 * @param verbose	Enable verbose printing if true
	 */
	public ResolveMaximization(XADD context, boolean verbose) {
		this.context = context;
		this.verbose = verbose;
	}

	//endregion

	//region Public methods

	/**
	 * Integrates the given variable from the given diagram
	 * @param rootId	The id of the root node of the diagram to integrate
	 * @param variable	The variable to eliminate
	 * @return	The integer node id of the resulting diagram
	 */
	public int maxOut(int rootId, Variable variable) {
		resolveCache = new HashMap<>();
		return maxOut(rootId, variable, ExprLib.POS_INF, ExprLib.NEG_INF, "");
	}

    /**
     * Log a message with the provided prefix and arguments, the message will be ignored if verbose is turned off
     * @param message   The format string message to log
     * @param prefix    The prefix to prepend
     * @param arguments The arguments provided for formatting
     */
	private void log(String message, String prefix, Object... arguments) {
		if(this.verbose) {
			System.out.println(prefix + format(message, arguments));
		}
	}

    /**
     * Recursively max out a variable from the given XADD
     * @param rootId    The integer node id of the XADD to eliminate the variable from
     * @param variable  The variable to eliminate
     * @param optUb The current upper bound for the variable
     * @param optLb The current lower bound for the variable
     * @param prefix    Logging prefix
     * @return  The integer node id corresponding to the resulting XADD
     */
	private int maxOut(int rootId, Variable variable, ArithExpr optUb, ArithExpr optLb, String prefix) {
		if(rootId == context.ZERO) {
			return context.ZERO;
		}

		ResolveKey key = new ResolveKey(rootId, variable, optUb, optLb);
		if(resolveCache.containsKey(key)) {
			log("Cache hit", prefix);
			// System.out.format("Cache hit %s %s % %s", rootId, variable, optLb, optUb);
			// System.out.println(rootId + " " + variable + " " + optLb + " " + optUb);
			return resolveCache.get(key);
		}

		XADD.XADDNode node = context.getNode(rootId);
		log("Resolve %s for var %s with ub %s and lb %s", prefix, node, variable, optUb, optLb);
		if(node instanceof XADD.XADDINode) {
			XADD.XADDINode internalNode = (XADD.XADDINode) node;
			HashSet<String> variables = new HashSet<>();
			internalNode.getDecision().collectVars(variables);
			if(!variables.contains(variable.getName())) {
				int resolveLow = maxOut(internalNode._low, variable, optUb, optLb, prefix + "\t");
				int resolveHigh = maxOut(internalNode._high, variable, optUb, optLb, prefix + "\t");
				int resolved = context.getINodeCanon(internalNode._var, resolveLow, resolveHigh);
				log("Resolved did not contain: %s", prefix, context.getNode(resolved));
				resolveCache.put(key, resolved);
				return resolved;
			} else {
				if(internalNode.getDecision() instanceof XADD.ExprDec) {
					ExprLib.CompExpr comparison = ((XADD.ExprDec) internalNode.getDecision())._expr;
					ExprLib.CoefExprPair pair = comparison._lhs.removeVarFromExpr(variable.getName());
					double coefficient = pair._coef;
					ArithExpr normalized = (ArithExpr) new ExprLib.OperExpr(ExprLib.ArithOperation.PROD,
							pair._expr, new ExprLib.DoubleExpr(1 / Math.abs(coefficient))).makeCanonical();
					final ArithExpr newBound;

					int ubId, lbId;

					if(coefficient < 0) {
						log("UB branch is true", prefix);
						ubId = internalNode._high;
						lbId = internalNode._low;
						newBound = normalized;
					} else if(coefficient > 0) {
						log("UB branch is false", prefix);
						ubId = internalNode._low;
						lbId = internalNode._high;
						ExprLib.OperExpr negated = new ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, normalized);
						newBound = (ArithExpr) negated.makeCanonical();
					} else {
						throw new IllegalStateException(format("Coefficient %s from expression %s should be non-zero",
								coefficient, comparison));
					}

					log(" Node %s, coefficient %.2f, bound: %s", prefix, comparison, coefficient, newBound);

					// f_u = (u_{new} \geq l) * \ite(u > u_{new}, br(x, h(f), u_{new}, l), br(x, h(f), u, l))$
					// f_l = (l_{new} \leq u) * \ite(l < l_{new}, br(x, l(f), u, l_{new}), br(x, l(f), u, l))$

					int ubConsistencyId, ubIte, lbConsistencyId, lbIte;

					// TODO pass_ub / pass/lb

					if(optLb != ExprLib.NEG_INF) {
						ubConsistencyId = comparisonToNodeId(ExprLib.CompOperation.GT_EQ, newBound, optLb);

						Supplier<Integer> resolveFalseSupplier =
								() -> maxOut(lbId, variable, optUb, optLb, prefix + "\t");
						Supplier<Integer> resolveTrueSupplier =
								() -> maxOut(lbId, variable, optUb, newBound, prefix + "\t");

						XADD.Decision decision = getDecision(ExprLib.CompOperation.LT_EQ, optLb, newBound);
						lbIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						ubConsistencyId = context.getTermNode(ExprLib.ONE);
						lbIte = maxOut(lbId, variable, optUb, newBound, prefix + "\t");
					}

					if(optUb != ExprLib.POS_INF) {
						lbConsistencyId = comparisonToNodeId(ExprLib.CompOperation.LT_EQ, newBound, optUb);

						Supplier<Integer> resolveFalseSupplier =
								() -> maxOut(ubId, variable, optUb, optLb, prefix + "\t");
						Supplier<Integer> resolveTrueSupplier =
								() -> maxOut(ubId, variable, newBound, optLb, prefix + "\t");

						XADD.Decision decision = getDecision(ExprLib.CompOperation.GT_EQ, optUb, newBound);
						ubIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						lbConsistencyId = context.getTermNode(ExprLib.ONE);
						ubIte = maxOut(ubId, variable, newBound, optLb, prefix + "\t");
					}

					// Branches
					int ubBranch = context.apply(ubConsistencyId, ubIte, XADD.PROD);
					if(reduce) {
						ubBranch = context.reduceLP(ubBranch);
					}
					int lbBranch = context.apply(lbConsistencyId, lbIte, XADD.PROD);
					if(reduce) {
						lbBranch = context.reduceLP(lbBranch);
					}

					if(ubBranch == 1 && lbBranch == 1) {
						log("Ub consistency: %s >= %s", prefix, newBound, optLb);
						log("%d", "", comparisonToNodeId(ExprLib.CompOperation.GT_EQ,
								newBound, optLb));
						log("%d", "", comparisonToVarId(ExprLib.CompOperation.GT_EQ,
								newBound, optLb));
						log("Product ub: %d and %d, lb: %d and %d", prefix, ubConsistencyId, ubIte, lbConsistencyId, lbIte);
						log("Consistency node %s", prefix, context.getNode(ubConsistencyId));
					}

					int resolved = context.apply(ubBranch, lbBranch, XADD.MAX);
					resolved = context.reduceLP(resolved);

					log("Resolved maxed: max(%s, %s) = %s", prefix, context.getNode(ubBranch),
							context.getNode(lbBranch), context.getNode(resolved));
					resolveCache.put(key, resolved);
					return resolved;
				} else {
					int maxed = context.apply(internalNode._low, internalNode._high, XADD.MAX);
					maxed = context.reduceLP(maxed);
					resolveCache.put(key, maxed);
					return maxed;
				}
			}
		} else if(node instanceof XADD.XADDTNode) {
			XADD.XADDTNode terminalNode = (XADD.XADDTNode) node;
			int resolved = computeMax(terminalNode._expr, variable, optUb, optLb);
			log("Terminal node integrated to return %s", prefix, context.getNode(resolved));
			resolveCache.put(key, resolved);
			return resolved;
		} else {
			throw new IllegalStateException(format("Unexpected subclass %s of XADDNode %s", node.getClass(), node));
		}
	}

    /**
     * Constructs an if-then-else XADD, where branches are only lazily evaluated if the decision is not a tautology
     * @param decision  The condition
     * @param ifTrue    A lazy node id for the true branch
     * @param ifFalse   A lazy node id for the false branch
     * @param prefix    Logging prefix
     * @return  The integer node id of the resulting XADD
     */
	private int simplifyIte(XADD.Decision decision, Supplier<Integer> ifTrue, Supplier<Integer> ifFalse, String prefix) {
		if(decision instanceof XADD.TautDec) {
			XADD.TautDec tautology = (XADD.TautDec) decision;
			if(tautology._bTautology) {
				log("%s is tautology, resolving only true branch", prefix, tautology);
				return ifTrue.get();
			} else {
				log("%s is inconsistency, resolving only false branch", prefix, tautology);
				return ifFalse.get();
			}
		} else {
			int varId = context.getVarIndex(decision, true);
			int resolveFalse = ifFalse.get();
			int resolveTrue = ifTrue.get();
			log("if %s then %s else %s", prefix, varId, resolveTrue, resolveFalse);
			return context.getINodeCanon(varId, resolveFalse, resolveTrue);
		}
	}

    /**
     * Maxes out the given variable from the expression
     * @param expr  The expression
     * @param variable  The variable to max out
     * @param optUb The upper bound of the variable
     * @param optLb The lower bound of the variable
     * @return  The integer node id representing the maxed out expression
     */
	private int computeMax(ArithExpr expr, Variable variable, ArithExpr optUb, ArithExpr optLb) {
		if(expr.equals(ExprLib.ZERO)) {
			log("Return 0 for the integration of 0 for %s in [%s, %s]", "", variable, optLb, optUb);
			return context.getTermNode(ExprLib.ZERO);
		}
		
		if(variable.isBool()) {
			return context.getTermNode(expr);
		} else if(variable.isReal()) {

			// TODO Fill in symbolic maximization
            // Optional: cache symbolic solution (without bounds filled in)
			ArithExpr integrated = expr.integrateExpr(variable.getName());
			ArithExpr substitutedUb = integrated.substitute(mapTo(variable.getName(), optUb));
			ArithExpr substitutedLb = integrated.substitute(mapTo(variable.getName(), optLb));
			ArithExpr result = ArithExpr.op(substitutedUb, substitutedLb, ExprLib.ArithOperation.MINUS);

			result = (ArithExpr) result.makeCanonical();
			log("Integrating %s for %s in [%s, %s] gives %s", "", expr, variable, optLb, optUb, result);
			return context.getTermNode(result);
		} else {
			throw new IllegalArgumentException(format("Could not integrate term for variable %s", variable));
		}
	}

	private XADD.Decision getDecision(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		return context.new ExprDec(new ExprLib.CompExpr(op, lhs, rhs));
	}

	private int comparisonToVarId(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		ExprLib.CompExpr compExpr = new ExprLib.CompExpr(op, lhs, rhs);
		return context.getVarIndex(context.new ExprDec(compExpr), true);
	}

	private int booleanNodeId(int varId) {
		log("Building i-node if %d then %d else %d)", "", varId, context.getTermNode(ExprLib.ONE),
				context.getTermNode(ExprLib.ZERO));
		return context.getINode(varId, context.getTermNode(ExprLib.ZERO), context.getTermNode(ExprLib.ONE));
	}

	private int comparisonToNodeId(ExprLib.CompOperation op, ArithExpr lhs, ArithExpr rhs) {
		return booleanNodeId(comparisonToVarId(op, lhs, rhs));
	}

	private <K, V> HashMap<K, V> mapTo(K key, V value) {
		HashMap<K, V> map = new HashMap<>();
		map.put(key, value);
		return map;
	}
	//endregion
}