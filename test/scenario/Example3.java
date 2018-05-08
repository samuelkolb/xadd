package scenario;

import diagram.XADDiagram;
import org.junit.Test;

import static diagram.XADDBuild.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

/**
 * Created by samuelkolb on 23/03/16.
 *
 * @author Samuel Kolb
 */
@SuppressWarnings("JavaDoc")
public class Example3 {

	private final static XADDiagram
			theory = /*bool("a").and(*/test("x > 0").and(test("x < y").and(test("y < 1")))//)
		//theory = test("x > 0").and(test("y > 0")).and(test("x < 1")).and(test("y < 1")).and(test("x > 0").and(test("x < y").and(test("y < 1")))
			.or(test("x + y < 1").and(test("x > 0")).and(test("y > 0")));//);

	@Test
	public void testIntegrationXY() {
		assertEquals(1.0, theory.integrate(emptyList(), asList("x", "y")), Example2.DELTA);
	}

	@Test
	public void testIntegrationYX() {
		assertEquals(1.0, theory.integrate(emptyList(), asList("y", "x")), Example2.DELTA);
	}
}
