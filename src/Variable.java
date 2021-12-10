public class Variable {
    private String type;    //todo var,num,str,array, func(函数返回。name=函数名)
    private String name;
    private int num;
    private Variable var;

    private int curReg = -1;     //当前分配的寄存器号

    private boolean kindofsymbol = false;   //是临时自定义变量(如ti) 或者局部、全局变量
    private Symbol symbol;      //若该Variable为符号表中变量，将其symbol类型附加在本类中

    private boolean usageInBlock;   //Block中的后续还会继续使用？

    //一般var， 传参时array也用了这个
    public Variable(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public Variable(String type, int num) {
        this.type = type;
        this.num = num;
    }

    //array 使用 name[var] 是输出的数组内容
    public Variable(String type, String name, Variable var) {
        this.type = type;
        this.name = name;
        this.var = var;
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

    public int getCurReg() {
        return curReg;
    }

    public Symbol getSymbol() {
        return symbol;
    }

    //访问 数组下标 var 时使用
    public Variable getVar() {
        return var;
    }

    public void setVar(Variable var) {
        this.var = var;
    }

    public void setCurReg(int curReg) {
        this.curReg = curReg;
    }

    public boolean isKindofsymbol() {
        return kindofsymbol;
    }

    public void setiskindofsymbolTrue() {
        kindofsymbol = true;
    }

    public void setSymbol(Symbol symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        if (type.equals("var") || type.equals("str") || type.equals("null")) {
            return name;

        } else if (type.equals("array")) {
            if (var != null) {  //正常数组
                return name + "[" + var.toString() + "]";

            } else {  //无var的传参array情况
                return name;
            }
        }
        return String.valueOf(num);
    }
}
