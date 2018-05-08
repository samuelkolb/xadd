package scenario;

import com.sun.org.apache.xpath.internal.operations.Bool;
import diagram.Assignment;
import diagram.BoolXADD;
import diagram.ResolveIntegration;
import diagram.XADDBuild;
import diagram.XADDiagram;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;

import static diagram.XADDBuild.*;
import static org.junit.Assert.*;

/**
 * Created by samuelkolb on 26/04/16.
 *
 * @author Samuel Kolb
 */
public class Example7 {

	public static XADDiagram buildXorSymbolic(int n) {
		return buildXor(n, i -> test("x <= c" + i),
				(i, bounds) -> bounds.and(test("c" + i + " <= 10")).and(test("c" + i + " >= 0")));

	}

	public static XADDiagram buildXorNumeric(int n) {
		long seed = System.currentTimeMillis();
		System.out.println("Seed: " + seed);
		return buildXorNumeric(n, seed);
	}

	public static XADDiagram buildXorNumeric(int n, long seed) {
		Random random = new Random(seed);
		return buildXor(n, i -> test("x <= " + 10 + random.nextInt(81)), (i, bounds) -> bounds);
	}

	private static XADDiagram buildXor(int n, Function<Integer, BoolXADD> testProducer,
									   BiFunction<Integer, BoolXADD, BoolXADD> boundsProducer) {
		BoolXADD[] tests = new BoolXADD[n + 1];
		BoolXADD bounds = test("x <= 100").and(test("x >= 0"));
		bounds = bounds.and(test("c <= 100").and(test("c >= 0")));

		for(int i = 0; i < n; i++) {
			int index = i + 1;
			bounds = boundsProducer.apply(index, bounds);
		}

		tests[0] = test("x <= c");

		for(int i = 0; i < n; i++) {
			int index = i + 1;
			tests[index] = testProducer.apply(index);
		}

		XADDiagram leaf_1 = val(3);
		XADDiagram leaf_2 = val(11);

		XADDiagram path_1 = leaf_1;
		XADDiagram path_2 = leaf_2;

		for(int i = 0; i < n; i++) {
			int index = n - i;
			BoolXADD current_test = tests[index];
			XADDiagram oldPath1 = path_1;
			XADDiagram oldPath2 = path_2;

			path_1 = current_test.assignWeights(oldPath1, oldPath2);
			path_2 =current_test.assignWeights(oldPath2, oldPath1);
		}
		return bounds.times(tests[0].assignWeights(path_1, path_2));
	}


	public static void main(String[] args) {
		int max = 20;
		/*
		for(int i = 2; i < max; i++) {
			System.out.println("Eliminating for n=" + i);
			eliminateAllVariables(i, true, false);
		}
		// */

		/*
		for(int i = 2; i < max; i++) {
			System.out.println("Eliminating for n=" + i);
			eliminateOneVariable("c" + (i + 1), i, true);
		}
		// */

		//*
		for(int i = 2; i < max; i++) {
			System.out.println("Eliminating for n=" + i);
			eliminateOneVariable("x", i, false, true);
		}
		// */
	}

	public static void eliminateOneVariable(String variable, int n, boolean symbolic, boolean newMethod) {
		double time, timeTaken;
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);
		List<String> booleanVariables = Collections.emptyList();
		List<String> continuousVariables = Collections.singletonList(variable);

		// Run integration
		time = System.currentTimeMillis();
		if(newMethod) {
			XADDiagram integrated = xor2.eliminateRealVars(variable);
		} else {
			XADDiagram integrated = xor2.getIntegratedDiagram(booleanVariables, continuousVariables);
		}
		timeTaken = System.currentTimeMillis() - time;
		System.out.format("Took %.3f seconds\n", timeTaken / 1000);
	}

	public static void eliminateAllVariables(int n, boolean symbolic, boolean reverse) {
		double time, timeTaken;
		XADDiagram xor2 = symbolic ? buildXorSymbolic(n) : buildXorNumeric(n);
		List<String> booleanVariables = Collections.emptyList();
		List<String> continuousVariables = new ArrayList<>();
		for(int i = 0; i < n; i++) {
			int index = i + 1;
			if(reverse) {
				index = n - i + 1;
			}
			continuousVariables.add("c" + index);
		}
		continuousVariables.add("c");
		continuousVariables.add("x");

		// Run integration
		time = System.currentTimeMillis();
		XADDiagram integrated = xor2;
		for(String var : continuousVariables) {
			double timeIteration = System.currentTimeMillis();
			integrated = integrated.getIntegratedDiagram(booleanVariables, Collections.singletonList(var));
			double timeIterationTaken = System.currentTimeMillis() - timeIteration;
			System.out.format("\tEliminating %s Took %.3f seconds\n", var, timeIterationTaken / 1000);
			// integrated.show("Integrated " + var);
		}
		timeTaken = System.currentTimeMillis() - time;
		System.out.format("Took %.3f seconds\n", timeTaken / 1000);
	}
}
