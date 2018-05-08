package diagram;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An interface for integration methods.
 *
 * @author Samuel Kolb
 */
public interface Integrator {

    /**
     * Integrates a set of variables from the given diagram and returns the resulting diagram.
     * @param diagram   The diagram to integrate over
     * @param variables The variables to integrate
     * @return  The diagram after integration
     */
    XADDiagram integrate(XADDiagram diagram, List<Variable> variables);

    /**
     * Integrates a set of real variables from the given diagram and returns the resulting diagram.
     * @param diagram   The diagram to integrate over
     * @param variables The variables to integrate
     * @return  The diagram after integration
     */
    default XADDiagram integrateReals(XADDiagram diagram, List<String> variables) {
        return integrate(diagram, variables.stream().map(Variable::real).collect(Collectors.toList()));
    }

    /**
     * Integrates a set of Boolean variables from the given diagram and returns the resulting diagram.
     * @param diagram   The diagram to integrate over
     * @param variables The variables to integrate
     * @return  The diagram after integration
     */
    default XADDiagram integrateBools(XADDiagram diagram, List<String> variables) {
        return integrate(diagram, variables.stream().map(Variable::bool).collect(Collectors.toList()));
    }

    /**
     * Integrates a set of variables (should be all) from the given diagram and returns the resulting diagram.
     * @param diagram   The diagram to integrate over
     * @param variables The variables to integrate
     * @return  The value after integration
     */
    default Double integrateAll(XADDiagram diagram, List<Variable> variables) {
        return integrate(diagram, variables).evaluate();
    }
}
