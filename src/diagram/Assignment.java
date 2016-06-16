package diagram;

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
		this.booleanVariables = booleanVariables;
		this.continuousVariables = continuousVariables;
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

	@Override
	public String toString() {
		return "Assignment{" + booleanVariables + ", " + continuousVariables + "}";
	}
}
