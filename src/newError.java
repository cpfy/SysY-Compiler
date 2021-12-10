public class newError {
    private int row;
    private String type;

    public newError(int row, String type) {
        this.row = row;
        this.type = type;
    }

    public String tostring() {
        return row + " " + type;
    }

    public int getRow() {
        return row;
    }

    public String getType() {
        return type;
    }
}
