/*
import java.util.ArrayList;

public class SymbolTableBuilder {
    private Symbol curFunc;  //当前调用的函数

    private boolean global = true;    //是否为顶层

    private void parseDef(Node n) {
        Node ident = n.getLeft();
        String name = ident.getName();   //指id
        String kind = ident.getKind();

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

    private void parseFuncDecl(Node n) {
        Node ident = n.getLeft();
        String funcname = ident.getName();
        String functype = ident.getKind();

        Symbol funcSymbol = new Symbol(funcname, Parser.TYPE.F);
        curFunc = funcSymbol;
        Parser.TYPE retType = functype.equals("int") ? Parser.TYPE.I : Parser.TYPE.V;
        funcSymbol.addFuncReturnType(retType);
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

        //parseBlock(n.getRight());    //0=不openScope，仅funcDecl使用；1=正常创建【舍弃，回到0方案】

        SymbolTable.closeScope();

        //todo handleErrorG(getLastToken());
    }

    private void parseFuncFParam(Node n) {
        Node ident = n;

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
                int dimennum2 = ident.getRight().calcuValue();      //改为了记在Right上
                symbol.setDimen2(dimennum2);
            }
        }

        symbol.setGlobal(global);

        curFunc.insertToParalist(symbol);

        SymbolTable.insertTable(symbol);
    }
}
*/
