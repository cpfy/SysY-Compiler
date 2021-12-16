import java.util.ArrayList;

public class Compiler {
    public static void main(String[] args) {
        TokenScanner myTokenScanner = new TokenScanner();
        ArrayList<Token> mytokens = myTokenScanner.getTokens(0);

        //GrammarScanner myGrammarScanner = new GrammarScanner(mytokens);
        //myGrammarScanner.start(1, 0);

        ASTBuilder astBuilder = new ASTBuilder(mytokens);
        Node ASTtree = astBuilder.getTree();

        //SymbolTableBuilder symbolTableBuilder = new SymbolTableBuilder(ASTtree);
        //symbolTableBuilder.buildtable(1);

        IRGenerator irGenerator = new IRGenerator(ASTtree);
        ArrayList<IRCode> irList = irGenerator.generate(1);

        if (true/*false*/) {
            Optimizer optimizer = new Optimizer(irList);
            irList = optimizer.optimize();
        }

        MIPSTranslator mipsTranslator = new MIPSTranslator(irList);
        mipsTranslator.tomips(1);
    }
}