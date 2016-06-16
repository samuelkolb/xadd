package algebra;

import diagram.XADDPath;
import xadd.ExprLib;

/**
 * Abstraction for solvers for various tasks
 *
 * @author Samuel Kolb
 */
public interface Solver<R> {

	enum Operation {
		MIN, MAX;
	}

	/**
	 * Set the xadd path
	 * @param path	The xadd path
	 */
	void setPath(XADDPath path);

	/**
	 * Set the objective of the solver
	 * @param expr	The expression
	 */
	void setObjective(ExprLib.ArithExpr expr);

	void setOpt(Operation operation);

	/**
	 * Returns whether this solver supports the given operation
	 * @param operation	The operation
	 * @return	True iff the given operation is supported
	 */
	boolean supports(Operation operation);

	/**
	 * Solve the created problem
	 * @return	The return value
	 */
	R solve();
}
