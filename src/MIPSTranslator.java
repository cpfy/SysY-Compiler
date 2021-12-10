import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MIPSTranslator {
    private ArrayList<IRCode> irList;
    private ArrayList<String> mipsList;
    private HashMap<IRCode, String> printstrMap;

    private int tabcount = 0;
    private int printcount = 0;

    private final String tab = "\t";

    private Register register;

    private int spoffset = 0;           //正数，统一度量衡了
    private boolean innerfunc = false;   //标记此时在函数体内
    private int infuncoffset = 0;   //正数, func内的偏移
    private Symbol curFunc;

    //private boolean nowispushing = false;
    //private int pushoffset = 0;         //标记push状态下若需要访问lw、sw时需要额外使sp的偏移量，为正数
    private ArrayList<Instrs> pushwaitList;     //需要先压现场寄存器入栈，因此push时请各个变量延迟进入这里等待
    //private int inblockoffset = 0;      //基本块内偏移，正数

    //private boolean indecl = true;
    private boolean infuncdef = false;
    private boolean inmain = false;     //放到global主要用于main函数return 0时的判断

    public MIPSTranslator(ArrayList<IRCode> irList) {
        this.irList = irList;
        this.mipsList = new ArrayList<>();
        this.printstrMap = new HashMap<>();
        this.register = new Register();
        this.pushwaitList = new ArrayList<>();
    }

    public void tomips(int mipsOutput) {
        mipsGenerate();

        if (mipsOutput == 1) {
            try {
                writefile("mips.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writefile(String dir) throws IOException {
        File file = new File(dir);
        FileWriter writer = new FileWriter(file);
        System.out.println("输出mips.txt...");
        for (String str : mipsList) {
            System.out.println(str);
            writer.write(str + "\n");
        }
        writer.flush();
        writer.close();
    }

    private void mipsGenerate() {
        add(".data");
        tabcount += 1;

        collectPrintStr();

        for (IRCode code : irList) {
            if (inmain) {
                addBranchStmt(code);    //todo 不包括常量、函数定义相关

            } else if (infuncdef) {
                addFuncdefStmt(code);

                if (code.getType().equals("note") && code.getRawstr().equals("#Start MainFunc")) {
                    inmain = true;
                    add("main:");
                    tabcount += 1;
                }

            } else {    //表明位于 indecl
                addDeclStmt(code);

                if (code.getType().equals("note") && code.getRawstr().equals("#Start FuncDecl")) {
                    infuncdef = true;
                    add(".text");
                    add(tab + "j main");
                }
            }
        }
        addProgramEnd();
    }

    private void collectPrintStr() {
        int printstr_count = 0;
        for (IRCode code : irList) {
            if (code.getType().equals("note") && code.getRawstr().equals("#Start Print")) {
                printcount += 1;
                printstr_count = 1;
            }

            if (code.getType().equals("print") && code.getVariable().getType().equals("str")) {
                String printstr = code.getVariable().getName();
                String strconstname = "print" + printcount + "_str" + printstr_count;
                String strconst = strconstname + ": .asciiz" + tab + "\"" + printstr + "\"";
                printstrMap.put(code, strconstname);
                printstr_count += 1;
                add(strconst);
            }
        }

        tabcount -= 1;
    }

    private void addBranchStmt(IRCode code) {
        String type = code.getType();
        switch (type) {
            case "note":
            case "label":
                addNotes(code);
                break;
            case "print":
                addPrints(code);
                break;
            case "jump":
                addJump(code);
                break;
            case "branch":
                addCompareBranch(code);
                break;
            case "setcmp":
                addSetCmp(code);
                break;
            case "getint":
                addGetint(code);
                break;
            case "push":
                addPush(code);
                break;
            case "call":
                addCall(code);
                break;
            case "assign":
                addAssign(code);
                break;
            case "assign2":
                addAssign2(code);
                break;
            case "assign_ret":      //形如i = RET，调用函数返回赋值
                addAssignRet(code);
                break;
            case "arrayDecl":
                addArrayDecl(code);
                break;
            case "intDecl":
                addIntDecl(code);
                break;
            case "return":          //函数内return返回值
                addReturn(code);
                break;
                /*case "funcDecl":
                break;
            case "funcpara":
                break;*/
            default:
                break;
        }
    }

    private void addNotes(IRCode code) {
        if (code.getRawstr().equals("#Out Block")) {
            SymbolTable.Scope scope = code.getScope();
            int iboffset = scope.inblockoffset;
            if (iboffset == 0) {
                add("# addi $sp, $sp, 0 (need sp+-)");

            } else {
                add("addi $sp, $sp, " + iboffset);
                //scope.inblockoffset = 0;      //xs,其实根本不用清零,编译程序只扫描执行一次
                if (innerfunc) {
                    infuncoffset -= iboffset;
                } else {
                    spoffset -= iboffset;   //可以不弄，不影响
                }
            }

        } else if (code.getRawstr().equals("#Out Block WhileCut")) {
            SymbolTable.Scope scope = code.getScope();
            int sumoffset = 0;

            while (/*scope.type == null || */!scope.type.equals("while")) {   //不断搜索，直到 father中第1个while块 的外面，路上全部清零+计数
                sumoffset += scope.inblockoffset;
                //scope.inblockoffset = 0;
                scope = scope.father;
            }
            sumoffset += scope.inblockoffset;
            //scope.inblockoffset = 0;
            scope = scope.father;

            if (sumoffset == 0) {
                add("# addi $sp, $sp, 0 (inblockoffset no need sp+-)");

            } else {
                add("addi $sp, $sp, " + sumoffset);
                //scope.inblockoffset = 0;          //xs,其实根本不用清零,编译程序只扫描执行一次
                /*if (innerfunc) {
                    infuncoffset -= sumoffset;
                } else {
                    spoffset -= sumoffset;   //可以不弄，不影响
                }*/
                //todo 非正常跳出不处理func与sp offset！
            }

        } else {
            add(code.getRawstr());
        }
    }

    private void addJump(IRCode code) {
        add("j " + code.getIRstring());
    }

    private void addCompareBranch(IRCode code) {
        String cmpinstr = code.getInstr();
        String jumplabel = code.getJumploc();

        Variable oper1 = code.getOper1();
        Variable oper2 = code.getOper2();
        String type1 = oper1.getType();
        String type2 = oper2.getType();

        if (type1.equals("var") && type2.equals("var")) {
            String op1reg = "null_reg!!";
            String op2reg = "null_reg!!";
            boolean op1registmp = false;
            boolean op2registmp = false;

            int tmpregforop1 = 0;
            int tmpregforop2 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }

            //todo 判定有隐患
            if (oper2.isKindofsymbol()) {
                Symbol oper2symbol = oper2.getSymbol();
                if (innerfunc && !oper2symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop2 = register.applyTmpRegister();
                    op2reg = register.getRegisterNameFromNo(tmpregforop2);
                    op2registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper2, op2reg);       //包装从函数体sp读取到reg过程

                } else if (oper2symbol.isGlobal() && oper2symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper2symbol.getName();
                    op2reg = searchRegName(oper2);
                    add("lw $" + op2reg + ", Global_" + globalvarname);

                } else {
                    op2reg = searchRegName(oper2);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op2reg, oper2symbol);
                }
            } else {
                op2reg = searchRegName(oper2);
            }

            add(cmpinstr + " $" + op1reg + ", $" + op2reg + ", " + jumplabel);

            if (op1registmp) {
                register.freeTmpRegister(tmpregforop1);
            } else {
                register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
            }

            if (op2registmp) {
                register.freeTmpRegister(tmpregforop2);
            } else {
                register.freeRegister(oper2);     //理论上需要判定活跃性，或是否为tmp
            }

        } else if ((type1.equals("var") && type2.equals("num")) || (type1.equals("num") && type2.equals("var"))) {
            boolean reverse = false;
            if (type1.equals("num") && type2.equals("var")) {
                Variable opertmp = oper1;
                oper1 = oper2;
                oper2 = opertmp;
                reverse = true;
            }
            int op2num = oper2.getNum();

            String op1reg = "null_reg!!";
            boolean op1registmp = false;
            int tmpregforop1 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }

            if (reverse) {
                cmpinstr = reverseCompareInstr(cmpinstr);
            }

            add(cmpinstr + " $" + op1reg + ", " + op2num + ", " + jumplabel);

        } else {    //两个均为数字
            int num1 = oper1.getNum();
            int num2 = oper2.getNum();

            boolean jump = false;
            switch (cmpinstr) {
                case "beq":
                    if (num1 == num2) {
                        jump = true;
                    }
                    break;
                case "bne":
                    if (num1 != num2) {
                        jump = true;
                    }
                    break;
                case "bge":
                    if (num1 >= num2) {
                        jump = true;
                    }
                    break;
                case "ble":
                    if (num1 <= num2) {
                        jump = true;
                    }
                    break;
                case "bgt":
                    if (num1 > num2) {
                        jump = true;
                    }
                    break;
                case "blt":
                    if (num1 < num2) {
                        jump = true;
                    }
                    break;
                default:
                    break;
            }

            if (jump) {
                add("# jump branch always true.");
                add("j " + jumplabel);
            } else {
                add("# jump branch always false.");
            }
        }
    }

    private void addSetCmp(IRCode code) {
        String cmpinstr = code.getOperator();  //创建ircode时instr存进了operator

        Variable oper1 = code.getOper1();
        Variable oper2 = code.getOper2();
        String type1 = oper1.getType();
        String type2 = oper2.getType();

        Variable dest = code.getDest();
        String destreg = searchRegName(dest);

        if (type1.equals("var") && type2.equals("var")) {
            String op1reg = "null_reg!!";
            String op2reg = "null_reg!!";
            boolean op1registmp = false;
            boolean op2registmp = false;

            int tmpregforop1 = 0;
            int tmpregforop2 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }

            //todo 判定有隐患
            if (oper2.isKindofsymbol()) {
                Symbol oper2symbol = oper2.getSymbol();
                if (innerfunc && !oper2symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop2 = register.applyTmpRegister();
                    op2reg = register.getRegisterNameFromNo(tmpregforop2);
                    op2registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper2, op2reg);       //包装从函数体sp读取到reg过程

                } else if (oper2symbol.isGlobal() && oper2symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper2symbol.getName();
                    op2reg = searchRegName(oper2);
                    add("lw $" + op2reg + ", Global_" + globalvarname);

                } else {
                    op2reg = searchRegName(oper2);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op2reg, oper2symbol);
                }
            } else {
                op2reg = searchRegName(oper2);
            }

            add(cmpinstr + " $" + destreg + ", $" + op1reg + ", $" + op2reg);

            if (op1registmp) {
                register.freeTmpRegister(tmpregforop1);
            } else {
                register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
            }

            if (op2registmp) {
                register.freeTmpRegister(tmpregforop2);
            } else {
                register.freeRegister(oper2);     //理论上需要判定活跃性，或是否为tmp
            }

        } else if ((type1.equals("var") && type2.equals("num")) || (type1.equals("num") && type2.equals("var"))) {
            boolean reverse = false;
            if (type1.equals("num") && type2.equals("var")) {
                Variable opertmp = oper1;
                oper1 = oper2;
                oper2 = opertmp;
                reverse = true;
            }
            int op2num = oper2.getNum();

            String op1reg = "null_reg!!";
            boolean op1registmp = false;
            int tmpregforop1 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }

            if (reverse) {
                cmpinstr = reverseCompareInstr(cmpinstr);
            }

            int tmpregforop2 = register.applyTmpRegister();
            String op2reg = register.getRegisterNameFromNo(tmpregforop2);

            add("li $" + op2reg + ", " + op2num);
            add(cmpinstr + " $" + destreg + ", $" + op1reg + ", $" + op2reg);

            if (op1registmp) {
                register.freeTmpRegister(tmpregforop1);
            } else {
                register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
            }

            register.freeTmpRegister(tmpregforop2);

        } else {    //两个均为数字
            int num1 = oper1.getNum();
            int num2 = oper2.getNum();

            boolean reljudge = false;   //判定RelExp是否成立
            switch (cmpinstr) {
                case "sge":
                    if (num1 >= num2) {
                        reljudge = true;
                    }
                    break;
                case "sgt":
                    if (num1 > num2) {
                        reljudge = true;
                    }
                    break;
                case "sle":
                    if (num1 <= num2) {
                        reljudge = true;
                    }
                    break;
                case "slt":
                    if (num1 < num2) {
                        reljudge = true;
                    }
                    break;
                case "seq":
                    if (num1 == num2) {
                        reljudge = true;
                    }
                    break;
                case "sne": //暂无用
                    if (num1 != num2) {
                        reljudge = true;
                    }
                    break;
                default:
                    break;
            }

            if (reljudge) {
                add("# RelExp judge always true.");
                add("li $" + destreg + ", 1");
            } else {
                add("# RelExp judge always false.");
                add("li $" + destreg + ", 0");
            }
        }
    }

    private void addPrints(IRCode code) {
        Variable printvar = code.getVariable();
        String type = printvar.getType();

        if (type.equals("str")) {
            String printstrloc = printstrMap.get(code);

            add("li $v0, 4");
            add("la $a0, " + printstrloc);
            add("syscall");

        } else if (type.equals("num")) {
            int num = printvar.getNum();

            add("li $v0, 1");
            add("li $a0, " + num);
            add("syscall");

        } else if (type.equals("var")) {    //todo 函数体内可以这么搞？【解决】
            //todo 判定有隐患
            if (printvar.isKindofsymbol()) {
                Symbol printvarsymbol = printvar.getSymbol();
                if (innerfunc && !printvarsymbol.isGlobal()) {    //函数内+symbol需要lw //todo 必须先判定，防止全局变量覆盖局部变量
                    add("li $v0, 1");
                    loadWordOfInfuncVarFromSpToReg(printvar, "a0");       //包装从函数体sp读取到reg
                    add("syscall");

                } else if (printvarsymbol.isGlobal() && printvarsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = printvarsymbol.getName();

                    add("li $v0, 1");
                    add("lw $a0" + ", Global_" + globalvarname);  //todo 也许不用加$zero
                    add("syscall");

                } else {
                    add("li $v0, 1");
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg("a0", printvarsymbol);
                    add("syscall");
                }
            } else {
                String varregname = searchRegName(printvar); //todo 不用分类是否为symbol！searchregname函数处理了

                add("li $v0, 1");
                add("move $a0, $" + varregname);
                add("syscall");

                register.freeRegister(printvar);     //理论上需要判定活跃性，或是否为tmp
            }


        } else {   //array:不会有此情况，聚合为了：t4 = l_const_6[t3] 和 print t4
            System.err.println("MIPSTranslator / addPrints() ??? print type");
        }
    }

    private void addGetint(IRCode code) {
        Variable getintvar = code.getVariable();  //todo 数组赋值【有可能】
        String type = getintvar.getType();
        String name = getintvar.getName();

        add("li $v0, 5");
        add("syscall");

        if (type.equals("var")) {     //读取到正常变量
            if (getintvar.isKindofsymbol()) {
                Symbol varsymbol = getintvar.getSymbol();
                if (innerfunc && !varsymbol.isGlobal()) {    //函数内+symbol需要lw
                    saveWordOfInfuncVarFromRegToSp(getintvar, "v0");

                } else if (varsymbol.isGlobal() && varsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = varsymbol.getName();
                    add("sw $v0" + ", Global_" + globalvarname);  //todo 也许不用加$zero

                } else {
                /*String savedreg = searchRegName(var);
                add("move $" + savedreg + ", $v0");*/

                    saveWordOfLocalMainfuncVarSymbolFromSpToReg("v0", varsymbol);

                }
            } else {
                String savedreg = searchRegName(getintvar);
                add("move $" + savedreg + ", $v0");
            }

        } else if (type.equals("array")) {     //读取到数组
            String arrayname = name;
            Symbol arrsymbol = getintvar.getSymbol();

            Variable offset = getintvar.getVar();    //todo 存疑[正确！]
            String offsetType = offset.getType();

            if (arrsymbol.isGlobal()) {    //全局取data段
                int tmpregno = register.applyTmpRegister();
                String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                if (offsetType.equals("num")) {    //offset = 数字
                    int offsetnum = offset.getNum();
                    add("li $" + tmpregname + ", " + offsetnum * 4);     //需要乘以4!!!
                    add("sw $v0, Global_" + arrayname + "($" + tmpregname + ")");   //与oper1主要区别lw变成sw

                } else {    //offset = var变量
                    //该函数：将任意variable加载到指定寄存器，oper1、2、dest等均可用
                    String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 不能原来这么简单粗暴;但此方法存疑

                    add("sll $" + offsetregname + ", $" + offsetregname + ", 2");   //需要乘以4!!!
                    add("sw $v0, Global_" + arrayname + "($" + offsetregname + ")");
                }

                register.freeTmpRegister(tmpregno);

            } else {    //局部取 堆栈
                if (innerfunc) {  //array数组在函数内
                    if (curFunc.varnameIsFuncPara(arrayname)) {     //数组为 函数参数
                        int tmpregno = register.applyTmpRegister();
                        int tmpregno2 = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);       //申请临时寄存器
                        String tmpregname2 = register.getRegisterNameFromNo(tmpregno2);     //申请临时寄存器2

                        int arraybaseaddroffset = calcuFuncParaOffset(arrayname);
                        add("lw $" + tmpregname + ", " + arraybaseaddroffset + "($sp)");  //取出存放array的基地址,存到tmpregname中

                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            add("sw $v0, " + numaddroffset + "($" + tmpregname + ")");

                        } else {    //offset = var变量
                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname2 + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $" + tmpregname2);
                            add("sw $v0, ($" + tmpregname + ")");
                        }

                        register.freeTmpRegister(tmpregno);
                        register.freeTmpRegister(tmpregno2);
                        //register.freeRegister(offset); 下面统一释放

                    } else {    //函数内 + 局部array
                        Symbol symbol = SymbolTable.lookupLocalTable(arrayname, Parser.TYPE.I, arrsymbol.getScope());
                        Assert.check(symbol, "MIPSTranslator / adAssign2() / dest / array / innerfunc");

                        int localarrayspoffset = calcuFuncLocalVarOffset(symbol);   //局部array的首地址
                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            localarrayspoffset += numaddroffset;
                            add("sw $v0, " + localarrayspoffset + "($sp)");

                        } else {    //offset = var变量
                            int tmpregno = register.applyTmpRegister();
                            String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $sp");
                            add("sw $v0, " + localarrayspoffset + "($" + tmpregname + ")");

                            register.freeTmpRegister(tmpregno);
                        }
                    }

                } else {  //array数组在正常结构内
                    int arraybaseaddr = arrsymbol.spBaseHex + arrsymbol.addrOffsetDec;

                    if (offsetType.equals("num")) {    //offset = 数字
                        int arraynumaddr = arraybaseaddr + offset.getNum() * 4;
                        String arroffsetHex = convertIntAddrToHex(arraynumaddr);   //地址格式int转16进制

                        add("sw $v0, " + arroffsetHex);

                    } else {    //offset = var变量
                        int tmpregno = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                        String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                        add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                        add("sw $v0, " + arraybaseaddr + "($" + tmpregname + ")");

                        register.freeTmpRegister(tmpregno);
                    }
                }
            }
            if (offset.getCurReg() != -1) {
                register.freeRegister(offset);  //统一释放存数组偏移量的reg
            }


        } else {
            System.err.println("MIPSTranslator / addGetint() / getint type  = ???");
        }
    }

    private void addPush(IRCode code) {
        //放这里会让func情况sp计算错误。不仅是这个问题，push移动了sp，push过程中若lw需要能正确访问位置，因此引入一个pushoffset
        //修改后，sp实际上没有移动，pushoffset在下面($sp)体现（之后先）。也可以省很多ALU

        //pushwaitList.add("addi $sp, $sp, -4");
        //pushoffset += 4;  //废弃。改为call时计数

        Instrs pushinstrs = new Instrs();

        Variable var = code.getVariable();
        String type = var.getType();
        if (type.equals("num")) {
            int num = var.getNum();
            int tmpregno = register.applyTmpRegister();
            String tmpregname = register.getRegisterNameFromNo(tmpregno);

            pushinstrs.addInstr(new Instr("li $" + tmpregname + ", " + num));      //无pushoffset隐患
            pushinstrs.addInstr(new Instr("sw $" + tmpregname + ", ", 0, "($sp)", "push"));

            register.freeTmpRegister(tmpregno);

        } else if (type.equals("var")) {
            //todo 判定有隐患?
            if (var.isKindofsymbol()) {
                Symbol varsymbol = var.getSymbol();
                int tmpregno = register.applyTmpRegister();
                String tmpregname = register.getRegisterNameFromNo(tmpregno);   //需要从sp中lw出来并sw

                if (innerfunc && !varsymbol.isGlobal()) {    //函数内+symbol需要lw
                    loadWordOfInfuncVarFromSpToReg(var, tmpregname, 1, pushinstrs);

                } else if (varsymbol.isGlobal() && varsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = varsymbol.getName();
                    pushinstrs.addInstr(new Instr("lw $" + tmpregname + ", Global_" + globalvarname));  //todo 也许不用加$zero

                } else {
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(tmpregname, varsymbol, 1, pushinstrs);
                }

                pushinstrs.addInstr(new Instr("sw $" + tmpregname + ", ", 0, "($sp)", "push"));

                //register.freeTmpRegister(tmpregno);   //不能放！因为实际还没存。。以下归还tmpregno
                Instr last = new Instr("#push an symbolkind var end.");  //用一个#标签包装处理
                last.hasRetReg = true;            //归还tmpregno
                last.setFreeRegNumber(tmpregno);  //释放的寄存器编号
                pushinstrs.addInstr(last);

            } else {    //临时变量如 t8
                String varregname = searchRegName(var); //不用分类是否为symbol！searchregname函数处理了

                int tmpregno = register.getRegisterNoFromName(varregname);
                pushinstrs.addInstr(new Instr("sw $" + varregname + ", ", 0, "($sp)", "push"));

                //register.freeRegister(var);     ///不能放！因为实际还没存。。以下归还var
                Instr last = new Instr("#push an nonsymbol(tmp)kind var end.");  //用一个#标签包装处理
                last.hasRetReg = true;            //归还tmpregno
                last.setFreeRegNumber(var.getCurReg());  //释放的寄存器编号 //todo 可能参数没在寄存器的情况?
                pushinstrs.addInstr(last);
            }

        } else if (type.equals("array")) {      //array, 此时传入地址，记得addpushlist
            Symbol arraysymbol = var.getSymbol();
            int tmpregno = register.applyTmpRegister();
            String tmpregname = register.getRegisterNameFromNo(tmpregno);   //需要从sp中lw出来并sw

            if (innerfunc && !arraysymbol.isGlobal()) {    //函数内+symbol需要lw
                loadAddressOfInfuncArrayVarFromSpToReg(var, tmpregname, 1, pushinstrs);     //函数内处理如b[1]或b[i]情况

            } else if (arraysymbol.isGlobal() && arraysymbol.getType() != Parser.TYPE.F) {     //全局数组
                String globalarrayname = arraysymbol.getName();

                if (var.getVar() != null) {     //处理如b[i]或b[1]等含偏移情况
                    Variable offset = var.getVar();     //此处offset 指的是array 的 index, 仅为命名统一取名offset
                    String offsetType = offset.getType();

                    if (offsetType.equals("num")) {    //offset = 数字
                        int arroffset = offset.getNum() * arraysymbol.getDimen2() * 4;    //偏移量=index * dimen2 * 4
                        pushinstrs.addInstr(new Instr("la $" + tmpregname + ", Global_" + globalarrayname + "+" + arroffset));

                    } else {    //offset = var变量
                        String offsetregname = loadWordOfAnyVariableToRegName(offset, 1, pushinstrs);
                        pushinstrs.addInstr(new Instr("sll $" + offsetregname + ", $" + offsetregname + ", 2"));   //！！！需要乘以4
                        pushinstrs.addInstr(new Instr("li $" + tmpregname + ", " + arraysymbol.getDimen2()));
                        pushinstrs.addInstr(new Instr("mult $" + offsetregname + ", $" + tmpregname));
                        pushinstrs.addInstr(new Instr("mflo $" + tmpregname));

                        pushinstrs.addInstr(new Instr("la $" + tmpregname + ", Global_" + globalarrayname + "($" + tmpregname + ")"));

                        //以下处理： register.freeRegister(offset);
                        if (offset.getCurReg() != -1) {
                            //register.freeRegister(offset);  //统一释放存数组偏移量的reg.此处不能放

                            Instr last = new Instr("#push/la an hasoffset global array end.");  //用一个#标签包装处理
                            last.hasRetReg = true;        //最后一个语句，附加一个归还offsetReg操作
                            last.setFreeRegNumber(offset.getCurReg());  //todo getCurReg方法存疑
                            pushinstrs.addInstr(last);

                        } else {
                            pushinstrs.addInstr(new Instr("#push an hasoffset global array end."));  //用一个#标签包装处理);
                        }
                    }

                } else {
                    pushinstrs.addInstr(new Instr("la $" + tmpregname + ", Global_" + globalarrayname));
                }

            } else {     //局部数组
                loadAddressOfLocalMainfuncArrayVarSymbolFromSpToReg(tmpregname, arraysymbol, 1, pushinstrs, var);    //函数内处理如b[1]情况
                //todo 处理offset！
            }

            pushinstrs.addInstr(new Instr("sw $" + tmpregname + ", ", 0, "($sp)", "push"));

            Instr last = new Instr("#push an global array end.");  //用一个#标签包装处理
            last.hasRetReg = true;            //归还tmpregno
            last.setFreeRegNumber(tmpregno);  //释放的寄存器编号
            pushinstrs.addInstr(last);

           /* String varregname = searchRegName(var);     //todo 可能参数没在寄存器的情况
            add("sw $" + varregname + ", ($sp)");*/

        } else {
            System.err.println("MIPSTranslator / addPush(): ?? type = " + type);
        }

        pushwaitList.add(pushinstrs);
    }

    private void addCall(IRCode code) {
        String funcname = code.getIRstring();

        //todo 活跃寄存器入栈！！！
        ArrayList<Integer> activeRegs = register.getActiveRegList();
        int activeRegNum = activeRegs.size();
        int activeRegOffset = activeRegNum * 4;     //正数

        for (int i = activeRegNum - 1; i >= 0; i--) {   //倒着推进去，正着取出来
            String regname = register.getRegisterNameFromNo(activeRegs.get(i));
            add("addi $sp, $sp, -4");
            add("sw $" + regname + ", ($sp)");
            System.out.println("Push Active Reg :" + regname);
        }

        Symbol symbol = SymbolTable.lookupFullTable(funcname, Parser.TYPE.F, SymbolTable.foreverGlobalScope);
        int paras = symbol.getParaNum();
        int paraAndraOffset = (paras + 1) * 4;

        //todo 参数入栈。此处有bug,应当仅处理func参数个数个pushinstr，如：fun3(2, fun3(3, 6))
        int pushoffset = 0; //负数

        ArrayList<Integer> freeRegNoList = new ArrayList<>();
        for (int i = pushwaitList.size() - paras; i < pushwaitList.size(); i++) {
            Instrs pushinstrs = pushwaitList.get(i);
            pushoffset -= 4;
            for (Instr pinstr : pushinstrs.getInstrList()) {
                if (pinstr.pushoffset) {
                    add(pinstr.toString(pushoffset));
                } else if (pinstr.activeRegoffset) {
                    add(pinstr.toString(activeRegOffset));
                } else {    //todo 正常字符串？
                    add(pinstr.toString(0));
                }
                //释放reg
                if (pinstr.hasRetReg) {
                    //register.freeTmpRegister(pinstr.freeRegNumber);
                    //todo 得先屯着，活跃寄存器出栈后一起free
                    freeRegNoList.add(pinstr.getFreeRegNumber());
                }
            }
        }

        add("addi $sp, $sp, " + (-paraAndraOffset));
        add("sw $ra, ($sp)");       //保存$ra，为处理递归准备

        add("jal " + "Func_" + funcname);       //todo 未处理函数体内局部变量导致的sp移动，需return时+sp

        add("lw $ra, ($sp)");       //加载$ra，为处理递归准备
        add("addi $sp, $sp, " + paraAndraOffset);    //移动push para的sp偏移

        //todo 活跃的寄存器出栈！！！
        for (int i = 0; i < activeRegNum; i++) {   //倒着推进去，正着取出来
            String regname = register.getRegisterNameFromNo(activeRegs.get(i));
            add("lw $" + regname + ", ($sp)");
            add("addi $sp, $sp, 4");
            System.out.println("Load Active Reg :" + regname);
        }

        //释放寄存器
        for (int no : freeRegNoList) {
            register.freeTmpRegister(no);
        }

        //复原各种状态
        //pushwaitList.clear();
        //System.out.println("list size=" + pushwaitList.size() + "; paras = " + paras);
        int size = pushwaitList.size();
        for (int i = size - 1; i >= size - paras; i--) {
            //System.out.println("i=" + i);
            pushwaitList.remove(i);
        }
    }

    private void addAssign(IRCode code) {
        //todo infunc时不知道会不会searchRegName出错【答】会的！如t2 = a + b。右侧可能是para，目前，左一定是var
        Variable dest = code.getDest();
        Variable oper1 = code.getOper1();
        Variable oper2 = code.getOper2();
        String operator = code.getOperator();
        String type1 = oper1.getType();
        String type2 = oper2.getType();

        String dreg = searchRegName(dest);

        if (type1.equals("var") && type2.equals("var")) {
            String op1reg = "null_reg!!";
            String op2reg = "null_reg!!";
            boolean op1registmp = false;
            boolean op2registmp = false;

            int tmpregforop1 = 0;
            int tmpregforop2 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }

            //todo 判定有隐患
            if (oper2.isKindofsymbol()) {
                Symbol oper2symbol = oper2.getSymbol();
                if (innerfunc && !oper2symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop2 = register.applyTmpRegister();
                    op2reg = register.getRegisterNameFromNo(tmpregforop2);
                    op2registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper2, op2reg);       //包装从函数体sp读取到reg过程

                } else if (oper2symbol.isGlobal() && oper2symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper2symbol.getName();
                    op2reg = searchRegName(oper2);
                    add("lw $" + op2reg + ", Global_" + globalvarname);

                } else {
                    op2reg = searchRegName(oper2);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op2reg, oper2symbol);
                }
            } else {
                op2reg = searchRegName(oper2);
            }

            switch (operator) {
                case "+":
                    //add("add $" + dreg + ", $" + op1reg + ", $" + op2reg);
                    add("addu $" + dreg + ", $" + op1reg + ", $" + op2reg);
                    break;
                case "-":
                    //add("sub $" + dreg + ", $" + op1reg + ", $" + op2reg);
                    add("subu $" + dreg + ", $" + op1reg + ", $" + op2reg);
                    break;
                case "*":
                    add("mult $" + op1reg + ", $" + op2reg);
                    add("mflo $" + dreg);
                    break;
                case "/":
                    add("div $" + op1reg + ", $" + op2reg);
                    add("mflo $" + dreg);
                    break;
                case "%":
                    add("div $" + op1reg + ", $" + op2reg);
                    add("mfhi $" + dreg);
                    break;
            }

            if (op1registmp) {
                register.freeTmpRegister(tmpregforop1);
            } else {
                register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
            }

            if (op2registmp) {
                register.freeTmpRegister(tmpregforop2);
            } else {
                register.freeRegister(oper2);     //理论上需要判定活跃性，或是否为tmp
            }

        } else if ((type1.equals("var") && type2.equals("num")) || (type1.equals("num") && type2.equals("var"))) {

            boolean reverse = false;
            if (type1.equals("num") && type2.equals("var")) {
                Variable opertmp = oper1;
                oper1 = oper2;
                oper2 = opertmp;
                reverse = true;
            }

            int num = oper2.getNum();

            String op1reg = "null_reg!!";
            boolean op1registmp = false;
            int tmpregforop1 = 0;

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1symbol.isGlobal()) {    //函数内+symbol需要lw
                    tmpregforop1 = register.applyTmpRegister();
                    op1reg = register.getRegisterNameFromNo(tmpregforop1);
                    op1registmp = true;

                    loadWordOfInfuncVarFromSpToReg(oper1, op1reg);       //包装从函数体sp读取到reg过程

                    //register.freeTmpRegister(tmpregforop1);
                    // todo 有隐患，但理论上可以此时释放【答】不行，可能与oper2冲突。md，先不还了

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1symbol.getName();
                    op1reg = searchRegName(oper1);
                    add("lw $" + op1reg + ", Global_" + globalvarname);

                } else {
                    op1reg = searchRegName(oper1);
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(op1reg, oper1symbol);
                }
            } else {
                op1reg = searchRegName(oper1);
            }


            switch (operator) {
                case "+":
                    add("addi $" + dreg + ", $" + op1reg + ", " + num);
                    break;
                case "-":
                    if (reverse) {
                        add("sub $" + op1reg + ", $zero, $" + op1reg);
                        add("addi $" + dreg + ", $" + op1reg + ", " + num);

                    } else {
                        add("subi $" + dreg + ", $" + op1reg + ", " + num);
                    }
                    break;
                case "*":
                    //todo 违规用了一下$v1，可能与tmpreg1冲突
                    MultOptimize(dreg, op1reg, num);
                    break;
                case "/":
                    DivOptimize(dreg, op1reg, num, reverse);
                    break;
                case "%":
                    add("li $v1, " + num);
                    if (reverse) {
                        add("div $v1, $" + op1reg);
                    } else {
                        add("div $" + op1reg + ", $v1");
                    }
                    add("mfhi $" + dreg);
                    break;
            }
            /*register.freeRegister(oper1);*/

            if (op1registmp) {
                register.freeTmpRegister(tmpregforop1);
            } else {
                register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
            }

        } else {    //两个均为数字
            int num;
            switch (operator) {
                case "+":
                    num = oper1.getNum() + oper2.getNum();
                    add("li $" + dreg + ", " + num);
                    break;
                case "-":
                    num = oper1.getNum() - oper2.getNum();
                    add("li $" + dreg + ", " + num);
                    break;
                case "*":
                    num = oper1.getNum() * oper2.getNum();
                    add("li $" + dreg + ", " + num);
                    break;
                case "/":
                    num = oper1.getNum() / oper2.getNum();
                    add("li $" + dreg + ", " + num);
                    break;
                case "%":
                    num = oper1.getNum() % oper2.getNum();
                    add("li $" + dreg + ", " + num);
                    break;
            }
        }
    }

    private void addAssign2(IRCode code) {  //需要处理 左部 或 右部 是数组的情况

        Variable dest = code.getDest();
        Variable oper1 = code.getOper1();

        //先处理右侧oper1
        String regForOper1 = searchRegName(oper1);
        String typeOper1 = oper1.getType();

        if (typeOper1.equals("array")) {    //会有如 t1 = array[2][3]; t1 = array[t2]情况
            String arrayname = oper1.getName();
            Symbol symbol_oper1 = oper1.getSymbol();

            Variable offset = oper1.getVar();
            String offsetType = offset.getType();

            if (symbol_oper1.isGlobal()) {    //全局取data段
                int tmpregno = register.applyTmpRegister();
                String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                if (offsetType.equals("num")) {    //offset = 数字
                    add("li $" + tmpregname + ", " + offset.getNum() * 4);    //！！！需要乘以4，省一步sll
                    add("lw $" + regForOper1 + ", Global_" + arrayname + "($" + tmpregname + ")");

                } else {    //offset = var变量
                    String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                    add("sll $" + offsetregname + ", $" + offsetregname + ", 2");   //！！！需要乘以4
                    add("lw $" + regForOper1 + ", Global_" + arrayname + "($" + offsetregname + ")");
                }

                register.freeTmpRegister(tmpregno);
                //register.freeRegister(offset);

            } else {    //局部取 堆栈。此处还要细分是否在函数体内的情况，地址计算不同
                if (innerfunc) {  //array数组在函数内
                    if (curFunc.varnameIsFuncPara(arrayname)) {     //函数参数为数组时处理
                        int tmpregno = register.applyTmpRegister();
                        int tmpregno2 = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器
                        String tmpregname2 = register.getRegisterNameFromNo(tmpregno2);   //申请临时寄存器

                        int arraybaseaddroffset = calcuFuncParaOffset(arrayname);
                        add("lw $" + tmpregname + ", " + arraybaseaddroffset + "($sp)");  //取出存放array的基地址,存到tmpregname中

                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            add("lw $" + regForOper1 + ", " + numaddroffset + "($" + tmpregname + ")");

                        } else {    //offset = var变量
                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname2 + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $" + tmpregname2);
                            add("lw $" + regForOper1 + ", ($" + tmpregname + ")");
                        }

                        register.freeTmpRegister(tmpregno);
                        register.freeTmpRegister(tmpregno2);
                        //register.freeRegister(offset); 下面统一释放

                    } else {     //函数内 + 局部数组
                        Symbol symbol = SymbolTable.lookupLocalTable(arrayname, Parser.TYPE.I, symbol_oper1.getScope());
                        Assert.check(symbol, "MIPSTranslator / adAssign2() / oper1 / array / innerfunc");

                        int localarrayspoffset = calcuFuncLocalVarOffset(symbol);   //局部array的首地址
                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            localarrayspoffset += numaddroffset;
                            add("lw $" + regForOper1 + ", " + localarrayspoffset + "($sp)");

                        } else {    //offset = var变量
                            int tmpregno = register.applyTmpRegister();
                            String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $sp");
                            add("lw $" + regForOper1 + ", " + localarrayspoffset + "($" + tmpregname + ")");

                            register.freeTmpRegister(tmpregno);
                        }
                    }

                } else {  //array数组在正常结构内
                    int arraybaseaddr = symbol_oper1.spBaseHex + symbol_oper1.addrOffsetDec;

                    if (offsetType.equals("num")) {    //offset = 数字
                        int arraynumaddr = arraybaseaddr + offset.getNum() * 4;
                        String arroffsetHex = convertIntAddrToHex(arraynumaddr);   //地址格式int转16进制

                        add("lw $" + regForOper1 + ", " + arroffsetHex);

                    } else {    //offset = var变量
                        int tmpregno = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                        String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                        add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                        add("lw $" + regForOper1 + ", " + arraybaseaddr + "($" + tmpregname + ")");

                        register.freeTmpRegister(tmpregno);
                    }
                }
            }

            if (offset.getCurReg() != -1) {
                register.freeRegister(offset);  //统一释放存数组偏移量的reg
            }

        } else if (typeOper1.equals("var")) {   //访问 右值为 var的各种情况

            //todo 判定有隐患
            if (oper1.isKindofsymbol()) {
                Symbol oper1symbol = oper1.getSymbol();
                if (innerfunc && !oper1.getSymbol().isGlobal()) {    //函数内+symbol需要lw
                    loadWordOfInfuncVarFromSpToReg(oper1, regForOper1);

                } else if (oper1symbol.isGlobal() && oper1symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = oper1.getSymbol().getName();
                    add("lw $" + regForOper1 + ", Global_" + globalvarname);  //todo 也许不用加$zero

                } else {
                    //System.err.println("MIPSTranslator addAssign2(): ??? unknown type = " + typeOper1);
                    //什么也不用管 regForoper1处理好了
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg(regForOper1, oper1symbol);
                }
            } else {
                //什么也不用管 regForoper1处理好了
            }

        } else if (typeOper1.equals("num")) {
            int num = oper1.getNum();
            add("li $" + regForOper1 + ", " + num);

        } else {
            System.err.println("MIPSTranslator addAssign2(): ??? unknown type = " + typeOper1);
        }

        //todo 后处理左侧dest
        String typeDest = dest.getType();

        if (typeDest.equals("array")) {
            String arrayname = dest.getName();
            Symbol symbol_dest = dest.getSymbol();

            Variable offset = dest.getVar();    //todo 存疑[正确！]
            String offsetType = offset.getType();

            if (symbol_dest.isGlobal()) {    //全局取data段
                int tmpregno = register.applyTmpRegister();
                String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                if (offsetType.equals("num")) {    //offset = 数字
                    int offsetnum = offset.getNum();
                    add("li $" + tmpregname + ", " + offsetnum * 4);     //需要乘以4!!!
                    add("sw $" + regForOper1 + ", Global_" + arrayname + "($" + tmpregname + ")");   //与oper1主要区别lw变成sw

                } else {    //offset = var变量
                    //该函数：将任意variable加载到指定寄存器，oper1、2、dest等均可用
                    String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 不能原来这么简单粗暴;但此方法存疑

                    add("sll $" + offsetregname + ", $" + offsetregname + ", 2");   //需要乘以4!!!
                    add("sw $" + regForOper1 + ", Global_" + arrayname + "($" + offsetregname + ")");
                }

                register.freeTmpRegister(tmpregno);

            } else {    //局部取 堆栈
                if (innerfunc) {  //array数组在函数内
                    if (curFunc.varnameIsFuncPara(arrayname)) {     //数组为 函数参数
                        int tmpregno = register.applyTmpRegister();
                        int tmpregno2 = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);       //申请临时寄存器
                        String tmpregname2 = register.getRegisterNameFromNo(tmpregno2);     //申请临时寄存器2

                        int arraybaseaddroffset = calcuFuncParaOffset(arrayname);
                        add("lw $" + tmpregname + ", " + arraybaseaddroffset + "($sp)");  //取出存放array的基地址,存到tmpregname中

                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            add("sw $" + regForOper1 + ", " + numaddroffset + "($" + tmpregname + ")");

                        } else {    //offset = var变量
                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname2 + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $" + tmpregname2);
                            add("sw $" + regForOper1 + ", ($" + tmpregname + ")");
                        }

                        register.freeTmpRegister(tmpregno);
                        register.freeTmpRegister(tmpregno2);
                        //register.freeRegister(offset); 下面统一释放

                    } else {    //函数内 + 局部array
                        Symbol symbol = SymbolTable.lookupLocalTable(arrayname, Parser.TYPE.I, symbol_dest.getScope());
                        Assert.check(symbol, "MIPSTranslator / adAssign2() / dest / array / innerfunc");

                        int localarrayspoffset = calcuFuncLocalVarOffset(symbol);   //局部array的首地址
                        if (offsetType.equals("num")) {    //offset = 数字
                            int numaddroffset = offset.getNum() * 4;    //乘以4，省一步li或sll
                            localarrayspoffset += numaddroffset;
                            add("sw $" + regForOper1 + ", " + localarrayspoffset + "($sp)");

                        } else {    //offset = var变量
                            int tmpregno = register.applyTmpRegister();
                            String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                            String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                            add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                            add("add $" + tmpregname + ", $" + tmpregname + ", $sp");
                            add("sw $" + regForOper1 + ", " + localarrayspoffset + "($" + tmpregname + ")");

                            register.freeTmpRegister(tmpregno);
                        }
                    }

                } else {  //array数组在正常结构内
                    int arraybaseaddr = symbol_dest.spBaseHex + symbol_dest.addrOffsetDec;

                    if (offsetType.equals("num")) {    //offset = 数字
                        int arraynumaddr = arraybaseaddr + offset.getNum() * 4;
                        String arroffsetHex = convertIntAddrToHex(arraynumaddr);   //地址格式int转16进制

                        add("sw $" + regForOper1 + ", " + arroffsetHex);

                    } else {    //offset = var变量
                        int tmpregno = register.applyTmpRegister();
                        String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                        String offsetregname = loadWordOfAnyVariableToRegName(offset);   //todo 存疑
                        add("sll $" + tmpregname + ", $" + offsetregname + ", 2");
                        add("sw $" + regForOper1 + ", " + arraybaseaddr + "($" + tmpregname + ")");

                        register.freeTmpRegister(tmpregno);
                    }
                }
            }
            if (offset.getCurReg() != -1) {
                register.freeRegister(offset);  //统一释放存数组偏移量的reg
            }

        } else if (typeDest.equals("var")) {
            //todo 需要处理函数局部变量和全局变量的问题【答】不用，oper1处理好了，存在了regForoper1中【大谬】可能赋值Global

            String regForDest = searchRegName(dest);
            add("move $" + regForDest + ", $" + regForOper1);   //全局变量存此寄存器，后续释放可能有问题

            if (dest.isKindofsymbol()) {
                Symbol destsymbol = dest.getSymbol();
                if (innerfunc && !dest.getSymbol().isGlobal()) {    //函数内+symbol需要lw
                    saveWordOfInfuncVarFromRegToSp(dest, regForOper1);       //包装从函数体sp读取到reg过程

                } else if (destsymbol.isGlobal() && destsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = destsymbol.getName();
                    add("sw $" + regForOper1 + ", Global_" + globalvarname);  //todo 也许不用加$zero

                } else {
                    //System.err.println("MIPSTranslator addAssign2(): ??? unknown type = " + typeOper1);
                    //什么也不用管 regForoper1处理好了
                    saveWordOfLocalMainfuncVarSymbolFromSpToReg(regForOper1, destsymbol);
                }
            } else {
                //add("move $" + regForDest + ", $" + regForOper1);
            }

        } else {
            System.err.println("MIPSTranslator addAssign2(): ??? unknown type = " + typeDest);
        }

        register.freeRegister(oper1);     //理论上需要判定活跃性，或是否为tmp
        //todo 也许需要free offset的reg
    }

    private void addAssignRet(IRCode code) {
        Variable dest = code.getVariable();
        String name = dest.getName();

        //todo 判定有隐患
        if (dest.isKindofsymbol()) {
            Symbol destsymbol = dest.getSymbol();
            if (innerfunc && !destsymbol.isGlobal()) {    //函数内+symbol需要lw
                System.out.println(code.getRawstr() + "      assign ret infunc name = " + name);
                saveWordOfInfuncVarFromRegToSp(dest, "v0");

            } else if (destsymbol.isGlobal() && destsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                String globalvarname = destsymbol.getName();
                add("sw $v0" + ", Global_" + globalvarname);  //todo 也许不用加$zero

            } else {
                /*String retreg = searchRegName(dest);
                add("move $" + retreg + ", $v0");*/

                saveWordOfLocalMainfuncVarSymbolFromSpToReg("v0", destsymbol);
            }
        } else {
            String retreg = searchRegName(dest);
            add("move $" + retreg + ", $v0");
        }
    }

    private void addArrayDecl(IRCode code) {
        String name = code.getName();
        int size;
        if (code.getArray2() == 0) {
            size = code.getArray1();
        } else {
            size = code.getArray1() * code.getArray2();
        }

        if (code.isGlobal()) {  //全局数组存.data段
            tabcount += 1;
            String arrayDeclWordInitStr = "Global_" + name + ": .word ";
            if (code.init) {
                add(arrayDeclWordInitStr + code.concatArrayInitNumStr());

            } else {
                add(arrayDeclWordInitStr + "0:" + size);
            }
            tabcount -= 1;

        } else {    //局部数组存 堆栈 段
            int addressOffsetSize = size * 4;
            //头地址存寄存器
            Symbol symbol = code.getSymbol();
            Assert.check(symbol, "MIPSTranslator / addIntDecl()");  //todo 取symbol存疑

            SymbolTable.Scope scope = code.getScope();  //todo 存疑，不一定获取到
            scope.inblockoffset += addressOffsetSize;   //记录目前block内偏移

            if (innerfunc) {
                infuncoffset += addressOffsetSize;
                symbol.addrOffsetDec = -infuncoffset;
            } else {
                spoffset += addressOffsetSize;      //记录sp指针偏移
                symbol.addrOffsetDec = -spoffset;    ///记录相对sp的地址
            }

            add("");
            add("# init local array");
            add("addi $sp, $sp, " + (-addressOffsetSize));

            if (code.init) {    //init则需要存每一个数
                int regno = register.applyTmpRegister();
                String regname = register.getRegisterNameFromNo(regno);

                ArrayList<Integer> initNumList = code.getInitList();
                for (int i = 0; i < initNumList.size(); i++) {
                    int offset = i * 4;
                    int num = initNumList.get(i);
                    add("li $" + regname + ", " + num);
                    add("sw $" + regname + ", " + offset + "($sp)");
                }

                register.freeTmpRegister(regno);
            }

            /*if (register.hasSpareRegister()) {
                String regname = register.applyRegister(symbol);
                add("la $" + regname + ", ($sp)");
            }*/
            //todo 没有空reg也需要处理?似乎不用，地址记在symbol里就行
        }
    }

    private void addIntDecl(IRCode code) {
        String name = code.getName();

        if (code.isGlobal()) {  //全局int变量存.data段
            String intDeclWordInitStr = "Global_" + name + ": .word ";
            tabcount += 1;
            if (code.init) {
                add(intDeclWordInitStr + code.getNum());

            } else {
                add(intDeclWordInitStr + "0:1");
            }
            tabcount -= 1;

        } else {    //局部int变量分寄存器或存sp段
            SymbolTable.Scope scope = code.getScope();  //修改后可获取到。！！！此处一定Main内也要这样处理！！！
            scope.inblockoffset += 4;   //记录目前block内偏移

            if (innerfunc) {   //在函数体内部定义intDecl表现不同
                add("addi $sp, $sp, -4");

                //todo 函数体内的decl不能与symbol绑定，最好不分寄存器，否则还得处理寄存器取入sp
                infuncoffset += 4;
                Symbol symbol = code.getSymbol();
                Assert.check(symbol, "MIPSTranslator / addIntDecl()");//todo 取symbol存疑
                symbol.addrOffsetDec = -infuncoffset;    ///todo 记录的偏移量是此时相对于函数头的偏移，后续还要运算

                if (code.init) {    //init则需要存数到$sp
                    int regno = register.applyTmpRegister();
                    String regname = register.getRegisterNameFromNo(regno);

                    int num = code.getNum();        //todo 存疑
                    add("li $" + regname + ", " + num);
                    add("sw $" + regname + ", 0($sp)");

                    register.freeTmpRegister(regno);
                }   //todo 否则当0就行？不太对吧


            } else {   //正常Main内部定义的intDecl
                add("addi $sp, $sp, -4");
                spoffset += 4;

                Symbol symbol = code.getSymbol();
                Assert.check(symbol, "MIPSTranslator / addIntDecl()");//todo 取symbol存疑
                symbol.addrOffsetDec = -spoffset;    ///todo 记录地址【完成！】

                /*if (register.hasSpareRegister()) {
                    String regname = register.applyRegister(symbol);
                    if (code.init) {
                        add("li $" + regname + ", " + code.getNum());
                    }

                } else*/
                //无多余寄存器
                if (code.init) {    //init则需要存数到$sp
                    int regno = register.applyTmpRegister();
                    String regname = register.getRegisterNameFromNo(regno);

                    int num = code.getNum();        //todo 存疑
                    add("li $" + regname + ", " + num);
                    add("sw $" + regname + ", 0($sp)");

                    register.freeTmpRegister(regno);
                }//否则当0就行

            }
        }
    }

    private void addDeclStmt(IRCode code) {
        addBranchStmt(code);
    }

    private void addFuncdefStmt(IRCode code) {
        String type = code.getType();

        switch (type) {
            case "funcDecl":
                add("Func_" + code.getName() + ":");
                Symbol symbol = SymbolTable.lookupFullTable(code.getName(), Parser.TYPE.F, SymbolTable.foreverGlobalScope);

                innerfunc = true;
                infuncoffset = 0;
                curFunc = symbol;
                tabcount += 1;
                break;

            case "note":
                if (code.getIRstring().equals("#end a func")) {
                    add("addi $sp, $sp, " + infuncoffset); //注意回复sp指针！处理无return的函数情况
                    add("jr $ra");  //主要防止void且空返回值函数情况
                    add("");

                    innerfunc = false;
                    tabcount -= 1;

                    register.resetAllReg();     //reset全部寄存器状态

                } else {
                    addNotes(code);
                }
                break;
            default:
                addBranchStmt(code);
                break;
        }
    }


    private void addReturn(IRCode code) {       //todo 有一个end标签负责统一加jr $ra了【不可！】需要跟随Return一起加
        if (inmain) {
            addProgramEnd();
            return;     //若在main函数内，无任何操作
        }

        if (code.voidreturn) {  //为空返回值的类型
            add("addi $sp, $sp, " + infuncoffset);
            add("jr $ra");
            add("");
            return;
        }

        Variable var = code.getVariable();
        String type = var.getType();

        if (type.equals("num")) {   //todo 也许要处理$sp操作。【答】后续统一处理了
            int num = var.getNum();
            add("li $v0, " + num);

        } else if (type.equals("var")) {    //todo 可能global情况
            if (var.isKindofsymbol()) {
                Symbol varsymbol = var.getSymbol();
                if (innerfunc && !varsymbol.isGlobal()) {    //函数内+symbol需要lw
                    loadWordOfInfuncVarFromSpToReg(var, "v0");

                } else if (varsymbol.isGlobal() && varsymbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                    String globalvarname = varsymbol.getName();
                    add("lw $v0, Global_" + globalvarname);  //不用加$zero

                } else {
                    loadWordOfLocalMainfuncVarSymbolFromSpToReg("v0", varsymbol);//似乎仅Main函数return 0有此情况
                }
            } else {
                String varregname = searchRegName(var);
                add("move $v0, $" + varregname);
                register.freeRegister(var);     //理论上需要判定活跃性，或是否为tmp
            }

        } else {   //array（好像也不会，用一个t1 = array已经处理了）,func不会
            //
        }

        add("addi $sp, $sp, " + infuncoffset);
        add("jr $ra");
        add("");
    }

    //辅助用函数
    private void add(String mipsstr) {
        if (tabcount == 1) {
            mipsstr = tab + mipsstr;
        }
        mipsList.add(mipsstr);
        System.out.println(mipsstr);
    }

    private void addProgramEnd() {
        add("#End Program");
        add("li $v0, 10");
        add("syscall");
    }

    private String reverseCompareInstr(String instr) {
        switch (instr) {
            case "beq":
                return "beq";
            case "bne":
                return "bne";
            case "bge":
                return "blt";
            case "ble":
                return "bgt";
            case "bgt":
                return "ble";
            case "blt":
                return "bge";
            case "sge":
                return "slt";
            case "sgt":
                return "sle";
            case "sle":
                return "sgt";
            case "slt":
                return "sge";
            case "seq":
                return "seq";
            case "sne":
                return "sne";
            default:
                break;
        }
        System.err.println("MIPSTranslator / reverseCompareInstr() ???instr type = " + instr);
        return null;
    }

    //一个10进制int类型地址转Hex格式的小函数
    private String convertIntAddrToHex(int intaddr) {
        return "0x" + Integer.toHexString(intaddr);
    }

    private String searchRegName(Variable v) {
        String regname;

        if (v.isKindofsymbol()) {  //是一个蛮重要的变量
            //System.out.println("v's name = " + v.getName());

            Symbol symbol = v.getSymbol();
            if (symbol.getCurReg() == -1) {  //仅初始化未分配
                regname = register.applyRegister(symbol);

            } else {
                int regno = symbol.getCurReg();
                regname = register.getRegisterNameFromNo(regno);
            }

        } else {  //临时的“阅后即焚”野鸡变量
            if (v.getCurReg() == -1) {  //仅初始化未分配
                regname = register.applyRegister(v);

            } else {
                int regno = v.getCurReg();
                regname = register.getRegisterNameFromNo(regno);
            }

            if (regname == null || regname.equals("")) {
                System.err.println("Null Reg :" + v.toString());

                //regname = register.applyRegister(v);
            }
        }
        return regname;
    }

    //计算一个函数参数的偏移量
    private int calcuFuncParaOffset(String name) {
        int paraorder = curFunc.varnameOrderInFuncPara(name);   //范围是1-n共n个参数
        int parafullnum = curFunc.getParaNum();
        int funcparaoffset = infuncoffset + 4 + (parafullnum - paraorder) * 4;
        return funcparaoffset;
    }

    //计算一个函数内局部变量的偏移量
    private int calcuFuncLocalVarOffset(Symbol symbol) {
        int localvaraddr = infuncoffset + symbol.addrOffsetDec;
        return localvaraddr;
    }

    //将函数 存在sp的内容加载到寄存器
    private void loadWordOfInfuncVarFromSpToReg(Variable var, String regname) {
        String name = var.getName();
        if (curFunc.varnameIsFuncPara(name)) {    //函数内+para需要lw
            int paraspoffset = calcuFuncParaOffset(name);
            add("lw $" + regname + ", " + paraspoffset + "($sp)");

        } else {    //函数内+local var需要lw
            Symbol symbol = var.getSymbol();
            int localvarspoffset = calcuFuncLocalVarOffset(symbol);
            add("lw $" + regname + ", " + localvarspoffset + "($sp)");
        }
    }

    //将函数 存在寄存器放回sp
    private void saveWordOfInfuncVarFromRegToSp(Variable var, String regname) {
        String name = var.getName();
        if (curFunc.varnameIsFuncPara(name)) {    //函数内+para需要lw
            int paraspoffset = calcuFuncParaOffset(name);
            add("sw $" + regname + ", " + paraspoffset + "($sp)");

        } else {    //函数内+local var需要lw
            Symbol symbol = var.getSymbol();
            int localvarspoffset = calcuFuncLocalVarOffset(symbol);
            add("sw $" + regname + ", " + localvarspoffset + "($sp)");
        }
    }

    //将 局部变量 存在sp的内容加载到寄存器
    private void loadWordOfLocalMainfuncVarSymbolFromSpToReg(String regname, Symbol symbol) {
        int symboladdr = symbol.spBaseHex + symbol.addrOffsetDec;
        //String hexaddr = "0x" + Integer.toHexString(symboladdr);
        String hexaddr = convertIntAddrToHex(symboladdr);
        add("lw $" + regname + ", " + hexaddr);
    }

    //将 局部变量 存在寄存器放回sp
    private void saveWordOfLocalMainfuncVarSymbolFromSpToReg(String regname, Symbol symbol) {
        int symboladdr = symbol.spBaseHex + symbol.addrOffsetDec;
        //String hexaddr = "0x" + Integer.toHexString(symboladdr);
        String hexaddr = convertIntAddrToHex(symboladdr);
        add("sw $" + regname + ", " + hexaddr);
    }

    //将任意variable加载到指定寄存器，oper1、2、dest等均可用; 优先给offset用
    private String loadWordOfAnyVariableToRegName(Variable oper0) {
        String op0reg = "null_reg!!";

        if (oper0.isKindofsymbol()) {       //todo 判定、分类有隐患?
            Symbol oper0symbol = oper0.getSymbol();
            if (innerfunc && !oper0symbol.isGlobal()) {    //函数内+symbol需要lw
                int tmpregforop0 = register.applyTmpRegister();
                op0reg = register.getRegisterNameFromNo(tmpregforop0);

                loadWordOfInfuncVarFromSpToReg(oper0, op0reg);       //包装从函数体sp读取到reg过程

                register.freeTmpRegister(tmpregforop0);

            } else if (oper0symbol.isGlobal() && oper0symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                String globalvarname = oper0symbol.getName();
                op0reg = searchRegName(oper0);
                add("lw $" + op0reg + ", Global_" + globalvarname);

            } else {
                op0reg = searchRegName(oper0);
                loadWordOfLocalMainfuncVarSymbolFromSpToReg(op0reg, oper0symbol);
            }
        } else {
            op0reg = searchRegName(oper0);
        }

        return op0reg;
    }

    //将函数 存在sp的 int 值加载到寄存器 [此处push时使用]
    private void loadWordOfInfuncVarFromSpToReg(Variable var, String regname, int pushsign, Instrs pushinstrs) {
        String name = var.getName();
        if (curFunc.varnameIsFuncPara(name)) {    //函数内+para需要lw
            int paraspoffset = calcuFuncParaOffset(name);

            Instr lwinstr = new Instr("lw $" + regname + ", ", paraspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
            //lwinstr.setAddroffset(true);
            pushinstrs.addInstr(lwinstr);

        } else {    //函数内+local var需要lw
            Symbol symbol = var.getSymbol();
            int localvarspoffset = calcuFuncLocalVarOffset(symbol);

            Instr lwinstr = new Instr("lw $" + regname + ", ", localvarspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
            //lwinstr.setAddroffset(true);
            pushinstrs.addInstr(lwinstr);
        }
    }

    //将 局部变量 存在sp的 int 加载到寄存器 [此处push时使用]
    private void loadWordOfLocalMainfuncVarSymbolFromSpToReg(String regname, Symbol symbol, int pushsign, Instrs pushinstrs) {
        int symboladdr = symbol.spBaseHex + symbol.addrOffsetDec;
        //String hexaddr = "0x" + Integer.toHexString(symboladdr);
        String hexaddr = convertIntAddrToHex(symboladdr);

        Instr hexinstr = new Instr("lw $" + regname + ", " + hexaddr);
        pushinstrs.addInstr(hexinstr);
    }

    //将函数 存在sp的 array 地址加载到寄存器 [此处push时使用]
    //todo 正确性存疑
    private void loadAddressOfInfuncArrayVarFromSpToReg(Variable var, String regname, int pushsign, Instrs pushinstrs) {
        String name = var.getName();
        Symbol arraysymbol = var.getSymbol();

        if (curFunc.varnameIsFuncPara(name)) {    //函数内+para需要lw
            int paraspoffset = calcuFuncParaOffset(name);

            if (var.getVar() != null) {     //处理如b[1], b[i]情况
                Variable offset = var.getVar();
                String offsetType = offset.getType();

                //分类arr[1]或arr[i]处理
                if (offsetType.equals("num")) {    //offset = 数字
                    int arroffset = offset.getNum() * arraysymbol.getDimen2() * 4;    //偏移量=index * dimen2 * 4
                    paraspoffset += arroffset;
                    //若para，原样lw传地址
                    Instr lwinstr = new Instr("lw $" + regname + ", ", paraspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
                    pushinstrs.addInstr(lwinstr);

                } else {    //offset = var变量
                    int tmpregno = register.applyTmpRegister();
                    String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                    String offsetregname = loadWordOfAnyVariableToRegName(offset, 1, pushinstrs);
                    pushinstrs.addInstr(new Instr("sll $" + offsetregname + ", $" + offsetregname + ", 2"));   //！！！需要乘以4
                    pushinstrs.addInstr(new Instr("li $" + tmpregname + ", " + arraysymbol.getDimen2()));
                    pushinstrs.addInstr(new Instr("mult $" + offsetregname + ", $" + tmpregname));
                    pushinstrs.addInstr(new Instr("mflo $" + tmpregname));

                    //先把函数参数中array首地址加载到regname
                    pushinstrs.addInstr(new Instr("lw $" + regname + ", ", paraspoffset, "($sp)", "actreg"));

                    //之后将regname中的地址增加偏移量(即$tmpregname)
                    pushinstrs.addInstr(new Instr("add $" + regname + ", $" + regname + ", $" + tmpregname));

                    //以下处理： register.freeRegister(offset);
                    if (offset.getCurReg() != -1) {
                        //register.freeRegister(offset);  //统一释放存数组偏移量的reg.此处不能放

                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        last.hasRetReg = true;        //最后一个语句，附加一个归还offsetReg操作
                        last.setFreeRegNumber(offset.getCurReg());  //todo getCurReg方法存疑
                        pushinstrs.addInstr(last);

                    } else {
                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        pushinstrs.addInstr(last);
                    }
                }

            } else {
                //若para，原样lw传地址; 若local variable，正常la传地址
                Instr lwinstr = new Instr("lw $" + regname + ", ", paraspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
                pushinstrs.addInstr(lwinstr);
            }

        } else {    //函数内+局部变量需要la

            int localvarspoffset = calcuFuncLocalVarOffset(arraysymbol);

            if (var.getVar() != null) {     //处理如b[1], b[i]情况
                Variable offset = var.getVar();
                String offsetType = offset.getType();

                //分类arr[1]或arr[i]处理
                if (offsetType.equals("num")) {    //offset = 数字
                    int arroffset = offset.getNum() * arraysymbol.getDimen2() * 4;    //偏移量=index * dimen2 * 4
                    localvarspoffset += arroffset;
                    //local variable，正常la传地址
                    Instr lwinstr = new Instr("la $" + regname + ", ", localvarspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
                    pushinstrs.addInstr(lwinstr);

                } else {    //offset = var变量
                    int tmpregno = register.applyTmpRegister();
                    String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                    String offsetregname = loadWordOfAnyVariableToRegName(offset, 1, pushinstrs);
                    pushinstrs.addInstr(new Instr("sll $" + offsetregname + ", $" + offsetregname + ", 2"));   //！！！需要乘以4
                    pushinstrs.addInstr(new Instr("li $" + tmpregname + ", " + arraysymbol.getDimen2()));
                    pushinstrs.addInstr(new Instr("mult $" + offsetregname + ", $" + tmpregname));
                    pushinstrs.addInstr(new Instr("mflo $" + tmpregname));

                    pushinstrs.addInstr(new Instr("add $" + tmpregname + ", $" + tmpregname + ", $sp"));
                    pushinstrs.addInstr(new Instr("la $" + regname + ", ", localvarspoffset, "($" + tmpregname + ")", "actreg"));

                    //以下处理： register.freeRegister(offset);
                    if (offset.getCurReg() != -1) {
                        //register.freeRegister(offset);  //统一释放存数组偏移量的reg.此处不能放

                        //todo la有点问题，好像本质就是move
                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        last.hasRetReg = true;        //最后一个语句，附加一个归还offsetReg操作
                        last.setFreeRegNumber(offset.getCurReg());  //todo getCurReg方法存疑
                        pushinstrs.addInstr(last);

                    } else {
                        Instr last = new Instr("#push an array end.");  //用一个#标签包装处理
                        pushinstrs.addInstr(last);
                    }
                }

            } else {
                //若para，原样lw传地址; 若local variable，正常la传地址
                Instr lwinstr = new Instr("la $" + regname + ", ", localvarspoffset, "($sp)", "actreg");  //特意用一个Instr包装处理
                pushinstrs.addInstr(lwinstr);
            }
        }
    }

    //将 局部变量 存在sp的 array 地址 加载到寄存器 [此处push时使用]
    private void loadAddressOfLocalMainfuncArrayVarSymbolFromSpToReg(String regname, Symbol symbol, int pushsign, Instrs pushinstrs, Variable var) {
        int symboladdr = symbol.spBaseHex + symbol.addrOffsetDec;

        if (var.getVar() != null) {     //处理如array[1]或array[i]情况
            Variable offset = var.getVar();
            String offsetType = offset.getType();

            //分类arr[1]或arr[i]处理
            if (offsetType.equals("num")) {    //offset = 数字
                int arroffset = offset.getNum() * var.getSymbol().getDimen2() * 4;    //偏移量=index * dimen2 * 4
                symboladdr += arroffset;

                String hexaddr = convertIntAddrToHex(symboladdr);
                Instr hexinstr = new Instr("li $" + regname + ", " + hexaddr);
                pushinstrs.addInstr(hexinstr);

            } else {    //offset = var变量
                int tmpregno = register.applyTmpRegister();
                String tmpregname = register.getRegisterNameFromNo(tmpregno);   //申请临时寄存器

                String offsetregname = loadWordOfAnyVariableToRegName(offset, 1, pushinstrs);
                pushinstrs.addInstr(new Instr("sll $" + offsetregname + ", $" + offsetregname + ", 2"));   //！！！需要乘以4
                pushinstrs.addInstr(new Instr("li $" + tmpregname + ", " + var.getSymbol().getDimen2()));
                pushinstrs.addInstr(new Instr("mult $" + offsetregname + ", $" + tmpregname));
                pushinstrs.addInstr(new Instr("mflo $" + tmpregname));

                //tmpregname是此时算出的偏移量
                pushinstrs.addInstr(new Instr("addi $" + regname + ", $" + tmpregname + ", " + symboladdr));

                //以下处理： register.freeRegister(offset);
                if (offset.getCurReg() != -1) {
                    //register.freeRegister(offset);  //统一释放存数组偏移量的reg.此处不能放
                    //todo la有点问题，好像本质就是move
                    Instr last = new Instr("#push an local array end.");  //用一个#标签包装处理
                    last.hasRetReg = true;        //最后一个语句，附加一个归还offsetReg操作
                    last.setFreeRegNumber(offset.getCurReg());  //todo getCurReg方法存疑
                    pushinstrs.addInstr(last);

                } else {
                    Instr last = new Instr("#push an local array end.");  //用一个#标签包装处理
                    pushinstrs.addInstr(last);
                }
            }

        } else {  //无偏移量
            String hexaddr = convertIntAddrToHex(symboladdr);
            Instr hexinstr = new Instr("li $" + regname + ", " + hexaddr);
            pushinstrs.addInstr(hexinstr);
        }
    }

    //将任意variable加载到指定寄存器，oper1、2、dest等均可用; 优先给offset用 [此处push时使用]
    private String loadWordOfAnyVariableToRegName(Variable oper0, int pushsign, Instrs pushinstrs) {
        String op0reg = "null_reg!!";

        if (oper0.isKindofsymbol()) {       //todo 判定、分类有隐患?
            Symbol oper0symbol = oper0.getSymbol();
            if (innerfunc && !oper0symbol.isGlobal()) {    //函数内+symbol需要lw
                int tmpregforop0 = register.applyTmpRegister();
                op0reg = register.getRegisterNameFromNo(tmpregforop0);

                loadWordOfInfuncVarFromSpToReg(oper0, op0reg, 1, pushinstrs);       //包装从函数体sp读取到reg过程

                //register.freeTmpRegister(tmpregforop0);
                //todo tmpregforop0没放

            } else if (oper0symbol.isGlobal() && oper0symbol.getType() != Parser.TYPE.F) {  //还要判断不是func返回值
                String globalvarname = oper0symbol.getName();
                op0reg = searchRegName(oper0);
                pushinstrs.addInstr(new Instr("lw $" + op0reg + ", Global_" + globalvarname));

            } else {
                op0reg = searchRegName(oper0);
                loadWordOfLocalMainfuncVarSymbolFromSpToReg(op0reg, oper0symbol, 1, pushinstrs);
            }
        } else {
            op0reg = searchRegName(oper0);
        }

        return op0reg;
    }

    //*优化
    private void MultOptimize(String dreg, String op1reg, int num) {
        if (num == 0) {
            add("li $" + dreg + ", 0");

        } else if (num == 1) {
            add("move $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);

        } else if (isPowerOfTwo(num + 1)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num - 1)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num + 2)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num - 2)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num + 3)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("add $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else if (isPowerOfTwo(num - 3)) {
            int mi = (int) (Math.log(num) / Math.log(2));
            add("sll $" + dreg + ", $" + op1reg + ", " + mi);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);
            add("sub $" + dreg + ", $" + dreg + ", $" + op1reg);

        } else {
            add("li $v1, " + num);
            add("mult $" + op1reg + ", $v1");
            add("mflo $" + dreg);
        }
    }

    //除法优化
    private void DivOptimize(String dreg, String op1reg, int num, boolean reverse) {
        if (reverse) {  //num÷x
            if (num == 0) {
                add("li $" + dreg + ", 0");
            } else {
                add("li $v1, " + num);
                add("div $v1, $" + op1reg);
                add("mflo $" + dreg);
            }

        } else {    //x÷num
            if (num == 1) {
                add("move $" + dreg + ", $" + op1reg);
            }
            add("li $v1, " + num);
            add("div $" + op1reg + ", $v1");
            add("mflo $" + dreg);
        }
    }

    //判断是否2的幂次
    private boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
}
