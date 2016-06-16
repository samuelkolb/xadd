package diagram;

import algebra.JOptSolver;
import algebra.LinearGLPKSolver;
import pair.TypePair;
import xadd.ExprLib;
import xadd.XADD;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static function.Architect.*;
import static function.Functional.*;

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

		private Set<String> initial;

		private IntegrationObserver(Set<String> initial) {
			this.initial = initial;
		}

		@Override
		public Set<String> getInitial() {
			return initial;
		}

		@Override
		public Double calculate(int nodeId, XADD.XADDTNode node, Set<String> state) {
			return Math.pow(2, state.size()) * XADDBuild.context.evaluate(nodeId, new HashMap<>(), new HashMap<>());
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
	protected int number;

	protected XADDiagram(int number) {
		this.number = number;
	}

	/**
	 * Multiplies this diagram with the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram times(XADDiagram diagram) {
		return new XADDiagram(XADDBuild.context.apply(this.number, diagram.number, XADD.PROD));
	}

	/**
	 * Sums this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram plus(XADDiagram diagram) {
		return new XADDiagram(XADDBuild.context.apply(this.number, diagram.number, XADD.SUM));
	}

	/**
	 * Constructs the minimum of this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram min(XADDiagram diagram) {
		return new XADDiagram(XADDBuild.context.apply(this.number, diagram.number, XADD.MIN));
	}

	/**
	 * Constructs the maximum of this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public XADDiagram max(XADDiagram diagram) {
		return new XADDiagram(XADDBuild.context.apply(this.number, diagram.number, XADD.MAX));
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
		return XADDBuild.context.evaluate(this.number, new HashMap<>(booleanVariables), new HashMap<>(continuousVariables));
	}

	public Double integrate(List<String> booleanVariables, List<String> continuousVariables) {
		int boolOnly = fold(this.number, XADDBuild.context::computeDefiniteIntegral, continuousVariables);
		new XADDiagram(boolOnly).show("bool only");
		return new XADDiagram(boolOnly).walk(new IntegrationObserver(set(booleanVariables)));
	}

	private Double evaluateIntegration(Set<String> variables, int nodeId) {

		XADD.XADDNode node = XADDBuild.context.getNode(nodeId);
		if(node instanceof XADD.XADDTNode) {
			return Math.pow(2, variables.size()) * XADDBuild.context.evaluate(nodeId, new HashMap<>(), new HashMap<>());
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
		int temp = XADDBuild.context.substituteBoolVars(this.number, new HashMap<>(booleanVariables));
		return new XADDiagram(XADDBuild.context.substitute(temp, map(ExprLib.DoubleExpr::new, continuousVariables)));
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
		return new XADDiagram(XADDBuild.context.reduce(this.number));
	}

	public Assignment maxArg() {
		//System.out.println(XADDBuild.context.linMaxArg(this.number));
		//OptimizationObserver.ValuedAssignment valuedAssignment = this.walk(new OptimizationObserver(false, this));
		//return valuedAssignment.assignment;
		// return this.walk(new Optimization(false, () -> new LinearGLPKSolver(getContinuous()))).assignment;
		return this.walk(new Optimization(false, () -> new JOptSolver(getContinuous()))).assignment;
	}

	/**
	 * Walk this XADD with the given observer
	 * @param observer	The observer
	 * @return	The result as aggregated by the observer
	 */
	public <S, R> R walk(NodeWalkerObserver<S, R> observer) {
		return walk(this.number, observer, observer.getInitial());
	}

	private static <S, R> R walk(int nodeId, NodeWalkerObserver<S, R> observer, S state) {
		XADD.XADDNode node = XADDBuild.context.getNode(nodeId);
		if(node instanceof XADD.XADDTNode) {
			return observer.calculate(nodeId, (XADD.XADDTNode) node, state);
		} else if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;
			TypePair<S> pair = observer.update(nodeId, iNode, state);
			return observer.combine(walk(iNode._low, observer, pair.one()), walk(iNode._high, observer, pair.two()));
		} else {
			throw new IllegalStateException("Unexpected structural error");
		}
	}


	/**
	 * Shows this diagram with the given title.
	 * @param title	The given title
	 */
	public void show(String title) {
		XADDBuild.context.showGraph(this.number, title);
	}

	/**
	 * Exports this diagram to a file
	 * @param filename	The name of the file
	 */
	public void export(String filename) {
		XADDBuild.context.exportXADDToFile(this.number, filename);
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
