package diagram;

import xadd.ExprLib;
import xadd.ExprLib.ArithExpr;
import xadd.XADD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static java.lang.String.format;

/**
 * Created by samuelkolb on 07/06/2017.
 *
 * @author Samuel Kolb
 */
public class ResolveAllIntegration {

	private class ResolveKey {
		final int rootId;
		final String variable;
		final Optional<ArithExpr> optUb;
		final Optional<ArithExpr> optLb;

		ResolveKey(int rootId, String variable, Optional<ArithExpr> optUb, Optional<ArithExpr> optLb) {
			this.rootId = rootId;
			this.variable = variable;
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
	private final static int BOOL = 1;
	private final static int REAL = 2;
	private HashMap<Integer, Integer> integratedLeafNodes;
	private HashMap<ResolveKey, Integer> resolveCache;
	private boolean verbose;
	private OrderedXADD context;
	private final int[] types;
	private Set<String> variables;

	public boolean isVerbose() {
		return verbose;
	}

	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}

	//endregion

	//region Construction

	/**
	 * @param context	The XADD pool / context
	 */
	public ResolveAllIntegration(OrderedXADD context, List<String> variableTypes) {
		this(context, variableTypes, false);
	}

	/**
	 * @param context	The XADD pool / context
	 * @param verbose	Enable verbose printing if true
	 */
	public ResolveAllIntegration(OrderedXADD context, List<String> variableTypes, boolean verbose) {
		this.context = context;
		this.verbose = verbose;
		this.types = new int[variableTypes.size()];
		for(int i = 0; i < variableTypes.size(); i++) {
			this.types[i] = getType(variableTypes.get(i));
		}

	}

	//endregion

	//region Public methods

	/**
	 * Integrates all variable from the given diagram
	 * @param rootId	The id of the root node of the diagram to integrate (ordered according to the variables TODO...)
	 * @return	The id of the resulting diagram
	 */
	public int integrate(int rootId) {
		integratedLeafNodes = new HashMap<>();
		resolveCache = new HashMap<>();
		return resetReturn(resolve(rootId, 0, Optional.empty(), Optional.empty(), ""));
	}

