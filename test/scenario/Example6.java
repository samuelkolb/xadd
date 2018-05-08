package scenario;

import diagram.Assignment;
import diagram.BoolXADD;
import diagram.XADDiagram;
import org.junit.Test;

import static diagram.XADDBuild.*;
import static org.junit.Assert.assertEquals;

/**
 * Created by samuelkolb on 26/04/16.
 *
 * @author Samuel Kolb
 */
public class Example6 {

	public static BoolXADD
		a = bool("a"),
		xg0 = test("x >= 0"),
		yg0 = test("y >= 0"),
		ygx = test("x <= y"),
		ys10 = test("y <= 10"),
		ys15 = test("y <= 15"),
		theory = xg0.and(yg0).and(ygx).and(a.and(ys10).or(a.not().and(ys15)));

	public static XADDiagram
		wa = a.assignWeights(val(2), val(1)),
		w = val("x + 0.2 * y"),
		comb = theory.applyWeights(wa, w).reduce();

	public static void main(String[] args) {
		comb.show("Combined");
	}

	@Test
	public void testMax() throws InterruptedException {
		Assignment assignment = comb.maxArg();
		System.out.println(assignment);
		assertEquals(true, assignment.getBool("a"));
		assertEquals(10.0, assignment.getDouble("x"), Example2.DELTA);
		assertEquals(10.0, assignment.getDouble("y"), Example2.DELTA);
		// assertEquals(24.0, comb.evaluate(assignment), Example2.DELTA); // TODO Valuation not working
	}
}
