public class Instr {
    private String str;     //大部分string类型串
    private boolean addroffset = false;     //是否需要地址offset处理
    private int offset;
    private String prestr;
    private String aftstr;

    public boolean pushoffset = false;
    public boolean activeRegoffset = false;

    public boolean hasRetReg = false;       //有欠着的寄存器需要还掉
    private int freeRegNumber;              //寄存器标号int no

    Instr(String str) {
        this.str = str;
    }

    Instr(String prestr, int offset, String aftstr, String type) {  //push 或 actreg 两种状态
        this.prestr = prestr;
        this.offset = offset;
        this.aftstr = aftstr;

        this.addroffset = true;

        if (type.equals("push")) {
            this.pushoffset = true;
        } else if (type.equals("actreg")) {
            this.activeRegoffset = true;
        }
    }

    public String getStr() {
        return str;
    }

    public int getFreeRegNumber() {
        return freeRegNumber;
    }

    public boolean isAddroffset() {
        return addroffset;
    }

    //set

    public void setFreeRegNumber(int freeRegNumber) {
        this.freeRegNumber = freeRegNumber;
    }

    public void setAddroffset(boolean addroffset) {
        this.addroffset = addroffset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String toString(int activeregoffset) {       //分为pushoffset与actregoffset两类
        if (addroffset) {
            offset += activeregoffset;
            return prestr + offset + aftstr;
        }
        return str;
    }

    public String toString() {
        return str;
    }
}
