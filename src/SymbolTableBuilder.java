import java.util.ArrayList;

public class SymbolTableBuilder {
    private Node tree;
    private ArrayList<newError> errList = new ArrayList<>();

    private Symbol curFunc;  //当前调用的函数

    private int funcParaIndex;      //对照参数是否匹配时的index
    //private boolean funcErrorE = false;     //是否具有E错误
    private int curDimen = 0;           //当前变量数组维度
    private boolean global = true;    //是否为顶层

    private int cycleDepth = 0;
    public boolean funcHasReturn = false;  //todo 没有处理结束，可能隐藏bug
    public boolean hasReturn = false;  //当前函数是否有过return
    public boolean lastIsReturn = false;       //是否最后一个为return

    private String curBlockType = "";   //标记当前block的种类，为mips时 while的continue与break用

    public SymbolTableBuilder(Node ASTtree) {
        this.tree = ASTtree;
    }

    public void buildtable(int handleError) {
        //SymbolTable.globalScope = SymbolTable.headerScope;
        parseTree();
    }

    private void parseTree() {
        if (tree.getLeft() != null) {
            parseDeclList(tree.getLeft());
        }
        global = false;
        if (tree.getMiddle() != null) {
            parseFuncDeclList(tree.getMiddle());
        }
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

        /*n.setHeaderScope(); //todo 不仅仅def时需要header，更需要的是使用变量时set header
        ident.setHeaderScope();*/
        //System.out.println("set head:" + name);

        processAllHeader(n);    //不仅仅def时需要header，更需要的是使用变量时set header.暴力处理了：d[constArray[1]][a[0][0] + 1]情况

        if (kind.equals("array") || kind.equals("const array")) {
            Symbol symbol = new Symbol(name, Parser.TYPE.I);
            symbol.setArray();

            Node dimen1 = ident.getLeft();
            int dimennum1 = dimen1.calcuValue();    //symbol计算的dimen1,2有隐患，尽量少用
            symbol.setDimen1(dimennum1);
            symbol.setArrayDimen(1);

            if (ident.getRight() != null) {
                Node dimen2 = ident.getRight();
                int dimennum2 = dimen2.calcuValue();
                symbol.setDimen2(dimennum2);
                symbol.setArrayDimen(2);
            }
            Node init = n.getRight();
            if (init != null) {
                //setExpSetHeaderScope(init);
                //todo !!!一定先初始化处理init，设置headerscope
                processHeaderExp(init);
                //int num = init.calcuValue();
                //symbol.setNum(num); //todo 不用管，后从未用到
            }

            symbol.setGlobal(global);
            SymbolTable.insertTable(symbol);

        } else {    //int与const int?
            Symbol symbol = new Symbol(name, Parser.TYPE.I);
            if (n.getType().equals("ConstDef")) {
                symbol.setConst();
            }
            //todo 暂时没管初始化【加入初始化】
            //todo 此处ConstInivial可立即计算初值，Inivial需计算表达式
            Node init = n.getRight();
            if (init != null) {
                processHeaderExp(init);     //todo !!!一定先初始化处理exp，设置headerscope

                //todo 仅constinitvial可calcuValue计算初值.可加一个函数？
                if (n.getType().equals("ConstDef")) {
                    int num = init.calcuValue();
                    //System.out.println("const symbol " + name + "'s num = " + num);
                    symbol.setNum(num);
                }
            }

            symbol.setGlobal(global);
            SymbolTable.insertTable(symbol);
        }
    }

    private void parseFuncDeclList(Node n) {
        for (Node lf : n.getLeafs()) {
            parseFuncDecl(lf);
        }
    }

