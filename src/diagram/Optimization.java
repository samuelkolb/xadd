package diagram;

import algebra.LinearGLPKSolver;
import algebra.Solver;
import build.Factory;
import pair.TypePair;
import scpsolver.lpsolver.GLPKSolver;
import xadd.XADD;

/**
 * Created by samuelkolb on 03/05/16.
 *
 * @author Samuel Kolb
 */
public class Optimization implements XADDiagram.NodeWalkerObserver<XADDPath, Assignment.Valued<Double>> {

	private final boolean minimize;
	private final Solver<Assignment.Valued<Double>> solver;

	Optimization(boolean minimize, Factory<Solver<Assignment.Valued<Double>>> factory) {
		this.minimize = minimize;
		this.solver = factory.create();
		this.solver.setOpt(minimize ? Solver.Operation.MIN : Solver.Operation.MAX);
	}

	@Override
	public XADDPath getInitial() {
		return new XADDPath();
	}

	@Override
	public Assignment.Valued<Double> calculate(int nodeId, XADD.XADDTNode node, XADDPath state) {
		solver.setPath(state);
		solver.setObjective(node._expr);
		return solver.solve();
	}

	@Override
	public TypePair<XADDPath> update(int nodeId, XADD.XADDINode node, XADDPath state) {
		return TypePair.make(state.extend(node, false), state.extend(node, true));
	}

	@Override
	public Assignment.Valued<Double> combine(Assignment.Valued<Double> result1, Assignment.Valued<Double> result2) {
		if(minimize) {
			return result1.value <= result2.value ? result1 : result2;
		} else {
			return result1.value >= result2.value ? result1 : result2;
		}
	}
}
