package scenario;

import com.sun.org.apache.xpath.internal.operations.Bool;
import diagram.Assignment;
import diagram.BoolXADD;
import diagram.ResolveIntegration;
import diagram.XADDBuild;
import diagram.XADDParser;
import diagram.XADDParserKt;
import diagram.XADDiagram;
import function.Architect;
import gurobi.*;
import junit.framework.Assert;
import org.junit.Test;
import time.Stopwatch;
import xadd.XADD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static diagram.XADDBuild.bool;
import static diagram.XADDBuild.test;
import static diagram.XADDBuild.val;
import static function.Architect.map;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * Created by samuelkolb on 09/06/2017.
 *
 * @author Samuel Kolb
 */
public class TestResolveIntegration {
	private static final double DELTA = 0.001;

	//region Variables

	//endregion

	//region Construction

	//endregion

	//region Public methods
	@Test
	public void testDiagram1() {
		/*
		bounds = b.test("x", ">=", 0) & b.test("x", "<=", 1)
        bounds &= b.test("y", ">=", 1) & b.test("y", "<=", 3)
        two = b.test("x", ">=", "y")
        d = b.ite(bounds, b.ite(two, b.terminal("x"), b.terminal("10")), b.terminal(0))
		 */

		BoolXADD bounds = test("x >= 0").and(test("x <= 1")).and(test("y >= 1"))
				.and(test("y <= 3"));
		BoolXADD both = test("x >= y");
		XADDiagram diagram = bounds.assignWeights(both.assignWeights(val("x"), val(10)), val(0));
		// diagram.show("Diagram");

		XADDiagram integrated = diagram.eliminateRealVars(singletonList("x"));
		// integrated.show("Integrated");

		XADDiagram control = diagram.getIntegratedDiagram(emptyList(), Collections.singletonList("x"));
		// control.show("Control");

		System.out.println("Controlling results:");
		for(double y = -1; y <= 5; y += 0.2) {
			Double resolvedResult = integrated.evaluate(new HashMap<>(), map("y").to(y));
			Double controlResult = control.evaluate(new HashMap<>(), map("y").to(y));
			if(Math.abs(resolvedResult - controlResult) > DELTA) {
				System.out.println(format("Error [y=%.2f], expected %.2f but got %.2f", y, controlResult,resolvedResult));
			} else {
				System.out.println(format("Success [y=%.2f], both results are %.2f", y, controlResult));
			}
		}
	}

	@Test
	public void testDiagram2() {
		BoolXADD test_2 = test("x <= 2"),
			test_5 = test("x <= 5"),
			test_10 = test("x <= 10"),
			test_16 = test("x <= 16"),
			test_9 = test("x <= 9"),
			test_21 = test("x <= 21"),
			test_28 = test("x <= 28"),
			test_964 = test("x <= 964");

		// double val_1 = 2616157026.45477;
		double val_1 = 13.17;
		XADDiagram leaf_1 = val(val_1);
		// double val_2 = 2615431790.14078;
		double val_2 = 19.23;
		XADDiagram leaf_2 = val(val_2);

		XADDiagram path_1 = test_2.not().and(test_5.not()).and(test_10.not()).and(test_16.not()).and(test_9.not())
				.and(test_21).and(test_28).and(test_964).times(leaf_1);
		XADDiagram path_2 = test_2.not().and(test_5.not()).and(test_10.not()).and(test_16).and(test_9.not())
				.and(test_21).and(test_28).and(test_964).times(leaf_2);

		XADDiagram test_diagram = path_1.plus(path_2);
		compareResults(test_diagram, emptyList(), singletonList("x"));
	}

	@Test
	public void testDiagram2Simplified() {
		BoolXADD test_2 = test("x <= 2"),
				test_10 = test("x <= 10"),
				test_16 = test("x <= 16"),
				test_9 = test("x <= 9"),
				test_21 = test("x <= 21");

		// double val_1 = 2616157026.45477;
		double val_1 = 13.17;
		XADDiagram leaf_1 = val(val_1);
		// double val_2 = 2615431790.14078;
		double val_2 = 19.23;
		XADDiagram leaf_2 = val(val_2);

		XADDiagram path_1 = test_2.not().and(test_10.not()).and(test_16.not()).and(test_9.not()).and(test_21)
				.times(leaf_1);
		XADDiagram path_2 = test_2.not().and(test_10.not()).and(test_16).and(test_9.not()).and(test_21).times(leaf_2);

		XADDiagram test_diagram = path_1.plus(path_2);
		// test_diagram.show("Test Diagram");
		double result = 181.23;
		Assert.assertEquals(result, compareResults(test_diagram, emptyList(), singletonList("x")), DELTA);
	}

