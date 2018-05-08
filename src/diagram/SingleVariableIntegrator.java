package diagram;

import java.util.List;

/**
 * Class for single-variable integration methods.
 *
 * @author Samuel Kolb
 */
public interface SingleVariableIntegrator extends Integrator {

    @Override
    default XADDiagram integrate(XADDiagram diagram, List<Variable> variables) {
        for(Variable variable : variables) {
            diagram = integrate(diagram, variable);
        }
        return diagram;
    }

    /**
     * Integrates a single variable
     * @param diagram   The diagram to integrate
     * @param variable  The variable
     * @return  The resulting diagram
     */
    XADDiagram integrate(XADDiagram diagram, Variable variable);
}
