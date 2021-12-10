import java.util.ArrayList;

public class Node {
    private String type;
    private String kind;    //todo int, const int, array, const array, func 五种， 和一种funcDef时记录函数返回值类型
    private String name;
    private int num;

    private Node left;
    private Node right;
    private Node middle;

    private ArrayList<Node> leafs = new ArrayList<>();

    //private SymbolTable.Scope scope;

    public Node(String type) {
        this.type = type;
    }

    //Number
    public Node(String type, int num) {
        this.type = type;
        this.num = num;
    }

    //Ident
    public Node(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public Node(String type, Node left) {
        this.type = type;
        this.left = left;
    }

    public Node(String type, Node left, Node right) {
        this.type = type;
        this.left = left;
        this.right = right;
    }

    //IfStatement
    public Node(String type, Node left, Node middle, Node right) {
        this.type = type;
        this.left = left;
        this.middle = middle;
        this.right = right;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public Node getLeft() {
        return left;
    }

    public Node getMiddle() {
        return middle;
    }

    public Node getRight() {
        return right;
    }

    public int getNum() {
        return num;
    }

    public ArrayList<Node> getLeafs() {
        return leafs;
    }

    /*public SymbolTable.Scope getScope() {
        return scope;
    }*/

    //set系列


    public void setNum(int num) {
        this.num = num;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public void setLeft(Node left) {
        this.left = left;
    }

    public void setRight(Node right) {
        this.right = right;
    }

    public void addNode(Node n) {
        leafs.add(n);
    }

    public void setHeaderScope() {
        //this.scope = SymbolTable.headerScope;
    }

    public int calcuValue() {
        if (OperDict.OPERATOR_LIST.contains(type)) {
            switch (type) {
                case "+":
                    if (right != null) {
                        return left.calcuValue() + right.calcuValue();
                    }
                    return left.calcuValue();
                case "-":
                    if (right != null) {
                        return left.calcuValue() - right.calcuValue();
                    }
                    return -left.calcuValue();
                case "*":
                    return left.calcuValue() * right.calcuValue();
                case "/":
                    return left.calcuValue() / right.calcuValue();
                case "%":
                    return left.calcuValue() % right.calcuValue();
                default:
                    System.err.println("Error calcuValue.");
                    return 0;
            }

        } else if (type.equals("Number")) {
            return num;

        } else if (type.equals("Ident")) {      //todo 包括函数调用func；整数int；数组array
            if (kind.equals("func")) {
                System.err.println("Node / calcuValue() / calcu func ing ???");

            } else if (kind.equals("const int") || kind.equals("int")) {
                Symbol symbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
                return symbol.getNum();

            } else if (kind.equals("const array") || kind.equals("array")) {   //array 与 const array
                Symbol symbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);

                if (right != null) {    //2维数组
                    int dimen2 = symbol.getDimen2();
                    int index1 = left.calcuValue();
                    int index2 = right.calcuValue();
                    int index = index1 * dimen2 + index2;
                    return symbol.arrayList.get(index);

                } else {  //1维数组
                    int index = left.calcuValue();    //dimen1 = left; dimen2 = right
                    return symbol.arrayList.get(index);
                }

            } else {   //kind.equals("int") || array. Error！
                System.err.println("Node / calcuValue() / calcu Non-Const int / array ???");
            }

        } else {
            System.err.println("Node / calcuValue(): type = " + type);
        }
        return -213491479;
    }

    /*public String calcuExp() {
        if (type.equals("Ident")) {
            if (kind.equals("int")) {
                return name;

            } else if (kind.equals("array")) {
                int dnum1 = left.calcuValue();
                if (right != null) {
                    //说明是2维
                    int dnum2 = right.calcuValue();
                    return name + "[" + dnum1 + "][" + dnum2 + "]";
                } else {
                    return name + "[" + dnum1 + "]";
                }

            } else if (kind.equals("func")) {
                String expstr = name;
                expstr += "(";
                for (Node rparam : left.getLeafs()) {
                    String texp = rparam.calcuExp();
                    if (expstr.charAt(expstr.length() - 1) == '(') {
                        expstr += texp;
                    } else {
                        expstr += "," + texp;
                    }
                }
                expstr += ")";
                return expstr;
            }

        } else if (type.equals("Number")) {
            //System.out.println(num);
            return String.valueOf(num);

        } else if (OperDict.OPERATOR_LIST.contains(type)) {
            //System.out.println(type);
            switch (type) {
                case "+":
                    if (right != null) {
                        return left.calcuExp() + "+" + right.calcuExp();
                    }
                    return left.calcuExp();
                case "-":
                    if (right != null) {
                        return left.calcuExp() + "-" + right.calcuExp();
                    }
                    return "-" + left.calcuExp();
                case "*":
                    return left.calcuExp() + "*" + right.calcuExp();
                case "/":
                    return left.calcuExp() + "/" + right.calcuExp();
                case "%":
                    return left.calcuExp() + "%" + right.calcuExp();
                default:
                    System.err.println("Error calcuExp.");
                    return null;
            }
        } else {
            System.err.println("??? what type ???");
            return null;
        }
        return null;
    }*/
}
