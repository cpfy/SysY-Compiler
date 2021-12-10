import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class TokenScanner {
    private String curToken;
    private ArrayList<Token> tokenList;
    private final TokenDictionary tokenDictionary;

    private int curRows = 1;
    private boolean readingString = false;
    private boolean readingNumber = false;
    private boolean readingFormatString = false;
    private boolean readingBool = false;
    private boolean readingSingleComments = false;
    private boolean readingMultiComments = false;

    private final String INPUT_DIR = "testfile.txt";
    private final String OUTPUT_DIR = "output.txt";

    public TokenScanner() {
        this.curToken = "";
        this.tokenList = new ArrayList<>();
        this.tokenDictionary = new TokenDictionary();
    }

    public ArrayList<Token> getTokens(int output) {
        try {
            scanfile(INPUT_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (output == 1) {
            try {
                writefile(OUTPUT_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return tokenList;
    }

    public ArrayList<Token> scanfile(String dir) throws IOException {
        File file = new File(dir);
        FileReader reader = new FileReader(file);

        int c;
        while ((c = reader.read()) != -1) {
            char cc = (char) c;
            scanchar(cc);
        }
        return tokenList;
    }

    public void writefile(String dir) throws IOException {
        File file = new File(dir);
        FileWriter writer = new FileWriter(file);
        for (Token t : tokenList) {
            writer.write(t.tostring() + "\n");
            System.out.println(t.tostring());
        }
        writer.flush();
        writer.close();
    }

    private void scanchar(char c) {
        TokenDictionary.TYPE type = tokenDictionary.queryCharType(String.valueOf(c));
        switch (type) {
            case LETTER:
                if (readingSingleComments || readingMultiComments) {
                    curToken = "";
                    break;
                } else if (readingFormatString) {
                    curToken += c;
                    break;
                } else if (readingBool) {
                    curToken = endOfOp();
                }
                if (curToken.isEmpty()) {
                    readingString = true;
                }
                curToken += c;
                break;
            case DIGIT:
                if (readingSingleComments || readingMultiComments) {
                    curToken = "";
                    break;
                } else if (readingFormatString) {
                    curToken += c;
                    break;
                } else if (readingBool) {
                    curToken = endOfOp();
                }
                if (curToken.isEmpty()) {
                    readingNumber = true;
                }
                curToken += c;
                break;
            case OPERATOR:
                if (readingMultiComments) {
                    if (curToken.equals("*") && c == '/') {
                        readingMultiComments = false;
                        curToken = "";
                    } else {
                        curToken = "";
                        curToken += c;
                    }
                    break;
                } else if (readingSingleComments) {
                    break;
                } else if (readingFormatString && c != '"') {
                    curToken += c;
                    break;

                } else if (readingString) {
                    curToken = endOfWord();
                    //readingString = false;

                    //curToken += c;
                    //createToken(tokenDictionary.queryOpCode(String.valueOf(c)));
                    handleOperator(c);

                } else if (readingNumber) {
                    createToken("INTCON");
                    readingNumber = false;
                    //curToken += c;
                    //createToken(tokenDictionary.queryOpCode(String.valueOf(c)));
                    handleOperator(c);

                } else if (readingBool) {
                    if ((curToken.equals("<") && (c == '=')) ||
                            (curToken.equals(">") && (c == '=')) ||
                            (curToken.equals("=") && (c == '=')) ||
                            (curToken.equals("!") && (c == '=')) ||
                            (curToken.equals("|") && (c == '|')) ||
                            (curToken.equals("&") && (c == '&'))
                    ) {
                        curToken += c;
                        createToken(tokenDictionary.queryOpCode(curToken));

                    } else if (curToken.equals("/")) {
                        if (c == '/') {
                            readingSingleComments = true;
                            curToken = "";
                        } else if (c == '*') {
                            readingMultiComments = true;
                            curToken = "";
                        } else {
                            //div
                            createToken(tokenDictionary.queryOpCode(curToken));
                            handleOperator(c);
                        }
                    } else {
                        createToken(tokenDictionary.queryOpCode(curToken));
                        handleOperator(c);
                    }
                    readingBool = false;

                } else {
                    handleOperator(c);
                }
                break;
            case SPACE:
                if (readingMultiComments/* || readingSingleComments*/) {
                    curToken = "";
                    break;
                } else if (readingFormatString) {
                    curToken += c;
                    break;

                } else if (readingBool) {
                    createToken(tokenDictionary.queryOpCode(curToken));
                    readingBool = false;

                } else if (readingString) {
                    curToken = endOfWord();

                } else if (readingNumber) {
                    createToken("INTCON");
                    readingNumber = false;

                } else {
                    //curToken = endOfWord();
                    //newline
                    if (c == Character.toChars(10)[0]) {
                        curRows++;
                        resetStatus();
                    }
                }
                break;
            case OTHERS:
                if (readingFormatString) {
                    curToken += c;
                }
                break;
            default:
                System.err.println("Unhandled char scanned!");
        }

        //System.out.println(c);
    }

    private String endOfWord() {
        if (tokenDictionary.queryIfKeyword(curToken)) {
            createToken(tokenDictionary.queryKeywordCode(curToken));
        } else {
            if (curToken.length() > 0) {
                createToken("IDENFR");
            }
        }
        resetStatus();
        return curToken;
    }

    private String endOfOp() {
        if (tokenDictionary.queryIfOpCode(curToken)) {
            createToken(tokenDictionary.queryOpCode(curToken));
        } else {
            System.err.println("Unhandled endOfOp!");
        }
        resetStatus();
        return curToken;
    }

    private String handleOperator(char c) {
        if (c == ';') {
            curToken = endOfWord();
            curToken = ";";
            createToken(tokenDictionary.queryOpCode(String.valueOf(c)));

        } else if (c == '"') {
            if (!readingFormatString) {
                readingFormatString = true;
                curToken += c;

            } else {
                curToken += c;
                createToken("STRCON");
                readingFormatString = false;
            }

        } else if (c == '<' || c == '>' || c == '!' || c == '=' || c == '/' || c == '|' || c == '&') {
            curToken = endOfWord();
            readingBool = true;
            curToken += c;

        } else if (tokenDictionary.queryIfOpCode(String.valueOf(c))) {
            curToken = endOfWord();
            curToken = String.valueOf(c);
            createToken(tokenDictionary.queryOpCode(String.valueOf(c)));

        } else {
            System.err.println("Unhandled operator scanned!");
        }
        return curToken;
    }

    private void createToken(String tokenCode) {
        Token t = new Token(tokenCode, curToken, curRows);
        tokenList.add(t);
        curToken = "";
    }

    private void resetStatus() {
        readingString = false;
        readingNumber = false;
        readingFormatString = false;
        readingBool = false;
        readingSingleComments = false;
    }
}
