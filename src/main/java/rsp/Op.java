package rsp;

public class Op {
    public final static int OP_OPEN = 1;
    public final static int OP_CLOSE = 2;
    public final static int OP_ATTR = 3;
    public final static int OP_TEXT = 4;
    public final static int OP_LAST_ATTR = 5;
    public final static int OP_END = 6;
    public final static int OP_STYLE = 100;

    // Header bytes sizes
    public final static int OP_SIZE = 1;
    public final static int OP_OPEN_SIZE = 9;
    public final static int OP_ATTR_SIZE = 14;
    public final static int OP_TEXT_SIZE = 5;
}
