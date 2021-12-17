import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class IRGenerator {
    private Node tree;
    private ArrayList<IRCode> irList = new ArrayList<>();
    //private int count = 1;
    private int varcount = 0;
    private int ifcount = 1;
    private int whilecount = 1;
    private int logicORcount = 1;

    private boolean global = true;

    //private SymbolTable.Scope curScope;     //记录当前block/scope的位置
    //private boolean noneedintoblock = false;    //用于while、if等自己intoblock处理后告知Block()不再处理， true=不处理

    private Symbol curFunc;     //符号表时用，标记当前正在处理的函数
    private boolean noneedopenblock = false;    //while、if等自建scope时标记不需再block

    public IRGenerator(Node ASTtree) {
        this.tree = ASTtree;
    }

    //优化，一串赋值code的起始位置
    private int startindex = 0;

    public ArrayList<IRCode> generate(int IRoutput) {
        parseTree();

        if (IRoutput == 1) {
            try {
                writefile("ircode.txt");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return irList;
    }

    public void writefile(String dir) throws IOException {
        File file = new File(dir);
        FileWriter writer = new FileWriter(file);
        System.out.println("输出ircode.txt...");
        for (IRCode irc : irList) {
            String t = irc.getRawstr();
            System.out.println(t);
            writer.write(t + "\n");
        }
        writer.flush();
        writer.close();
    }

    private void parseTree() {
        if (tree.getLeft() != null) {
            parseDeclList(tree.getLeft());
        }
        global = false;
        createIRCode("note", "#Start FuncDecl");
        if (tree.getMiddle() != null) {
            parseFuncDeclList(tree.getMiddle());
        }
        createIRCode("note", "#Start MainFunc");
        parseMainFuncDef(tree.getRight());
    }

    private void parseDeclList(Node n) {
        for (Node lf : n.getLeafs()) {
            if (lf.getType().equals("ConstDecl") || lf.getType().equals("VarDecl")) {
                parseDecl(lf);
            }
        }
    }

    private void parseDecl(Node n) {
        for (Node lf : n.getLeafs()) {
            if (lf.getType().equals("ConstDef") || lf.getType().equals("VarDef")) {
                parseDef(lf);
            }
        }
    }

    private void parseDef(Node n) {
        Node ident = n.getLeft();
        String name = ident.getName();   //指id
        String kind = ident.getKind();

        makeSymbolDef(n);   //第1步，建符号表

        Symbol symbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
        Assert.check(symbol, "IRGenerator / parseDef()");

        Node init = n.getRight();
        if (kind.equals("array") || kind.equals("const array")) {       //md const array也是这类
            parseArrayDef(n);

        } else {    //此处ConstInivial可立即计算初值，Inivial需计算表达式
            symbol.setIrindex(irList.size());

            if (init != null) {
                if (kind.equals("const int") || global) {
                    int constinitnum = init.calcuValue();

                    IRCode ir = new IRCode("intDecl", kind, name, constinitnum);
                    ir.setInitIsTrue();
                    ir.setSymbol(symbol);
                    ir4init(ir);

                } else {    //todo Inivial需计算表达式。采用了一个生成两条IRCode的处理
                    IRCode numInitIr = new IRCode("intDecl", kind, name);
                    numInitIr.init = false;
                    numInitIr.setSymbol(symbol);
                    ir4init(numInitIr);

                    //第2条，赋值
                    startindex = irList.size();     //初始化ir赋值语句起始位置

                    Variable intinitvar = parseExp(init);
                    Variable intInitLval = new Variable("var", name);

                    intInitLval.setSymbol(symbol);
                    intInitLval.setiskindofsymbolTrue();  //todo 此处不可调用parseIdent？

                    //createIRCode("assign2", intInitLval, intinitvar);
                    IRCode ir = new IRCode("assign2", intInitLval, intinitvar);
                    //ir.releaseDest = true;  //释放dest
                    ir4init(ir);
                }

            } else {
                IRCode ir = new IRCode("intDecl", kind, name);
                ir.init = false;
                ir.setSymbol(symbol);
                ir4init(ir);
            }
        }
    }

    private void parseArrayDef(Node n) {    //此处ConstInivial可立即计算初值，Inivial需计算表达式
        Node ident = n.getLeft();
        String name = ident.getName();   //指id
        String kind = ident.getKind();

        Node dimen1 = ident.getLeft();
        int dimennum1 = dimen1.calcuValue();

        int dimennum2 = 0;
        if (ident.getRight() != null) {
            Node dimen2 = ident.getRight();
            dimennum2 = dimen2.calcuValue();
        }

        IRCode arrayir = new IRCode("arrayDecl", name, dimennum1, dimennum2);
        Symbol symbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
        Assert.check(symbol, "IRGenerator / parseArrayDef()");
        arrayir.setSymbol(symbol);

        //Part.II InitVal
        Node init = n.getRight();

        if (init != null) {
            if (kind.equals("const array") || global) {     //常量数组 | 全局数组 必然每个元素有固定初始值
                if (dimennum2 == 0) {   //一维数组
                    for (int i = 0; i < dimennum1; i++) {
                        int initnum = init.getLeafs().get(i).calcuValue();  //todo 也需分可直接算出的ConstInivial与Initval两种情况
                        arrayir.init = true;
                        arrayir.addInitList(initnum);

                    }
                } else {
                    for (int i = 0; i < dimennum1; i++) {
                        for (int j = 0; j < dimennum2; j++) {
                            int initnum = init.getLeafs().get(i).getLeafs().get(j).calcuValue();
                            arrayir.init = true;
                            arrayir.addInitList(initnum);
                        }
                    }
                }
                ir4init(arrayir);   //打包初始化

            } else {    //Inivial需计算表达式。采用生成N条IRCode的处理
                //第1条，声明
                startindex = irList.size();     //初始化ir赋值语句起始位置
                arrayir.init = false;
                ir4init(arrayir);

                //第2条，赋值
                if (dimennum2 == 0) {   //一维数组
                    for (int i = 0; i < dimennum1; i++) {
                        int index = i;
                        Variable arrIndex = new Variable("num", index);
                        Variable arrElementInitvar = parseExp(init.getLeafs().get(i));
                        Variable arrElementInitLval = new Variable("array", name, arrIndex);

                        arrElementInitLval.setSymbol(symbol);
                        arrElementInitLval.setiskindofsymbolTrue();

                        //createIRCode("assign2", arrElementInitLval, arrElementInitvar);

                        IRCode ir = new IRCode("assign2", arrElementInitLval, arrElementInitvar);
                        ir.releaseDest = true;  //释放dest
                        ir4init(ir);
                    }
                } else {
                    for (int i = 0; i < dimennum1; i++) {
                        for (int j = 0; j < dimennum2; j++) {
                            int index = i * dimennum2 + j;
                            Variable arrIndex = new Variable("num", index);
                            Variable arrElementInitvar = parseExp(init.getLeafs().get(i).getLeafs().get(j));
                            Variable arrElementInitLval = new Variable("array", name, arrIndex);

                            arrElementInitLval.setSymbol(symbol);
                            arrElementInitLval.setiskindofsymbolTrue();

                            //createIRCode("assign2", arrElementInitLval, arrElementInitvar);

                            IRCode ir = new IRCode("assign2", arrElementInitLval, arrElementInitvar);
                            ir.releaseDest = true;  //释放dest
                            ir4init(ir);
                        }
                    }
                }
            }

        } else {
            arrayir.init = false;
            ir4init(arrayir);
        }
    }

    private void parseFuncDeclList(Node n) {
        for (Node lf : n.getLeafs()) {
            parseFuncDecl(lf);
        }
    }

    private void parseFuncDecl(Node n) {
        Node ident = n.getLeft();
        String funcname = ident.getName();
        String functype = ident.getKind();

        //第1步，先制作符号表
        Symbol funcSymbol = new Symbol(funcname, Parser.TYPE.F);
        curFunc = funcSymbol;
        Parser.TYPE retType = functype.equals("int") ? Parser.TYPE.I : Parser.TYPE.V;
        funcSymbol.addFuncReturnType(retType);
        funcSymbol.setGlobal(global);
        SymbolTable.insertTable(funcSymbol);
        //*******符号表*******//

        createIRCode("funcDecl", functype, funcname);

        SymbolTable.openScope("func");

        Node paras = ident.getLeft();
        if (paras != null) {
            for (Node lf : paras.getLeafs()) {
                parseFuncFParam(lf);
            }
        }

        parseBlock(n.getRight(), -111111);
        createIRCode("note", "#end a func");

        SymbolTable.closeScope();
    }

    private void parseFuncFParam(Node n) {
        if (!n.getType().equals("Ident")) {
            System.err.println("ER:FuncFParam Type=" + n.getType());
            return;
        }

        String name = n.getName();
        String kind = n.getKind();

        //第1步，先制作符号表
        Symbol symbol = new Symbol(name, Parser.TYPE.I);

        if (kind.equals("int")) {
            int num = n.getNum();
            symbol.setNum(num);

        } else if (kind.equals("array")) {
            int dimens = n.getNum();
            symbol.setArray();
            symbol.setArrayDimen(dimens);
            if (dimens == 1) {  //FParam不会有dimennum1

            } else if (dimens == 2) {
                int dimennum2 = n.getRight().calcuValue();      //改为了记在Right上
                symbol.setDimen2(dimennum2);
            }
        }
        symbol.setGlobal(global);
        curFunc.insertToParalist(symbol);
        SymbolTable.insertTable(symbol);
        //*******符号表*******//

        String irstr = null;
        if (n.getKind().equals("int") || n.getKind().equals("const int")) {
            irstr = "para int " + name;

        } else if (n.getKind().equals("array") || n.getKind().equals("const array")) {
            int dimen = n.getNum();
            if (dimen == 1) {
                irstr = "para int " + name + "[]";
            } else if (dimen == 2) {
                irstr = "para int " + name + "[][]";
            }
        } else {
            System.out.println("kind = " + n.getKind());
        }
        createIRCode("funcPara", irstr);
    }

    private void parseMainFuncDef(Node n) {
        SymbolTable.openScope("main");
        //noneedopenblock = true;   //不处理，并非到Stmt
        parseBlock(n, -11111);
        SymbolTable.closeScope();
        //noneedopenblock = false;
    }

    private void parseBlock(Node n, int localwhilecount) {
        //jump进入基本块
        /*if (curScope != null) {
            int scpcnt = curScope.innercnt;
            curScope.innercnt += 1;
            curScope = curScope.innerScopeList.get(scpcnt);
        }*/

        if (!n.getType().equals("Block")) {
            System.err.println("ER:Block Type=" + n.getType());
            return;
        }
        for (Node item : n.getLeafs()) {
            parseBlockItem(item, localwhilecount);
        }

        /*//跳出基本块.break与continue需额外处理，循环向father找while块
        if (curScope != null && curScope.father != null) {
            createIRCode("note", "#Out Block");     //jump时配sign的方法处理不了连续 }}}} 情况，与jump共存; mips处理#Out Block时移动sp
            curScope = curScope.father;                         //跳转到father的块.此处IRCode顺序极重要！
        }*/
    }

    private void parseBlockItem(Node n, int localwhilecount) {
        if (n.getType().equals("BlockItem_Decl")) {
            parseDecl(n.getLeft());

        } else if (n.getType().equals("BlockItem_Stmt")) {
            parseStmt(n.getLeft(), localwhilecount);
        } else {
            System.err.println("IRGenerator / parseBlockItem() : ??? what BlockItem type ???");
        }
    }

    private void parseStmt(Node n, int localwhilecount) {
        if (n == null || n.getType() == null) {
            return;
        }

        String type = n.getType();
        SymbolTable.Scope curScope = SymbolTable.headerScope;

        switch (type) {
            case "IfStatement":
                parseIfStatement(n, localwhilecount);
                break;
            case "WhileLoop":
                parseWhileLoop(n);
                break;
            case "break":   //WhileCut情况特殊处理,跳转到father中第1个while块
                String breakstr = "end_loop" + localwhilecount;
                createIRCode("note", "#Out Block WhileCut");     //jump时配sign的方法处理不了连续 }}}} 情况，改此
                createIRCode("jump", breakstr);     //不用 sign=1 标记，Out Block已经处理了

                /*while (curScope != null && curScope.father != null && !curScope.type.equals("while")) {
                    curScope = curScope.father;         //跳转到father中第1个while块 的外面.此处IRCode顺序极重要！
                }

                if (curScope != null && curScope.father != null) {
                    curScope = curScope.father;
                } else {
                    System.err.println("IRGenerator / parseStmt() / Null curScope(or father) !!!");
                }*/

                break;
            case "continue":
                String ctnstr = "begin_loop" + localwhilecount;
                createIRCode("note", "#Out Block WhileCut");     //jump时配sign的方法处理不了连续 }}}} 情况，改此
                createIRCode("jump", ctnstr);     //不用 sign=1 标记，Out Block已经处理了

                /*while (curScope != null && curScope.father != null && !curScope.type.equals("while")) {
                    curScope = curScope.father;         //跳转到father中第1个while块 的外面.此处IRCode顺序极重要！
                }

                if (curScope != null && curScope.father != null) {
                    curScope = curScope.father;
                } else {
                    System.err.println("IRGenerator / parseStmt() / Null curScope(or father) !!!");
                }*/

                break;
            case "Return":
                if (n.getLeft() != null) {
                    Variable t = parseExp(n.getLeft());

                    IRCode ir = new IRCode("return", t);
                    ir.voidreturn = false;  //todo 重要！
                    ir4init(ir);

                } else {
                    IRCode ir = new IRCode("return", "void return");
                    ir.voidreturn = true;  //todo 重要！
                    ir4init(ir);
                }
                break;
            case "Printf":
                parsePrintf(n);
                break;
            case "Block":
                boolean localneedblk = true;
                if (noneedopenblock) {      //noneedblock能抵一次{}
                    localneedblk = false;
                    noneedopenblock = false;
                }
                if (localneedblk) {
                    SymbolTable.openScope("void");
                }

                parseBlock(n.getLeft(), localwhilecount);

                if (localneedblk) {
                    createIRCode("note", "#Out Block");
                    SymbolTable.closeScope();
                }

                break;
            case "Assign_getint":
                Variable getintexp = parseLVal(n.getLeft());    //需要LVal而不是Exp处理，因为可能得sw
                createIRCode("getint", getintexp);
                break;
            case "Assign_value":
                startindex = irList.size();     //初始化ir赋值语句起始位置
                Variable lval = parseLVal(n.getLeft()); //todo 本质上就是parseIdent？【答】不一样，如不需要出临时变量t2
                Variable exp = parseExp(n.getRight());
                createIRCode("assign2", lval, exp);
                break;
            case "Exp":
                if (n.getLeft() != null) {
                    parseExp(n.getLeft());//todo 正确性存疑，看ircode
                }
                break;
            default:
                System.err.println("IRGenerator / parseStmt() :??? what stmt type ???:" + type);
                break;
        }
    }

    private void parseIfStatement(Node n, int localwhilecount) {
        int localifcount = ifcount;   //变为本地，防止嵌套循环 导致 编号混乱的情况
        ifcount += 1;

        String endifLabel = "end_if" + localifcount;
        String endifelseLabel = "end_ifelse" + localifcount;
        String intoblocklabel = "into_if" + localifcount;      //主要用于 || 中间判断成立直接跳入

        parseCond(n.getLeft(), endifLabel, intoblocklabel);

        //进入基本块
        SymbolTable.openScope("if");
        noneedopenblock = true;
        createIRCode("label", intoblocklabel + ":");
        parseStmt(n.getMiddle(), localwhilecount);


        if (n.getRight() != null) {
            createIRCode("note", "#Out Block");     //出基本块sp移动,必须保证此条code在scope内
            createIRCode("jump", endifelseLabel);       //1、跳到end_if, if结构最后一句; 2、不用再处理sp了，Block负责处理好了
            SymbolTable.closeScope();
            noneedopenblock = false;

            createIRCode("label", endifLabel + ":");

            SymbolTable.openScope("else");
            noneedopenblock = true;
            parseStmt(n.getRight(), localwhilecount);
            SymbolTable.closeScope();
            noneedopenblock = false;

            createIRCode("label", endifelseLabel + ":");

        } else {
            createIRCode("note", "#Out Block");     //出基本块sp移动,必须保证此条code在scope内
            SymbolTable.closeScope();
            noneedopenblock = false;

            createIRCode("label", endifLabel + ":");
        }
    }

    private void parseWhileLoop(Node n) {
        int localwhilecount = whilecount;   //变为本地，防止嵌套循环 导致 编号混乱的情况
        whilecount += 1;

        String beginlabel = "begin_loop" + localwhilecount;
        String endlabel = "end_loop" + localwhilecount;
        String intoblocklabel = "into_loop" + localwhilecount;      //主要用于 || 中间判断成立直接跳入

        int whilestartindex = irList.size();

        createIRCode("label", beginlabel + ":");

        parseCond(n.getLeft(), endlabel, intoblocklabel);

        //进入基本块
        SymbolTable.openScope("while");
        SymbolTable.headerScope.startindex = whilestartindex;

        noneedopenblock = true;
        createIRCode("label", intoblocklabel + ":");
        parseStmt(n.getRight(), localwhilecount);

        createIRCode("note", "#Out Block");     //还需要处理sp，修改了Stmt最后一句的Block处理逻辑.必须保证此code在scope内
        createIRCode("jump", beginlabel);
        SymbolTable.closeScope();
        noneedopenblock = false;

        createIRCode("label", endlabel + ":");
    }

    private void parseCond(Node n, String jumpoutlabel, String jumpinlabel) {      //服务于&&，一旦不成立，跳到jumpoutlabel
        String type = n.getType();

        if (type.equals("||") || type.equals("&&")) {
            if (type.equals("&&")) {  //一旦不符合，跳到jumplabel
                parseCond(n.getLeft(), jumpoutlabel, jumpinlabel);
                parseCond(n.getRight(), jumpoutlabel, jumpinlabel);

            } else {
                String logicORjumpLabel = jumpoutlabel + "_logicOR" + logicORcount;
                logicORcount += 1;

                parseCond(n.getLeft(), logicORjumpLabel, jumpinlabel);    //left一旦不成立则跳到logicORjumplabel, ||之后紧接着一条branch跳到成立
                createIRCode("jump", jumpinlabel);

                createIRCode("label", logicORjumpLabel + ":");

                parseCond(n.getRight(), jumpoutlabel, jumpinlabel);    //right一旦不成立则跳到jumpoutlabel
            }

        } else if (type.equals("==") || type.equals("!=")) {
            if (type.equals("==")) {  //一旦不符合，跳到jumplabel
                Variable leftEq = parseEqExp(n.getLeft());     //RelExp是含<、>、<=、>=的Exp
                Variable rightEq = parseEqExp(n.getRight());
                createIRCode("branch", "bne", jumpoutlabel, leftEq, rightEq);

            } else {
                Variable leftEq = parseEqExp(n.getLeft());     //RelExp是含<、>、<=、>=的Exp
                Variable rightEq = parseEqExp(n.getRight());
                createIRCode("branch", "beq", jumpoutlabel, leftEq, rightEq);
            }

            //parseEqExp(n);

        } else if (OperDict.OPCMP_LIST.contains(type)) {

            Variable leftexp = parseRelExp(n.getLeft());
            Variable rightexp = parseRelExp(n.getRight());

            switch (type) {
                case ">=":  //<时branch
                    createIRCode("branch", "blt", jumpoutlabel, leftexp, rightexp);
                    break;
                case "<=":
                    createIRCode("branch", "bgt", jumpoutlabel, leftexp, rightexp);
                    break;
                case ">":
                    createIRCode("branch", "ble", jumpoutlabel, leftexp, rightexp);
                    break;
                case "<":
                    createIRCode("branch", "bge", jumpoutlabel, leftexp, rightexp);
                    break;
                default:
                    break;
            }

        } else {
            if (n.getType().equals("!")) {      //应当满足(!nexp)!=0，因此 nexp!=0 立即跳转
                Variable nexp = parseExp(n.getLeft());
                createIRCode("branch", "bne", jumpoutlabel, nexp, new Variable("num", 0));

            } else {
                //已经是AddExp的情况，应当满足exp!=0，因此 ==0 立即跳转
                Variable exp = parseExp(n);
                createIRCode("branch", "beq", jumpoutlabel, exp, new Variable("num", 0));
            }
        }
    }

    private Variable parseEqExp(Node n) {
        String type = n.getType();
        if (type.equals("==") || type.equals("!=")) {
            Variable lefteq = parseEqExp(n.getLeft());
            Variable righteq = parseEqExp(n.getRight());
            Variable tmpvar = getTmpVar();

            if (type.equals("==")) {  //需要sne和seq
                createIRCode("setcmp", "seq", tmpvar, lefteq, righteq);
            } else {
                createIRCode("setcmp", "sne", tmpvar, lefteq, righteq);
            }
            return tmpvar;

        } else {
            return parseRelExp(n);
        }
    }

    private Variable parseRelExp(Node n) {
        String type = n.getType();
        if (type.equals(">") || type.equals("<") || type.equals(">=") || type.equals("<=")) {
            Variable leftrel = parseRelExp(n.getLeft());
            Variable rightrel = parseRelExp(n.getRight());
            Variable tmpvar = getTmpVar();

            switch (type) {
                case ">=":  //<时branch
                    createIRCode("setcmp", "sge", tmpvar, leftrel, rightrel);
                    break;
                case "<=":
                    createIRCode("setcmp", "sle", tmpvar, leftrel, rightrel);
                    break;
                case ">":
                    createIRCode("setcmp", "sgt", tmpvar, leftrel, rightrel);
                    break;
                case "<":
                    createIRCode("setcmp", "slt", tmpvar, leftrel, rightrel);
                    break;
                default:
                    break;
            }
            return tmpvar;

        } else {
            return parseExp(n);
        }
    }

    private void parsePrintf(Node n) {
        String formatString = n.getLeft().getName();
        formatString = formatString.substring(1, formatString.length() - 1);
        //todo 若str为空的情况

        createIRCode("note", "#Start Print");

        if (n.getRight() != null) {
            String[] splits = formatString.split("%d", -1);
            Node explist = n.getRight();
            for (int i = 0; i < splits.length; i++) {
                String splitstr = splits[i];
                if (!splitstr.equals("")) {
                    Variable var_splitstr = new Variable("str", splitstr);
                    createIRCode("print", var_splitstr);
                }
                if (explist.getLeafs() == null || i > explist.getLeafs().size() - 1) {
                    break;
                }
                Node oneexp = explist.getLeafs().get(i);
                Variable printexp = parseExp(oneexp);
                createIRCode("print", printexp);
            }
        } else {
            Variable var_formatString = new Variable("str", formatString);
            createIRCode("print", var_formatString);
        }
    }

    private Variable parseExp(Node n) {
        String type = n.getType();

        if (type.equals("Ident")) {
            //todo 包括函数调用func；整数int；数组array
            Variable ident = parseIdent(n);

            if (!n.getKind().equals("func")) {    //todo 访问非func类的 int和array
                //System.out.println("name = " + n.getName() + ",  kind = " + n.getKind());
                Symbol symbol = SymbolTable.lookupFullTable(n.getName(), Parser.TYPE.I, SymbolTable.headerScope);
                Assert.check(symbol, "IRGenerator / parseExp() !func " + n.getName());
                ident.setSymbol(symbol);
                //ident.setiskindofsymbolTrue();  //不可统一设置，有t8等情况

            }//todo func一切并不需要

            return ident;

        } else if (type.equals("Number")) {
            return new Variable("num", n.getNum());

        } else if (OperDict.OPERATOR_LIST.contains(type)) {
            switch (type) {
                case "+":
                    if (n.getRight() != null) {
                        return generateOperatorIR(n, "+");
                    }
                    return parseExp(n.getLeft());
                case "-":
                    if (n.getRight() != null) {
                        return generateOperatorIR(n, "-");
                    }
                    // return "-" + parseExp(n.getLeft());
                    //todo 也许更好的方法

                    Variable leftexp = new Variable("num", 0);
                    Variable rightexp = parseExp(n.getLeft());

                    if (rightexp.getType().equals("num")) {
                        return new Variable("num", -rightexp.getNum());

                    } else {
                        Variable tmpvar = getTmpVar();
                        createIRCode("assign", "-", tmpvar, leftexp, rightexp);
                        return tmpvar;
                    }

                case "*":
                    return generateOperatorIR(n, "*");
                case "/":
                    return generateOperatorIR(n, "/");
                case "%":
                    return generateOperatorIR(n, "%");
                default:
                    System.err.println("Get error op when calcuExp! OpType = " + type);
                    return null;
            }
        } else if (type.equals("!")) {
            Variable nexp = parseExp(n.getLeft());
            Variable tmpvar = getTmpVar();
            createIRCode("setcmp", "seq", tmpvar, nexp, new Variable("num", 0));    //seq tmpvar等于0置1
            return tmpvar;

        } else {
            System.err.println("IRGenerator / parseExp() :??? what type ??? type = " + type);
            return null;
        }
    }

    private Variable parseLVal(Node n) {
        String kind = n.getKind();

        if (kind.equals("int") || kind.equals("const int")) {   //todo !!!!!!!md const int也是这类
            Variable lval = new Variable("var", n.getName());

            Symbol symbol = SymbolTable.lookupFullTable(n.getName(), Parser.TYPE.I, SymbolTable.headerScope);
            Assert.check(symbol, "IRGenerator / parseLVal()");
            lval.setSymbol(symbol);
            lval.setiskindofsymbolTrue();

            return lval;

        } else if (kind.equals("array") || kind.equals("const array")) { //todo !!!!!!!md const array也是这类
            Variable name_and_index = parseArrayVisit(n);   //已经很完善地访问数组了，包括setSymbol
            return name_and_index;

        } else {
            System.out.println("IRGenerator / parseLVal(): ??? kind = " + kind);
            return null;
        }
    }

    private Variable parseIdent(Node n) {
        String kind = n.getKind();

        if (kind.equals("int") || kind.equals("const int")) {   //todo !!!!!!!md const int也是这类
            if (kind.equals("const int")) {     //优化
                String name = n.getName();
                Symbol symbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
                int num = symbol.getNum();
                return new Variable("num", num);

            } else {
                Variable intvar = new Variable("var", n.getName());
                intvar.setiskindofsymbolTrue();     //在parseIdent内独立设置
                return intvar;
            }

        } else if (kind.equals("array") || kind.equals("const array")) {    //todo !!!!!!!md const array也是这类
            if (kind.equals("const array")) {
                return parseConstArrayVisit(n);

            } else {
                startindex = irList.size();     //初始化ir赋值语句起始位置
                Variable name_and_index = parseArrayVisit(n);
                Variable tmpvar = getTmpVar();
                createIRCode("assign2", tmpvar, name_and_index);
                return tmpvar;
            }

        } else if (kind.equals("func")) {   //left = paras
            String funcname = n.getName();
            Node rparams = n.getLeft();

            Symbol func = SymbolTable.lookupFullTable(funcname, Parser.TYPE.F, SymbolTable.foreverGlobalScope);

            if (rparams != null) {      //函数有参数则push
                for (int i = 0; i < rparams.getLeafs().size(); i++) {
                    Node para = rparams.getLeafs().get(i);

                    Symbol fparami = func.getParalist().get(i); //函数的第i个参数类型
                    int arraydimen = fparami.getArrayDimen();

                    if (fparami.getIsArray()) {       //如果是array类型的函数参数
                        Variable paraexp = parseArrayExp(para, arraydimen);       //需返回array类型
                        createIRCode("push", paraexp);

                    } else {
                        Variable paraexp = parseExp(para);      //正常的var类型exp
                        createIRCode("push", paraexp);
                    }
                }
            }

            IRCode ir = new IRCode("call", funcname);   //补充了把func的Symbol塞入call的ircode
            ir.setSymbol(func);
            ir4init(ir);

            if (func.getFuncReturnType() != null && func.getFuncReturnType() != Parser.TYPE.V) {
                Variable tmpvar = getTmpVar();
                createIRCode("assign_ret", tmpvar);
                return tmpvar;
            }
            return null;    //todo viod类型函数返回值

        } else {
            System.err.println("IRGenerator / parseIdent() : ??? what kind = " + kind);
            return null;
        }
    }

    private Variable parseArrayExp(Node n, int arrdimen) {     //为函数传参服务，只返回array类型，arrdimen是参数应当的维度
        String type = n.getType();
        String name = n.getName();

        Symbol arraysymbol = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
        Assert.check(arraysymbol, "IRGenerator / parseArrayExp()" + n.getName());

        Variable arrayident = new Variable("array", name);

        if (arraysymbol.getArrayDimen() == 2) {   //x=2时，原貌传入,无需处理
            if (arrdimen == 1) {
                /*int arraynum = n.getLeft().calcuValue();    //数组第一维下标//todo 也许得parseExp
                Variable numvar = new Variable("num", arraynum);*/

                Variable numvar = parseExp(n.getLeft());        //数组第一维下标
                arrayident.setVar(numvar);
            }

        } else if (arraysymbol.getArrayDimen() == 1) {   //原貌传入,无需处理

        } else {
            System.err.println("IRGenerator / parseArrayExp() : ??? what array = " + type);
            return null;
        }
        arrayident.setSymbol(arraysymbol);
        arrayident.setiskindofsymbolTrue(); //todo 也许不用
        return arrayident;
    }

    private Variable parseArrayVisit(Node n) {
        Node ident = n;
        String name = ident.getName();   //指id

        Symbol array = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
        Assert.check(array, "IRGenerator / parseArrayVisit()");

        if (array.getArrayDimen() == 2) {   //二维数组处理成一维如a[t1]
            int arraydimen2 = array.getDimen2();

            Variable var_x = parseExp(n.getLeft());
            Variable var_arraydimen2 = new Variable("num", arraydimen2);
            Variable var_y = parseExp(n.getRight());

            if (var_x.getType().equals("num")) {
                if (var_y.getType().equals("num")) {
                    int offsetnum = var_x.getNum() * arraydimen2 + var_y.getNum();
                    Variable offset = new Variable("num", offsetnum);
                    Variable retVar = new Variable("array", name, offset);
                    retVar.setSymbol(array);    //只设置symbol，但不可kindofSymbol=True
                    return retVar;

                } else {
                    int t1num = var_x.getNum() * arraydimen2;
                    Variable t1 = new Variable("num", t1num);
                    Variable offset = getTmpVar();
                    createIRCode("assign", "+", offset, var_y, t1);
                    Variable retVar = new Variable("array", name, offset);
                    retVar.setSymbol(array);    //只设置symbol，但不可kindofSymbol=True
                    return retVar;
                }

            } else {
                Variable tmpvar1 = getTmpVar();
                createIRCode("assign", "*", tmpvar1, var_x, var_arraydimen2);

                Variable offset = getTmpVar();

                createIRCode("assign", "+", offset, var_y, tmpvar1);

                Variable retVar = new Variable("array", name, offset);
                retVar.setSymbol(array);    //只设置symbol，但不可kindofSymbol=True
                return retVar;
            }

        } else {    //一维数组正常访问
            Variable var_x = parseExp(ident.getLeft());
            Variable retVar = new Variable("array", name, var_x);
            retVar.setSymbol(array);
            return retVar;
        }
    }

    //常量直接取array num
    private Variable parseConstArrayVisit(Node n) {
        Node ident = n;
        String name = ident.getName();   //指id

        Symbol array = SymbolTable.lookupFullTable(name, Parser.TYPE.I, SymbolTable.headerScope);
        Assert.check(array, "IRGenerator / parseArrayVisit()");

        if (array.getArrayDimen() == 2) {   //二维数组处理成一维如a[t1]
            int arraydimen2 = array.getDimen2();

            Variable var_x = parseExp(n.getLeft());
            Variable var_arraydimen2 = new Variable("num", arraydimen2);
            Variable var_y = parseExp(n.getRight());

            if (var_x.getType().equals("num")) {
                if (var_y.getType().equals("num")) {
                    int offsetnum = var_x.getNum() * arraydimen2 + var_y.getNum();
                    int arrayvalue = array.arrayList.get(offsetnum);
                    return new Variable("num", arrayvalue);

                } else {
                    int t1num = var_x.getNum() * arraydimen2;
                    Variable t1 = new Variable("num", t1num);
                    Variable offset = getTmpVar();
                    createIRCode("assign", "+", offset, var_y, t1);
                    Variable retVar = new Variable("array", name, offset);
                    retVar.setSymbol(array);    //只设置symbol，但不可kindofSymbol=True
                    return retVar;
                }

            } else {
                Variable tmpvar1 = getTmpVar();
                createIRCode("assign", "*", tmpvar1, var_x, var_arraydimen2);

                Variable offset = getTmpVar();

                createIRCode("assign", "+", offset, var_y, tmpvar1);

                Variable retVar = new Variable("array", name, offset);
                retVar.setSymbol(array);    //只设置symbol，但不可kindofSymbol=True
                return retVar;
            }

        } else {    //一维数组正常访问
            Variable var_x = parseExp(ident.getLeft());
            Variable retVar = new Variable("array", name, var_x);
            retVar.setSymbol(array);
            return retVar;
        }
    }

    //辅助函数
    private void createIRCode(String type, String IRstring) {
        IRCode ir = new IRCode(type, IRstring);
        ir4init(ir);
    }

    private void createIRCode(String type, Variable variable) {
        IRCode ir = new IRCode(type, variable);
        ir4init(ir);
    }

    private void createIRCode(String type, String kind, String name) {
        IRCode ir = new IRCode(type, kind, name);
        ir.init = false;
        ir4init(ir);
    }

    //assign2
    private void createIRCode(String type, Variable dest, Variable oper1) {
        IRCode ir = new IRCode(type, dest, oper1);
        ir4init(ir);
    }

    //assign
    private void createIRCode(String type, String operator, Variable dest, Variable oper1, Variable oper2) {
        IRCode ir = new IRCode(type, operator, dest, oper1, oper2);
        ir4init(ir);
    }

    //Cond branch部分专用
    private void createIRCode(String type, String instr, String jumploc, Variable oper1, Variable oper2) {
        IRCode ir = new IRCode(type, instr, jumploc, oper1, oper2);
        ir4init(ir);
    }

    //该部分摘自建符号表 parseDef ,为int或array的定义建符号表
    private void makeSymbolDef(Node n) {
        Node ident = n.getLeft();
        String name = ident.getName();   //指id
        String kind = ident.getKind();

        if (kind.equals("array") || kind.equals("const array")) {
            Symbol symbol = new Symbol(name, Parser.TYPE.I);
            symbol.setArray();
            if (n.getType().equals("ConstDef")) {
                symbol.setConst();
            }

            Node dimen1 = ident.getLeft();
            int dimennum1 = dimen1.calcuValue();    //symbol计算的dimen1,2有隐患，尽量少用。【修复】可用
            int dimennum2 = 0;
            symbol.setDimen1(dimennum1);
            symbol.setArrayDimen(1);

            if (ident.getRight() != null) {
                Node dimen2 = ident.getRight();
                dimennum2 = dimen2.calcuValue();
                symbol.setDimen2(dimennum2);
                symbol.setArrayDimen(2);
            }

            Node init = n.getRight();
            if (init != null && (kind.equals("const array") || global)) {
                if (dimennum2 == 0) {   //一维数组
                    for (int i = 0; i < dimennum1; i++) {
                        int initnum = init.getLeafs().get(i).calcuValue();  //todo 也需分可直接算出的ConstInivial与Initval两种情况
                        symbol.arrayList.add(initnum);
                    }
                } else {
                    for (int i = 0; i < dimennum1; i++) {
                        for (int j = 0; j < dimennum2; j++) {
                            int initnum = init.getLeafs().get(i).getLeafs().get(j).calcuValue();
                            symbol.arrayList.add(initnum);
                        }
                    }
                }
            }

            symbol.setGlobal(global);
            SymbolTable.insertTable(symbol);

        } else {    //int与const int?
            Symbol symbol = new Symbol(name, Parser.TYPE.I);
            if (n.getType().equals("ConstDef")) {
                symbol.setConst();
            }
            //【加入初始化】const的类型会用到。此处ConstInivial可立即计算初值，Inivial需计算表达式

            Node init = n.getRight();
            if (init != null && (kind.equals("const int") || global)) {
                int constinitnum = init.calcuValue();
                symbol.setNum(constinitnum);
            }

            symbol.setGlobal(global);
            SymbolTable.insertTable(symbol);
        }
    }

    //包装global/scope/rawstr/irList.add()
    private void ir4init(IRCode ir) {
        ir.setGlobal(global);
        ir.setScope(SymbolTable.headerScope);
        ir.concatRawstr();

        //new
        ir.startindex = startindex;

        irList.add(ir);
    }

    private Variable getTmpVar() {
        varcount += 1;
        String name = "t" + varcount;
        Variable var = new Variable("var", name);
        var.scope = SymbolTable.headerScope;
        return var;
    }

    private Variable generateOperatorIR(Node n, String op) {
        Variable leftexp = parseExp(n.getLeft());
        Variable rightexp = parseExp(n.getRight());

        if (leftexp.getType().equals("num") && rightexp.getType().equals("num")) {
            int leftnum = leftexp.getNum();
            int rightnum = rightexp.getNum();

            switch (op) {
                case "+":
                    return new Variable("num", leftnum + rightnum);
                case "-":
                    return new Variable("num", leftnum - rightnum);
                case "*":
                    return new Variable("num", leftnum * rightnum);
                case "/":
                    return new Variable("num", leftnum / rightnum);
                case "%":
                    return new Variable("num", leftnum % rightnum);
                default:
                    System.err.println("IRGenerator / generateOperatorIR what op type??");
                    return null;
            }
        } else {
            Variable tmpvar = getTmpVar();
            createIRCode("assign", op, tmpvar, leftexp, rightexp);
            return tmpvar;
        }
    }
}
