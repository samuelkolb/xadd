package diagram;

import xadd.XADD;

import java.util.List;
import java.util.stream.Collectors;

import static function.Architect.map;
import static function.Functional.fold;

/**
 * Implements integration through path enumeration (accumulating paths).
 *
 * @author Samuel Kolb
 */
public class PathEnumerationIntegrator implements Integrator {

    @Override
    public XADDiagram integrate(XADDiagram diagram, List<Variable> variables) {
        List<String> booleans = variables.stream()
                .filter(Variable::isBool)
                .map(Variable::getName)
                .collect(Collectors.toList());
        List<String> reals = variables.stream()
                .filter(Variable::isReal)
                .map(Variable::getName)
                .collect(Collectors.toList());

        XADD context = diagram.xadd;

        int result = diagram.number;
        for(String realVar : reals) {
            result = context.computeDefiniteIntegral(result, realVar);
        }

        for(String boolVar : booleans) {
            // TODO just double if it does not occur
            int trueBranch = context.substituteBoolVars(result, map(boolVar).to(true));
            int falseBranch = context.substituteBoolVars(result, map(boolVar).to(false));
            result = context.apply(trueBranch, falseBranch, XADD.SUM);
        }

        return diagram.xadd(result);
    }
}
