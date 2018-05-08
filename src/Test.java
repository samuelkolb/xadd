import log.Suppress;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.GLPKSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

import java.util.Arrays;

/**
 * Created by samuelkolb on 11/03/16.
 *
 * @author Samuel Kolb
 */
public class Test {

	public static void main(String[] args) throws Exception {
		test();
	}

	public static String something() {
		System.out.println("Hello?");
		return "";
	}
	public static void test() {
		LinearProgram lp = new LinearProgram(new double[]{1.0, 1.0});
		lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{2, 1}, 2, "c1"));
		lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{0.5, 1}, 1, "c2"));
		lp.addConstraint(new LinearSmallerThanEqualsConstraint(new double[]{1, 0}, 0.6, "c3"));
		lp.setMinProblem(false);
		GLPKSolver solver = (GLPKSolver) SolverFactory.newDefault();
		double[] result = Suppress.call(() -> solver.solve(lp));
		double[] result2 = Suppress.call(() -> solver.solve(lp));
		System.out.println(Arrays.toString(result));
		// Test XADD substitution and max


		// int

		/*int theoryKey = context.buildCanonicalXADDFromString(
				"(a" +
				"	([x >= 0]" +
				"		([x < 1]" +
				"			([1])" +
				"			([x < 2]" +
				"				([1])" +
				"				([0])" +
				"			)" +
				"		)" +
				"		([0])" +
				"	)" +
				"	([x >= 1]" +
				"		([x < 2]" +
				"			([1])" +
				"			([0])" +
				"		)" +
				"		([0])" +
				"	)" +
				")");

		int theory1Key = context.buildCanonicalXADDFromString(
				"(a ([x >= 0] ([x < 1] ([1]) ([0])) ([0])) ([0]))"
		);

		int theory2Key = context.buildCanonicalXADDFromString(
				"([x >= 0] ([x < 2] ([1]) ([0])) ([0]))"
		);


		int theorySKey = context.apply(context.apply(theory1Key, theory2Key, XADD.SUM), const1Key, XADD.MIN);

		int weight1Key = context.buildCanonicalXADDFromString(
				"(a" +
				"	([2])" +
				"	([x])" +
				")"
		);

		int weight2Key = context.buildCanonicalXADDFromString(
				"([x >= 0]" +
				"	([x < 1]" +
				"		([2])" +
				"		([x < 2]" +
				"			([x])" +
				"			([0])" +
				"		)" +
				"	)" +
				"	([0])" +
				")"
		);

		//int weightsKey = context.apply(weight1Key, weight2Key, XADD.PROD);
		int combinedKey = context.apply(context.apply(theoryKey, weight1Key, XADD.PROD), weight2Key, XADD.PROD);
		//context.showGraph(weightsKey, "weights");

		//context.showGraph(theoryKey, "theory");
		//context.showGraph(theorySKey, "theory S");
		//context.showGraph(weight1Key, "weight1");
		//context.showGraph(weight2Key, "weight2");
		//context.showGraph(combinedKey, "combined");

 		// TODO Check out every boolean variable? a = true + false ...?
		//context.showGraph(context.computeDefiniteIntegral(combinedKey, "x"), "Integral");*/
	}
}
