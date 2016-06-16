package scenario;

import diagram.Assignment;
import diagram.BoolXADD;
import diagram.XADDBuild;
import diagram.XADDiagram;
import function.Architect;
import function.Functional;
import org.junit.Assert;
import org.junit.Test;

import static diagram.XADDBuild.*;
import static function.Architect.*;
import static org.junit.Assert.*;

/**
 * Created by samuelkolb on 20/04/16.
 *
 * @author Samuel Kolb
 */
public class Example5 {

	public static final BoolXADD
		a = bool("a"),
		x1 = test("x > 0").and(test("x < 1")),
		x2 = test("x > 1").and(test("x < 2")),
		theory = a.and(x1).or(a.not().and(x2));

	public static final XADDiagram
		wa = a.assignWeights(val("x"), val("-1")),
		wx = cases(map(x1, x2).to(val("2"), val("-3*x"))),
		comb = theory.applyWeights(wa, wx);

	@Test
	public void testMax() throws InterruptedException {
		Assignment assignment = comb.maxArg();
		System.out.println(assignment);
		assertEquals(false, assignment.getBool("a"));
		assertEquals(2.0, assignment.getDouble("x"), Example2.DELTA);
		// assertEquals(6.0, comb.evaluate(assignment), Example2.DELTA); // TODO Valuation not working
	}

	public static void main(String[] args) {
		comb.show("Combined");
	}
}
