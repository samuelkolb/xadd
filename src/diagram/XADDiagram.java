package diagram;

import solving.JOptSolver;
import solving.LinearGLPKSolver;
import pair.TypePair;
import xadd.ExprLib;
import xadd.XADD;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static function.Architect.*;
import static function.Functional.*;
import static java.lang.String.format;

/**
 * The XADDiagram class encapsulates XADD representations and operations.
 *
 * @author Samuel Kolb
 */
public class XADDiagram {

	interface NodeWalkerObserver<S, R> {
		S getInitial();
		R calculate(int nodeId, XADD.XADDTNode node, S state);
		// TODO not clear -> boolean indicator true / not true
		TypePair<S> update(int nodeId, XADD.XADDINode node, S state);
		R combine(R result1, R result2);
	}

	private static class IntegrationObserver implements NodeWalkerObserver<Set<String>, Double> {

		private XADD xadd;
		private Set<String> initial;

		private IntegrationObserver(XADD xadd, Set<String> initial) {
			this.xadd = xadd;
			this.initial = initial;
		}

		@Override
		public Set<String> getInitial() {
			return initial;
		}

		@Override
		public Double calculate(int nodeId, XADD.XADDTNode node, Set<String> state) {
			return Math.pow(2, state.size()) * xadd.evaluate(nodeId, new HashMap<>(), new HashMap<>());
		}

		@Override
		public TypePair<Set<String>> update(int nodeId, XADD.XADDINode node, Set<String> state) {
			Set<String> copy = set(state);
			if(!copy.remove(node.getDecision().toString())) {
				throw new IllegalStateException("Removing non-existing variable");
			}
			return TypePair.make(copy, copy);
		}

		@Override
		public Double combine(Double result1, Double result2) {
			return result1 + result2;
		}
	}

	// Data: number
	public final XADD xadd;
	public final int number;

	public XADDiagram(XADD context, int number) {
		this.xadd = context;
		this.number = number;
	}

	protected XADDiagram xadd(int number) {
		return new XADDiagram(xadd, number);
	}

	/**
	 * Multiplies this diagram with the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram times(XADDiagram diagram) {
		return xadd(xadd.apply(this.number, diagram.number, XADD.PROD));
	}

	/**
	 * Sums this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram plus(XADDiagram diagram) {
		return xadd(xadd.apply(this.number, diagram.number, XADD.SUM));
	}

	/**
	 * Constructs the minimum of this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram min(XADDiagram diagram) {
		return xadd(xadd.apply(this.number, diagram.number, XADD.MIN));
	}

	/**
	 * Constructs the maximum of this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram max(XADDiagram diagram) {
		return xadd(xadd.apply(this.number, diagram.number, XADD.MAX));
	}

	public double evaluate() {
		return evaluate(new HashMap<>(), new HashMap<>());
	}

	public double evaluate(Assignment assignment) {
		return evaluate(assignment.getBooleanVariables(), assignment.getContinuousVariables());
	}

	/**
	 * Evaluates this diagram for a given assignment of variables
	 * @param booleanVariables		The boolean variable assignments
	 * @param continuousVariables	The continuous variable assignments
	 * @return	The result of the evaluation
	 */
	public Double evaluate(Map<String, Boolean> booleanVariables, Map<String, Double> continuousVariables) {
		Double result = xadd.evaluate(number, new HashMap<>(booleanVariables), new HashMap<>(continuousVariables));
		if(result == null) {
			Set<String> found = xadd.collectVars(number);
			Set<String> given = new HashSet<>(booleanVariables.keySet());
			given.addAll(continuousVariables.keySet());
			found.removeAll(given);
			throw new IllegalArgumentException("Not all required variables were assigned, missing: " + found);
		}
		return result;
	}

	public Double integrate(List<String> booleanVariables, List<String> continuousVariables) {
		int boolOnly = fold(this.number, xadd::computeDefiniteIntegral, continuousVariables);
		return xadd(boolOnly).walk(new IntegrationObserver(xadd, set(booleanVariables)));
	}

	public XADDiagram getIntegratedDiagram(List<String> booleanVariables, List<String> continuousVariables) {
		int result = fold(this.number, xadd::computeDefiniteIntegral, continuousVariables);
		for(String booleanVar : booleanVariables) {
			// TODO just double if it does not occur
			int trueBranch = xadd.substituteBoolVars(result, map(booleanVar).to(true));
			int falseBranch = xadd.substituteBoolVars(result, map(booleanVar).to(false));
			result = xadd.apply(trueBranch, falseBranch, XADD.SUM);
		}
		return xadd(result);
	}

	/**
	 * Wrapper for vararg arguments
	 * @param variables	The real variables to eliminate
	 * @return	eliminateRealVars(asList(variables))
	 */
	public XADDiagram eliminateRealVars(String... variables) {
		return eliminateRealVars(Arrays.asList(variables));
	}