    private void parseFuncDecl(Node n) {
        //Token thisIdent = new Token("a", "a", -100);

        Node ident = n.getLeft();
        String funcname = ident.getName();
        String functype = ident.getKind();

        n.setHeaderScope();

        funcHasReturn = functype.equals("int");
        hasReturn = false;
        lastIsReturn = false;

        Symbol funcSymbol = new Symbol(funcname, Parser.TYPE.F);
        curFunc = funcSymbol;
        Parser.TYPE retType = functype.equals("int") ? Parser.TYPE.I : Parser.TYPE.V;
        funcSymbol.addFuncReturnType(retType);

        /*if (SymbolTable.lookupNameOnly(funcname) != null) {
            ErrorAt(thisIdent, "b");
        } else*/
        funcSymbol.setGlobal(global);
        SymbolTable.insertTable(funcSymbol);

        SymbolTable.openScope("func");

        //todo 此处para要插入funcpara

        Node paras = ident.getLeft();
        if (paras != null) {
            for (Node lf : paras.getLeafs()) {
                parseFuncFParam(lf);
            }
        }

        parseBlock(n.getRight());    //0=不openScope，仅funcDecl使用；1=正常创建【舍弃，回到0方案】

        SymbolTable.closeScope();

        //todo handleErrorG(getLastToken());
    }

    private void parseFuncFParam(Node n) {
        if (!n.getType().equals("Ident")) {
            System.err.println("ER:FuncFParam Type=" + n.getType());
            return;
        }
        Node ident = n;
        ident.setHeaderScope();

        //Token thisIdent = new Token("a", "a", -100);

        String name = ident.getName();
        Symbol symbol = new Symbol(name, Parser.TYPE.I);

        if (ident.getKind().equals("int")) {
            int num = ident.getNum();
            symbol.setNum(num);

        } else if (ident.getKind().equals("array")) {
            int dimens = ident.getNum();
            symbol.setArray();
            symbol.setArrayDimen(dimens);
            if (dimens == 1) {
                //int dimennum1 = ident.getLeft().calcuValue();
                //symbol.setDimen1(dimennum1);

                //todo FParam不会有dimennum1

            } else if (dimens == 2) {
                //int dimennum1 = ident.getLeft().calcuValue();
                int dimennum2 = ident.getRight().calcuValue();      //改为了记在Right上
                //symbol.setDimen1(dimennum1);
                symbol.setDimen2(dimennum2);
            }
        }

        /*if (checkErrorB(thisIdent, Parser.TYPE.I)) {
            ErrorAt(thisIdent, "b");
        } else*/

        symbol.setGlobal(global);

        curFunc.insertToParalist(symbol);

        SymbolTable.insertTable(symbol);
    }

    private void parseMainFuncDef(Node n) {
        SymbolTable.openScope("main");
        //curBlockType = "main";
        parseBlock(n);    //0=不openScope，仅funcDecl使用；1=正常创建
        //curBlockType = "";
        SymbolTable.closeScope();
    }

    private void parseBlock(Node n/*, int create*/) {
        if (!n.getType().equals("Block")) {
            System.err.println("ER:Block Type=" + n.getType());
            return;
        }

        /*String blktype = "void";
        if(!curBlockType.equals("")){
            blktype = curBlockType;
            curBlockType = "";
        }

        if (create == 1) {
            SymbolTable.openScope(blktype);
        }*/

        for (Node item : n.getLeafs()) {
            parseBlockItem(item);
        }

        /*if (create == 1) {
            SymbolTable.closeScope();
        }*/
    }

    private void parseBlockItem(Node n) {
        if (n.getType().equals("BlockItem_Decl")) {
            parseDecl(n.getLeft());

        } else if (n.getType().equals("BlockItem_Stmt")) {
            parseStmt(n.getLeft());

        } else {
            System.err.println("SystemTableBuilder / parseBlockItem() : ??? what BlockItem type ???");
        }
    }

    private void parseStmt(Node n) {
        if (n == null || n.getType() == null) {
            return;
        }
        String type = n.getType();
        switch (type) {
            case "IfStatement":
                parseIfStatement(n);
                break;
            case "WhileLoop":
                parseWhileLoop(n);
                break;
            case "break":
                break;
            case "continue":
                break;
            case "Return":
                parseReturn(n); //todo !!!!return里如果有exp也要处理！！！！
                break;
            case "Printf":
                parsePrintf(n);
                break;
            case "Block":
                SymbolTable.openScope("void");
                parseBlock(n.getLeft());
                SymbolTable.closeScope();
                break;
            case "Assign_getint":
                parseLVal(n.getLeft());
                break;
            case "Assign_value":    //left = lval; right = exp
                parseLVal(n.getLeft());
                processHeaderExp(n.getRight());
                break;
            case "Exp":
                processAllHeader(n);
                break;
            default:
                System.err.println("SystemTableBuilder / parseStmt() :??? what stmt type ??? type = " + type);
                break;
        }
    }

