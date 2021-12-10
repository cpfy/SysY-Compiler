import java.util.ArrayList;
import java.util.HashMap;

public class Register {
    private HashMap<Integer, String> regMap;
    private HashMap<String, Integer> regNameMap;

    private ArrayList<Integer> freeRegList;
    private HashMap<Integer, Variable> varAllocMap;
    private ArrayList<Integer> activeRegList;   //当前活跃的变量占用的、已分配出的reg
    private ArrayList<Variable> varRegUsageList;

    public Register() {
        this.regMap = new HashMap<>();
        this.regNameMap = new HashMap<>();

        this.freeRegList = new ArrayList<>();
        this.varAllocMap = new HashMap<>();
        this.activeRegList = new ArrayList<>();

        initRegMap();
        initRegnameMap();
        initFreeRegList();
    }

    private void initRegMap() {
        regMap.put(0, "zero");
        regMap.put(1, "at");
        regMap.put(2, "v0");
        regMap.put(3, "v1");
        regMap.put(4, "a0");
        regMap.put(5, "a1");
        regMap.put(6, "a2");
        regMap.put(7, "a3");
        regMap.put(8, "t0");
        regMap.put(9, "t1");
        regMap.put(10, "t2");
        regMap.put(11, "t3");
        regMap.put(12, "t4");
        regMap.put(13, "t5");
        regMap.put(14, "t6");
        regMap.put(15, "t7");
        regMap.put(16, "s0");
        regMap.put(17, "s1");
        regMap.put(18, "s2");
        regMap.put(19, "s3");
        regMap.put(20, "s4");
        regMap.put(21, "s5");
        regMap.put(22, "s6");
        regMap.put(23, "s7");
        regMap.put(24, "t8");
        regMap.put(25, "t9");
        regMap.put(26, "k0");
        regMap.put(27, "k1");
        regMap.put(28, "gp");
        regMap.put(29, "sp");
        regMap.put(30, "fp");
        regMap.put(31, "ra");
    }

    private void initRegnameMap() {
        regNameMap.put("zero", 0);
        regNameMap.put("at", 1);
        regNameMap.put("v0", 2);
        regNameMap.put("v1", 3);
        regNameMap.put("a0", 4);
        regNameMap.put("a1", 5);
        regNameMap.put("a2", 6);
        regNameMap.put("a3", 7);
        regNameMap.put("t0", 8);
        regNameMap.put("t1", 9);
        regNameMap.put("t2", 10);
        regNameMap.put("t3", 11);
        regNameMap.put("t4", 12);
        regNameMap.put("t5", 13);
        regNameMap.put("t6", 14);
        regNameMap.put("t7", 15);
        regNameMap.put("s0", 16);
        regNameMap.put("s1", 17);
        regNameMap.put("s2", 18);
        regNameMap.put("s3", 19);
        regNameMap.put("s4", 20);
        regNameMap.put("s5", 21);
        regNameMap.put("s6", 22);
        regNameMap.put("s7", 23);
        regNameMap.put("t8", 24);
        regNameMap.put("t9", 25);
        regNameMap.put("k0", 26);
        regNameMap.put("k1", 27);
        regNameMap.put("gp", 28);
        regNameMap.put("sp", 29);
        regNameMap.put("fp", 30);
        regNameMap.put("ra", 31);
    }

    private void initFreeRegList() {
        for (int i = 8; i < 32; i++) {
            if (i != 29 && i != 31) {
                freeRegList.add(i);
            }
        }
    }

    //查询 no -> name
    public String getRegisterNameFromNo(int no) {
        return regMap.get(no);
    }

    //查询 name -> no
    public int getRegisterNoFromName(String name) {
        return regNameMap.get(name);
    }

    //临时变量-申请寄存器
    public String applyRegister(Variable v) {
        int no;

        if (!freeRegList.isEmpty()) {
            no = freeRegList.get(0);

            varAllocMap.put(no, v);
            freeRegList.remove(0);
            v.setCurReg(no);
            addActiveListNoRep(no);     //无重复加入activeregList 活跃变量表

        } else {    //todo 无空寄存器
            System.err.println("No free Reg! Alloc $v1.");
            no = 3;
        }

        System.out.println("Alloc Reg $" + regMap.get(no) + " to variable " + v.toString());

        return regMap.get(no);
    }

    //定义变量-申请寄存器
    public String applyRegister(Symbol s) {
        int no;
        if (!freeRegList.isEmpty()) {
            no = freeRegList.get(0);

            freeRegList.remove(0);
            s.setCurReg(no);
            addActiveListNoRep(no);     //无重复加入activeregList 活跃变量表

        } else {    //todo 无空寄存器
            System.err.println("No free Reg! Alloc $v1.");
            no = 3;
        }

        System.out.println("Alloc Reg $" + regMap.get(no) + " to symbol " + s.getName());
        return regMap.get(no);
    }

    //申请临时存器
    public int applyTmpRegister() {
        int regno;
        if (!freeRegList.isEmpty()) {
            regno = freeRegList.get(0);
            freeRegList.remove(0);
            addActiveListNoRep(regno);     //无重复加入activeregList 活跃变量表

        } else {    //todo 无空寄存器,分$v1
            System.err.println("No free Reg! Alloc $v1.");
            regno = 3;
        }

        System.out.println("Alloc Reg $" + regMap.get(regno) + " to Tmp");
        return regno;
    }

    public void freeTmpRegister(int regno) {
        if (regno < 8 || regno == 29 || regno == 31) {
            System.err.println("Register freeTmpRegister() : Error free tmp Reg No!! regno = " + regno);

        } else if (!freeRegList.contains(regno)) {
            removeActiveRegList(regno);     //删除变量in activeregList 活跃变量表
            freeRegList.add(regno);
            System.out.println("Free Reg $" + regMap.get(regno) + " from Tmp");
        }//todo 其它的free寄存器都得检查是否重复！
    }

    public void freeTmpRegisterByName(String regname) {
        int regno = regNameMap.get(regname);
        freeTmpRegister(regno);
    }

    //释放寄存器
    public void freeRegister(Variable v) {
        if (v.isKindofsymbol()) {
            Symbol s = v.getSymbol();
            Assert.check(s);
            int regno = s.getCurReg();
            //freeRegList.add(regno);
            freeTmpRegister(regno);

            s.setCurReg(-1);    //reg使用状态回到未分配的-1

            removeActiveRegList(regno);     //删除变量in activeregList 活跃变量表
            System.out.println("Free Reg $" + regMap.get(regno) + " from " + s.getName());

        } else {
            int regno = v.getCurReg();
            //freeRegList.add(regno);
            freeTmpRegister(regno);
            removeActiveRegList(regno);     //删除变量in activeregList 活跃变量表
            System.out.println("Free Reg $" + regMap.get(regno) + " from " + v.toString());
        }
    }


    //查询是否有空闲寄存器
    public boolean hasSpareRegister() {
        return !freeRegList.isEmpty();
    }

    //查询是否需保存现场，active内有内容？
    public ArrayList<Integer> getActiveRegList() {
        return activeRegList;
    }

    private void addActiveListNoRep(int no) {
        if (!activeRegList.contains(no)) {
            activeRegList.add(no);
        }
    }

    //删除变量in activeregList 活跃变量表
    private void removeActiveRegList(int no) {
        activeRegList.removeIf(i -> i == no);
    }

    //reset全部寄存器状态
    public void resetAllReg() {
        freeRegList.clear();
        varAllocMap.clear();
        activeRegList.clear();

        initFreeRegList();
    }
}
