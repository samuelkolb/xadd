package diagram;

import xadd.ExprLib;
import xadd.ExprLib.ArithExpr;
import xadd.XADD;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Supplier;

import static function.Architect.map;
import static java.lang.String.format;

/**
 * Created by samuelkolb on 07/06/2017.
 *
 * @author Samuel Kolb
 */
public class SymbolicResolveIntegration {

	private class ResolveKey {
		final int rootId;
		final String variable;

		ResolveKey(int rootId, String variable) {
			this.rootId = rootId;
			this.variable = variable;
		}

		@Override
		public boolean equals(Object o) {
			if(this == o) return true;
			if(o == null || getClass() != o.getClass()) return false;

			ResolveKey that = (ResolveKey) o;

			if(rootId != that.rootId) return false;
			if(variable != null ? !variable.equals(that.variable) : that.variable != null) return false;
			return true;
		}

		@Override
		public int hashCode() {
			int result = rootId;
			result = 31 * result + (variable != null ? variable.hashCode() : 0);
			return result;
		}
	}

	//region Variables
	private final static int BOOL = 1;
	private final static int REAL = 2;
	private HashMap<Integer, Integer> integratedLeafNodes;
	private HashMap<ResolveKey, Integer> resolveCache;
	private final boolean verbose;
	private final boolean reduce = false;
	private XADD context;
	//endregion

	//region Construction

	/**
	 * @param context	The XADD pool / context
	 */
	public SymbolicResolveIntegration(XADD context) {
		this(context, false);
	}

	/**
	 * @param context	The XADD pool / context
	 * @param verbose	Enable verbose printing if true
	 */
	public SymbolicResolveIntegration(XADD context, boolean verbose) {
		this.context = context;
		this.verbose = verbose;
	}

	//endregion

	//region Public methods

	/**
	 * Integrates the given variable from the given diagram
	 * @param rootId	The id of the root node of the diagram to integrate
	 * @param variable	The variable to eliminate
	 * @return	The id of the resulting diagram
	 */
	public int integrate(int rootId, String variable, String type) {
		integratedLeafNodes = new HashMap<>();
		resolveCache = new HashMap<>();
		return resolve(rootId, variable, getType(type), Optional.empty(), Optional.empty(), "");
	}

	private int getType(String type) {
		type = type.toLowerCase();
		if(type.startsWith("bool")) {
			return BOOL;
		} else if(type.startsWith("real") || type.startsWith("cont")) {
			return REAL;
		} else {
			throw new IllegalArgumentException(format("Unknown type %s", type));
		}
	}

	private void log(String message, String prefix, Object... arguments) {
		if(this.verbose) {
			System.out.println(prefix + format(message, arguments));
		}
	}

