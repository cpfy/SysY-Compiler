import java.util.ArrayList;

public class ASTBuilder {
    private Token sym;
    private int index;
    private final ArrayList<Token> tokenList;
    private ArrayList<String> grammarList;
    private SymbolTable table;

    private int curDimen = 0;           //当前变量数组维度

    private final String OUTPUT_DIR = "output.txt";

    public ASTBuilder(ArrayList<Token> tokenList) {
        this.index = 0;
        this.sym = tokenList.get(0);
        this.tokenList = tokenList;
        this.grammarList = new ArrayList<>();
        this.table = new SymbolTable();
    }

    public Node getTree() {
        return CompUnit();
    }

    /*不用输出的类型： <BlockItem><Decl><BType> */
    /*CompUnit → {Decl} {FuncDef} MainFuncDef
    声明 Decl → ConstDecl | VarDecl
        → 'const' BType ConstDef { ',' ConstDef } ';' | BType VarDef { ',' VarDef } ';'
            → 常数定义 ConstDef → Ident { '[' ConstExp ']' } '=' ConstInitVal
            → 变量定义 VarDef → Ident { '[' ConstExp ']' }  | Ident { '[' ConstExp ']' } '=' InitVal
        → 'const' 'int' Ident... | 'int' Ident { '[' ConstExp ']' } ['=' InitVal] { ',' VarDef } ';'
    函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
        → 函数类型 FuncType → 'void' | 'int'
        → ('void' | 'int') Ident '(' [FuncFParams] ')' Block
    主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block
     */
    private Node CompUnit() {
        Node decl = new Node("Decl");
        Node func = new Node("Func");
        while ((symCodeIs("CONSTTK") && symPeek("INTTK", 1) && symPeek("IDENFR", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("LBRACK", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("ASSIGN", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("COMMA", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("SEMICN", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && !symPeek("LPARENT", 2))
        ) {
            Node n = Decl();
            decl.addNode(n);
        }
        while ((symCodeIs("VOIDTK") && symPeek("IDENFR", 1) && symPeek("LPARENT", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("LPARENT", 2))
        ) {
            Node n = FuncDef();
            func.addNode(n);
        }

        Node main = MainFuncDef();
        Node root = new Node("CompUnit", decl, func, main);
        return root;
    }

    private Node FuncDef() {    //函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
        String functype = sym.getTokenValue();
        FuncType();

        Node ident = Ident();
        ident.setKind(functype);

        match("(");

        Node funcparams = null;
        if (symIs(")")) {
            getsym();

        } else {
            funcparams = FuncFParams();
            ident.setLeft(funcparams);
            match(")");
        }
        Node funcbody = Block();

        return new Node("FuncDef", ident, funcbody);
    }

    private Node MainFuncDef() {    //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block
        matchCode("INTTK");
        matchCode("MAINTK");
        match("(");
        match(")");

        Node root = Block();

        return root;
    }

    private Node FuncType() {   //函数类型 FuncType → 'void' | 'int'
        if (symCodeIs("VOIDTK") || symCodeIs("INTTK")) {
            getsym();
            //grammarList.add("<FuncType>");
        } else {
            error();
        }
        return null;
    }

    private Node FuncFParams() {    //函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
        Node root = new Node("FuncFParams");
        Node branch = FuncFParam();
        root.addNode(branch);

        while (symIs(",")) {
            getsym();
            branch = FuncFParam();
            root.addNode(branch);
        }
        return root;
    }

    private Node FuncFParam() {     //函数形参 FuncFParam → BType Ident ['[' ']' { '[' ConstExp ']' }]
        BType();

        Node ident = Ident();
        ident.setKind("int");

        Node cexp = null;
        if (symIs("[")) {
            getsym();
            match("]");

            ident.setKind("array");
            ident.setNum(1);    //标记dimen=1

            if (symIs("[")) {
                getsym();
                cexp = ConstExp();
                ident.setRight(cexp);
                ident.setNum(2);    //标记dimen=2
                match("]");
            }
        }
        return ident;
    }

    private Node BType() {
        matchCode("INTTK");
        return null;
    }

    private Node Ident() {
        String name = sym.getTokenValue();
        matchCode("IDENFR");
        return new Node("Ident", name);
    }

    private Node Block() {      //语句块 Block → '{' { BlockItem } '}'
        Node root = new Node("Block");
        match("{");

        while (!symIs("}")) {
            Node item = BlockItem();
            root.addNode(item);
        }

        match("}");
        return root;
    }

    //语句块项 BlockItem → Decl | Stmt
    /*声明 Decl → ConstDecl | VarDecl
        → 'const' BType ConstDef { ',' ConstDef } ';' | BType VarDef { ',' VarDef } ';'     */
    /*Stmt → LVal '=' Exp ';'
        | [Exp] ';'
        | Block
        | 'if' '( Cond ')' Stmt [ 'else' Stmt ]
        | 'while' '(' Cond ')' Stmt
        | 'break' ';' | 'continue' ';'
        | 'return' [Exp] ';'
        | LVal = 'getint''('')'';'
        | 'printf''('FormatString{,Exp}')'';'   */
    private Node BlockItem() {
        if (symIs("const") || symIs("int")) {
            Node root = new Node("BlockItem_Decl");
            Node decl = Decl();
            root.setLeft(decl);
            return root;
        } else {
            Node root = new Node("BlockItem_Stmt");
            Node stmt = Stmt();
            root.setLeft(stmt);
            return root;
        }
    }

    //声明 Decl → ConstDecl | VarDecl
    //常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
    //变量声明 VarDecl → BType VarDef { ',' VarDef } ';'
    private Node Decl() {
        if (symIs("const")) {
            return ConstDecl();
        } else {
            return VarDecl();
        }
    }

    private Node VarDecl() {     //变量声明 VarDecl → BType VarDef { ',' VarDef } ';'
        BType();

        Node root = new Node("VarDecl");
        root.setKind("int");

        Node var = VarDef();
        root.addNode(var);

        while (symIs(",")) {
            getsym();
            var = VarDef();
            root.addNode(var);
        }
        match(";");

        return root;
    }

    //变量定义 VarDef → Ident { '[' ConstExp ']' }  |  Ident { '[' ConstExp ']' } '=' InitVal
    //变量定义 VarDef → Ident { '[' ConstExp ']' }  ['=' InitVal]
    private Node VarDef() {
        Node ident = Ident();
        ident.setKind("int");

        if (symIs("[")) {
            getsym();
            Node dimen1 = ConstExp();
            ident.setKind("array");
            ident.setLeft(dimen1);
            match("]");
        }

        if (symIs("[")) {
            getsym();
            Node dimen2 = ConstExp();
            ident.setRight(dimen2);
            match("]");
        }

        Node initval = null;
        if (symIs("=")) {
            getsym();
            initval = InitVal();
        }

        return new Node("VarDef", ident, initval);
    }

    private Node ConstDecl() {      //常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
        if (!symIs("const")) error();
        getsym();

        BType();

        Node root = new Node("ConstDecl");
        root.setKind("const int");

        Node constdef = ConstDef();
        root.addNode(constdef);

        while (symIs(",")) {
            getsym();
            constdef = ConstDef();
            root.addNode(constdef);
        }
        match(";");
        return root;
    }

    private Node ConstDef() {    //常数定义 ConstDef → Ident { '[' ConstExp ']' } '=' ConstInitVal
        Node ident = Ident();
        ident.setKind("const int");

        if (symIs("[")) {
            getsym();
            Node dimen1 = ConstExp();
            ident.setKind("const array");
            ident.setLeft(dimen1);
            match("]");
        }

        if (symIs("[")) {
            getsym();
            Node dimen2 = ConstExp();
            ident.setRight(dimen2);
            match("]");
        }

        match("=");

        Node constInit = ConstInitVal();

        return new Node("ConstDef", ident, constInit);
    }

    private Node InitVal() {     //变量初值 InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '}'
        if (symIs("{")) {
            Node initlist = new Node("InitVal");
            getsym();
            if (symIs("}")) {
                getsym();
            } else {
                Node initv = InitVal();
                initlist.addNode(initv);
                while (symIs(",")) {
                    getsym();
                    initv = InitVal();
                    initlist.addNode(initv);
                }
                match("}");
            }
            return initlist;

        } else {
            return Exp();
        }
    }

    //常量初值 ConstInitVal → ConstExp | '{' [ ConstInitVal { ',' ConstInitVal } ] '}'
    private Node ConstInitVal() {
        if (symIs("{")) {
            Node root = new Node("ConstInitVal");
            getsym();
            if (symIs("}")) {
                getsym();
            } else {
                Node cinit1 = ConstInitVal();
                root.addNode(cinit1);
                while (symIs(",")) {
                    getsym();
                    Node cinit2 = ConstInitVal();
                    root.addNode(cinit2);
                }
                match("}");
            }
            return root;

        } else {
            return ConstExp();
        }
    }

    private Node FuncRParams() {     //函数实参表 FuncRParams → Exp { ',' Exp }
        Node root = new Node("FuncRParams");
        Node node = Exp();
        root.addNode(node);

        while (symIs(",")) {
            getsym();
            node = Exp();
            root.addNode(node);
        }
        return root;
    }

    /*Stmt →
        | 'if' '( Cond ')' Stmt [ 'else' Stmt ]
        | 'while' '(' Cond ')' Stmt
        | 'break' ';' | 'continue' ';'
        | 'return' [Exp] ';'
        | 'printf''('FormatString{,Exp}')'';'
        | Block
        | LVal = 'getint''('')'';'
        | LVal '=' Exp ';'
        | [Exp] ';'
         */
    //LVal → Ident {'[' Exp ']'}
    //Block → '{' { BlockItem } '}'
    /*Exp → AddExp → MulExp {('+' | '−') MulExp} → UnaryExp {('*' | '/' | '%') UnaryExp}
     → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
     → '(' Exp ')' | LVal | Number | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp */
    private Node Stmt() {
        if (symIs("if")) {
            getsym();
            match("(");
            Node ifcond = Cond();
            match(")");

            Node ifstmt = Stmt();

            Node elstmt = null;

            if (symCodeIs("ELSETK")) {
                getsym();
                elstmt = Stmt();
            }
            return new Node("IfStatement", ifcond, ifstmt, elstmt);

        } else if (symIs("while")) {
            getsym();
            match("(");
            Node wcond = Cond();
            match(")");
            Node wstmt = Stmt();

            return new Node("WhileLoop", wcond, wstmt);

        } else if (symIs("break") || symIs("continue")) {
            String str = sym.getTokenValue();
            getsym();
            match(";");
            return new Node(str);

        } else if (symIs("return")) {
            getsym();

            Node retvalue = null;
            if (symIs(";")) {
                getsym();
            } else {
                retvalue = Exp();
                match(";");
            }
            return new Node("Return", retvalue);

        } else if (symIs("printf")) {
            getsym();
            match("(");
            Node fmstr = FormatString();

            Node printexp = new Node("ExpList");
            while (symIs(",")) {
                getsym();
                Node exp = Exp();
                printexp.addNode(exp);
            }
            match(")");
            match(";");

            return new Node("Printf", fmstr, printexp);

        } else if (symIs("{")) {    //Block
            Node node = Block();
            return new Node("Block", node);
        }

        /*剩余的几个
        //todo 用这个：  | LVal = 'getint''('')'';'
                        | LVal '=' Exp ';'
                        | [Exp] ';'
        */
        //LVal → Ident {'[' Exp ']'}
        //Exp → '(' Exp ')' | LVal | Number | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        else if (symCodeIs("IDENFR") && assignPeek()) {
            Node lval = LVal();
            match("=");
            if (symIs("getint")) {
                getsym();
                match("(");
                match(")");
                match(";");
                Node getint = new Node("getint");
                return new Node("Assign_getint", lval, getint);

            } else {
                Node expnode = Exp();
                match(";");
                return new Node("Assign_value", lval, expnode);
            }

        } else {
            //todo 此分支优化时应可完全删除 ！！！！此论大谬！可能函数调用且有print
            Node node = null;
            if (symIs(";")) {
                getsym();
            } else {
                node = Exp();
                match(";");
            }
            return new Node("Exp", node);
            //return node;
        }
    }

    //一些中间式
    private Node LVal() {        //左值表达式 LVal → Ident {'[' Exp ']'}
        Node ident = Ident();
        ident.setKind("int");

        if (symIs("[")) {
            getsym();
            Node dimen1 = Exp();
            ident.setKind("array");
            ident.setLeft(dimen1);
            ident.setNum(1);
            match("]");
        }
        if (symIs("[")) {
            getsym();
            Node dimen2 = Exp();
            ident.setRight(dimen2);
            ident.setNum(2);
            match("]");
        }

        return ident;
    }

    private Node Number() {
        int num = Integer.valueOf(sym.getTokenValue());
        matchCode("INTCON");
        return new Node("Number", num);
    }

    private Node Cond() {    //条件表达式 Cond → LOrExp
        return LOrExp();
    }

    private Node FormatString() {
        if (!symCodeIs("STRCON")) error();
        String formatstring = sym.getTokenValue();
        getsym();
        return new Node("FormatString", formatstring);
    }

    private Node UnaryOp() {
        if (symIs("+") || symIs("-") || symIs("!")) {
            String op = sym.getTokenValue();
            getsym();
            return new Node(op);
        } else {
            error();
        }
        return null;
    }

    //各种Exps
    private Node Exp() {    //表达式 Exp → AddExp
        return AddExp();
    }

    //加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
    //加减表达式 AddExp → MulExp {('+' | '−') MulExp}
    private Node AddExp() {
        Node root = MulExp();
        while (symIs("+") || symIs("-")) {
            String op = sym.getTokenValue();
            getsym();
            Node branch = MulExp();
            Node nroot = new Node(op, root, branch);
            root = nroot;
        }
        return root;
    }

    //乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    //乘除模表达式 MulExp → UnaryExp {('*' | '/' | '%') UnaryExp}
    private Node MulExp() {
        Node root = UnaryExp();
        while (symIs("*") || symIs("/") || symIs("%")) {
            String op = sym.getTokenValue();
            getsym();
            Node branch = UnaryExp();
            Node nroot = new Node(op, root, branch);
            root = nroot;
        }
        return root;
    }

    /*一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')'  |   UnaryOp UnaryExp
        → '(' Exp ')' | LVal | Number | Ident '(' [FuncRParams] ')'  |   UnaryOp UnaryExp
        LVal → Ident {'[' Exp ']'}
        单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
    */
    private Node UnaryExp() {
        if (symCodeIs("IDENFR") && symPeek("LPARENT", 1)) {
            Node ident = Ident();
            ident.setKind("func");

            getsym();

            Node paras = null;
            if (symIs(")")) {
                getsym();
            } else {
                int tmp = curDimen;
                paras = FuncRParams();
                curDimen = tmp;
                match(")");
            }

            ident.setLeft(paras);
            return ident;

        } else if (symIs("+") || symIs("-") || symIs("!")) {
            String op = sym.getTokenValue();
            UnaryOp();
            Node exp = UnaryExp();
            return new Node(op, exp);
        } else {
            return PrimaryExp();
        }
    }

    //基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number
    private Node PrimaryExp() {
        Node root = null;
        if (symIs("(")) {
            getsym();
            root = Exp();
            match(")");

        } else if (symCodeIs("IDENFR")) {
            root = LVal();
        } else if (symCodeIs("INTCON")) {
            int num = Integer.valueOf(sym.getTokenValue());
            Number();
            root = new Node("Number", num);

        } else {
            error();
        }
        return root;
    }

    private Node ConstExp() {       //todo 常量表达式 ConstExp → AddExp  注：使用的Ident 必须是常量
        return AddExp();
    }

    //逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
    //逻辑或表达式 LOrExp → LAndExp {'||' LAndExp}
    private Node LOrExp() {
        Node root = LAndExp();

        while (symCodeIs("OR")) {
            getsym();
            Node branch = LAndExp();
            Node nroot = new Node("||", root, branch);
            root = nroot;
        }
        return root;
    }

    //逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
    //逻辑与表达式 LAndExp → EqExp {'&&' EqExp}
    private Node LAndExp() {
        Node root = EqExp();
        while (symCodeIs("AND")) {
            getsym();
            Node branch = EqExp();
            Node nroot = new Node("&&", root, branch);
            root = nroot;
        }
        return root;
    }

    //相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
    //相等性表达式 EqExp → RelExp {('==' | '!=') RelExp}
    private Node EqExp() {
        Node root = RelExp();
        while (symCodeIs("EQL") || symCodeIs("NEQ")) {
            String op = sym.getTokenValue();
            getsym();
            Node branch = RelExp();
            Node nroot = new Node(op, root, branch);
            root = nroot;
        }
        return root;
    }

    //关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
    //关系表达式 RelExp → AddExp {('<' | '>' | '<=' | '>=') AddExp}
    private Node RelExp() {
        Node root = AddExp();
        while (symIs("<") || symIs(">") || symIs("<=") || symIs(">=")) {
            String op = sym.getTokenValue();
            getsym();
            Node branch = AddExp();
            Node nroot = new Node(op, root, branch);
            root = nroot;
        }
        return root;
    }

    //基础操作；辅助函数
    private void getsym() {
        grammarList.add(sym.tostring());
        if (index < tokenList.size() - 1) {
            index += 1;
            sym = tokenList.get(index);
        } else {
            //todo System.out.println("Token List to End.");
        }
    }

    private void error() {
        System.err.println("Error! 目前输出为：");
        for (String s : grammarList) {
            System.out.println(s);
        }
        System.out.println("Error at :" + sym.tostring());
        System.exit(0);
    }

    private void match(String s) {   //匹配字符string本身
        if (!symIs(s)) {
            error();

        } else {
            getsym();
        }
    }

    private void matchCode(String s) {   //匹配字符string本身
        if (!symCodeIs(s)) {
            error();
        } else {
            getsym();
        }
    }

    private boolean symIs(String s) {
        return sym.getTokenValue().equals(s);
    }

    private boolean symCodeIs(String s) {
        return sym.getTokenCode().equals(s);
    }

    private boolean symPeek(String s, int offset) {
        if (index + offset >= tokenList.size()) {
            return false;
        }
        Token newsym = tokenList.get(index + offset);
        return newsym.getTokenCode().equals(s);
    }

    private boolean assignPeek() {   //查找分号前有无等号
        int offset = 1;
        int curRow = tokenList.get(index).getRow();
        while (index + offset < tokenList.size()) {
            Token newsym = tokenList.get(index + offset);
            if (newsym.getTokenValue().equals(";") ||
                    newsym.getRow() > curRow) {
                break;
            } else if (newsym.getTokenValue().equals("=")) {
                return true;
            }
            offset += 1;
        }
        return false;
    }

    private Token getLastToken() {    //获取上一个 符
        if (index == 0) {
            return tokenList.get(0);
        }
        return tokenList.get(index - 1);
    }
}
