package diagram;

import basic.ArrayUtil;
import bsh.This;
import xadd.XADD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Created by samuelkolb on 03/07/2017.
 *
 * @author Samuel Kolb
 */
public class OrderedXADD extends XADD {

	private final List<String> variableOrder;

	public List<String> getVariableOrder() {
		return variableOrder;
	}

	private final boolean orderOnFirst;

	/**
	 * Constructs an ordered xadd
	 * @param variableOrder	The order of the variables used: variables occurring first are ordered higher up
	 * @param orderOnFirst	If true the ordering prioritizes the first element, otherwise it prioritizes the last
	 */
	public OrderedXADD(List<String> variableOrder, boolean orderOnFirst) {
		this.variableOrder = Collections.unmodifiableList(new ArrayList<>(variableOrder));
		this.orderOnFirst = orderOnFirst;
	}

	List<String> getVariables(Decision decision) {
		HashSet<String> collector = new HashSet<>();
		decision.collectVars(collector);
		return new ArrayList<>(collector);
	}
	
	int[] getOrder(List<String> variables) {
		int[] order = new int[variables.size()];
		for(int i = 0; i < variables.size(); i++) {
			order[i] = getVariableOrder().indexOf(variables.get(i));
		}
		Arrays.sort(order);
		return order;
	}

	@Override
	public boolean localOrderCompareGE(int var1, int var2) {
		// test1 >= test2
		Decision dec1 = _alOrder.get(var1);
		Decision dec2 = _alOrder.get(var2);

		List<String> vars1 = getVariables(dec1);
		List<String> vars2 = getVariables(dec2);

		int[] order1 = getOrder(vars1);
		int[] order2 = getOrder(vars2);

		if(!orderOnFirst) {
			ArrayUtil.reverse(order1);
			ArrayUtil.reverse(order2);
		}

		for(int i = 0; i < Math.max(order1.length, order2.length); i++) {
			if(i >= order1.length) {
				// All variables the same, but variables2 has additional ones
				return false;  // test2 > test1
			} else if(i >= order2.length) {
				// All variables the same, but variables1 has additional ones
				return true;  // test1 > test2
			} else if(order1[i] > order2[i]) {
				// Variable in variables1 is greater
				return true;  // test1 > test2
			} else if(order1[i] < order2[i]) {
				// Variable in variables2 is greater
				return false;  // test1 < test2
			} else if(order1[i] == -1 || order2[i] == -1) {
				throw new IllegalStateException("Could not find one of the variables () in the order.");
			}
		}
		// All variables where the same
		// test1 = test2 for booleans
		if(dec1 instanceof BoolDec) {
			return true;
		}
		// For arithmetic look at constants
		if(dec1 instanceof ExprDec) {
			HashMap<String, Double> assignment = new HashMap<>();
			for(String var : vars1) {
				assignment.put(var, 0.0);
			}
			double constant1 = ((ExprDec) dec1)._expr._lhs.evaluate(assignment);
			double constant2 = ((ExprDec) dec2)._expr._lhs.evaluate(assignment);
			for(String var : vars1) {
				assignment.put(var, 1.0);
				double val1 = ((ExprDec) dec1)._expr._lhs.evaluate(assignment) - constant1;
				double val2 = ((ExprDec) dec2)._expr._lhs.evaluate(assignment) - constant2;
 				if(val1 < 0 && val2 > 0) {
					return false;
				} else if(val1 > 0 && val2 < 0) {
					return true;
				}
				if(val1 < 0) {
					double factor1 = constant1 / -val1;
					double factor2 = constant2 / -val2;
					if(factor1 != factor2) {
						return factor1 > factor2;
					}
				} else {
					double factor1 = -constant1 / val1;
					double factor2 = -constant2 / val2;
					if(factor1 != factor2) {
						return factor1 < factor2;
					}
				}
				assignment.put(var, 0.0);
				if(val1 > val2) {
					return true;
				} else if(val1 < val2) {
					return false;
				}
			}
			if(constant1 > constant2) {
				return true;
			} else if(constant1 < constant2) {
				return false;
			}
		}
		return true;  // test1 == test2
	}
}