	private int resolve(int rootId, String variable, int type, Optional<ArithExpr> optUb, Optional<ArithExpr> optLb, String prefix) {
		if(rootId == context.ZERO) {
			return context.ZERO;
		}

		ResolveKey key = new ResolveKey(rootId, variable);
		if(resolveCache.containsKey(key)) {
			log("Cache hit", prefix);
			// System.out.format("Cache hit %s %s % %s", rootId, variable, optLb, optUb);
			// System.out.println(rootId + " " + variable + " " + optLb + " " + optUb);
			return resolveCache.get(key);
		}

		ArithExpr ubSym = new ExprLib.VarExpr("_ub");
		ArithExpr lbSym = new ExprLib.VarExpr("_lb");

		Optional<ArithExpr> optUbSym = Optional.of(ubSym);
		Optional<ArithExpr> optLbSym = Optional.of(lbSym);

		ExprLib.DoubleExpr posInf = new ExprLib.DoubleExpr(Double.POSITIVE_INFINITY);
		ExprLib.DoubleExpr negInf = new ExprLib.DoubleExpr(Double.NEGATIVE_INFINITY);

		XADD.XADDNode node = context.getNode(rootId);
		log("Resolve %s for var %s with ub %s and lb %s", prefix, node, variable, optUb, optLb);
		if(node instanceof XADD.XADDINode) {
			XADD.XADDINode internalNode = (XADD.XADDINode) node;
			HashSet<String> variables = new HashSet<>();
			internalNode.getDecision().collectVars(variables);

			if(!variables.contains(variable)) {
				int resolveLow = resolve(internalNode._low, variable, type, optUbSym, optLbSym, prefix + "\t");
				int resolveHigh = resolve(internalNode._high, variable, type, optUbSym, optLbSym, prefix + "\t");
				int resolved = context.getINodeCanon(internalNode._var, resolveLow, resolveHigh);
				// resolved = context.reduceLP(resolved);
				log("Resolved did not contain: %s", prefix, context.getNode(resolved));
				resolveCache.put(key, resolved);
				return context.substitute(resolved, map("_ub", "_lb").to(
						optUb.orElse(posInf),
						optLb.orElse(negInf)));
			} else {
				if(internalNode.getDecision() instanceof XADD.ExprDec) {
					ExprLib.CompExpr comparison = ((XADD.ExprDec) internalNode.getDecision())._expr;
					ExprLib.CoefExprPair pair = comparison._lhs.removeVarFromExpr(variable);
					double coefficient = pair._coef;
					ArithExpr normalized = (ArithExpr) new ExprLib.OperExpr(ExprLib.ArithOperation.PROD,
							pair._expr, new ExprLib.DoubleExpr(1 / Math.abs(coefficient))).makeCanonical();
					final Optional<ArithExpr> newBound;

					int ubId, lbId;

					if(coefficient < 0) {
						log("UB branch is true", prefix);
						ubId = internalNode._high;
						lbId = internalNode._low;
						newBound = Optional.of(normalized);
					} else if(coefficient > 0) {
						log("UB branch is false", prefix);
						ubId = internalNode._low;
						lbId = internalNode._high;
						ExprLib.OperExpr negated = new ExprLib.OperExpr(ExprLib.ArithOperation.MINUS, ExprLib.ZERO, normalized);
						newBound = Optional.of((ArithExpr) negated.makeCanonical());
					} else {
						throw new IllegalStateException(format("Coefficient %s from expression %s should be non-zero",
								coefficient, comparison));
					}

					log(" Node %s, coefficient %.2f, bound: %s", prefix, comparison, coefficient, newBound.get());

					// f_u = (u_{new} \geq l) * \ite(u > u_{new}, br(x, h(f), u_{new}, l), br(x, h(f), u, l))$
					// f_l = (l_{new} \leq u) * \ite(l < l_{new}, br(x, l(f), u, l_{new}), br(x, l(f), u, l))$

					int ubConsistencyId, ubIte, lbConsistencyId, lbIte;

					// TODO pass_ub / pass/lb

					int genericLbResolve = resolve(lbId, variable, type, optUbSym, optLbSym, prefix + "\t");
					ubConsistencyId = comparisonToNodeId(ExprLib.CompOperation.GT_EQ, newBound.get(), lbSym);


					Supplier<Integer> resolveLbFalseSupplier =
							() -> genericLbResolve;
					Supplier<Integer> resolveLbTrueSupplier =
							() -> context.substitute(genericLbResolve, map("_lb").to(newBound.get()));

					XADD.Decision lbDecision = getDecision(ExprLib.CompOperation.LT_EQ, lbSym, newBound.get());
					lbIte = simplifyIte(lbDecision, resolveLbTrueSupplier, resolveLbFalseSupplier, prefix);


					int genericUbResolve = resolve(ubId, variable, type, optUbSym, optLbSym, prefix + "\t");
					lbConsistencyId = comparisonToNodeId(ExprLib.CompOperation.LT_EQ, newBound.get(), ubSym);

					Supplier<Integer> resolveUbFalseSupplier =
							() -> genericUbResolve;
					Supplier<Integer> resolveUbTrueSupplier =
							() -> context.substitute(genericUbResolve, map("_ub").to(newBound.get()));

					XADD.Decision ubDecision = getDecision(ExprLib.CompOperation.GT_EQ, ubSym, newBound.get());
					ubIte = simplifyIte(ubDecision, resolveUbTrueSupplier, resolveUbFalseSupplier, prefix);

					// Branches
					int ubBranch = context.apply(ubConsistencyId, ubIte, XADD.PROD);
					if(reduce) {
						ubBranch = context.reduce(ubBranch);
					}
					int lbBranch = context.apply(lbConsistencyId, lbIte, XADD.PROD);
					if(reduce) {
						lbBranch = context.reduce(lbBranch);
					}

					if(ubBranch == 1 && lbBranch == 1) {
					 	if(optLb.isPresent()) {
							log("Ub consistency: %s >= %s", prefix, newBound.get(), optLb.get());
							log("%d", "", comparisonToNodeId(ExprLib.CompOperation.GT_EQ,
									newBound.get(), optLb.get()));
							log("%d", "", comparisonToVarId(ExprLib.CompOperation.GT_EQ,
									newBound.get(), optLb.get()));
						}
						log("Product ub: %d and %d, lb: %d and %d", prefix, ubConsistencyId, ubIte, lbConsistencyId, lbIte);
						log("Consistency node %s", prefix, context.getNode(ubConsistencyId));
					}

					int resolved = context.apply(ubBranch, lbBranch, XADD.SUM);
					// resolved = context.reduce(resolved);

					log("Resolved summed: %s + %s = %s", prefix, context.getNode(ubBranch),
							context.getNode(lbBranch), context.getNode(resolved));
					resolveCache.put(key, resolved);
					// new XADDiagram(context, resolved).show(rootId + " resolved");
					int substituted = context.substitute(resolved, map("_ub", "_lb").to(
							optUb.orElse(posInf), optLb.orElse(negInf)));
					// new XADDiagram(context, resolved).show(rootId + " substituted");
					// substituted = context.reduceLP(substituted);
					return substituted;
				} else {
					// TODO
					int summed = context.apply(internalNode._low, internalNode._high, XADD.SUM);
					summed = context.reduceLP(summed);
					resolveCache.put(key, summed);
					return summed;
				}
			}
		} else if(node instanceof XADD.XADDTNode) {
			XADD.XADDTNode terminalNode = (XADD.XADDTNode) node;
			/*
			TODO Expression caching?
			if(!integratedLeafNodes.containsKey(rootId)) {
				integratedLeafNodes.put(rootId, computeIntegral(terminalNode._expr, variable, optUb, optLb));
			}
			return integratedLeafNodes.get(rootId);*/
			int resolved = computeIntegral(terminalNode._expr, variable, type, optUbSym, optLbSym);
			log("Terminal node integrated to return %s", prefix, context.getNode(resolved));
			resolveCache.put(key, resolved);
			return context.substitute(resolved, map("_ub", "_lb").to(
					optUb.orElse(posInf), optLb.orElse(negInf)));
		} else {
			throw new IllegalStateException(format("Unexpected subclass %s of XADDNode %s", node.getClass(), node));
		}
	}

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

