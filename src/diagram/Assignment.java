package diagram;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by samuelkolb on 22/03/16.
 *
 * @author Samuel Kolb
 */
public class Assignment {

	public static class Valued<R> {
		public final Assignment assignment;
		public final R value;

		public Valued(Assignment assignment, R value) {
			this.assignment = assignment;
			this.value = value;
		}
	}

	private Map<String, Boolean> booleanVariables;

	public Map<String, Boolean> getBooleanVariables() {
		return booleanVariables;
	}

	private Map<String, Double> continuousVariables;

	public Map<String, Double> getContinuousVariables() {
		return continuousVariables;
	}

	public Assignment(Map<String, Boolean> booleanVariables, Map<String, Double> continuousVariables) {
		this.booleanVariables = new HashMap<>(booleanVariables);
		this.continuousVariables = new HashMap<>(continuousVariables);
	}

	public Assignment() {
		this(new HashMap<>(), new HashMap<>());
	}

	public Boolean getBool(String name) {
		return this.booleanVariables.get(name);
	}

	public Double getDouble(String name) {
		return this.continuousVariables.get(name);
	}

	public <R> Valued<R> value(R value) {
		return new Valued<R>(this, value);
	}

	/**
	 * Creates a new assignment that extends this assignment with a new boolean variable
	 * @param name	The name of the new boolean variable
	 * @param value	The value to be assigned to the new variable
	 * @return	A new assignment containing all previous variables and the new variable
	 */
	public Assignment setBool(String name, Boolean value) {
		Map<String, Boolean> boolVars = new HashMap<>(booleanVariables);
		boolVars.put(name, value);
		return new Assignment(boolVars, continuousVariables);
	}

	/**
	 * Creates a new assignment that extends this assignment with a new real variable
	 * @param name	The name of the new variable
	 * @param value	The value to be assigned to the new real variable
	 * @return	A new assignment containing all previous variables and the new variable
	 */
	public Assignment setReal(String name, Double value) {
		Map<String, Double> realVars = new HashMap<>(continuousVariables);
		realVars.put(name, value);
		return new Assignment(booleanVariables, realVars);
	}

	@Override
	public String toString() {
		return "Assignment{" + booleanVariables + ", " + continuousVariables + "}";
	}
}
