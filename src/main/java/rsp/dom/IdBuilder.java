package rsp.dom;

import java.nio.IntBuffer;

public class IdBuilder {
    private static final int DEFAULT_MAX_LEVEL = 256;

    private final int maxLevel;
    private final IntBuffer buffer;

    private int level = 1;
    private int index = level - 1;

    public IdBuilder(int maxLevel) {
        this.maxLevel = maxLevel;
        buffer = IntBuffer.allocate(maxLevel);
        buffer.limit(level);
    }

    public IdBuilder() {
        this(DEFAULT_MAX_LEVEL);
    }

    public Id id() {
        return new Id(mkArray());
    }

    private int index() { return level - 1;}

    public void incLevel() {
        level += 1;
        buffer.limit(level);
    }

    /** Just decreases level */
    private void decLevelTmp() {
        level -= 1;
        buffer.limit(level);
    }

    /** Resets current id and decreases level */
    public void decLevel() {
        buffer.put(index, (short) 0);
        level -= 1;
        buffer.limit(level);
    }

    public void incId() {
        final int updated = buffer.get(index) + 1;
        buffer.put(index,  (short) updated);
    }

    public void decId() {
        final int updated = buffer.get(index) - 1;
        buffer.put(index, (short) updated);
    }

    public int getLevel() {
        return level;
    }

    public int[] mkArray() {
        int[] clone = new int[buffer.limit()];
        buffer.rewind();
        var i = 0;
        while (buffer.hasRemaining()) {
            clone[i] = buffer.get();
            i += 1;
        }
        return clone;
    }
}
