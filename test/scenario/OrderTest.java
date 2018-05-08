package scenario;

import diagram.BoolXADD;
import diagram.OrderedXADD;
import diagram.XADDBuild;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import xadd.XADD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by samuelkolb on 05/07/2017.
 *
 * @author Samuel Kolb
 */
public class OrderTest {

	static List<String> tests;

	@BeforeClass
	public static void setUp() {
		tests = new ArrayList<>();
		tests.add("x < 0");
		tests.add("x < y");
		tests.add("x < z");
		tests.add("x < y + z");
		tests.add("y < z");
		tests.add("y < 0");
		tests.add("z < 0");
	}

	@Test
	public void testOrder() {
		XADDBuild.Builder b = new XADDBuild.Builder(new OrderedXADD(Arrays.asList("x", "y", "z"), true));
		BoolXADD diagram = b.val(true);
		for(String testString : tests) {
			diagram = diagram.and(b.test(testString));
		}
		List<List<String>> expected = new ArrayList<>();
		expected.add(Arrays.asList("x"));
		expected.add(Arrays.asList("x", "y"));
		expected.add(Arrays.asList("x", "y", "z"));
		expected.add(Arrays.asList("x", "z"));
		expected.add(Arrays.asList("y"));
		expected.add(Arrays.asList("y", "z"));
		expected.add(Arrays.asList("z"));

		checkOrder(diagram, expected);
	}

	@Test
	public void testLastOrder() {
		XADDBuild.Builder b = new XADDBuild.Builder(new OrderedXADD(Arrays.asList("x", "y", "z"), false));
		BoolXADD diagram = b.val(true);
		for(String testString : tests) {
			diagram = diagram.and(b.test(testString));
		}
		List<List<String>> expected = new ArrayList<>();
		expected.add(Arrays.asList("x"));
		expected.add(Arrays.asList("y"));
		expected.add(Arrays.asList("x", "y"));
		expected.add(Arrays.asList("z"));
		expected.add(Arrays.asList("x", "z"));
		expected.add(Arrays.asList("y", "z"));
		expected.add(Arrays.asList("x", "y", "z"));

		checkOrder(diagram, expected);
	}

	@Test
	public void testNumericOrder() throws InterruptedException {
		XADDBuild.Builder b = new XADDBuild.Builder(new OrderedXADD(Arrays.asList("x"), false));
		BoolXADD diagram = b.val(true);
		// diagram = diagram.and(b.test("2*x > -10"));
		// diagram = diagram.and(b.test("4*x > -10"));
		diagram = diagram.and(b.test("2*x < 10"));
		diagram = diagram.and(b.test("4*x < 10"));
		diagram.show("Numeric order");
		diagram.reduceLp().show("Numeric order reduced");
		Thread.sleep(1000000);
	}

	private Set<String> getVars(XADD context, int rootId) {
		HashSet<String> collector = new HashSet<>();
		XADD.XADDNode node = context.getNode(rootId);
		if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;
			iNode.getDecision().collectVars(collector);
			return collector;
		} else {
			return Collections.emptySet();
		}
	}

	private void checkOrder(BoolXADD diagram, List<List<String>> expected) {
		XADD context = diagram.xadd;
		int rootId = diagram.number;
		Set<String> vars = getVars(context, rootId);

		if(expected.isEmpty()) {
			Assert.assertTrue(vars.isEmpty());
		} else {
			Assert.assertTrue(String.format("Vars %s contained a different number of variables than expected (%s)",
					vars, expected.get(0)), vars.size() == expected.get(0).size());
			for(String var : expected.get(0)) {
				Assert.assertTrue(String.format("Var %s not in vars %s", var, vars), vars.contains(var));
			}
			expected.remove(0);
			checkOrder(new BoolXADD(context, ((XADD.XADDINode) context.getNode(rootId))._high), expected);
		}
	}
}