	/**
	 * Wrapper for vararg arguments
	 * @param variables	The real variables to eliminate
	 * @return	eliminateRealVars(asList(variables))
	 */
	public XADDiagram eliminateRealVarsSym(String... variables) {
		return eliminateRealVarsSym(Arrays.asList(variables));
	}

	/**
	 * Wrapper for real variables
	 * @param variables	The real variables to eliminate
	 * @return	eliminateVars(variables, [real] * len(variables))
	 */
	public XADDiagram eliminateRealVars(List<String> variables) {
		return eliminateVars(variables, Collections.nCopies(variables.size(), "real"));
	}

	/**
	 * Wrapper for real variables
	 * @param variables	The real variables to eliminate
	 * @return	eliminateVars(variables, [real] * len(variables))
	 */
	public XADDiagram eliminateRealVarsSym(List<String> variables) {
		return eliminateVarsSym(variables, Collections.nCopies(variables.size(), "real"));
	}

	/**
	 * Wrapper for vararg arguments
	 * @param variables	The boolean variables to eliminate
	 * @return	eliminateBoolVars(asList(variables))
	 */
	public XADDiagram eliminateBoolVars(String... variables) {
		return eliminateBoolVars(Arrays.asList(variables));
	}

	/**
	 * Wrapper for boolean variables
	 * @param variables	The boolean variables to eliminate
	 * @return	eliminateVars(variables, [bool] * len(variables))
	 */
	public XADDiagram eliminateBoolVars(List<String> variables) {
		return eliminateVars(variables, Collections.nCopies(variables.size(), "bool"));
	}

	/**
	 * Wrapper for vararg arguments
	 * @param variables	The boolean variables to eliminate
	 * @return	eliminateBoolVars(asList(variables))
	 */
	public XADDiagram eliminateBoolVarsSym(String... variables) {
		return eliminateBoolVarsSym(Arrays.asList(variables));
	}

	/**
	 * Wrapper for boolean variables
	 * @param variables	The boolean variables to eliminate
	 * @return	eliminateVars(variables, [bool] * len(variables))
	 */
	public XADDiagram eliminateBoolVarsSym(List<String> variables) {
		return eliminateVarsSym(variables, Collections.nCopies(variables.size(), "bool"));
	}

	/**
	 * Eliminate the given variables from this diagram and return the resulting diagram (using bound-resolve)
	 * @param variables	The variables to eliminate
	 * @param types	The types of the variables
	 * @return	The diagram in which the variables have been eliminated
	 */
	public XADDiagram eliminateVars(List<String> variables, List<String> types) {
		if(variables.size() != types.size()) {
			throw new IllegalArgumentException(format("Diverging number of variables (%d) and types (%d)",
					variables.size(), types.size()));
		}
		ResolveIntegration integrator = new ResolveIntegration(xadd);
		int result = number;
		for(int i = 0; i < variables.size(); i++) {
			result = integrator.integrate(result, variables.get(i), types.get(i));
			// new XADDiagram(this.xadd, result).show("After " + variables.get(i));
			// result = this.xadd.reduceLP(result);
		}
		return new XADDiagram(xadd, result);
	}

	/**
	 * Eliminate the given variables from this diagram and return the resulting diagram (using bound-resolve)
	 * @param variables	The variables to eliminate
	 * @param types	The types of the variables
	 * @return	The diagram in which the variables have been eliminated
	 */
	public XADDiagram eliminateVarsSym(List<String> variables, List<String> types) {
		if(variables.size() != types.size()) {
			throw new IllegalArgumentException(format("Diverging number of variables (%d) and types (%d)",
					variables.size(), types.size()));
		}
		SymbolicResolveIntegration integrator = new SymbolicResolveIntegration(xadd);
		int result = number;
		for(int i = 0; i < variables.size(); i++) {
			result = integrator.integrate(result, variables.get(i), types.get(i));
			// new XADDiagram(this.xadd, result).show("After " + variables.get(i));
			// result = this.xadd.reduceLP(result);
		}
		return new XADDiagram(xadd, result);
	}

