package diagram;

import xadd.XADD;

import java.util.Map;

import static function.Functional.autoFold;
import static function.Functional.zip;
import static java.lang.String.format;

/**
 * Created by samuelkolb on 19/04/16.
 *
 * @author Samuel Kolb
 */
public class XADDBuild {

	public static class Builder {
		private final XADD xadd;

		/**
		 * Obtain an xadd builder
		 * @param context	The XADD instance to use for building
		 */
		public Builder(XADD context) {
			this.xadd = context;
		}

		/**
		 * Builds a diagram from a string.
		 * @param string	The input string
		 * @return	The diagram
		 */
		public XADDiagram fromString(String string) {
			return new XADDiagram(xadd, contextBuild(string));
		}

		private BoolXADD fromStringBool(String string) {
			return new BoolXADD(xadd, contextBuild(string));
		}

		private int contextBuild(String string) {
			return xadd.buildCanonicalXADDFromString(string);
		}

		/**
		 * Builds a diagram that returns 1 if the given variable is true, 0 otherwise.
		 * @param string	The name of the variable
		 * @return	The diagram
		 */
		public BoolXADD bool(String string) {
			return fromStringBool("(" + string + " ([1]) ([0]))");
		}

		/**
		 * Builds a diagram that returns 1 if the given expression is true, 0 otherwise.
		 * @param string	The expression
		 * @return	The diagram
		 */
		public BoolXADD test(String string) {
			return fromStringBool("([" + string + "] ([1]) ([0]))");
		}

		/**
		 * Build a case wise defined XADD
		 * @param caseMap	Mapping from mutually exclusive cases to values
		 * @return	The combined XADD
		 */
		public XADDiagram cases(Map<BoolXADD, XADDiagram> caseMap) {
			return autoFold(XADDiagram::plus, zip(XADDiagram::times, caseMap));
		}

		/**
		 * Returns a constant BoolXADD
		 * @param value	The value to represent
		 * @return	The corresponding XADD
		 */
		public BoolXADD val(boolean value) {
			return fromStringBool("([" + (value ? "1" : "0") + "])");
		}

		/**
		 * Returns a constant XADD
		 * @param value	The value to represent
		 * @return	The corresponding XADD
		 */
		public XADDiagram val(double value) {
			return val(Double.toString(value));
		}

		/**
		 * Returns a constant XADD
		 * @param value	The value to represent
		 * @return	The corresponding XADD
		 */
		public XADDiagram val(int value) {
			return val(Integer.toString(value));
		}

		/**
		 * Returns a constant XADD
		 * @param value	The value to represent
		 * @return	The corresponding XADD
		 */
		public XADDiagram val(String value) {
			try {
				return fromString("([" + value + "])");
			} catch(Exception e) {
				throw new IllegalArgumentException(format("Could not parse string value %s", value), e);
			}
		}
	}

	// Static: context
	//public static XADD context = new XADD();
	static final Builder builder = new Builder(new XADD());

	/**
	 * Builds a diagram from a string.
	 * @param string	The input string
	 * @return	The diagram
	 */
	public static XADDiagram fromString(String string) {
		return builder.fromString(string);
	}

	/**
	 * Builds a diagram that returns 1 if the given variable is true, 0 otherwise.
	 * @param string	The name of the variable
	 * @return	The diagram
	 */
	public static BoolXADD bool(String string) {
		return builder.bool(string);
	}

	/**
	 * Builds a diagram that returns 1 if the given expression is true, 0 otherwise.
	 * @param string	The expression
	 * @return	The diagram
	 */
	public static BoolXADD test(String string) {
		return builder.test(string);
	}

	/**
	 * Build a case wise defined XADD
	 * @param caseMap	Mapping from mutually exclusive cases to values
	 * @return	The combined XADD
	 */
	public static XADDiagram cases(Map<BoolXADD, XADDiagram> caseMap) {
		return builder.cases(caseMap);
	}

	/**
	 * Returns a constant BoolXADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static BoolXADD val(boolean value) {
		return builder.val(value);
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(double value) {
		return builder.val(value);
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(int value) {
		return builder.val(value);
	}

	/**
	 * Returns a constant XADD
	 * @param value	The value to represent
	 * @return	The corresponding XADD
	 */
	public static XADDiagram val(String value) {
		return builder.val(value);
	}

	/**
	 * Creates a builder instance and returns it
	 * @param xadd	The XADD instance to pass to the builder
	 * @return	A new builder instance
	 */
	public static Builder builder(XADD xadd) {
		return new Builder(xadd);
	}
}
