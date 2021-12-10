import java.util.ArrayList;
import java.util.HashMap;

public class TokenDictionary {
    private static final ArrayList<String> KEYWORDS_LIST;
    private static final HashMap<String, String> KEYWORDS_DICT;
    private static final HashMap<String, String> OPERATORS_DICT;
    private static final HashMap<String, TYPE> TYPE_DICT;

    enum TYPE {
        LETTER, DIGIT, OPERATOR, SPACE, OTHERS
    }

    static {
        KEYWORDS_LIST = new ArrayList<>();
        KEYWORDS_LIST.add("main");
        KEYWORDS_LIST.add("const");
        KEYWORDS_LIST.add("int");
        KEYWORDS_LIST.add("break");
        KEYWORDS_LIST.add("continue");
        KEYWORDS_LIST.add("if");
        KEYWORDS_LIST.add("else");
        KEYWORDS_LIST.add("while");
        KEYWORDS_LIST.add("getint");
        KEYWORDS_LIST.add("printf");
        KEYWORDS_LIST.add("return");
        KEYWORDS_LIST.add("void");
    }

    static {
        KEYWORDS_DICT = new HashMap<>();
        for (String keyword : KEYWORDS_LIST) {
            KEYWORDS_DICT.put(keyword, String.format("%sTK", keyword.toUpperCase()));
        }
    }

    static {
        OPERATORS_DICT = new HashMap<>();
        OPERATORS_DICT.put("!", "NOT");
        OPERATORS_DICT.put("&&", "AND");
        OPERATORS_DICT.put("||", "OR");
        OPERATORS_DICT.put("+", "PLUS");
        OPERATORS_DICT.put("-", "MINU");
        OPERATORS_DICT.put("*", "MULT");
        OPERATORS_DICT.put("/", "DIV");
        OPERATORS_DICT.put("%", "MOD");
        OPERATORS_DICT.put("<", "LSS");
        OPERATORS_DICT.put("<=", "LEQ");
        OPERATORS_DICT.put(">", "GRE");
        OPERATORS_DICT.put(">=", "GEQ");
        OPERATORS_DICT.put("==", "EQL");
        OPERATORS_DICT.put("!=", "NEQ");
        OPERATORS_DICT.put("=", "ASSIGN");
        OPERATORS_DICT.put(";", "SEMICN");
        OPERATORS_DICT.put(",", "COMMA");
        OPERATORS_DICT.put("(", "LPARENT");
        OPERATORS_DICT.put(")", "RPARENT");
        OPERATORS_DICT.put("[", "LBRACK");
        OPERATORS_DICT.put("]", "RBRACK");
        OPERATORS_DICT.put("{", "LBRACE");
        OPERATORS_DICT.put("}", "RBRACE");
    }

    static {
        TYPE_DICT = new HashMap<>();
        //space: ASCII(1-32)
        for (int i = 1; i < 33; i++) {
            String c = String.valueOf(Character.toChars(i)[0]);
            TYPE_DICT.put(c, TYPE.SPACE);
        }
        //digit: ASCII(48-57)
        for (int i = 48; i < 58; i++) {
            String c = String.valueOf(Character.toChars(i)[0]);
            TYPE_DICT.put(c, TYPE.DIGIT);
        }
        //letter: ASCII(A-Z, 65-90; a-z, 97-122)
        for (int i = 65; i < 91; i++) {
            String c = String.valueOf(Character.toChars(i)[0]);
            TYPE_DICT.put(c, TYPE.LETTER);
            TYPE_DICT.put(c.toLowerCase(), TYPE.LETTER);
        }
        //下划线也算入Letter
        final int UNDERLINE = 95;
        TYPE_DICT.put(String.valueOf(Character.toChars(UNDERLINE)[0]), TYPE.LETTER);

        //operator
        for (String key : OPERATORS_DICT.keySet()) {
            TYPE_DICT.put(key, TYPE.OPERATOR);
        }
        //单双引号、冒号 : 也算入operator
        //TYPE_DICT.put(String.valueOf('\''), TYPE.OPERATOR);
        //TYPE_DICT.put(String.valueOf(':'), TYPE.OPERATOR);
        TYPE_DICT.put(String.valueOf('"'), TYPE.OPERATOR);
        TYPE_DICT.put(String.valueOf('|'), TYPE.OPERATOR);
        TYPE_DICT.put(String.valueOf('&'), TYPE.OPERATOR);
    }

    public TYPE queryCharType(String value) {
        if (TYPE_DICT.containsKey(value)) {
            return TYPE_DICT.get(value);
        }
        return TYPE.OTHERS;
    }

    public boolean queryIfOpCode(String value) {
        return OPERATORS_DICT.containsKey(value);
    }

    public String queryOpCode(String value) {
        return OPERATORS_DICT.get(value);
    }

    public boolean queryIfKeyword(String word) {
        //System.out.println("word = " + word);
        return KEYWORDS_DICT.containsKey(word);
    }

    public String queryKeywordCode(String word) {
        return KEYWORDS_DICT.get(word);
    }
}
