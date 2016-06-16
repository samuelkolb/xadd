package diagram;

import xadd.ExprLib;
import xadd.XADD;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a (partial) path through an XADD.
 *
 * @author Samuel Kolb
 */
public class XADDPath {

	protected static class ConstraintState extends XADDPath {
		private final ExprLib.CompExpr decision;

		ConstraintState(XADDPath parent, ExprLib.CompExpr decision) {
			super(parent);
			this.decision = decision;
		}

		@Override
		public void fillDecisions(List<ExprLib.CompExpr> decisions) {
			super.fillDecisions(decisions);
			decisions.add(this.decision);
		}

		@Override
		public String toString() {
			return super.toString() + "[" + decision.toString() + "]";
		}
	}

	protected static class BoolState extends XADDPath {
		private final String name;
		private final boolean bool;

		BoolState(XADDPath parent, String name, boolean bool) {
			super(parent);
			this.name = name;
			this.bool = bool;
		}

		@Override
		public void fillAssignments(Map<String, Boolean> map) {
			super.fillAssignments(map);
			map.put(name, bool);
		}

		@Override
		public String toString() {
			return super.toString() + "[" + (bool ? "" : "not ") + name + "]";
		}
	}

	private final Optional<XADDPath> parent;

	/**
	 * Creates an empty path.
	 */
	public XADDPath() {
		this.parent = Optional.empty();
	}

	protected XADDPath(XADDPath parent) {
		this.parent = Optional.of(parent);
	}

	protected void fillDecisions(List<ExprLib.CompExpr> decisions) {
		if(this.parent.isPresent()) {
			this.parent.get().fillDecisions(decisions);
		}
	}

	protected void fillAssignments(Map<String, Boolean> booleans) {
		if(this.parent.isPresent()) {
			this.parent.get().fillAssignments(booleans);
		}
	}

	/**
	 * Obtain a list of decisions (comparative expressions)
	 * @return	The list
	 */
	public List<ExprLib.CompExpr> getDecisions() {
		List<ExprLib.CompExpr> list = new ArrayList<>();
		fillDecisions(list);
		return list;
	}

	/**
	 * Combine this path with an assignment to continuous variables
	 * @param doubles	The assignment values for continuous variables
	 * @return	A combined assignment that takes into account the boolean decisions made on this path
	 */
	public Assignment getAssignment(Map<String, Double> doubles) {
		HashMap<String, Boolean> map = new HashMap<>();
		fillAssignments(map);
		return new Assignment(map, doubles);
	}

	/**
	 * Extend the path with the given node and the branch to follow
	 * @param node		The node to expand
	 * @param isTrue	What branch to follow (isTrue => high)
	 * @return	An extended path
	 */
	public XADDPath extend(XADD.XADDINode node, boolean isTrue) {
		if(node.getDecision() instanceof XADD.BoolDec) {
			String var = ((XADD.BoolDec) node.getDecision())._sVarName;
			return new BoolState(this, var, isTrue);
		} else if(node.getDecision() instanceof XADD.ExprDec) {
			ExprLib.CompExpr expr = ((XADD.ExprDec) node.getDecision())._expr;
			if(!isTrue) {
				expr = new ExprLib.CompExpr(ExprLib.CompExpr.flipCompOper(expr._type), expr._lhs, expr._rhs);
			}
			return new ConstraintState(this, expr);
		}
		throw new RuntimeException("Could not treat " + node.getDecision());
	}

	@Override
	public String toString() {
		if(!this.parent.isPresent()) {
			return "root";
		}
		return this.parent.get().toString() + " -> ";
	}
}
