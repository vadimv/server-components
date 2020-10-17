package rsp.examples.tetris;

import java.util.Arrays;

public class Stage {
    public static final int WIDTH = 12;
    public static final int HEIGHT = 20;

    private final char[][] cells;
    private final Tetromions.Tetromino tetramino;
    private final int tetraminoX;
    private final int tetraminoY;


    public Stage(char[][] cells, Tetromions.Tetromino tetramino, int tetraminoX, int tetraminoY) {
        this.cells = cells;
        this.tetramino = tetramino;
        this.tetraminoX = tetraminoX;
        this.tetraminoY = tetraminoY;
    }

    public char[][] cells() {
        final char[][] c = Arrays.stream(cells).map(char[]::clone).toArray(char[][]::new); // copy
        for(int y= 0; y < tetramino.shape.length; y++) {
            for(int x = 0; x < tetramino.shape[0].length; x++) {
                final char type = tetramino.shape[y][x];
                if (type != '0') {
                    c[tetraminoY + y][tetraminoX + x]  = tetramino.shape[y][x];
                }
            }
        }
        return c;
    }

    public Stage addTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new Stage(cells, tetramino, x, y);
    }

    public boolean checkCollision(int dx, int dy, boolean rotate) {
        final char[][] m = rotate ? rotateMatrix(tetramino.shape) : tetramino.shape;
        final int h = m.length;
        final int w = m[0].length;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (m[y][x] != '0') {
                    if ((tetraminoY + y + dy >= HEIGHT)
                        || ((tetraminoX + x + dx) < 0)
                        || ((tetraminoX + x + dx) >= WIDTH)
                        || (cells[tetraminoY + y + dy][tetraminoX + x + dx] != '0')) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Stage moveTetraminoDown() {
        return new Stage(cells, tetramino, tetraminoX, tetraminoY + 1);
    }

    public Stage moveTetraminoLeft() {
        return new Stage(cells, tetramino, tetraminoX - 1, tetraminoY);
    }

    public Stage moveTetraminoRight() {
        return new Stage(cells, tetramino, tetraminoX + 1, tetraminoY);
    }

    private static char[][] rotateMatrix(char[][] m) {
        final int h = m.length;
        final int w = m[0].length;
        final char[][] t = new char[h][w];

        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++) {
                t[w - x - 1][y]  = m[y][x];
            }
        }
        return t;
    }

    public Stage rotateCcw() {
        return new Stage(cells, new Tetromions.Tetromino(rotateMatrix(tetramino.shape)), tetraminoX, tetraminoY);
    }

    public static Stage create() {
        final char[][] cells = new char[HEIGHT][WIDTH];
        for(int y = 0; y < HEIGHT; y++) {
            for(int x = 0; x < WIDTH; x++) {
                cells[y][x] = '0';
            }
        }
        return new Stage(cells, Tetromions.tetrominoMap.get('0'), 0, 0);
    }
}
