public class Assert {

    public static void check(Object obj) {
        if (obj == null) {
            System.err.println("Assert Check Error! Null symbol!");
        }
    }

    public static void check(Object obj, String loc) {
        if (obj == null) {
            System.err.println(loc+ ": Assert Check Error! Null symbol!");
        }
    }
}
