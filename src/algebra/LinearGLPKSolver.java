package algebra;

import diagram.Assignment;
import diagram.XADDPath;
import function.Architect;
import scpsolver.constraints.LinearConstraint;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;
import vector.SafeList;
import xadd.ExprLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by samuelkolb on 02/05/16.
 *
 * @author Samuel Kolb
 */
public class LinearGLPKSolver implements Solver<Assignment.Valued<Double>> {

	private final SafeList<String> continuousVars;
	private boolean min = true;
	private XADDPath path;
	private List<LinearConstraint> constraints;
	private double[] coefficients;
	private double constant;
	private Converter converter;

	/**
	 * Creates a new solver
	 * @param continuousVars	The continuous variables occurring in the xadd
	 */
	public LinearGLPKSolver(Collection<String> continuousVars) {
		this.continuousVars = new SafeList<>(continuousVars);
		this.converter = new Converter(this.continuousVars);
	}

	protected void addConstraint(ExprLib.CompExpr expr) {
		this.constraints.add(this.converter.getSCPLinearConstraint(expr, this.constraints.size() + 1));
	}

	@Override
	public void setPath(XADDPath path) {
		this.path = path;
		this.constraints = new ArrayList<>();
		path.getDecisions().forEach(this::addConstraint);
	}

	@Override
	public void setObjective(ExprLib.ArithExpr expr) {
		this.coefficients = this.converter.getLinearCoefficients(expr);
		this.constant = this.converter.getLinearConstant(expr);
	}

	@Override
	public void setOpt(Operation operation) {
		if(operation == Operation.MAX) {
			this.min = false;
		} else if(operation == Operation.MIN) {
			this.min = true;
		} else {
			throw new IllegalArgumentException("Unsupported operation: " + operation);
		}
	}

	@Override
	public boolean supports(Operation operation) {
		return operation == Operation.MAX || operation == Operation.MIN;
	}

	@Override
	public Assignment.Valued<Double> solve() {
		LinearProgram lp = new LinearProgram(this.coefficients);
		this.constraints.forEach(lp::addConstraint);
		lp.setMinProblem(this.min);
		double[] values = SolverFactory.newDefault().solve(lp);
		if(values == null) {
			System.err.println("Function: " + Arrays.toString(this.coefficients));
			System.err.println("Constraints: " + this.path.getDecisions());
			throw new RuntimeException();
		} else {
			double result = this.converter.getResult(this.constant, this.coefficients, values);
			System.out.println("Result: " + Arrays.toString(values) + " = " + result);
			return this.path.getAssignment(Architect.map(continuousVars).to(values)).value(result);
		}
	}
}
