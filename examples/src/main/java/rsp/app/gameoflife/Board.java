package rsp.app.gameoflife;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Immutable Conway board; its backing array never escapes the actor. */
public final class Board {
    public static final int HEIGHT = 25;
    public static final int WIDTH = 40;
    private static final int LENGTH = HEIGHT * WIDTH;
    private static final float RANDOM_FILL_RATIO = 0.2f;

    private final boolean[] cells;

    private Board(boolean[] cells) {
        this.cells = cells;
    }

    public static Board empty() {
        return new Board(new boolean[LENGTH]);
    }

    public static Board random(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        boolean[] cells = new boolean[LENGTH];
        for (int index = 0; index < cells.length; index++) {
            cells[index] = random.nextFloat() < RANDOM_FILL_RATIO;
        }
        return new Board(cells);
    }

    public int size() {
        return cells.length;
    }

    public boolean isAlive(int index) {
        return cells[index];
    }

    public boolean contains(int x, int y) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT;
    }

    public static int x(int index) {
        return index % WIDTH;
    }

    public static int y(int index) {
        return index / WIDTH;
    }

    private static int index(int y, int x) {
        return y * WIDTH + x;
    }

    public Board toggle(int x, int y) {
        if (!contains(x, y)) {
            throw new IllegalArgumentException("Cell is outside the board");
        }
        boolean[] copy = cells.clone();
        int index = index(y, x);
        copy[index] = !copy[index];
        return new Board(copy);
    }

    public Board advance() {
        boolean[] next = new boolean[cells.length];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int neighbours = neighbours(x, y);
                int index = index(y, x);
                next[index] = neighbours == 3 || cells[index] && neighbours == 2;
            }
        }
        return new Board(next);
    }

    private int neighbours(int x, int y) {
        int top = (y + HEIGHT - 1) % HEIGHT;
        int bottom = (y + 1) % HEIGHT;
        int left = (x + WIDTH - 1) % WIDTH;
        int right = (x + 1) % WIDTH;
        return alive(top, left) + alive(top, x) + alive(top, right)
                + alive(y, left) + alive(y, right)
                + alive(bottom, left) + alive(bottom, x) + alive(bottom, right);
    }

    private int alive(int y, int x) {
        return cells[index(y, x)] ? 1 : 0;
    }
}
