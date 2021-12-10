import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class GrammarScanner {
    private Token sym;
    private int index;
    private final ArrayList<Token> tokenList;
    private ArrayList<String> grammarList;
    private final ErrorDisposal erdp;
    private SymbolTable table;

    //private Symbol curFunc = null;  //当前调用的函数
    private int funcParaIndex;      //对照参数是否匹配时的index
    //private boolean funcErrorE = false;     //是否具有E错误
    private int curDimen = 0;           //当前变量数组维度
    private boolean isGlobal = true;    //是否为顶层

    private final String OUTPUT_DIR = "output.txt";

    public GrammarScanner(ArrayList<Token> tokenList) {
        this.index = 0;
        this.sym = tokenList.get(0);
        this.tokenList = tokenList;
        this.grammarList = new ArrayList<>();
        this.erdp = new ErrorDisposal();
        this.table = new SymbolTable();
    }

    public void start(int output, int erroutput) {
        CompUnit();
        if (output == 1) {
            try {
                writefile(OUTPUT_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (erroutput == 1) {
            try {
                erdp.writefile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void writefile(String dir) throws IOException {
        File file = new File(dir);
        FileWriter writer = new FileWriter(file);
        System.out.println("开始输出：");
        for (String t : grammarList) {
            System.out.println(t);
            writer.write(t + "\n");
        }
        writer.flush();
        writer.close();
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
    private void CompUnit() {
        while ((symCodeIs("CONSTTK") && symPeek("INTTK", 1) && symPeek("IDENFR", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("LBRACK", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("ASSIGN", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("COMMA", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("SEMICN", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && !symPeek("LPARENT", 2))
        ) {
            Decl();
        }
        while ((symCodeIs("VOIDTK") && symPeek("IDENFR", 1) && symPeek("LPARENT", 2)) ||
                (symCodeIs("INTTK") && symPeek("IDENFR", 1) && symPeek("LPARENT", 2))
        ) {
            FuncDef();
        }
        isGlobal = false;
        MainFuncDef();
        grammarList.add("<CompUnit>");
    }

    private void FuncDef() {    //函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
        String functype = sym.getTokenValue();
        erdp.funcHasReturn = functype.equals("int");
        erdp.hasReturn = false;
        erdp.lastIsReturn = false;
        FuncType();

        Token thisident = sym;
        Ident();

        Symbol funcSymbol = new Symbol(thisident.getTokenValue(), Parser.TYPE.F);
        Parser.TYPE retType = functype.equals("int") ? Parser.TYPE.I : Parser.TYPE.V;
        funcSymbol.addFuncReturnType(retType);

        if (table.lookupNameOnly(thisident.getTokenValue()) != null) {
            erdp.ErrorAt(thisident, ErrorDisposal.ER.ERR_B);
        } else {
            table.insertTable(funcSymbol);
        }

        table.openScope();

        match("(");

        if (symIs(")")) {
            getsym();
        } else if (symIs("{")) {
            erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_J);
        } else {
            FuncFParams();
            match(")");
        }

        funcSymbol.addParas(table.getSymbolTable());

        Block();
        table.closeScope();

        erdp.handleErrorG(getLastToken());

        grammarList.add("<FuncDef>");
    }

    private void MainFuncDef() {    //主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block
        matchCode("INTTK");
        matchCode("MAINTK");
        match("(");
        match(")");

        erdp.funcHasReturn = true;
        erdp.hasReturn = false;
        erdp.lastIsReturn = false;

        table.openScope();
        Block();
        table.closeScope();

        //handleErrorG(getLastToken());
        erdp.handleErrorG(sym);

        grammarList.add("<MainFuncDef>");
    }

    private void FuncType() {   //函数类型 FuncType → 'void' | 'int'
        if (symCodeIs("VOIDTK") || symCodeIs("INTTK")) {
            getsym();
            grammarList.add("<FuncType>");
        } else {
            error();
        }
    }

    private void FuncFParams() {    //函数形参表 FuncFParams → FuncFParam { ',' FuncFParam }
        FuncFParam();
        while (symIs(",")) {
            getsym();
            FuncFParam();
        }
        grammarList.add("<FuncFParams>");
    }

    private void FuncFParam() {     //函数形参 FuncFParam → BType Ident ['[' ']' { '[' ConstExp ']' }]
        BType();

        Token thisIdent = sym;
        Ident();

        int dimen = 0;

        if (symIs("[")) {
            getsym();
            match("]");
            dimen += 1;

            while (symIs("[")) {
                getsym();
                ConstExp();
                match("]");
                dimen += 1;
            }
        }

        if (checkErrorB(thisIdent, Parser.TYPE.I)) {
            erdp.ErrorAt(thisIdent, ErrorDisposal.ER.ERR_B);
        } else {
            Symbol symbol = new Symbol(thisIdent.getTokenValue(), Parser.TYPE.I);
            symbol.setArray();
            symbol.setArrayDimen(dimen);
            table.insertTable(symbol);
        }

        grammarList.add("<FuncFParam>");
    }

    private void BType() {
        matchCode("INTTK");
    }

    private void Ident() {
        matchCode("IDENFR");
    }

    private void Block() {      //语句块 Block → '{' { BlockItem } '}'
        match("{");

        while (!symIs("}")) {
            erdp.lastIsReturn = false;
            BlockItem();
        }

        match("}");

        grammarList.add("<Block>");
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
    private void BlockItem() {
        if (symIs("const") || symIs("int")) {
            Decl();
        } else {
            Stmt();
        }
        //grammarList.add("<BlockItem>");
    }

    //声明 Decl → ConstDecl | VarDecl
    //常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
    //变量声明 VarDecl → BType VarDef { ',' VarDef } ';'
    private void Decl() {
        if (symIs("const")) {
            ConstDecl();
        } else {
            VarDecl();
        }
        //grammarList.add("<Decl>");
    }

    private void VarDecl() {     //变量声明 VarDecl → BType VarDef { ',' VarDef } ';'
        BType();
        VarDef();
        while (symIs(",")) {
            getsym();
            VarDef();
        }
        match(";");

        grammarList.add("<VarDecl>");
    }

    //变量定义 VarDef → Ident { '[' ConstExp ']' }  |  Ident { '[' ConstExp ']' } '=' InitVal
    //变量定义 VarDef → Ident { '[' ConstExp ']' }  ['=' InitVal]
    private void VarDef() {
        Token thisIdent = sym;
        Ident();

        boolean isArray = false;
        int dimen = 0;

        while (symIs("[")) {
            getsym();
            ConstExp();
            match("]");
            isArray = true;
            dimen += 1;
        }

        if (symIs("=")) {
            getsym();
            InitVal();
        }

        Parser.TYPE vartype = Parser.TYPE.I;
        if (isGlobal) {
            if (table.lookupNameOnly(thisIdent.getTokenValue()) != null) {
                erdp.ErrorAt(thisIdent, ErrorDisposal.ER.ERR_B);
            } else {
                Symbol symbol = new Symbol(thisIdent.getTokenValue(), vartype);
                if (isArray) {
                    symbol.setArray();
                    symbol.setArrayDimen(dimen);
                }
                table.insertTable(symbol);
            }

        } else if (checkErrorB(thisIdent, vartype)) {
            erdp.ErrorAt(thisIdent, ErrorDisposal.ER.ERR_B);
        } else {
            Symbol symbol = new Symbol(thisIdent.getTokenValue(), vartype);
            if (isArray) {
                symbol.setArray();
                symbol.setArrayDimen(dimen);
            }
            table.insertTable(symbol);
        }

        grammarList.add("<VarDef>");
    }

    private void ConstDecl() {      //常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
        if (!symIs("const")) {
            error();
        }
        getsym();

        BType();
        ConstDef();
        while (symIs(",")) {
            getsym();
            ConstDef();
        }
        match(";");
        grammarList.add("<ConstDecl>");
    }

    private void ConstDef() {    //常数定义 ConstDef → Ident { '[' ConstExp ']' } '=' ConstInitVal
        Token thisIdent = sym;
        Ident();

        boolean isArray = false;
        int dimen = 0;

        while (symIs("[")) {
            getsym();
            ConstExp();
            match("]");
            isArray = true;
            dimen += 1;
        }
        match("=");

        ConstInitVal();

        Parser.TYPE vartype = Parser.TYPE.I;
        if (isGlobal) {
            if (table.lookupNameOnly(thisIdent.getTokenValue()) != null) {
                erdp.ErrorAt(thisIdent, ErrorDisposal.ER.ERR_B);
            } else {
                Symbol symbol = new Symbol(thisIdent.getTokenValue(), vartype);
                symbol.setConst();
                if (isArray) {
                    symbol.setArray();
                    symbol.setArrayDimen(dimen);
                }
                table.insertTable(symbol);
            }

        } else if (checkErrorB(thisIdent, vartype)) {
            erdp.ErrorAt(thisIdent, ErrorDisposal.ER.ERR_B);
        } else {
            Symbol symbol = new Symbol(thisIdent.getTokenValue(), vartype);
            symbol.setConst();
            if (isArray) {
                symbol.setArray();
                symbol.setArrayDimen(dimen);
            }
            table.insertTable(symbol);
        }

        grammarList.add("<ConstDef>");
    }

    private void InitVal() {     //变量初值 InitVal → Exp | '{' [ InitVal { ',' InitVal } ] '}'
        if (symIs("{")) {
            getsym();
            if (symIs("}")) {
                getsym();
            } else {
                InitVal();
                while (symIs(",")) {
                    getsym();
                    InitVal();
                }
                match("}");
            }
        } else {
            Exp();
        }
        grammarList.add("<InitVal>");
    }

    //常量初值 ConstInitVal → ConstExp | '{' [ ConstInitVal { ',' ConstInitVal } ] '}'
    private void ConstInitVal() {
        if (symIs("{")) {
            getsym();
            if (symIs("}")) {
                getsym();
            } else {
                ConstInitVal();
                while (symIs(",")) {
                    getsym();
                    ConstInitVal();
                }
                match("}");
            }

        } else {
            ConstExp();
        }
        grammarList.add("<ConstInitVal>");
    }

    private int FuncRParams(Symbol myfunc) {     //函数实参表 FuncRParams → Exp { ',' Exp }   //返回值为参数个数paranum
        curDimen = 0; //Exp可以 curDime
        Exp();

        if (myfunc.getParaNum() != 0 && myfunc.getParaDimen(funcParaIndex) != curDimen) {
            myfunc.errorE = true;
            System.out.println("ROW " + sym.getRow() + "! 期望参数类型：" + myfunc.getParaDimen(funcParaIndex) + "; 实际参数类型：" + curDimen);
        }
        int paranum = 1;

        while (symIs(",")) {
            funcParaIndex += 1;
            getsym();
            Exp();
            if (myfunc.getParaNum() != 0 && myfunc.getParaDimen(funcParaIndex) != curDimen) {
                myfunc.errorE = true;
                System.out.println("ROW " + sym.getRow() + "! 期望参数类型：" + myfunc.getParaDimen(funcParaIndex) + "; 实际参数类型：" + curDimen);
            }
            paranum += 1;
        }
        grammarList.add("<FuncRParams>");
        return paranum;
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
    private void Stmt() {
        int curRow = sym.getRow();
        if (symIs("if")) {
            getsym();
            match("(");
            Cond();
            match(")");

            table.openScope();
            Stmt();
            table.closeScope();

            if (symCodeIs("ELSETK")) {
                table.openScope();
                getsym();
                Stmt();
                table.closeScope();
            }

        } else if (symIs("while")) {
            getsym();
            match("(");
            Cond();
            match(")");
            erdp.enterCycleBlock();
            table.openScope();
            Stmt();
            table.closeScope();
            erdp.quitCycleBlock();

        } else if (symIs("break") || symIs("continue")) {

            if (erdp.checkErrorM()) {
                erdp.ErrorAt(sym, ErrorDisposal.ER.ERR_M);
            }
            getsym();
            match(";");

        } else if (symIs("return")) {
            erdp.hasReturn = false;
            erdp.lastIsReturn = false;
            Token possibleRT = sym;
            getsym();
            if (symIs(";")) {
                getsym();
            } else if (symIs("}")) {
                erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_I);
            } else {
                Exp();
                erdp.hasReturn = true;
                erdp.lastIsReturn = true;
                match(";");
            }

            erdp.handleErrorF(possibleRT);

        } else if (symIs("printf")) {
            Token possiblePF = sym;
            getsym();
            match("(");
            String formatString = sym.getTokenValue();
            FormatString();
            int expCount = 0;
            while (symIs(",")) {
                getsym();
                Exp();
                expCount += 1;
            }
            match(")");
            match(";");
            if (erdp.checkErrorL(formatString, expCount)) {
                erdp.ErrorAt(possiblePF, ErrorDisposal.ER.ERR_L);
            }

        } else if (symIs("{")) {    //Block
            table.openScope();
            Block();
            erdp.lastIsReturn = false;
            table.closeScope();
        }

        /*剩余的几个
        //todo 用这个：  | LVal = 'getint''('')'';'
                        | LVal '=' Exp ';'
                        | [Exp] ';'
        */
        //LVal → Ident {'[' Exp ']'}
        //Exp → '(' Exp ')' | LVal | Number | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
        else if (symCodeIs("IDENFR") && assignPeek()) {
            Token possibleLV = sym;
            LVal();
            match("=");
            if (symIs("getint")) {
                getsym();
                match("(");
                match(")");
                match(";");
            } else {
                Exp();
                match(";");
            }

            //todo 此处参数存疑
            Symbol syb = table.lookupFullTable(possibleLV.getTokenValue(), Parser.TYPE.I);
            if (syb != null && syb.getIsConst()) {
                erdp.ErrorAt(possibleLV, ErrorDisposal.ER.ERR_H);
            }

        } else {
            if (symIs(";")) {
                getsym();
            } else {
                Exp();
                match(";");
            }
        }
        grammarList.add("<Stmt>");
    }

    //一些中间式
    private void LVal() {        //左值表达式 LVal → Ident {'[' Exp ']'}
        if (checkErrorC(sym, Parser.TYPE.I)) {
            erdp.ErrorAt(sym, ErrorDisposal.ER.ERR_C);
            curDimen = -50;

        } else {
            Symbol symbol = table.lookupFullTable(sym.getTokenValue(), Parser.TYPE.I);
            if (symbol.getIsArray()) {
                //System.out.println("Row:" + sym.getRow() + ";    SB name:" + symbol.getName() + "    Dimen=" + symbol.getArrayDimen());
                curDimen = symbol.getArrayDimen();
            } else {
                curDimen = 0;
            }
        }
        Ident();

        while (symIs("[")) {
            getsym();
            Exp();
            match("]");
            curDimen -= 1;
        }
        grammarList.add("<LVal>");
    }

    private void Number() {
        //curDimen = 0;
        matchCode("INTCON");
        grammarList.add("<Number>");
    }

    private void Cond() {    //条件表达式 Cond → LOrExp
        LOrExp();
        grammarList.add("<Cond>");
    }

    private void FormatString() {
        if (!symCodeIs("STRCON")) error();
        erdp.handleErrorA(sym);
        getsym();
        //grammarList.add("<FormatString>");
    }

    private void UnaryOp() {
        if (symIs("+") || symIs("-") || symIs("!")) {
            getsym();
        } else {
            error();
        }
        grammarList.add("<UnaryOp>");
    }

    //各种Exps
    private void Exp() {    //表达式 Exp → AddExp
        AddExp();
        grammarList.add("<Exp>");
    }

    //加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
    //加减表达式 AddExp → MulExp {('+' | '−') MulExp}
    private void AddExp() {
        MulExp();
        while (symIs("+") || symIs("-")) {
            grammarList.add("<AddExp>");
            getsym();
            MulExp();
        }
        grammarList.add("<AddExp>");
    }

    //乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
    //乘除模表达式 MulExp → UnaryExp {('*' | '/' | '%') UnaryExp}
    private void MulExp() {
        UnaryExp();
        while (symIs("*") || symIs("/") || symIs("%")) {
            grammarList.add("<MulExp>");
            getsym();
            UnaryExp();
        }
        grammarList.add("<MulExp>");
    }

    /*一元表达式 UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')'  |   UnaryOp UnaryExp
        → '(' Exp ')' | LVal | Number | Ident '(' [FuncRParams] ')'  |   UnaryOp UnaryExp
        LVal → Ident {'[' Exp ']'}
        单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中
    */
    private void UnaryExp() {
        if (symCodeIs("IDENFR") && symPeek("LPARENT", 1)) {
            Symbol thisFunc = null;
            boolean dupfunc = false;

            if (checkErrorC(sym, Parser.TYPE.F)) {
                erdp.ErrorAt(sym, ErrorDisposal.ER.ERR_C);
                dupfunc = true;
            } else {
                thisFunc = table.lookupFullTable(sym.getTokenValue(), Parser.TYPE.F);
                Parser.TYPE thisfuncRtype = thisFunc.getFuncReturnType();
                curDimen = (thisfuncRtype == Parser.TYPE.I) ? 0 : -20;
            }
            Token possibleID = sym;

            getsym();
            getsym();

            funcParaIndex = 0;
            int paranum = 0;
            if (thisFunc != null) {
                thisFunc.errorE = false;    //todo 可能全局的errorE被内层覆盖了【并没有】
            }

            if (symIs(")")) {
                getsym();
            } else if (symIs("]")) {
                erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_J);
            } else {
                if (!dupfunc) {
                    int tmp = curDimen;
                    paranum = FuncRParams(thisFunc);
                    curDimen = tmp;
                }
                match(")");
            }

            if (!dupfunc) {
                if (thisFunc.getParaNum() != paranum) {
                    System.out.println("期望参数个数：" + thisFunc.getParaNum() + "; 实际参数个数：" + paranum);
                    erdp.ErrorAt(possibleID, ErrorDisposal.ER.ERR_D);
                } else if (thisFunc.errorE) {    //todo 可能funcE全局导致嵌套函数报错N次【并没有】
                    //Erprt e = new Erprt(possibleID.getRow(), "e");
                    //if (!erdp.errList.contains(e)) {
                    erdp.ErrorAt(possibleID, ErrorDisposal.ER.ERR_E);
                    //}

                }
            }

        } else if (symIs("+") || symIs("-") || symIs("!")) {
            UnaryOp();
            UnaryExp();
        } else {
            PrimaryExp();
        }
        grammarList.add("<UnaryExp>");
    }

    //基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number
    private void PrimaryExp() {
        if (symIs("(")) {
            getsym();
            Exp();
            match(")");

        } else if (symCodeIs("IDENFR")) {
            LVal();
        } else if (symCodeIs("INTCON")) {
            Number();
        } else {
            error();
        }
        grammarList.add("<PrimaryExp>");
    }

    private void ConstExp() {       //todo 常量表达式 ConstExp → AddExp  注：使用的Ident 必须是常量
        AddExp();
        grammarList.add("<ConstExp>");
    }

    //逻辑或表达式 LOrExp → LAndExp | LOrExp '||' LAndExp
    //逻辑或表达式 LOrExp → LAndExp {'||' LAndExp}
    private void LOrExp() {
        LAndExp();
        while (symCodeIs("OR")) {
            grammarList.add("<LOrExp>");
            getsym();
            LAndExp();
        }
        grammarList.add("<LOrExp>");
    }

    //逻辑与表达式 LAndExp → EqExp | LAndExp '&&' EqExp
    //逻辑与表达式 LAndExp → EqExp {'&&' EqExp}
    private void LAndExp() {
        EqExp();
        while (symCodeIs("AND")) {
            grammarList.add("<LAndExp>");
            getsym();
            EqExp();
        }
        grammarList.add("<LAndExp>");
    }

    //相等性表达式 EqExp → RelExp | EqExp ('==' | '!=') RelExp
    //相等性表达式 EqExp → RelExp {('==' | '!=') RelExp}
    private void EqExp() {
        RelExp();
        while (symCodeIs("EQL") || symCodeIs("NEQ")) {
            grammarList.add("<EqExp>");
            getsym();
            RelExp();
        }
        grammarList.add("<EqExp>");
    }

    //关系表达式 RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
    //关系表达式 RelExp → AddExp {('<' | '>' | '<=' | '>=') AddExp}
    private void RelExp() {
        AddExp();
        while (symIs("<") || symIs(">") || symIs("<=") || symIs(">=")) {
            grammarList.add("<RelExp>");
            getsym();
            AddExp();
        }
        grammarList.add("<RelExp>");
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
        System.err.println("Error!");
        System.out.println("目前输出：");
        for (String s : grammarList) {
            System.out.println(s);
        }
        System.out.println("Error at :" + sym.tostring());
        System.exit(0);
    }

    private void match(String s) {   //匹配字符string本身
        if (!symIs(s)) {
            switch (s) {
                case ";":
                    erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_I);
                    break;
                case ")":
                    erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_J);
                    break;
                case "]":
                    erdp.ErrorAt(getLastToken(), ErrorDisposal.ER.ERR_K);
                    break;
                default:
                    error();
                    break;
            }
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

    //check error
    public boolean checkErrorB(Token tk, Parser.TYPE type) {
        Symbol syb = table.lookupLocalTable(tk.getTokenValue(), type);
        return syb != null;
    }

    public boolean checkErrorC(Token tk, Parser.TYPE type) {
        //todo 下面此处函数参数2存疑
        Symbol syb = table.lookupFullTable(tk.getTokenValue(), type);
        return syb == null;
    }
}
