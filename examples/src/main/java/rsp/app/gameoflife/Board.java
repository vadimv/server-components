package rsp.app.gameoflife;

import java.util.Random;

public class Board {
    public static final int HEIGHT = 50;
    public static final int WIDTH = 100;
    private static final int LENGTH = HEIGHT * WIDTH;

    private static final float RANDOM_FILL_RATIO = 0.2f;

    public final boolean[] cells;

    private Board(boolean[] cells) {
        this.cells = cells;
    }

    public static Board create(boolean random) {
        return new Board(random ? randomFilled(LENGTH) : new boolean[LENGTH]);
    }

    private static boolean[] randomFilled(int size) {
        final boolean[] b = new boolean[size];
        final Random random = new Random();
        for (int i = 0; i < size; i++) {
            b[i] = random.nextFloat() < RANDOM_FILL_RATIO;
        }
        return b;
    }

    public static int x(int index) {
        return index % WIDTH;
    }

    public static int y(int index) {
        return index / WIDTH;
    }

    public static int index(int y, int x) {
        return (y * WIDTH) + (x % WIDTH);
    }

    public Board setActive(int x, int y, boolean a) {
        final boolean[] copy = cells.clone();
        copy[index(y, x)] = a;
        return new Board(copy);
    }

    public Board toggle(int x, int y) {
        return setActive(x, y, !cells[index(y, x)]);
    }

    public Board advance() {
        final boolean[] copy = new boolean[cells.length];
        for(int y = 0; y < HEIGHT; y++) {
            for(int x = 0; x < WIDTH; x++) {
                final int n = neighbours(x, y);
                final int i = index(y, x);
                if (cells[index(y, x)]) {
                    if (n < 2 || n > 3) copy[i] = false; // the cell dies
                        else copy[i] = cells[i];
                } else {
                    if (n == 3) copy[i] = true; // becomes a live cell
                        else copy[i] = cells[i];
                }
            }
        }
        return new Board(copy);
    }

    private int neighbours(int x, int y) {
        // If the cell is at the edge use as its neighbours the cells on the opposite edge
        final int topY = y - 1 < 0 ? (HEIGHT - 1) : y - 1;
        final int bottomY = (y + 1 == HEIGHT) ? 0 : y + 1;
        final int leftX = x - 1 < 0 ? (WIDTH - 1) : x - 1;
        final int rightX = (x + 1 == WIDTH) ? 0 : x + 1;

        return b2i(cells[index(topY, leftX)])
                + b2i(cells[index(topY, x)])
                + b2i(cells[index(topY, rightX)])
                + b2i(cells[index(y, leftX)])
                + b2i(cells[index(y, rightX)])
                + b2i(cells[index(bottomY, leftX)])
                + b2i(cells[index(bottomY, x)])
                + b2i(cells[index(bottomY, rightX)]);
    }

    private static int b2i(boolean value) {
        return value ? 1 : 0;
    }
}
