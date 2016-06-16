package diagram;

import xadd.XADD;

import java.util.Map;

import static function.Functional.autoFold;
import static function.Functional.zip;

/**
 * Created by samuelkolb on 19/04/16.
 *
 * @author Samuel Kolb
 */
public class XADDBuild {

	// Static: context
	protected static XADD context = new XADD();

	/**
	 * Builds a diagram from a string.
	 * @param string	The input string
	 * @return	The diagram
	 */
	public static XADDiagram fromString(String string) {
		return new XADDiagram(contextBuild(string));
	}

	private static int contextBuild(String string) {
		return context.buildCanonicalXADDFromString(string);
	}

	/**
	 * Builds a diagram that returns 1 if the given variable is true, 0 otherwise.
	 * @param string	The name of the variable
	 * @return	The diagram
	 */
	public static BoolXADD bool(String string) {
		return new BoolXADD(contextBuild("(" + string + " ([1]) ([0]))"));
	}

	/**
	 * Builds a diagram that returns 1 if the given expression is true, 0 otherwise.
	 * @param string	The expression
	 * @return	The diagram
	 */
	public static BoolXADD test(String string) {
		return new BoolXADD(contextBuild("([" + string + "] ([1]) ([0]))"));
	}

	/**
	 * Build a case wise defined XADD
	 * @param caseMap	Mapping from mutually exclusive cases to values
	 * @return	The combined XADD
	 */
	public static XADDiagram cases(Map<BoolXADD, XADDiagram> caseMap) {
		return autoFold(XADDiagram::plus, zip(XADDiagram::times, caseMap));
	}

	/**
	 * Returns a constant BoolXADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static BoolXADD val(boolean value) {
		return new BoolXADD(contextBuild("([" + (value ? "1" : "0") + "])"));
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(double value) {
		return val(Double.toString(value));
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(int value) {
		return val(Integer.toString(value));
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(String value) {
		return fromString("([" + value + "])");
	}
}