	private Double evaluateIntegration(Set<String> variables, int nodeId) {
		XADD.XADDNode node = xadd.getNode(nodeId);
		if(node instanceof XADD.XADDTNode) {
			return Math.pow(2, variables.size()) * xadd.evaluate(nodeId, new HashMap<>(), new HashMap<>());
		} else if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;
			if(!variables.remove(iNode.getDecision().toString())) {
				throw new IllegalStateException("Removing non-existing variable");
			}
			return evaluateIntegration(set(variables), iNode._high) + evaluateIntegration(set(variables), iNode._low);
		} else {
			throw new IllegalStateException("Unexpected structural error");
		}
	}

	public XADDiagram evaluatePartial(Map<String, Boolean> booleanVariables, Map<String, Double> continuousVariables) {
		int temp = xadd.substituteBoolVars(this.number, new HashMap<>(booleanVariables));
		return xadd(xadd.substitute(temp, map(ExprLib.DoubleExpr::new, continuousVariables)));
		/*XADD.XADDNode node = context.getNode(this.number);
		if(node instanceof XADD.XADDTNode) {
			return node
		}*/
	}

	/*private int shrink(int nodeId, Map<String, Boolean> booleanVariables, Map<String, Double> continuousVariables) {
		XADD.XADDNode node = context.getNode(this.number);
		if(node instanceof XADD.XADDTNode) {
			return nodeId;
		} else if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;

			if(!variables.remove(iNode.getDecision().toString())) {
				throw new IllegalStateException("Removing non-existing variable");
			}
			return evaluateIntegration(set(variables), iNode._high) + evaluateIntegration(set(variables), iNode._low);
		} else {
			throw new IllegalStateException("Unexpected structural error");
		}
		return 0.0;
	}*/

	public XADDiagram reduce() {
		return xadd(xadd.reduce(this.number));
	}

	public XADDiagram reduceLp() {
		return xadd(xadd.reduceLP(this.number));
	}

	public Double maxValue() {
		return xadd.linMaxVal(this.number);
	}

	public Assignment maxArg() {
		//System.out.println(XADDBuild.context.linMaxArg(this.number));
		//OptimizationObserver.ValuedAssignment valuedAssignment = this.walk(new OptimizationObserver(false, this));
		//return valuedAssignment.assignment;
		// return this.walk(new Optimization(false, () -> new LinearGLPKSolver(getContinuous()))).assignment;
		return this.walk(new Optimization(false, () -> new JOptSolver(getContinuous()))).assignment;
	}

	private Assignment.Valued<Double> maxAssignment() {
		return  this.walk(new Optimization(false, () -> new LinearGLPKSolver(getContinuous())));
	}

	/**
	 * Walk this XADD with the given observer
	 * @param observer	The observer
	 * @return	The result as aggregated by the observer
	 */
	public <S, R> R walk(NodeWalkerObserver<S, R> observer) {
		return walk(xadd, this.number, observer, observer.getInitial());
	}

	private static <S, R> R walk(XADD xadd, int nodeId, NodeWalkerObserver<S, R> observer, S state) {
		XADD.XADDNode node = xadd.getNode(nodeId);
		if(node instanceof XADD.XADDTNode) {
			return observer.calculate(nodeId, (XADD.XADDTNode) node, state);
		} else if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;
			TypePair<S> pair = observer.update(nodeId, iNode, state);
			return observer.combine(walk(xadd, iNode._low, observer, pair.one()), walk(xadd, iNode._high, observer, pair.two()));
		} else {
			throw new IllegalStateException("Unexpected structural error");
		}
	}


	/**
	 * Shows this diagram with the given title.
	 * @param title	The given title
	 */
	public void show(String title) {
		xadd.showGraph(this.number, title);
	}

	/**
	 * Exports a graph representation to a file.
	 * @param filename	The name of the file
	 */
	public void exportGraph(String filename) {
		xadd.getGraph(this.number).genDotFile(filename);
	}

	/**
	 * Exports this diagram to a file
	 * @param filename	The name of the file
	 */
	public void export(String filename) {
		xadd.exportXADDToFile(this.number, filename);
	}

	public Set<String> getDiscrete() {
		return walk(new NodeWalkerObserver<HashSet<String>, HashSet<String>>() {
			@Override
			public HashSet<String> getInitial() {
				return new HashSet<>();
			}

			@Override
			public HashSet<String> calculate(int nodeId, XADD.XADDTNode node, HashSet<String> state) {
				return state;
			}

			@Override
			public TypePair<HashSet<String>> update(int nodeId, XADD.XADDINode node, HashSet<String> state) {
				if(node.getDecision() instanceof XADD.BoolDec) {
					node.getDecision().collectVars(state);
				}
				return TypePair.make(state, state);
			}

			@Override
			public HashSet<String> combine(HashSet<String> result1, HashSet<String> result2) {
				result1.addAll(result2);
				return result1;
			}
		});
	}

	public Set<String> getContinuous() {
		return walk(new NodeWalkerObserver<HashSet<String>, HashSet<String>>() {
			@Override
			public HashSet<String> getInitial() {
				return new HashSet<>();
			}

			@Override
			public HashSet<String> calculate(int nodeId, XADD.XADDTNode node, HashSet<String> state) {
				return state;
			}

			@Override
			public TypePair<HashSet<String>> update(int nodeId, XADD.XADDINode node, HashSet<String> state) {
				if(!(node.getDecision() instanceof XADD.BoolDec)) {
					node.getDecision().collectVars(state);
				}
				return TypePair.make(state, state);
			}

			@Override
			public HashSet<String> combine(HashSet<String> result1, HashSet<String> result2) {
				result1.addAll(result2);
				return result1;
			}
		});
	}
}
