import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ErrorDisposal {
    private final String ERROR_DIR = "error.txt";
    public ArrayList<Erprt> errList;
    private static final HashMap<ER, Error> ERROR_DICT;

    private int cycleDepth = 0;
    public boolean funcHasReturn = false;  //todo 没有处理结束，可能隐藏bug
    public boolean hasReturn = false;  //当前函数是否有过return
    public boolean lastIsReturn = false;       //是否最后一个为return

    public ErrorDisposal() {
        this.errList = new ArrayList<>();
    }

    enum ER {
        ERR_A, ERR_B, ERR_C, ERR_D, ERR_E, ERR_F, ERR_G, ERR_H, ERR_I, ERR_J, ERR_K, ERR_L, ERR_M
    }

    static {

        ERROR_DICT = new HashMap<>();
        ERROR_DICT.put(ER.ERR_A, new Error(ER.ERR_A, "a", "非法符号"));
        ERROR_DICT.put(ER.ERR_B, new Error(ER.ERR_B, "b", "名字重定义"));
        ERROR_DICT.put(ER.ERR_C, new Error(ER.ERR_C, "c", "未定义的名字"));
        ERROR_DICT.put(ER.ERR_D, new Error(ER.ERR_D, "d", "函数参数个数不匹配"));
        ERROR_DICT.put(ER.ERR_E, new Error(ER.ERR_E, "e", "函数参数类型不匹配"));
        ERROR_DICT.put(ER.ERR_F, new Error(ER.ERR_F, "f", "无返回值的函数存在不匹配的return语句"));
        ERROR_DICT.put(ER.ERR_G, new Error(ER.ERR_G, "g", "有返回值的函数缺少return语句"));
        ERROR_DICT.put(ER.ERR_H, new Error(ER.ERR_H, "h", "不能改变常量的值"));
        ERROR_DICT.put(ER.ERR_I, new Error(ER.ERR_I, "i", "缺少分号"));
        ERROR_DICT.put(ER.ERR_J, new Error(ER.ERR_J, "j", "缺少右小括号’)’"));
        ERROR_DICT.put(ER.ERR_K, new Error(ER.ERR_K, "k", "缺少右中括号’]’"));
        ERROR_DICT.put(ER.ERR_L, new Error(ER.ERR_L, "l", "printf中格式字符与表达式个数不匹配"));
        ERROR_DICT.put(ER.ERR_M, new Error(ER.ERR_M, "m", "在非循环块中使用break和continue语句"));
    }

    public void writefile() throws IOException {
        File file = new File(ERROR_DIR);
        FileWriter writer = new FileWriter(file);
        System.out.println("Error部分开始输出：");
        for (Erprt r : errList) {
            System.out.println(r.tostring());
            writer.write(r.tostring() + "\n");
        }
        writer.flush();
        writer.close();
    }

    public String getErrCode(ER errtype) {
        return ERROR_DICT.get(errtype).getErrCode();
    }

    public String getErrInfo(ER errtype) {
        return ERROR_DICT.get(errtype).printErrInfo();
    }

    public void ErrorAt(Token tk, ErrorDisposal.ER errtype) {
        /*if (errtype != ER.ERR_E) {
            return;
        }*/

        Erprt e = new Erprt(tk.getRow(), getErrCode(errtype));

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
        System.err.println("At row " + tk.getRow() + " " + tk.tostring() + ": " + getErrInfo(errtype));
    }

    public void handleErrorA(Token tk) {
        String formatString = tk.getTokenValue();
        if (checkErrorA(formatString)) {
            ErrorAt(tk, ErrorDisposal.ER.ERR_A);
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



/*    //<LVal>为常量时，不能对其修改。报错行号为<LVal>所在行号。
    public boolean checkErrorH(Token tk) {
        lookup
        return tk.getTokenCode().equals("");
    }*/


    //无返回值的函数存在不匹配的return语句
    public void handleErrorF(Token tk) {
        if (!funcHasReturn && hasReturn) {
            ErrorAt(tk, ErrorDisposal.ER.ERR_F);
        }
    }

    public void handleErrorG(Token tk) {
        if (funcHasReturn && !lastIsReturn) {
            ErrorAt(tk, ErrorDisposal.ER.ERR_G);
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
