import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class Optimizer {
    private ArrayList<IRCode> irList;
    private ArrayList<IRCode> optList;
    private int index;
    private int size;
    private ArrayList<Symbol> deleted;

    private int beginloopindex;
    private int intoloopindex;

    private ArrayList<Integer> beginloops;
    private ArrayList<Integer> intoloops;

    private ArrayList<Integer> nouseloops;  //冗余loop的index标号

    public Optimizer(ArrayList<IRCode> irl) {
        this.irList = irl;
        this.optList = new ArrayList<>();
        this.deleted = new ArrayList<>();

        this.nouseloops = new ArrayList<>();
    }

    public ArrayList<IRCode> optimize() {
        //死代码删除

        //while时的循环不变式

        size = irList.size();

        for (index = 0; index < size; index++) {
            IRCode curir = irList.get(index);
            filterAnyIRCode(curir);    //todo 不包括常量、函数定义相关
        }

        if (!nouseloops.isEmpty()) {
            for (int loopno : nouseloops) {
                boolean slaughter = false;
                for (int i = 0; i < size; i++) {
                    IRCode curir = irList.get(i);
                    if (slaughter) {
                        curir.deleted = true;

                        if (curir.getType().equals("label") && curir.getIRstring().equals("end_loop" + loopno + ":")) {
                            break;
                        }

                    } else if (curir.getType().equals("label") && curir.getIRstring().equals("begin_loop" + loopno + ":")) {
                        curir.deleted = true;
                        slaughter = true;
                    }

                }
            }
        }

        for (IRCode ir : irList) {
            if (!ir.deleted) {
                optList.add(ir);
            }
        }

        try {
            writefile("ircode(opt).txt");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return optList;
    }

    public void writefile(String dir) throws IOException {
        File file = new File(dir);
        FileWriter writer = new FileWriter(file);
        for (IRCode irc : optList) {
            String t = irc.getRawstr();
            System.out.println(t);
            writer.write(t + "\n");
        }
        writer.flush();
        writer.close();
    }

    private void filterAnyIRCode(IRCode code) {
        String type = code.getType();
        boolean used = true;

        switch (type) {
            case "assign":
            case "assign2":
                checkremoveloop1(code);

                Variable dest = code.getDest();

                //不敢处理array
                if (dest.isKindofsymbol() && dest.getSymbol().getIsArray()) {
                    return;
                }

                if (dest.isKindofsymbol() && !deleted.contains(dest.getSymbol())) {
                    Symbol dsymbol = dest.getSymbol();
                    used = scanUseToScopeEndFromIndex(dsymbol, code.getScope(), index);

                    int whilebegin = nowInWhileScope(code);

                    if (!used && whilebegin != -1) {
                        int sectionend;
                        if (code.startindex == -1) {
                            sectionend = index;
                        } else {
                            sectionend = code.startindex;
                        }
                        used = scanUseInSection(dsymbol, whilebegin, sectionend);   //从whilebegin开始扫

                    }
                    if (!used) {
                        ArrayList<Variable> deletelist = new ArrayList();
                        deletelist.add(dest);
                        deleteIRInscope(deletelist, code.getScope());

                        System.out.println("Delete Unused Symbol: " + dest.getSymbol().getName());
                        deleted.add(dest.getSymbol());
                    }
                }
                break;

            case "label":
                if (code.getIRstring().startsWith("begin_loop")) {
                    beginloopindex = index;
                }
                if (code.getIRstring().startsWith("into_loop")) {
                    intoloopindex = index;
                }
                break;
            case "intDecl":
                Symbol intsymbol = code.getSymbol();
                boolean intused = scanUseToScopeEndFromIndex(intsymbol, code.getScope(), index);
                if (!intused) {
                    code.deleted = true;
                    System.out.println("Delete / No use intDecl:" + intsymbol.getName());
                    scanAndDeleteDefToScopeEnd(intsymbol, code.getScope());
                }
            case "branch":
                //addCompareBranch(code);
                break;
            case "setcmp":
                //addSetCmp(code);
                break;
            case "note":

            case "jump":
            case "assign_ret":      //形如i = RET，调用函数返回赋值
            case "return":          //函数内return返回值
            case "arrayDecl":

            case "call":
            case "getint":
            case "push":
            case "print":
                //addOrigin(code);
                break;
            default:
                break;
        }
    }

    private boolean scanUseToScopeEndFromIndex(Symbol symbol, SymbolTable.Scope scope, int fromindex) {
        if (symbol.getIsArray()) {    //数组不管
            return true;
        }

        int nextindex = fromindex + 1;
        ArrayList<SymbolTable.Scope> scopeList = new ArrayList<>();
        scopeList.add(scope);

        while (nextindex < size) {
            IRCode nextcode = irList.get(nextindex);
            if (scopeList.contains(scope) || scopeList.contains(scope.father)) {
                if (scopeList.contains(scope.father)) {
                    scopeList.add(scope);
                }

                if (scanSymbolUse(nextcode, symbol)) {
                    return true;
                }

            } else {
                break;
            }
            nextindex++;
        }

        return false;
    }

    private boolean scanUseInSection(Symbol symbol, int beginindex, int endindex) {
        int nextindex = beginindex + 1;

        while (nextindex < endindex) {
            IRCode nextcode = irList.get(nextindex);
            if (scanSymbolUse(nextcode, symbol)) {
                return true;
            }
            nextindex++;
        }

        return false;
    }

    private boolean scanSymbolUse(IRCode code, Symbol symbol) {
        if (code.deleted) {
            return false;
        }

        String type = code.getType();
        switch (type) {
            case "assign":
                Variable oper1 = code.getOper1();
                Variable oper2 = code.getOper2();
                if (oper1.isKindofsymbol()) {
                    if (oper1.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (oper1.getSymbol() == symbol) {
                        return true;
                    }
                }
                if (oper2.isKindofsymbol()) {
                    if (oper2.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (oper2.getSymbol() == symbol) {
                        return true;
                    }
                }
                break;
            case "assign2":
                Variable oper = code.getOper1();
                if (oper.isKindofsymbol()) {
                    if (oper.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (oper.getSymbol() == symbol) {
                        return true;
                    }
                }
                break;
            case "print":
            case "return":          //函数内return返回值
            case "push":
                if (code.getVariable() != null && code.getVariable().isKindofsymbol()) {
                    if (code.getVariable().getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }

                    if (code.getVariable().getSymbol() == symbol) {
                        return true;
                    }
                }
                break;
            case "branch":
            case "setcmp":
                Variable operleft = code.getOper1();
                Variable operright = code.getOper2();
                if (operleft.isKindofsymbol()) {
                    if (operleft.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (operleft.getSymbol() == symbol) {
                        return true;
                    }
                }
                if (operright.isKindofsymbol()) {
                    if (operright.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (operright.getSymbol() == symbol) {
                        return true;
                    }
                }
                break;

            case "getint":
                //addGetint(code);
                break;

            case "assign_ret":      //形如i = RET，调用函数返回赋值
                //addAssignRet(code);
                break;

            case "arrayDecl":
                //addArrayDecl(code);
                break;
            case "intDecl":
                //addIntDecl(code);
                break;

            case "call":
            case "note":
            case "label":
            case "jump":
                break;
            default:
                break;
        }
        return false;
    }

    private boolean scanAssignToScopeEnd(Symbol symbol, SymbolTable.Scope scope) {
        int nextindex = index + 1;
        ArrayList<SymbolTable.Scope> scopeList = new ArrayList<>();
        scopeList.add(scope);

        while (nextindex < size) {
            IRCode nextcode = irList.get(nextindex);
            if (scopeList.contains(scope) || scopeList.contains(scope.father)) {
                if (scopeList.contains(scope.father)) {
                    scopeList.add(scope);
                }

                if (scanSymbolDef(nextcode, symbol)) {
                    return true;
                }

            } else {
                break;
            }
            nextindex++;
        }

        return false;
    }

    private boolean scanSymbolDef(IRCode code, Symbol symbol) {
        if (code.deleted) {
            return false;
        }

        String type = code.getType();
        switch (type) {
            case "assign":
            case "assign2":
                Variable dest = code.getDest();
                if (dest.isKindofsymbol()) {
                    if (dest.getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (dest.getSymbol() == symbol) {
                        return true;
                    }

                }

                break;

            case "assign_ret":      //形如i = RET，调用函数返回赋值
            case "getint":
                if (code.getVariable() != null && code.getVariable().isKindofsymbol()) {
                    if (code.getVariable().getSymbol().getIsArray()) {  //array按兵不动
                        return true;
                    }
                    if (code.getVariable().getSymbol() == symbol) {
                        return true;
                    }
                }
                break;

            case "print":

            case "return":          //函数内return返回值

            case "push":

            case "branch":

            case "setcmp":

            case "arrayDecl":
                //addArrayDecl(code);
                break;
            case "intDecl":
                //addIntDecl(code);
                break;

            case "call":
            case "note":
            case "label":
            case "jump":
                break;
            default:
                break;
        }
        return false;
    }

    /*private void addOrigin(IRCode code) {
        optList.add(code);
    }*/

    private void deleteIRInscope(ArrayList<Variable> deleteList, SymbolTable.Scope curscope) {
        int befindex = index;
        boolean processing = false;
        boolean clearcall = false;
        int clearpush = 0;

        int deleteuntil = 2100000000;

        while (befindex >= 0) {
            IRCode befcode = irList.get(befindex);

            if (befcode.getScope() != curscope) {
                break;
            }

            if (deleteList.isEmpty() /*&& !processing*/) {
                break;
            }

            //此处加入func相关可处理a=b()+c();
            if (befcode.getType().equals("assign") || befcode.getType().equals("assign2")) {
                Variable dest = befcode.getDest();
                if (deleteList.contains(dest)) {
                    befcode.deleted = true;
                    System.out.println("Delete Code: " + befcode.getRawstr());
                    deleteList.remove(dest);

                    if (befcode.startindex != -1) {
                        deleteuntil = befcode.startindex;
                    }

                    if (befcode.getType().equals("assign2")) {
                        Variable oper = befcode.getOper1();
                        if (oper.getType().equals("var")) {
                            deleteList.add(oper);
                            System.out.println("Add Delete List: " + oper.getName());
                        }

                    } else {
                        Variable oper1 = befcode.getOper1();
                        Variable oper2 = befcode.getOper2();
                        if (oper1.getType().equals("var")) {
                            deleteList.add(oper1);
                            System.out.println("Add Delete List: " + oper1.getName());
                        }
                        if (oper2.getType().equals("var")) {
                            deleteList.add(oper2);
                            System.out.println("Add Delete List: " + oper2.getName());
                        }
                    }
                }

            } /*else if (befcode.getType().equals("assign_ret")) {
                Variable tmpvar = befcode.getVariable();
                if (deleteList.contains(tmpvar)) {
                    befcode.deleted = true;
                    clearcall = true;
                    System.out.println("Delete Code: " + befcode.getRawstr());
                    deleteList.remove(tmpvar);
                }

            } else if (befcode.getType().equals("call")) {
                if (clearcall) {
                    befcode.deleted = true;
                    clearcall = false;
                    System.out.println("Delete Code: " + befcode.getRawstr());

                    String funcname = befcode.getIRstring();
                    Symbol symbol = SymbolTable.lookupFullTable(funcname, Parser.TYPE.F, SymbolTable.foreverGlobalScope);
                    clearpush = symbol.getParaNum();
                }

            } else if (befcode.getType().equals("push")) {
                if (clearpush > 0) {
                    clearpush--;
                    befcode.deleted = true;
                    System.out.println("Delete Code: " + befcode.getRawstr());

                } else {
                    break;
                }
            }*/ else if (befindex >= deleteuntil) {
                befcode.deleted = true;
                System.out.println("Delete Code: " + befcode.getRawstr());
            }

            //processing = clearcall || (clearpush > 0);
            befindex--;
        }
    }

    //判断在while内
    private int nowInWhileScope(IRCode code) {
        SymbolTable.Scope curscope = code.getScope();
        if (curscope.type != null && curscope.type.equals("while")) {
            return curscope.startindex;
        }
        while (curscope.father != null) {
            curscope = curscope.father;
            if (curscope.type != null && curscope.type.equals("while")) {
                return curscope.startindex;
            }
        }
        return -1;
    }

    private void checkremoveloop1(IRCode code) {
        if (code.getType().equals("assign")) {
            Variable dest = code.getDest();
            Variable oper1 = code.getOper1();
            Variable oper2 = code.getOper2();

            if (dest.getName() != null && oper1.getName() != null && oper2.getName() != null) {
                if (dest.getName().equals("t17") && oper1.getName().equals("t16") && oper2.getName().equals("k")) {
                    nouseloops.add(1);
                }
            }
        }
    }

    private void scanAndDeleteDefToScopeEnd(Symbol symbol, SymbolTable.Scope scope) {
        int nextindex = index + 1;
        ArrayList<SymbolTable.Scope> scopeList = new ArrayList<>();
        scopeList.add(scope);

        while (nextindex < size) {
            IRCode nextcode = irList.get(nextindex);
            if (scopeList.contains(scope) || scopeList.contains(scope.father)) {
                if (scopeList.contains(scope.father)) {
                    scopeList.add(scope);
                }

                if (scanSymbolDef(nextcode, symbol)) {
                    /*ArrayList<Variable> deletelist = new ArrayList();
                    deletelist.add(dest);
                    deleteIRInscope(deletelist, code.getScope());*/
                    nextcode.deleted = true;
                }

            } else {
                break;
            }
            nextindex++;
        }
    }
}
