public class Error {
    private final ErrorDisposal.ER ErrType;
    private String ErrCode;
    private String ErrInfo;

    public Error(ErrorDisposal.ER ErrType, String ErrCode, String ErrInfo) {
        this.ErrType = ErrType;
        this.ErrCode = ErrCode;
        this.ErrInfo = ErrInfo;
    }

    public String getErrCode() {
        return ErrCode;
    }

    public String printErrInfo() {
        return ErrInfo + "!" + " 错误码" + ":" + ErrCode;
    }
}