    private void parseIfStatement(Node n) {
        parseCond(n.getLeft());

        SymbolTable.openScope("if");

        //curBlockType = "if";
        parseStmt(n.getMiddle());
        //curBlockType = "";

        SymbolTable.closeScope();

        if (n.getRight() != null) {
            SymbolTable.openScope("else");

            //curBlockType = "else";
            parseStmt(n.getRight());
            //curBlockType = "";

            SymbolTable.closeScope();
        }
    }

    private void parseWhileLoop(Node n) {
        parseCond(n.getLeft());

        SymbolTable.openScope("while");

        //curBlockType = "while";
        parseStmt(n.getRight());
        //curBlockType = "";

        SymbolTable.closeScope();
    }

    private void parsePrintf(Node n) {
        //todo 此处需要对exp进行 setheader

        if (n.getRight() != null) {
            Node explist = n.getRight();
            for (int i = 0; i < explist.getLeafs().size(); i++) {
                Node oneexp = explist.getLeafs().get(i);
                processHeaderExp(oneexp);   //对所有exp中ident设置headerscope
                //todo 改了一下
            }
        }
    }

    private void parseCond(Node n) {
        processAllHeader(n);
    }

    private void parseReturn(Node n) {  //left = return exp
        if (n.getLeft() != null) {
            processHeaderExp(n.getLeft());
        }
    }

    private void processHeaderExp(Node n) {
        processAllHeader(n);

        /*n.setHeaderScope(); //todo N重保险，总之一定scope设置好

        String type = n.getType();
        if (type.equals("Ident")) {
            //todo 包括函数调用func；整数int；数组array
            //todo 此处若为func， left=para，也得调用，直接headerAll了
            parseIdent(n);

        } else if (type.equals("Number")) {
            n.setHeaderScope();

        } else if (type.equals("ConstInitVal") || type.equals("InitVal")) {
            n.setHeaderScope();
            if (n.getLeafs() != null) {
                for (Node leafn : n.getLeafs()) {
                    //System.out.println("process Node name = "+leafn.getName());
                    processHeaderExp(leafn);
                }
            }

        } else if (OperDict.OPERATOR_LIST.contains(type)) {
            switch (type) {
                case "+":
                    processHeaderExp(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    if (n.getRight() != null) {
                        processHeaderExp(n.getRight());
                    }
                    break;
                case "-":
                    processHeaderExp(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    if (n.getRight() != null) {
                        processHeaderExp(n.getRight());
                    }
                    break;
                case "*":
                    processHeaderExp(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    processHeaderExp(n.getRight());
                    break;
                case "/":
                    processHeaderExp(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    processHeaderExp(n.getRight());
                    break;
                case "%":
                    processHeaderExp(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    processHeaderExp(n.getRight());
                    break;
                default:
                    System.err.println("Get error op when calcuExp! OpType = " + type);
                    break;
            }
        } else {
            System.err.println("SymbolTableBuilder / processHeaderExp(): unknown type :" + type);
        }*/
    }

    private void processAllHeader(Node n) {
        n.setHeaderScope();
        if (n.getLeft() != null) {
            processAllHeader(n.getLeft());
        }
        if (n.getRight() != null) {
            processAllHeader(n.getRight());
        }
        if (n.getLeafs() != null) {
            for (Node leafn : n.getLeafs()) {
                processAllHeader(leafn);
            }
        }
        return;
    }

    private void parseIdent(Node n) {
        n.setHeaderScope();
        //System.out.println("setheader: " + n.getKind() + " " + n.getName());
    }

    private void parseLVal(Node n) {
        //n.setHeaderScope();
        processAllHeader(n);//todo 上面那个应该就行
    }

