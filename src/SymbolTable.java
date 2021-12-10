import java.util.ArrayList;
import java.util.Iterator;

public class SymbolTable {
    static class Scope {
        ArrayList<Symbol> symbolTable = new ArrayList<>(); // symbol table for the current scope
        Scope father = null;   //当前Scope的父作用域，仅当最外层时为null
        int level = 0;
        int inblockoffset = 0;  //正数，记录block块内偏移

        ArrayList<Scope> innerScopeList = new ArrayList<>();    //子block的list
        int innercnt = 0;   //计数当前访问到第几个子块，mips用
        String type;    //Block种类：while(主要用到), if, else, main, func, void(空白块)
    }

    public static Scope headerScope = new Scope();
    public static Scope foreverGlobalScope = headerScope;

    public static Symbol lookupNameOnly(String name) {
        Iterator<Symbol> it = headerScope.symbolTable.iterator();
        while (it.hasNext()) {
            Symbol sb = it.next();
            if (sb.getName().equals(name)) {
                return sb;
            }
        }
        return null;
    }

    public static Symbol lookupLocalTable(String name, Parser.TYPE type) {
        Iterator<Symbol> it = headerScope.symbolTable.iterator();
        while (it.hasNext()) {
            Symbol sb = it.next();
            if (sb.getName().equals(name) && sb.getType() == type) {
                return sb;
            }
        }
        return null;
    }

    public static Symbol lookupGlobalTable(String name, Parser.TYPE type) {
        Scope sc = headerScope;
        while (sc.father != null) {
            sc = sc.father;
            Iterator<Symbol> it = sc.symbolTable.iterator();
            while (it.hasNext()) {
                Symbol sb = it.next();
                if (sb.getName().equals(name) && sb.getType() == type) {
                    return sb;
                }
            }
        }
        return null;
    }

    public static Symbol lookupFullTable(String name, Parser.TYPE type) {
        Symbol syb = lookupLocalTable(name, type);
        if (syb == null) {
            syb = lookupGlobalTable(name, type);
        }
        return syb;
    }

    public static ArrayList<Symbol> getSymbolTable() {
        ArrayList<Symbol> sybList = new ArrayList<>();
        for (Symbol sb : headerScope.symbolTable) {
            sybList.add(sb);
        }
        return sybList;
    }

    //自带Scope参数的IR生成阶段lookup
    public static Symbol lookupNameOnly(String name, Scope scope) {
        Iterator<Symbol> it = scope.symbolTable.iterator();
        while (it.hasNext()) {
            Symbol sb = it.next();
            if (sb.getName().equals(name)) {
                return sb;
            }
        }
        return null;
    }

    public static Symbol lookupLocalTable(String name, Parser.TYPE type, Scope scope) {
        Iterator<Symbol> it = scope.symbolTable.iterator();
        while (it.hasNext()) {
            Symbol sb = it.next();
            if (sb.getName().equals(name) && sb.getType() == type) {
                return sb;
            }
        }
        return null;
    }

    public static Symbol lookupGlobalTable(String name, Parser.TYPE type, Scope scope) {
        Scope sc = scope;
        while (sc.father != null) {
            sc = sc.father;
            Iterator<Symbol> it = sc.symbolTable.iterator();
            while (it.hasNext()) {
                Symbol sb = it.next();
                if (sb.getName().equals(name) && sb.getType() == type) {
                    return sb;
                }
            }
        }
        return null;
    }

    public static Symbol lookupFullTable(String name, Parser.TYPE type, Scope scope) {
        Symbol syb = lookupLocalTable(name, type, scope);
        if (syb == null) {
            syb = lookupGlobalTable(name, type, scope);
        }
        return syb;
    }

    //Scope相关操作
    public static void insertTable(Symbol symbol) {
        headerScope.symbolTable.add(symbol);
        symbol.setScope(headerScope);
        //System.out.println("insert & setH: " + symbol.getName());
    }

    //无type版本(仅服务于GrammarScanner分支版本)
    public static void openScope() {
        Scope innerScope = new Scope();
        innerScope.level = headerScope.level + 1;

        innerScope.father = headerScope;

        headerScope.innerScopeList.add(innerScope);     //每个子scope加入list

        headerScope = innerScope;
    }

    //含type版本
    public static void openScope(String blocktype) {
        Scope innerScope = new Scope();
        innerScope.type = blocktype;
        innerScope.level = headerScope.level + 1;

        innerScope.father = headerScope;

        headerScope.innerScopeList.add(innerScope);     //每个子scope加入list

        headerScope = innerScope;
    }

    public static void closeScope() {
        setScopeForEachSymbol();
        headerScope = headerScope.father;
    }

    //mips后引入
    private static void setScopeForEachSymbol() {
        Iterator<Symbol> it = headerScope.symbolTable.iterator();
        while (it.hasNext()) {
            Symbol sb = it.next();
            sb.setScope(headerScope);
        }
    }
}
