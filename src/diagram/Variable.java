package diagram;

/**
 * A typed XADD variable.
 *
 * @author Samuel Kolb
 */
public class Variable {
    private enum Type {
        BOOL, REAL
    }

    private final String name;
    private final Type type;

    public String getName() {
        return name;
    }

    public boolean isBool() {
        return type == Type.BOOL;
    }

    public boolean isReal() {
        return type == Type.REAL;
    }

    private Variable(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return String.format("VAR(%s, %s)", name, type);
    }

    /**
     * Creates a real variable.
     * @param name  The name
     * @return  The variable
     */
    public static Variable bool(String name) {
        return new Variable(name, Type.BOOL);
    }

    /**
     * Creates a real variable.
     * @param name  The name
     * @return  The variable
     */
    public static Variable real(String name) {
        return new Variable(name, Type.REAL);
    }
}
