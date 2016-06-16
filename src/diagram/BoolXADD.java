package diagram;

import function.Architect;
import xadd.XADD;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import static diagram.XADDBuild.*;
import static function.Architect.*;
import static function.Functional.fold;

/**
 * Created by samuelkolb on 19/04/16.
 *
 * @author Samuel Kolb
 */
public class BoolXADD extends XADDiagram {

	protected BoolXADD(int number) {
		super(number);
	}

	/**
	 * Performs logical AND on this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public BoolXADD and(BoolXADD diagram) {
		return convert(times(diagram), false);
	}

	/**
	 * Performs logical OR on this diagram and the given diagram.
	 * @param diagram	The given diagram
	 * @return The resulting diagram
	 */
	public BoolXADD or(BoolXADD diagram) {
		return convert(plus(diagram).min(val(1)), false);
	}

	/**
	 * Performs logical NOT on this diagram
	 * @return	The resulting diagram
	 */
	public BoolXADD not() {
		return convert(plus(val(-1)).times(val(-1)), false);
	}

	/**
	 * Returns an XADD that assigns weightTrue whenever this XADD is true and weightFalse otherwise
	 * @param weightTrue	The weight when this XADD is true
	 * @param weightFalse	The weight when this XADD is false
	 * @return	The combined XADD
	 */
	public XADDiagram assignWeights(XADDiagram weightTrue, XADDiagram weightFalse) {
		return cases(map(this, this.not()).to(weightTrue, weightFalse));
	}

	/**
	 * Applies multiple weights to this XADD
	 * @param weights	The weights to apply
	 * @return	The combined XADD
	 */
	public XADDiagram applyWeights(XADDiagram... weights) {
		return applyWeights(Arrays.asList(weights));
	}

	/**
	 * Applies multiple weights to this XADD
	 * @param weights	The weights to apply
	 * @return	The combined XADD
	 */
	public XADDiagram applyWeights(Collection<XADDiagram> weights) {
		return fold(this, XADDiagram::times, weights);
	}

	/**
	 * Convert the given XADD
	 * @param diagram	The diagram to convert
	 * @return	The corresponding boolean XADD
	 */
	public static BoolXADD convert(XADDiagram diagram) {
		return convert(diagram, true);
	}

	/**
	 * Convert the given XADD
	 * @param diagram	The diagram to convert
	 * @param check		Whether to check the diagram or trust the input to have only boolean values
	 * @return	The corresponding boolean XADD
	 */
	public static BoolXADD convert(XADDiagram diagram, boolean check) {
		if(check && !isBool(diagram.number)) {
			throw new IllegalArgumentException("The given XADD does not have boolean (1 or 0) values");
		}
		return new BoolXADD(diagram.number);
	}

	private static boolean isBool(int nodeId) {
		XADD.XADDNode node = context.getNode(nodeId);
		if(node instanceof XADD.XADDTNode) {
			double value = context.evaluate(nodeId, new HashMap<>(), new HashMap<>());
			return value == 1 || value == 0;
		} else if(node instanceof XADD.XADDINode) {
			XADD.XADDINode iNode = (XADD.XADDINode) node;
			return isBool(iNode._high) && isBool(iNode._low);
		} else {
			throw new IllegalStateException("Unexpected structural error");
		}
	}
}
