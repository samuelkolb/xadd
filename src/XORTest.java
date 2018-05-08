
import diagram.Assignment;
import diagram.BoolXADD;
import diagram.OrderedXADD;
import diagram.ResolveAllIntegration;
import diagram.ResolveIntegration;
import diagram.XADDBuild;
import diagram.XADDiagram;
import time.Stopwatch;
import xadd.XADD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Collections.singletonList;

/**
 * Created by samuelkolb on 26/04/16.
 *
 * @author Samuel Kolb
 */
public class XORTest {

	private static XADDBuild.Builder builder;

	public static XADDiagram buildXorSymbolic(int n) {
		return buildXor(n, i -> builder.test("x <= c" + i),
				(i, bounds) -> bounds.and(builder.test("c" + i + " <= 10")).and(builder.test("c" + i + " >= 0")));

	}

	public static XADDiagram buildXorNumeric(int n) {
		long seed = System.currentTimeMillis();
		System.out.println("Seed: " + seed);
		Random random = new Random(seed);
		return buildXor(n, i -> builder.test("x <= " + 1 + random.nextInt(9)), (i, bounds) -> bounds);
	}

	private static XADDiagram buildXor(int n, Function<Integer, BoolXADD> testProducer,
									   BiFunction<Integer, BoolXADD, BoolXADD> boundsProducer) {
		BoolXADD[] tests = new BoolXADD[n + 1];
		BoolXADD bounds = builder.test("x <= 10").and(builder.test("x >= 0"));
		bounds = bounds.and(builder.test("c <= 10").and(builder.test("c >= 0")));

		for(int i = 0; i < n; i++) {
			int index = i + 1;
			bounds = boundsProducer.apply(index, bounds);
		}

		tests[0] = builder.test("x <= c");

		for(int i = 0; i < n; i++) {
			int index = i + 1;
			tests[index] = testProducer.apply(index);
		}

		XADDiagram leaf_1 = builder.val(13);
		XADDiagram leaf_2 = builder.val(14);

		XADDiagram path_1 = leaf_1;
		XADDiagram path_2 = leaf_2;

		for(int i = 0; i < n; i++) {
			int index = n - i;
			BoolXADD current_test = tests[index];
			XADDiagram oldPath1 = path_1;
			XADDiagram oldPath2 = path_2;

			path_1 = current_test.assignWeights(oldPath1, oldPath2);
			path_2 = current_test.assignWeights(oldPath2, oldPath1);
		}
		return bounds.times(tests[0].assignWeights(path_1, path_2)).reduce();
	}


	public static void main(String[] args) {
		int max = 20;

		builder = new XADDBuild.Builder(new XADD());

		//*
		for(int i = 2; i <= max; i++) {
			System.out.println("Eliminating for n=" + i);
			//eliminateAllVariables(i, true, false, true);
			/*{
				List<String> variables = new ArrayList<>();

				variables.add("x");
				for(int j = 0; j < i; j++) {
					int index = j + 1;
					variables.add("c" + index);
				}
				variables.add("c");
				OrderedXADD orderedXADD = new OrderedXADD(variables, false);
				builder = new XADDBuild.Builder(orderedXADD);
				eliminateAllVariablesDirect(i, true, orderedXADD);
			}*/
			{
				List<String> variables = new ArrayList<>();

				for(int j = 0; j < i; j++) {
					int index = j + 1;
					variables.add("c" + index);
				}
				variables.add("c");
				variables.add("x");
				OrderedXADD orderedXADD = new OrderedXADD(variables, false);
				builder = new XADDBuild.Builder(orderedXADD);
				eliminateAllVariablesDirect(i, true, orderedXADD);
			}
		}
		// */

		/*
		for(int i = 2; i < max; i++) {
			System.out.println("Eliminating for n=" + i);
			eliminateX(i, true, true);
		}
		// */

		/*
		for(int i = 2; i < max; i++) {
			System.out.println("Eliminating for n=" + i);
			eliminateOneVariable("c" + (i + 1), i, true, true);
		}
		// */
	}

	public static void eliminateOneVariable(String variable, int n, boolean symbolic, boolean resolve) {
		double time, timeTaken;
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);
		List<String> booleanVariables = Collections.emptyList();
		List<String> continuousVariables = new ArrayList<>();
		continuousVariables.add(variable);

		// Run integration
		time = System.currentTimeMillis();
		XADDiagram integrated;
		if(resolve) {
			integrated = xor2.eliminateRealVars(singletonList(variable));
		} else {
			integrated = xor2.getIntegratedDiagram(booleanVariables, continuousVariables);
		}
		timeTaken = System.currentTimeMillis() - time;
		System.out.format("Took %.3f seconds\n", timeTaken / 1000);
	}

	public static void eliminateAllVariables(int n, boolean symbolic, boolean reverse, boolean resolve) {
		double time, timeTaken;
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);
		// xor2.show("");
		List<String> booleanVariables = Collections.emptyList();
		List<String> continuousVariables = new ArrayList<>();
		if(symbolic) {
			for(int i = 0; i < n; i++) {
				int index = i + 1;
				if(reverse) {
					index = n - i + 1;
				}
				continuousVariables.add("c" + index);
			}
		}
		continuousVariables.add("c");
		continuousVariables.add("x");

		// Run integration
		time = System.currentTimeMillis();
		XADDiagram integrated = xor2;
		for(String var : continuousVariables) {
			double timeIteration = System.currentTimeMillis();
			if(resolve) {
				integrated = integrated.eliminateRealVars(singletonList(var));
			} else {
				integrated = integrated.getIntegratedDiagram(booleanVariables, singletonList(var));
			}
			double timeIterationTaken = System.currentTimeMillis() - timeIteration;
			// System.out.format("\tEliminating %s Took %.3f seconds\n", var, timeIterationTaken / 1000);
			// integrated.show("Integrated " + var);
		}
		System.out.format("Result for n = %d: %s\n", n, integrated.evaluate(new Assignment()));
		timeTaken = System.currentTimeMillis() - time;
		System.out.format("Took %.3f seconds\n", timeTaken / 1000);
	}

	public static void eliminateAllVariablesDirect(int n, boolean symbolic, OrderedXADD context) {
		double time, timeTaken;

		System.out.println(context.getVariableOrder());
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);

		//*
		xor2.show("XOR");
		try {
			Thread.sleep(100000);
		} catch(InterruptedException e) {
			e.printStackTrace();
		}//*/

		// Run integration
		time = System.currentTimeMillis();
		List<String> types = Collections.nCopies(context.getVariableOrder().size(), "real");
		XADDiagram integrated = new XADDiagram(context, new ResolveAllIntegration(context, types).integrate(xor2.number));
		System.out.format("Result for n = %d: %s\n", n, integrated.evaluate(new Assignment()));
		timeTaken = System.currentTimeMillis() - time;
		System.out.format("Took %.3f seconds\n", timeTaken / 1000);
	}


	public static void eliminateX(int n, boolean symbolic, boolean resolve) {
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);

		// Run integration
		Stopwatch timer = new Stopwatch(true);
		XADDiagram integrated;
			if(resolve) {
				integrated = xor2.eliminateRealVars(singletonList("x"));
			} else {
				integrated = xor2.getIntegratedDiagram(Collections.emptyList(), singletonList("x"));
			}
		System.out.format("Took %.3f seconds\n", timer.stop() / 1000);
	}
}
