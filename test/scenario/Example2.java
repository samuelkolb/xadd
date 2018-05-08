package scenario;

import diagram.BoolXADD;
import org.junit.Assert;
import org.junit.Test;
import diagram.XADDiagram;

import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import static diagram.XADDBuild.*;
import static function.Architect.*;
import static java.util.Arrays.asList;

/**
 * Created by samuelkolb on 15/03/16.
 *
 * @author Samuel Kolb
 */
public class Example2 {

	public static final double DELTA = 1e-8;

	public static final BoolXADD
			a = bool("a"),
			xLargerZero = test("x >= 0"),
			xSmallerOne = test("x < 1"),
			xLargerOne = test("x >= 1"),
			xSmallerTwo = test("x < 2");

	public static final XADDiagram
			weightTwo = val(2),
			weightX = val("x");


	public static BoolXADD getTheory() {
		return a.and(xLargerZero).and(xSmallerOne).or(xLargerOne.and(xSmallerTwo));
	}

	public static List<XADDiagram> getWeights() {
		return asList(
				a.assignWeights(weightTwo, weightX),
				cases(map(xLargerZero.and(xSmallerOne), xLargerOne.and(xSmallerTwo)).to(weightTwo, weightX))
		);
	}

	public static XADDiagram getCombined() {
		return getTheory().applyWeights(getWeights());
	}

	@Test
	public void testTheoryEvaluation() {
		Assert.assertEquals(1, getTheory().evaluate(map("a").to(true), map("x").to(0.5)), 0);
		Assert.assertEquals(0, getTheory().evaluate(map("a").to(true), map("x").to(-0.5)), 0);
		Assert.assertEquals(1, getTheory().evaluate(map("a").to(true), map("x").to(1.5)), 0);
		Assert.assertEquals(1, getTheory().evaluate(map("a").to(false), map("x").to(1.5)), 0);
		Assert.assertEquals(0, getTheory().evaluate(map("a").to(false), map("x").to(2.5)), 0);
	}

	@Test
	public void testWeight1Evaluation() throws Exception {
		Assert.assertEquals(2, getWeights().get(0).evaluate(map("a").to(true), map("x").to(3.0)), 0);
		Assert.assertEquals(3, getWeights().get(0).evaluate(map("a").to(false), map("x").to(3.0)), 0);
	}

	@Test
	public void testWeight2Evaluation() throws Exception {
		Assert.assertEquals(0, getWeights().get(1).evaluate(new HashMap<>(), map("x").to(-0.5)), 0);
		Assert.assertEquals(2, getWeights().get(1).evaluate(new HashMap<>(), map("x").to(0.5)), 0);
		Assert.assertEquals(1.5, getWeights().get(1).evaluate(new HashMap<>(), map("x").to(1.5)), 0);
		Assert.assertEquals(0, getWeights().get(1).evaluate(new HashMap<>(), map("x").to(2.5)), 0);
	}

	@Test
	public void testIntegration() {
		Assert.assertEquals(9.0 + 1.0 / 3.0, getCombined().integrate(asList("a"), asList("x")), DELTA);
	}

	@Test
	public void testEvidence() {
		XADDiagram partial = getCombined().evaluatePartial(map("a").to(false), new HashMap<>());
		Assert.assertEquals(2.0 + 1.0 / 3.0, partial.integrate(asList(), asList("x")), DELTA);
	}

	public void show() {
		getTheory().show("Theory");
		getWeights().get(0).show("Weight1");
		getWeights().get(1).show("Weight2");
		getCombined().show("Combined");
		new Scanner(System.in).next();
	}
}
