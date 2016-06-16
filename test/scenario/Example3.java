package scenario;

import diagram.XADDBuild;
import diagram.XADDiagram;
import org.junit.Test;

import java.util.Arrays;

import static diagram.XADDBuild.*;
import static diagram.XADDiagram.*;
import static java.util.Arrays.asList;

/**
 * Created by samuelkolb on 23/03/16.
 *
 * @author Samuel Kolb
 */
public class Example3 {

	private final static XADDiagram
			theory = /*bool("a").and(*/test("x > 0").and(test("x < y").and(test("y < 1")))//)
		//theory = test("x > 0").and(test("y > 0")).and(test("x < 1")).and(test("y < 1")).and(test("x > 0").and(test("x < y").and(test("y < 1")))
			.or(test("x + y < 1").and(test("x > 0")).and(test("y > 0")));//);

	@Test
	public void testIntegration() {
		theory.export("integration_error.txt");
		System.out.println(theory/*.times(fromString("([x])"))*/.integrate(asList(), asList("y", "x" /*, "x"*/)));
	}
}