	/**
	 * Integrates the given variables from the given diagram
	 * @param rootId	The id of the root node of the diagram to integrate (ordered according to the variables TODO...)
	 * @param variables	The variables to eliminate
	 * @return	The id of the resulting diagram
	 */
	public int integrate(int rootId, Set<String> variables) {
		integratedLeafNodes = new HashMap<>();
		resolveCache = new HashMap<>();
		this.variables = variables;
		int first = -1;
		for(int i = 0; i < context.getVariableOrder().size(); i++) {
			if(variables.contains(context.getVariableOrder().get(i))) {
				first = i;
				break;
			}
		}
		// System.out.println(first);
		return resetReturn(resolve(rootId, first, Optional.empty(), Optional.empty(), ""));
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

	private int resolve(int rootId, int vIndex, Optional<ArithExpr> optUb, Optional<ArithExpr> optLb, String prefix) {
		XADD.XADDNode node = context.getNode(rootId);
		if(vIndex >= context.getVariableOrder().size()) {
			System.err.println(context.getNode(rootId));
			throw new IllegalArgumentException(
					String.format("Index %d is exceeds variables %s", vIndex, context.getVariableOrder()));
		}
		String variable = context.getVariableOrder().get(vIndex);
		int type = types[vIndex];
		log("Resolve %s for var %s with ub %s and lb %s", prefix, node, variable, optUb, optLb);

		ResolveKey key = new ResolveKey(rootId, variable, optUb, optLb);
		if(resolveCache.containsKey(key)) {
			log("Cache hit", prefix);
			return resolveCache.get(key);
		}

		if(node instanceof XADD.XADDINode) {
			XADD.XADDINode internalNode = (XADD.XADDINode) node;
			HashSet<String> nodeVariables = new HashSet<>();
			internalNode.getDecision().collectVars(nodeVariables);

			if(getLast(nodeVariables) > vIndex) {
				log("Treating %s (%d), last was %s", prefix, variable, vIndex, getLast(nodeVariables));
				int resultId = resolve(rootId, vIndex + 1, Optional.empty(), Optional.empty(), prefix + "\t");
				// resultId = context.reduceLP(resultId);
				// Result-ID is a diagram containing nothing
				int eliminated = context.reduceLP(resolve(resultId, vIndex, optUb, optLb, prefix));
				resolveCache.put(key, eliminated);
				return eliminated;
			}

			if(!nodeVariables.contains(variable)) {
				// Variable not in node, should not occur because all variables are being eliminated
				int resolveLow = resolve(internalNode._low, vIndex, optUb, optLb, prefix + "\t");
				int resolveHigh = resolve(internalNode._high, vIndex, optUb, optLb, prefix + "\t");
				int resolved = context.getINodeCanon(internalNode._var, resolveLow, resolveHigh);
				log("Resolved did not contain: %s", prefix, context.getNode(resolved));
				resolveCache.put(key, resolved);
				return resolved;
				// throw new IllegalStateException("");
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

					if(optLb.isPresent()) {
						ArithExpr lb = optLb.get();
						ubConsistencyId = comparisonToNodeId(ExprLib.CompOperation.GT_EQ, newBound.get(), optLb.get());

						Supplier<Integer> resolveFalseSupplier =
								() -> resolve(lbId, vIndex, optUb, optLb, prefix + "\t");
						Supplier<Integer> resolveTrueSupplier =
								() -> resolve(lbId, vIndex, optUb, newBound, prefix + "\t");

						XADD.Decision decision = getDecision(ExprLib.CompOperation.LT_EQ, lb, newBound.get());
						lbIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						ubConsistencyId = context.getTermNode(ExprLib.ONE);
						lbIte = resolve(lbId, vIndex, optUb, newBound, prefix + "\t");
					}

					if(optUb.isPresent()) {
						ArithExpr ub = optUb.get();
						lbConsistencyId = comparisonToNodeId(ExprLib.CompOperation.LT_EQ, newBound.get(), optUb.get());

						Supplier<Integer> resolveFalseSupplier =
								() -> resolve(ubId, vIndex, optUb, optLb, prefix + "\t");
						Supplier<Integer> resolveTrueSupplier =
								() -> resolve(ubId, vIndex, newBound, optLb, prefix + "\t");

						XADD.Decision decision = getDecision(ExprLib.CompOperation.GT_EQ, ub, newBound.get());
						ubIte = simplifyIte(decision, resolveTrueSupplier, resolveFalseSupplier, prefix);
					} else {
						lbConsistencyId = context.getTermNode(ExprLib.ONE);
						ubIte = resolve(ubId, vIndex, newBound, optLb, prefix + "\t");
					}

					// Branches
					int ubBranch = context.apply(ubConsistencyId, ubIte, XADD.PROD);
					int lbBranch = context.apply(lbConsistencyId, lbIte, XADD.PROD);

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
					log("Resolved summed: %s + %s = %s", prefix, context.getNode(ubBranch),
							context.getNode(lbBranch), context.getNode(resolved));
					resolveCache.put(key, resolved);
					return resolved;
				} else {
					int low = resolve(internalNode._low, vIndex + 1, Optional.empty(), Optional.empty(), prefix + "\t");
					int high = resolve(internalNode._high, vIndex + 1, Optional.empty(), Optional.empty(), prefix + "\t");
					int summed = context.apply(low, high, XADD.SUM);
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
			int resolved = computeIntegral(terminalNode._expr, variable, type, optUb, optLb);
			log("Terminal node integrated to return %s", prefix, context.getNode(resolved));
			resolveCache.put(key, resolved);
			return resolved;
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

	private int resetReturn(int result) {
		integratedLeafNodes = null;
		resolveCache = null;
		variables = null;
		return result;
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

	private int getLast(Set<String> variables) {
		for(int i = context.getVariableOrder().size() - 1; i >= 0; i--) {
			String variable = context.getVariableOrder().get(i);
			if((this.variables == null || this.variables.contains(variable)) && variables.contains(variable)) {
				return i;
			}
		}
		return -1;
		// throw new IllegalArgumentException(format("Variable set %s did not contain any of the variables %s",
		//		variables, context.getVariableOrder()));
	}
	//endregion
}