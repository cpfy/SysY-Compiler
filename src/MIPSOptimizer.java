import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MIPSOptimizer {
    private ArrayList<String> optList;

    public MIPSOptimizer(ArrayList<String> list) {
        this.optList = list;
    }

    public ArrayList<String> optim() {
        Pattern offsetdigit = Pattern.compile("-?\\d+");
        //Pattern regpattern = Pattern.compile("\\$[a-rt-z]");

        for (int i = 0; i < optList.size(); i++) {
            String mipscode = optList.get(i).trim();

            if (mipscode.startsWith("lw") && mipscode.endsWith("($sp)")) {
                String lastcode = optList.get(i - 1).trim();

                if (lastcode.startsWith("sw") && lastcode.endsWith("($sp)")) {
                    Matcher lw_m = offsetdigit.matcher(mipscode);
                    Matcher sw_m = offsetdigit.matcher(lastcode);
                    String offset1, offset2;

                    if (lw_m.find()) {
                        offset1 = lw_m.group();
                        if (lw_m.find()) {
                            offset1 = lw_m.group();
                        }
                        //System.out.println(lw_m.group());


                    } else {
                        continue;
                    }

                    if (sw_m.find()) {
                        offset2 = sw_m.group();
                        if (sw_m.find()) {
                            offset2 = sw_m.group();
                        }
                        //System.out.println(sw_m.group());

                    } else {
                        continue;
                    }

                    if (offset1.equals(offset2)) {
                        String lwreg = mipscode.substring(3, 6);
                        String swreg = lastcode.substring(3, 6);
                        String newmove = "\t" + "move " + lwreg + ", " + swreg;
                        optList.set(i, newmove);
                    }
                }
            }
        }

        return optList;
    }

}
