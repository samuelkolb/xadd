package algebra;

import com.joptimizer.functions.ConvexMultivariateRealFunction;
import com.joptimizer.functions.LinearMultivariateRealFunction;
import com.joptimizer.optimizers.JOptimizer;
import com.joptimizer.optimizers.OptimizationRequest;
import diagram.Assignment;
import diagram.XADDPath;
import function.Architect;
import scpsolver.constraints.LinearConstraint;
import vector.SafeList;
import xadd.ExprLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Created by samuelkolb on 03/05/16.
 *
 * @author Samuel Kolb
 */
public class JOptSolver implements Solver<Assignment.Valued<Double>> {

	private final SafeList<String> continuousVars;
	private boolean min = true;
	private XADDPath path;
	private List<LinearMultivariateRealFunction> constraints;
	private double[] coefficients;
	private double constant;
	private Converter converter;

	public JOptSolver(Set<String> continuousVars) {
		this.continuousVars = new SafeList<>(continuousVars);
		this.converter = new Converter(this.continuousVars);
	}

	@Override
	public void setPath(XADDPath path) {
		this.path = path;
		this.constraints = new ArrayList<>();
		System.out.println(path);
		System.out.println(path.getDecisions());
		this.path.getDecisions().forEach((e) -> constraints.add(this.converter.getJOptLinearConstraint(e)));
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
		LinearMultivariateRealFunction objectiveFunction = new LinearMultivariateRealFunction(coefficients, constant);
		ConvexMultivariateRealFunction[] constraints =
				this.constraints.toArray(new ConvexMultivariateRealFunction[this.constraints.size()]);
		System.out.println("Coefficients: " + Arrays.toString(coefficients));
		System.out.println("Constraints: " + Arrays.toString(constraints));
		OptimizationRequest or = new OptimizationRequest();
		or.setF0(objectiveFunction);
		or.setFi(constraints);
		or.setToleranceFeas(1.E-9);
		or.setTolerance(1.E-9);

		//optimization
		JOptimizer opt = new JOptimizer();
		opt.setOptimizationRequest(or);
		try {
			int returnCode = opt.optimize();
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
		double[] solution = opt.getOptimizationResponse().getSolution();
		double result = this.converter.getResult(constant, coefficients, solution);
		return this.path.getAssignment(Architect.map(this.continuousVars).to(solution)).value(result);
	}
}
