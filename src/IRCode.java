import java.util.ArrayList;

public class IRCode {
    private String type;    //15种中间代码种类
    private String rawstr;  //输出的ircode字符串格式

    private String IRstring;
    private String kind;    //const 等情况
    private String name;
    private int num;

    public boolean global;   //是否全局
    public boolean init = false;    //int,array是否有初始化值
    private ArrayList<Integer> initList = new ArrayList<>(); //数组的初始化值List
    public boolean voidreturn;

    private Variable variable;  //含有表达式等情况时，对应的Variable类型

    private int array1;     //数组形式时第1维的大小
    private int array2;     //数组形式时第2维的大小

    private String operator;
    private Variable dest;      //二元运算或一元运算中的目标变量
    private Variable oper1;     //二元运算中的第1个操作数，或一元运算的右操作数
    private Variable oper2;     //二元运算第2个操作数

    private Symbol symbol;  //含有表达式等情况时，对应的symbol类型的符号
    private SymbolTable.Scope scope;    //todo inblockoffset用到

    private String instr;   //branch跳转 的bne等类型
    private String jumploc; //branch的跳转位置

    //优化
    public boolean deleted = false;
    public int startindex = -1;

    public boolean releaseDest = false;

    //public boolean processjump = false;     //True表示jump时需处理inblockoffset,仅当break、continue时使用

    //todo 分类包括：note,label
    public IRCode(String type, String IRstring) {
        this.type = type;
        this.IRstring = IRstring;
    }

    //todo 分类包括：print
    public IRCode(String type, Variable variable) {
        this.type = type;
        this.variable = variable;
    }

    //todo "decl"类 int 的无初值情况
    //或 "funcDecl" functype + " " + funcname + "()"
    public IRCode(String type, String kind, String name) {
        this.type = type;
        this.kind = kind;
        this.name = name;
    }

    //const int 初始化
    public IRCode(String type, String kind, String name, int num) {
        this.type = type;
        this.kind = kind;
        this.name = name;
        this.num = num;
    }

    //int 初始化，初始值是一个存在variable里面的var。【废弃】改为拆成2条IRCode
    public IRCode(String type, String kind, String name, Variable var) {
        this.type = type;
        this.kind = kind;
        this.name = name;
        this.variable = var;
    }

    //todo arrayDef：类别，数组名，第一维大小，第二维（0表示无第2维）
    public IRCode(String type, String name, int array1, int array2) {
        this.type = type;
        this.name = name;
        this.array1 = array1;
        this.array2 = array2;
    }

/*    //todo arrayInit：类别，数组名，第一维大小，第二维（0表示无第2维）,初始化Exp
    public IRCode(String type, String name, int array1, int array2, String initstr) {
        this.type = type;
        this.name = name;
        this.array1 = array1;
        this.array2 = array2;
        this.IRstring = initstr;
    }*/

    public IRCode(String type, Variable dest, Variable oper1) {
        this.type = type;
        this.dest = dest;
        this.oper1 = oper1;
    }

    public IRCode(String type, String operator, Variable dest, Variable oper1, Variable oper2) {
        this.type = type;
        this.operator = operator;
        this.dest = dest;
        this.oper1 = oper1;
        this.oper2 = oper2;
    }

    //Cond部分专用
    public IRCode(String type, String instr, String jumploc, Variable oper1, Variable oper2) {
        this.type = type;
        this.instr = instr;
        this.jumploc = jumploc;
        this.oper1 = oper1;
        this.oper2 = oper2;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public int getNum() {
        return num;
    }

    public String getRawstr() {
        return rawstr;
    }

    public String getIRstring() {
        return IRstring;
    }

    public ArrayList<Integer> getInitList() {
        return initList;
    }

    public Variable getVariable() {
        return variable;
    }

    public String getOperator() {
        return operator;
    }

    public Variable getDest() {
        return dest;
    }

    public Variable getOper1() {
        return oper1;
    }

    public Variable getOper2() {
        return oper2;
    }

    public int getArray1() {
        return array1;
    }

    public int getArray2() {
        return array2;
    }

    public SymbolTable.Scope getScope() {
        return scope;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    public String getInstr() {
        return instr;
    }

    public String getJumploc() {
        return jumploc;
    }

    //非get函数
    public boolean isGlobal() {
        return global;
    }

    public void setGlobal(boolean globalBool) {
        this.global = globalBool;
    }

    public void setInitIsTrue() {
        init = true;
    }

    public void addInitList(int initnum) {
        initList.add(initnum);
    }

    public void setScope(SymbolTable.Scope scope) {
        this.scope = scope;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    public void concatRawstr() {
        switch (type) {
            case "note":
            case "label":
            case "funcPara":
                rawstr = IRstring;
                break;
            case "call":
                rawstr = type + " " + IRstring;
                break;
            case "return":
                if (voidreturn) {
                    rawstr = "ret";
                } else {
                    rawstr = "ret " + variable.toString();
                }
                break;
            case "print":
            case "push":
            case "getint":
                rawstr = type + " " + variable.toString();
                break;
            case "assign_ret":
                rawstr = variable.toString() + " = RET";
                break;
            case "assign":
                rawstr = dest.toString() + " = " + oper1.toString() + " " + operator + " " + oper2.toString();
                break;
            case "assign2":
                rawstr = dest.toString() + " = " + oper1.toString();
                break;
            case "intDecl":
                if (init) {
                    rawstr = kind + " " + name + " = " + num;
                }
                rawstr = kind + " " + name;
                break;
            case "arrayDecl":
                rawstr = "arr int " + name + "[" + array1 + "]";
                if (array2 != 0) {
                    rawstr += "[" + array2 + "]";
                }
                if (init) {
                    rawstr += " = {";
                    for (int unit : initList) {
                        rawstr += unit + ",";
                    }
                    rawstr = rawstr.substring(0, rawstr.length() - 1);
                    rawstr += "}";
                }
                break;
            case "funcDecl":
                rawstr = kind + " " + name + "()";
                break;
            case "funcpara":
                rawstr = IRstring;
                break;
            case "jump":
                rawstr = "goto " + IRstring;
                break;
            case "branch":
                rawstr = instr + " " + oper1.toString() + ", " + oper2.toString() + ", " + jumploc;
                break;
            case "setcmp":   //此处sge、sgt等instr存入了operator
                rawstr = operator + " " + dest.toString() + ", " + oper1.toString() + ", " + oper2.toString();
                break;
            default:
                break;
        }
        //System.out.println(rawstr);
    }

    //todo 数组初始化.word时用
    public String concatArrayInitNumStr() {
        String str = "";
        for (int unit : initList) {
            str += unit + ",";
        }
        str = str.substring(0, str.length() - 1);
        return str;
    }
}
