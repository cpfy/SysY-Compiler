import java.util.ArrayList;

public class Symbol {
    private String name;
    private Parser.TYPE type = null;
    private boolean isConst = false;
    private boolean isArray = false;
    private boolean global = false;

    private int num;
    private int arrayDimen; //数组维度
    private int dimen1; //第一维
    private int dimen2; //第二维

    public ArrayList<Integer> arrayList = new ArrayList<>();    //数组的初始化

    private Parser.TYPE funcReturnType = null;
    private ArrayList<Symbol> paralist = new ArrayList<>();    //Only when Func(para_count = length)

    public boolean errorE;

    Symbol next; // pointer to the next entry in the symbolTable bucket list

    //优化时，找回定义处位置用，标记ir的index
    private int irindex;

    //以下为mips使用内容
    private SymbolTable.Scope scope;
    public final int spBaseHex = 0x7fffeffc;
    public int addrOffsetDec;
    private int curReg = -1;
    public boolean loadInReg = false;   //是否加载到寄存器

    public Symbol(String name, Parser.TYPE type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Parser.TYPE getType() {
        return type;
    }

    public int getNum() {
        return num;
    }

    public Parser.TYPE getFuncReturnType() {
        return funcReturnType;
    }

    public int getParaNum() {
        if (paralist == null) {
            return 0;
        }
        return paralist.size();
    }

    public boolean getIsConst() {
        return isConst;
    }

    public boolean getIsArray() {
        return isArray;
    }

    public int getArrayDimen() {
        return arrayDimen;
    }

    public int getParaDimen(int index) {
        if (index >= paralist.size()) {
            return -10;
        }
        return paralist.get(index).arrayDimen;
    }

    public int getDimen1() {
        return dimen1;
    }

    public int getDimen2() {
        return dimen2;
    }

    public SymbolTable.Scope getScope() {
        return scope;
    }

    public int getCurReg() {
        return curReg;
    }

    public ArrayList<Symbol> getParalist() {
        return paralist;
    }

    public boolean isGlobal() {
        return global;
    }

    public int getIrindex(){
        return irindex;
    }

    //Not Get

    public void addFuncReturnType(Parser.TYPE type) {
        this.funcReturnType = type;
    }

    public void addParas(ArrayList<Symbol> symbolList) {
        paralist = new ArrayList<>();
        paralist.addAll(symbolList);
    }

    public void insertToParalist(Symbol para) {
        paralist.add(para);
    }

    public void setNum(int num) {
        this.num = num;
    }

    public void setConst() {
        isConst = true;
    }

    public void setArray() {
        isArray = true;
    }

    public void setArrayDimen(int dimen) {
        arrayDimen = dimen;
    }

    public void setDimen1(int dimen1) {
        this.dimen1 = dimen1;
    }

    public void setDimen2(int dimen2) {
        this.dimen2 = dimen2;
    }

    public void setScope(SymbolTable.Scope scope) {
        this.scope = scope;
    }

    public void setCurReg(int curReg) {
        this.curReg = curReg;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public void setIrindex(int irindex) {
        this.irindex = irindex;
    }

    //其他函数
    public boolean varnameIsFuncPara(String name) {
        if (paralist == null) {
            return false;
        }
        for (int i = 0; i < paralist.size(); i++) {
            if (paralist.get(i).getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public int varnameOrderInFuncPara(String name) {
        for (int i = 0; i < paralist.size(); i++) {
            if (paralist.get(i).getName().equals(name)) {
                return i + 1;
            }
        }
        System.err.println("Symbol / varnameOrderInFuncPara() ??? no name = " + name);
        return -10000;
    }
}