    //处理print,const情况，exp中每一个变量setheader
    /*private void setExpSetHeaderScope(Node n){
        String type = n.getType();
        if (type.equals("Ident")) {
            //todo 包括函数调用func；整数int；数组array
            parseIdent(n);

        } else if (type.equals("Number")) {
            return;

        } else if (OperDict.OPERATOR_LIST.contains(type)) {
            switch (type) {
                case "+":
                    setExpSetHeaderScope(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    if (n.getRight() != null) {
                        setExpSetHeaderScope(n.getRight());
                    }
                    break;
                case "-":
                    setExpSetHeaderScope(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    if (n.getRight() != null) {
                        setExpSetHeaderScope(n.getRight());
                    }
                    break;
                case "*":
                    setExpSetHeaderScope(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    setExpSetHeaderScope(n.getRight());
                    break;
                case "/":
                    setExpSetHeaderScope(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    setExpSetHeaderScope(n.getRight());
                    break;
                case "%":
                    setExpSetHeaderScope(n.getLeft());      //仅起到对Ident 的 setHeaderScope作用
                    setExpSetHeaderScope(n.getRight());
                    break;
                default:
                    System.err.println("SymbolTableBuilder/setExpSetHeaderScope(): Get error op when calcuExp! OpType = " + type);
                    break;
            }
        } else {
            System.err.println("SymbolTableBuilder/setExpSetHeaderScope(): ??? what type ???");
        }
    }*/


    //辅助函数(以下为 Error部分)
    public void ErrorAt(Token tk, String type) {
        newError e = new newError(tk.getRow(), type);

        //一个拙劣的排序！！
        int i = 0;
        if (!errList.isEmpty()) {
            i = errList.size();
            while (errList.get(i - 1).getRow() > e.getRow() /*||
                    (errList.get(i - 1).getRow() == e.getRow() && errList.get(i - 1).getType().compareTo(e.getType()) > 0)*/) {
                i -= 1;
                if (i == 0) {
                    break;
                }
            }
        }
        errList.add(i, e);
    }

    public void handleErrorA(Token tk) {
        String formatString = tk.getTokenValue();
        if (checkErrorA(formatString)) {
            ErrorAt(tk, "a");
        }
    }

    //格式字符串中出现非法字符，报错行号为<FormatString>所在行数。
    private boolean checkErrorA(String formatstring) {
        int index = 0;
        formatstring = formatstring.substring(1, formatstring.length() - 1);
        while (index < formatstring.length()) {
            char c = formatstring.charAt(index);
            int a = (int) c;
            if (a == 32 || a == 33 || (a >= 40 && a <= 126 && a != 92)) {
                index += 1;
            } else if (a == 37) {
                if (index + 1 < formatstring.length() && formatstring.charAt(index + 1) == 'd') {
                    index += 2;
                } else {
                    return true;
                }
            } else if (a == 92) {
                if (index + 1 < formatstring.length() && formatstring.charAt(index + 1) == 'n') {
                    index += 2;
                } else {
                    return true;
                }
            } else {
                return true;
            }
        }
        return false;
    }

    //check error
    public boolean checkErrorB(Token tk, Parser.TYPE type) {
        Symbol syb = SymbolTable.lookupLocalTable(tk.getTokenValue(), type);
        return syb != null;
    }

    public boolean checkErrorC(Token tk, Parser.TYPE type) {
        //todo 下面此处函数参数2存疑
        Symbol syb = SymbolTable.lookupFullTable(tk.getTokenValue(), type);
        return syb == null;
    }

/*    //<LVal>为常量时，不能对其修改。报错行号为<LVal>所在行号。
    public boolean checkErrorH(Token tk) {
        lookup
        return tk.getTokenCode().equals("");
    }*/

    //无返回值的函数存在不匹配的return语句
    public void handleErrorF(Token tk) {
        if (!funcHasReturn && hasReturn) {
            ErrorAt(tk, "f");
        }
    }

    public void handleErrorG(Token tk) {
        if (funcHasReturn && !lastIsReturn) {
            ErrorAt(tk, "g");
        }
    }

    //printf中格式字符与表达式个数不匹配
    public boolean checkErrorL(String formatString, int num) {
        int fdNum = formatString.split("%d", -1).length - 1;
        return fdNum != num;
    }

    //在非循环块中使用break和continue语句
    public boolean checkErrorM() {
        return cycleDepth == 0;
    }

    public void enterCycleBlock() {
        cycleDepth += 1;
    }

    public void quitCycleBlock() {
        cycleDepth -= 1;
    }
}
