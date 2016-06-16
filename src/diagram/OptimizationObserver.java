package diagram;

import basic.MathUtil;
import function.Functional;
import math.Range;
import pair.TypePair;
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearConstraint;
import scpsolver.constraints.LinearEqualsConstraint;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;
import xadd.ExprLib;
import xadd.XADD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static function.Architect.map;

/**
 * Created by samuelkolb on 20/04/16.
 *
 * Future work:
 * Only recompute LP when critical constraints have been removed or new constraints forbid current solution
 *
 * @author Samuel Kolb
 */
public class OptimizationObserver implements XADDiagram.NodeWalkerObserver<OptimizationObserver.OptimizationState,
		OptimizationObserver.ValuedAssignment> {

	// TODO -> Strict inequalities
	// TODO -> QP
	// TODO -> Toussaints geometric
	// TODO -> set of SDDs -> XADD

	private final Map<String, Integer> vars;
	private final List<String> continuous;
	private int counter = 1;
	private final Map<String, String> constraintPrintMap = new HashMap<>();

	abstract class OptimizationState {
		List<LinearConstraint> getConstraints() {
			List<LinearConstraint> list = new ArrayList<>();
			getConstraints(list);
			return list;
		}

		abstract void getConstraints(List<LinearConstraint> list);

		Assignment getAssignment(double[] values) {
			HashMap<String, Boolean> map = new HashMap<>();
			getAssignment(map);
			HashMap<String, Double> cMap = new HashMap<>();
			for(int i = 0; i < values.length; i++) {
				cMap.put(continuous.get(i), values[i]);
			}
			return new Assignment(map, cMap);
		}

		abstract void getAssignment(Map<String, Boolean> map);
	}

	class RootState extends OptimizationState {
		@Override
		public void getConstraints(List<LinearConstraint> list) {

		}

		@Override
		public void getAssignment(Map<String, Boolean> map) {

		}
	}

	class ChildState extends OptimizationState {
		private final OptimizationState parent;

		ChildState(OptimizationState parent) {this.parent = parent;}

		@Override
		public void getConstraints(List<LinearConstraint> list) {
			parent.getConstraints(list);
		}

		@Override
		public void getAssignment(Map<String, Boolean> map) {
			this.parent.getAssignment(map);
		}
	}

	class ConstraintState extends ChildState {
		private final LinearConstraint constraint;

		ConstraintState(OptimizationState parent, LinearConstraint constraint) {
			super(parent);
			this.constraint = constraint;}

		@Override
		public void getConstraints(List<LinearConstraint> list) {
			super.getConstraints(list);
			list.add(this.constraint);
		}
	}

	class BoolState extends ChildState {
		private final String name;
		private final boolean bool;

		BoolState(OptimizationState parent, String name, boolean bool) {
			super(parent);
			this.name = name;
			this.bool = bool;
		}

		@Override
		public void getAssignment(Map<String, Boolean> map) {
			super.getAssignment(map);
			map.put(name, bool);
		}
	}
		/*private HashMap<String, Boolean> assignments = new HashMap<>();
		public HashMap<String, Boolean> getAssignments() {
			HashMap<String, Boolean> discrete = new HashMap<>(this.assignments);
			if(this.previous.isPresent()) {
				discrete.putAll(this.previous.get().getAssignments());
			}
			return discrete;
		}

		private List<LinearConstraint> constraints;
		public List<LinearConstraint> getConstraints() {
			List<LinearConstraint> constraints = new ArrayList<>(this.constraints);
			if(this.previous.isPresent()) {
				constraints.addAll(this.previous.get().constraints);
			}
			return constraints;
		}

		public final Optional<OptimizationState> previous;

		public OptimizationState() {
			this.previous = Optional.empty();
		}

		public OptimizationState(OptimizationState previous) {
			this.previous = Optional.of(previous);
		}

		public void addDiscrete(String name, Boolean value) {
			this.assignments.put(name, value);
		}

		public void addConstraint(LinearConstraint constraint) {
			this.constraints.add(constraint);
		}
	}*/

	static class ValuedAssignment {
		public final Assignment assignment;
		public final double value;

		public ValuedAssignment(Assignment assignment, double value) {
			this.assignment = assignment;
			this.value = value;
		}
	}

	private final boolean minimize;

	public OptimizationObserver(boolean minimize, XADDiagram diagram) {
		this.minimize = minimize;
		this.continuous = new ArrayList<>(diagram.getContinuous());
		this.vars = map(this.continuous).to(Range.integerRange(0, this.continuous.size() - 1).getAll());
	}

	@Override
	public OptimizationObserver.OptimizationState getInitial() {
		return new RootState();
	}

	@Override
	public OptimizationObserver.ValuedAssignment calculate(int nodeId, XADD.XADDTNode node, OptimizationState state) {
		System.out.println();
		ExprLib.ArithExpr expr = node._expr;
		System.out.println(expr);
		double[] coefficients = new double[this.continuous.size()];
		double constant = build(coefficients, expr, 0, 1); // TODO auto
		System.out.println(constant + " " + Arrays.toString(coefficients));
		LinearProgram lp = new LinearProgram(coefficients);
		state.getConstraints().forEach(lp::addConstraint);
		state.getConstraints().forEach(c -> System.out.println(constraintPrintMap.get(c.getName())));
		lp.setMinProblem(this.minimize);
		double[] values = SolverFactory.newDefault().solve(lp);
		double result = constant;
		for(int i = 0; i < coefficients.length; i++) {
			result += coefficients[i] * values[i];
		}
		System.out.println("Result: " + Arrays.toString(values) + " = " + result);
		return new ValuedAssignment(state.getAssignment(values), result);
	}

	private double build(double[] coefficients, ExprLib.ArithExpr expr, double c, double m) {
		if(expr instanceof ExprLib.DoubleExpr) {
			return ((ExprLib.DoubleExpr) expr)._dConstVal;
		} else if(expr instanceof ExprLib.VarExpr) {
			coefficients[vars.get(((ExprLib.VarExpr) expr)._sVarName)] = m;
			return c;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				double nC = build(coefficients, operExpr._terms.get(0), c, m);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					nC = build(coefficients, operExpr._terms.get(i), nC, m);
				}
				return nC;
			} else if(operExpr._type == ExprLib.ArithOperation.PROD) {
				double nM = ((ExprLib.DoubleExpr) operExpr._terms.get(0))._dConstVal;
				return build(coefficients, operExpr._terms.get(1), c, nM);
			}
		}
		throw new RuntimeException("Could not treat " + expr);
	}

	@Override
	public TypePair<OptimizationState> update(int nodeId, XADD.XADDINode node, OptimizationState state) {
		if(node.getDecision() instanceof XADD.BoolDec) {
			String var = ((XADD.BoolDec) node.getDecision())._sVarName;
			return TypePair.make(new BoolState(state, var, false), new BoolState(state, var, true));
		} else if(node.getDecision() instanceof XADD.ExprDec) {
			ExprLib.CompExpr expr = ((XADD.ExprDec) node.getDecision())._expr;
			ExprLib.CompExpr inverted = new ExprLib.CompExpr(ExprLib.CompExpr.flipCompOper(expr._type), expr._lhs, expr._rhs);
			return TypePair.make(new ConstraintState(state, exprToConstraint(inverted)),
					new ConstraintState(state, exprToConstraint(expr)));
		}
		throw new RuntimeException("Could not treat " + node.getDecision());
	}

	private LinearConstraint exprToConstraint(ExprLib.CompExpr expr) {
		double c = - extractConstant(expr._lhs, 0);
		double[] coefficients = extractCoefficients(expr._lhs);
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < coefficients.length; i++) {
			builder.append(coefficients[i]).append(continuous.get(i)).append(" ");
		}
		builder.append(expr._type).append(" ").append(c);
		constraintPrintMap.put("c" + counter, builder.toString());
		// TODO Deal with strict / unstrict
		if(expr._type == ExprLib.CompOperation.EQ) {
			return new LinearEqualsConstraint(coefficients, c, "c" + (counter++));
		} else if(expr._type == ExprLib.CompOperation.LT || expr._type == ExprLib.CompOperation.LT_EQ) {
			return new LinearSmallerThanEqualsConstraint(coefficients, c, "c" + (counter++));
		} else {
			return new LinearBiggerThanEqualsConstraint(coefficients, c, "c" + (counter++));
		}
	}

	private double extractConstant(ExprLib.ArithExpr expr, double c) {
		if(expr instanceof ExprLib.DoubleExpr) {
			return ((ExprLib.DoubleExpr) expr)._dConstVal;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				double nC = extractConstant(operExpr._terms.get(0), c);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					nC = extractConstant(operExpr._terms.get(i), nC);
				}
				return nC;
			}
		}
		return c;
	}

	private double[] extractCoefficients(ExprLib.ArithExpr expr) {
		double[] coefficients = new double[this.continuous.size()];
		extractCoefficients(expr, coefficients, 1);
		return coefficients;
	}

	private void extractCoefficients(ExprLib.ArithExpr expr, double[] coefficients, double m) {
		if(expr instanceof ExprLib.VarExpr) {
			coefficients[vars.get(((ExprLib.VarExpr) expr)._sVarName)] = m;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				extractCoefficients(operExpr._terms.get(0), coefficients, m);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					extractCoefficients(operExpr._terms.get(i), coefficients, m);
				}
			} else if(operExpr._type == ExprLib.ArithOperation.PROD) {
				double nM = ((ExprLib.DoubleExpr) operExpr._terms.get(0))._dConstVal;
				extractCoefficients(operExpr._terms.get(1), coefficients, nM);
			}
		}
	}

	@Override
	public ValuedAssignment combine(ValuedAssignment result1, ValuedAssignment result2) {
		if(minimize) {
			return result1.value <= result2.value ? result1 : result2;
		} else {
			return result1.value >= result2.value ? result1 : result2;
		}
	}
}