	@Test
	public void testDiagram3() {
		// (A OR B) AND NOT (A AND B) AND (NOT A OR X < 3) AND (NOT B OR X < 7) AND (0 <= X) AND (X <= 10)
		// w(A) = X, w(NOT A) = 2
		// w(B) = 3X, w(NOT B) = 4X
		BoolXADD a = bool("a"), b = bool("b"), x3 = test("x < 3"), x7 = test("x < 7"),
				x0 = test("0 <= x"), x10 = test("x <= 10"),
				f = a.or(b).and(a.and(b).not()).and(a.not().or(x3)).and(b.not().or(x7)).and(x0).and(x10);
		XADDiagram w1 = a.assignWeights(val("x"), val(2)),
				w2 = b.assignWeights(val("3*x"), val("4*x"));

		XADDiagram total = f.applyWeights(w1, w2);
		Assert.assertEquals(183, compareResults(total, asList("a", "b"), singletonList("x")), DELTA);
	}

	@Test
	public void testDiagram3Import() {
		String wmi = "(* (& (| (var bool A) (var bool B)) (~ (& (var bool A) (var bool B))) (| (~ (var bool A)) (< (var real x) (const real 3.0))) (| (~ (var bool B)) (< (var real x) (const real 7.0))) (<= (const real 0.0) (var real x)) (<= (var real x) (const real 10.0))) (ite (var bool A) (var real x) (const real 2.0)) (ite (var bool B) (* (var real x) (const real 3.0)) (* (var real x) (const real 4.0))))".toLowerCase();
		XADDiagram diagram = new XADDParser(new XADD()).parseXadd(wmi);
		Assert.assertEquals(183, compareResults(diagram, asList("a", "b"), singletonList("x")), DELTA);
	}

	@Test
	public void testUnusedBooleanVar() {
		// XADDiagram diagram = test("0 <= x").and(test("x <= 1"));
		final double value = 2;
		XADDiagram diagram = val(value);
		Assert.assertEquals(value * 2, compareResults(diagram, singletonList("b"), emptyList()));
	}

	@Test
	public void testXorAll() {
		final int n = 4;
		XADDiagram xor = Example7.buildXorSymbolic(n);
		List<String> variables = new ArrayList<>();
		for(int i = n ; i >= 1; i--) {
			variables.add("c" + i);
		}
		variables.add("c");
		variables.add("x");
		compareResults(xor, emptyList(), variables);
	}

	@Test
	public void testWmi1() {
		XADDBuild.Builder b = new XADDBuild.Builder(new XADD());
		BoolXADD bounds = b.test("x > 0")
				.and(b.test("x < 100"))
				.and(b.test("(1 + (0.07692308 * x_1)) > 0"))
				.and(b.test("(1 + (-0.01176471 * x_1)) > 0"))
				.and(b.test("(1 + (0.01298701 * x_2)) > 0"))
				.and(b.test("(1 + (-0.04 * x_2)) > 0"))
				.and(b.test("(1 + (0.02 * x_3)) > 0"))
				.and(b.test("(1 + (-0.02222222 * x_3)) > 0"));

		BoolXADD a1 = b.bool("a_1");
		BoolXADD a2 = b.bool("a_2");
		BoolXADD t1 = b.test("(-1 + (-3 * x_1) + (-2 * x_3)) > 0");
		BoolXADD t2 = b.test("(1 + (-1.33333333 * x_2)) > 0");
		BoolXADD t3 = b.test("(-1 + (1.8 * x_0) + (-0.2 * x_1) + (1.4 * x_2) + (1.4 * x_3)) > 0");
		XADDiagram v1 = b.val("(2 * x_1 * x_1)");
		XADDiagram v2 = b.val("((40 * x_0 * x_0 * x_0 * x_0 * x_1) + (-4 * x_0 * x_0 * x_1 * x_2 * x_2))");

		XADDiagram s1 = t3.assignWeights(v2, v1);
		XADDiagram s2 = t2.assignWeights(v2, s1);
		XADDiagram s3 = t1.assignWeights(s2, s1);
		XADDiagram s4 = a2.assignWeights(v2, s1);
		XADDiagram s5 = a2.assignWeights(v2, s3);
		XADDiagram s6 = a1.assignWeights(s5, s4);

		XADDiagram d = bounds.times(s6);
		// d.exportGraph("wmi_1.dot");

		int resolved = d.number;
		for(String var : Arrays.asList("x_1", "x_2", "x_3", "a_0", "a_1", "a_2", "a_3")) {
			System.out.println("Eliminate " + var);
			resolved = new ResolveIntegration(d.xadd).integrate(resolved, var, var.startsWith("x") ? "real" : "bool");
			resolved = d.xadd.reduceLP(resolved);
			new XADDiagram(d.xadd, resolved).exportGraph("wmi_after_" + var + ".dot");
		}
	}

