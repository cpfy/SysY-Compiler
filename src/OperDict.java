import java.util.ArrayList;

public class OperDict {
    public static final ArrayList<String> OPERATOR_LIST;
    public static final ArrayList<String> OPCMP_LIST;

    static {
        OPERATOR_LIST = new ArrayList<>();
        OPERATOR_LIST.add("+");
        OPERATOR_LIST.add("-");
        OPERATOR_LIST.add("*");
        OPERATOR_LIST.add("/");
        OPERATOR_LIST.add("%");
    }

    static {
        OPCMP_LIST = new ArrayList<>();
        OPCMP_LIST.add(">");
        OPCMP_LIST.add("<");
        OPCMP_LIST.add(">=");
        OPCMP_LIST.add("<=");
        OPCMP_LIST.add("==");
        OPCMP_LIST.add("!=");
        OPCMP_LIST.add("&&");
        OPCMP_LIST.add("||");
    }
}
