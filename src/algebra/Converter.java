package algebra;

import com.joptimizer.functions.LinearMultivariateRealFunction;
import scpsolver.constraints.LinearBiggerThanEqualsConstraint;
import scpsolver.constraints.LinearConstraint;
import scpsolver.constraints.LinearEqualsConstraint;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import vector.SafeList;
import xadd.ExprLib;

/**
 * Created by samuelkolb on 02/05/16.
 *
 * @author Samuel Kolb
 */
public class Converter {

	private final SafeList<String> continuousVars;

	Converter(SafeList<String> continuousVars) {
		this.continuousVars = continuousVars;
	}

	/**
	 * Converts the given expression into an SCP linear constraint
	 * @param expr		The expression to convert
	 * @param number	The number of the constraint
	 * @return	A linear constraint
	 */
	public LinearConstraint getSCPLinearConstraint(ExprLib.CompExpr expr, int number) {
		double c = - extractSCPLinearConstraintConstant(expr._lhs, 0);
		double[] coefficients = extractSCPLinearConstraintCoefficients(expr._lhs);
		// TODO Deal with strict / unstrict
		if(expr._type == ExprLib.CompOperation.EQ) {
			return new LinearEqualsConstraint(coefficients, c, "c" + number);
		} else if(expr._type == ExprLib.CompOperation.LT || expr._type == ExprLib.CompOperation.LT_EQ) {
			return new LinearSmallerThanEqualsConstraint(coefficients, c, "c" + number);
		} else {
			return new LinearBiggerThanEqualsConstraint(coefficients, c, "c" + number);
		}
	}

	/**
	 * Converts the given expression into a linear multivariate real function
	 * @param expr	The expression to convert
	 * @return	A linear function
	 */
	public LinearMultivariateRealFunction getJOptLinearConstraint(ExprLib.CompExpr expr) {
		double c = - extractSCPLinearConstraintConstant(expr._lhs, 0);
		double[] coefficients = extractSCPLinearConstraintCoefficients(expr._lhs);
		if(expr._type == ExprLib.CompOperation.EQ) {
			throw new UnsupportedOperationException(); // TODO Unsupported -> A matrix
		} else if(expr._type == ExprLib.CompOperation.LT || expr._type == ExprLib.CompOperation.LT_EQ) {
			return new LinearMultivariateRealFunction(coefficients, c);
		} else {
			for(int i = 0; i < coefficients.length; i++) {
				coefficients[i] = - coefficients[i];
			}
			return new LinearMultivariateRealFunction(coefficients, - c);
		}
	}

	/**
	 * Retrieves the linear coefficients of the given expression
	 * @param expr	The arithmetic expression
	 * @return	The linear coefficients
	 */
	public double[] getLinearCoefficients(ExprLib.ArithExpr expr) {
		double[] coefficients = new double[this.continuousVars.size()];
		getObjectiveCoefficients(coefficients, expr, 1);
		return coefficients;
	}

	/**
	 * Retrieves the constant in the given (linear) expression
	 * @param expr	The arithmetic expression
	 * @return	The constant term
	 */
	public double getLinearConstant(ExprLib.ArithExpr expr) {
		return getConstant(expr, 0);
	}

	/**
	 * Computes the resulting optimization value
	 * @param constant		The constant term
	 * @param coefficients	The variable coefficients
	 * @param values		The variable values
	 * @return	The result
	 */
	public double getResult(double constant, double[] coefficients, double[] values) {
		double result = constant;
		for(int i = 0; i < coefficients.length; i++) {
			result += coefficients[i] * values[i];
		}
		return result;
	}

	private double extractSCPLinearConstraintConstant(ExprLib.ArithExpr expr, double c) {
		if(expr instanceof ExprLib.DoubleExpr) {
			return ((ExprLib.DoubleExpr) expr)._dConstVal;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				double nC = extractSCPLinearConstraintConstant(operExpr._terms.get(0), c);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					nC = extractSCPLinearConstraintConstant(operExpr._terms.get(i), nC);
				}
				return nC;
			}
		}
		return c;
	}

	private double[] extractSCPLinearConstraintCoefficients(ExprLib.ArithExpr expr) {
		double[] coefficients = new double[this.continuousVars.size()];
		extractSCPLinearConstraintCoefficients(expr, coefficients, 1);
		return coefficients;
	}

	private void extractSCPLinearConstraintCoefficients(ExprLib.ArithExpr expr, double[] coefficients, double m) {
		if(expr instanceof ExprLib.VarExpr) {
			coefficients[this.continuousVars.indexOf(((ExprLib.VarExpr) expr)._sVarName)] = m;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				extractSCPLinearConstraintCoefficients(operExpr._terms.get(0), coefficients, m);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					extractSCPLinearConstraintCoefficients(operExpr._terms.get(i), coefficients, m);
				}
			} else if(operExpr._type == ExprLib.ArithOperation.PROD) {
				double nM = ((ExprLib.DoubleExpr) operExpr._terms.get(0))._dConstVal;
				extractSCPLinearConstraintCoefficients(operExpr._terms.get(1), coefficients, nM);
			}
		}
	}

	private void getObjectiveCoefficients(double[] coefficients, ExprLib.ArithExpr expr, double m) {
		if(expr instanceof ExprLib.DoubleExpr) {
			return;
		} else if(expr instanceof ExprLib.VarExpr) {
			coefficients[continuousVars.indexOf(((ExprLib.VarExpr) expr)._sVarName)] = m;
			return;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				getObjectiveCoefficients(coefficients, operExpr._terms.get(0), m);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					getObjectiveCoefficients(coefficients, operExpr._terms.get(i), m);
				}
				return;
			} else if(operExpr._type == ExprLib.ArithOperation.PROD) {
				double nM = ((ExprLib.DoubleExpr) operExpr._terms.get(0))._dConstVal;
				getObjectiveCoefficients(coefficients, operExpr._terms.get(1), nM);
				return;
			}
		}
		throw new RuntimeException("Could not treat " + expr);
	}

	private double getConstant(ExprLib.ArithExpr expr, double c) {
		// TODO clean up
		if(expr instanceof ExprLib.DoubleExpr) {
			return ((ExprLib.DoubleExpr) expr)._dConstVal;
		} else if(expr instanceof ExprLib.VarExpr) {
			return c;
		} else if(expr instanceof ExprLib.OperExpr) {
			ExprLib.OperExpr operExpr = (ExprLib.OperExpr) expr;
			if(operExpr._type == ExprLib.ArithOperation.SUM) {
				double nC = getConstant(operExpr._terms.get(0), c);
				for(int i = 1; i < operExpr._terms.size(); i++) {
					nC = getConstant(operExpr._terms.get(i), nC);
				}
				return nC;
			} else if(operExpr._type == ExprLib.ArithOperation.PROD) {
				return c;
			}
		}
		throw new RuntimeException("Could not treat " + expr);
	}

}
