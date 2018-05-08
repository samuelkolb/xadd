package scenario;

import diagram.BoolXADD;
import diagram.XADDiagram;
import org.junit.Test;

import java.util.function.Function;

import static diagram.XADDBuild.*;
import static function.Architect.map;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.junit.Assert.*;

/**
 * Created by samuelkolb on 13/04/16.
 *
 * @author Samuel Kolb
 */
@SuppressWarnings("JavaDoc")
public class Example4 {

	public static final BoolXADD
			a = bool("a"),
			b = bool("b"),
			c = bool("c"),
			x = test("x > 0").and(test("x < 1")),
			y1 = test("y > -2").and(test("y < -1")),
			y2 = test("y > 0").and(test("y < 1"));

	public static final XADDiagram
			wa = cases(map(a, a.not()).to(fromString("([2])"), fromString("([-1])"))),
			wb = cases(map(b, b.not()).to(fromString("([0])"), fromString("([10])"))),
			wc = cases(map(c, c.not()).to(fromString("([v])"), fromString("([w])"))),
			wx = cases(map(x, x.not()).to(fromString("([2*x])"), fromString("([0])"))),
			wiu = fromString("([2*x + 4*y])");

	public static final boolean DISPLAY = false;

	@Test
	public void finiteFact() {
		evalTest(10, c -> c.integrate(asList("a", "b"), emptyList()), a.or(b.not()), wa, wb);
	}

	@Test
	public void infiniteFact() {
		evalTest(1, c -> c.integrate(singletonList("a"), singletonList("x")), x.or(a.not()), wa, wx);
	}

	/*@Test
	public void infiniteUnFact() {
		BoolXADD theory = x.and(y1.or(y2));
		theory.show("Theory");
		comb.show("Combined");
		System.out.println(comb.integrate(asList(), asList("x", "y")));
		evalTest(1, c -> c.integrate(asList("a", "b"), emptyList()), x.or(a.not()), wa, wx);
	}

	public void compose() {
		XADDiagram wa = cases(map(a, a.not()).to(fromString("([at])"), fromString("([0])"))),
				wb = cases(map(b, b.not()).to(fromString("([bt])"), fromString("([bf])"))),
				wc = cases(map(c, c.not()).to(fromString("([ct])"), fromString("([cf])"))),
				theory = a.or(b.not()).and(c.not().or(b)).and(c.or(b.not())),
				comb = theory.times(wa).times(wb).times(wc);
		theory.show("Theory");
		comb.show("Combined");
		System.out.println(comb.integrate(asList(), asList("x", "y")));
	}*/

	private void evalTest(double result, Function<XADDiagram, Double> f, BoolXADD theory, XADDiagram... weights) {
		XADDiagram combined = theory.applyWeights(weights);
		if(DISPLAY) {
			theory.show("Theory");
			combined.show("Combined");
		}
		double obtained = f.apply(combined);
		assertEquals(result, obtained, Example2.DELTA);
	}
}
