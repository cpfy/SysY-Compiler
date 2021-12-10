import java.util.ArrayList;

public class Instrs {
    private ArrayList<Instr> instrList;

    public Instrs() {
        instrList = new ArrayList<>();
    }

    public ArrayList<Instr> getInstrList() {
        return instrList;
    }

    public void addInstr(Instr instr) {
        instrList.add(instr);
    }
}