	private int computeIntegral(ArithExpr expr, String variable, int type, Optional<ArithExpr> optUb, Optional<ArithExpr> optLb) {
		if(expr.equals(ExprLib.ZERO)) {
			log("Return 0 for the integration of 0 for %s in [%s, %s]", "", variable, optLb, optUb);
			return context.getTermNode(ExprLib.ZERO);
		}
		
		if(type == BOOL) {
			final ExprLib.DoubleExpr two = new ExprLib.DoubleExpr(2);
			ArithExpr result = (ArithExpr) ArithExpr.op(two, expr, ExprLib.ArithOperation.PROD).makeCanonical();
			return context.getTermNode(result);
		} else if(type == REAL) {

			ArithExpr ub = optUb.isPresent() ? optUb.get() : ExprLib.POS_INF;
			ArithExpr lb = optLb.isPresent() ? optLb.get() : ExprLib.NEG_INF;
			ArithExpr integrated = expr.integrateExpr(variable);
			ArithExpr substitutedUb = integrated.substitute(mapTo(variable, ub));
			ArithExpr substitutedLb = integrated.substitute(mapTo(variable, lb));
			ArithExpr result = ArithExpr.op(substitutedUb, substitutedLb, ExprLib.ArithOperation.MINUS);
			result = (ArithExpr) result.makeCanonical();
			log("Integrating %s for %s in [%s, %s] gives %s", "", expr, variable, lb, ub, result);
			return context.getTermNode(result);
		} else {
			throw new IllegalArgumentException(format("Could not integrate term for variable of type %d", type));
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