	private double compareResults(XADDiagram diagram, List<String> boolVars, List<String> realVars) {
		// Test path enumeration approach
		Stopwatch timer = new Stopwatch(true);
		XADDiagram integratedTraditional = diagram.getIntegratedDiagram(boolVars, realVars);
		double timeTraditional = timer.stop();
		double resultTraditional = integratedTraditional.evaluate(new Assignment());

		// Test bound resolve approach
		timer.start();
		XADDiagram integratedBr = diagram.eliminateBoolVars(boolVars).eliminateRealVars(realVars);
		double timeResolve = timer.stop();
		double resultResolve = integratedBr.evaluate(new Assignment());

		System.out.println(format("Time taken %.2f (traditional) vs %.2f (resolve)", timeTraditional, timeResolve));
		System.out.println(format("Obtained %.2f (traditional) and %.2f (resolve)", resultTraditional, resultResolve));
		Assert.assertEquals(resultTraditional, resultResolve, DELTA);
		return resultTraditional;
	}

	@Test
	public void gurobi() {
		try {
			GRBEnv env   = new GRBEnv();
			env.set(GRB.IntParam.OutputFlag, 0);

			GRBModel model = new GRBModel(env);

			// Create variables

			GRBVar x = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x");
			GRBVar y = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y");
			GRBVar z = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "z");

			// Set objective: maximize x + y + 2 z

			GRBLinExpr expr = new GRBLinExpr();
			expr.addTerm(1.0, x); expr.addTerm(1.0, y); expr.addTerm(2.0, z);
			model.setObjective(expr, GRB.MAXIMIZE);

			// Add constraint: x + 2 y + 3 z <= 4

			expr = new GRBLinExpr();
			expr.addTerm(1.0, x); expr.addTerm(2.0, y); expr.addTerm(3.0, z);
			model.addConstr(expr, GRB.LESS_EQUAL, 4.0, "c0");

			// Add constraint: x + y >= 1

			expr = new GRBLinExpr();
			expr.addTerm(1.0, x); expr.addTerm(1.0, y);
			model.addConstr(expr, GRB.GREATER_EQUAL, 1.0, "c1");

			// Optimize model

			model.optimize();

			System.out.println(x.get(GRB.StringAttr.VarName)
					+ " " +x.get(GRB.DoubleAttr.X));
			System.out.println(y.get(GRB.StringAttr.VarName)
					+ " " +y.get(GRB.DoubleAttr.X));
			System.out.println(z.get(GRB.StringAttr.VarName)
					+ " " +z.get(GRB.DoubleAttr.X));

			System.out.println("Obj: " + model.get(GRB.DoubleAttr.ObjVal));

			// Dispose of model and environment

			model.dispose();
			env.dispose();

		} catch (GRBException e) {
			System.out.println("Error code: " + e.getErrorCode() + ". " +
					e.getMessage());
		}
	}

	public static void main(String[] args) {
		new TestResolveIntegration().testDiagram2Simplified();
	}
	//endregion
}
