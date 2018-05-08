package scenario;

import diagram.BoolXADD;
import diagram.XADDiagram;

import java.util.Arrays;

import static diagram.XADDBuild.*;

/**
 * Created by samuelkolb on 26/04/16.
 *
 * @author Samuel Kolb
 */
public class Example8 {

	public static void main(String[] args) {
		BoolXADD xaddTheory = test("0 <= x").and(test("x <= 10"))
				.and(test("0 <= y").and(test("y <= 10")))
				.and(bool("p").or(test("x + y <= 5")));

		// A weight of 1 needs to be substituted by u, and re-substituted afterwards
		XADDiagram w1 = bool("p").times(val(0.5)).plus(bool("p").not().times(val("2*x"))),
				w2 = test("0 <= x").and(test("x < 5")).assignWeights(val("y + 5"), val("1")),
				w3 = test("5 <= x").and(test("x <= 10")).assignWeights(val("x + y"), val("1"));

		// Remaining 1's need to be removed
		XADDiagram weights = w1.times(w2).times(w3);

		/*(0 <= x <= 10) AND (0 <= y <= 10) AND (p OR (x + y <= 5))

		w(p) = 0.5, w(¬p) = 2*x
		w(0 <= x < 5) = y + 5
		w(5 <= x <= 10) = x + y
*/

		XADDiagram result = xaddTheory.applyWeights(weights);
		System.out.println(result.integrate(Arrays.asList("p"), Arrays.asList("x", "y")));
	}
}
