package rsp.examples.tetris;

import java.util.Arrays;
import java.util.Objects;

public class Stage {
    public static final int WIDTH = 12;
    public static final int HEIGHT = 20;

    private final char[][] cells;
    private final Tetromions.Tetromino tetramino;
    private final int tetraminoX;
    private final int tetraminoY;

    public final int collapsedLayersCount;

    public Stage(char[][] cells, Tetromions.Tetromino tetramino, int tetraminoX, int tetraminoY, int collapsedLayersCount) {
        this.cells = cells;
        this.tetramino = tetramino;
        this.tetraminoX = tetraminoX;
        this.tetraminoY = tetraminoY;
        this.collapsedLayersCount = collapsedLayersCount;
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

    public Stage setTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new Stage(cells, tetramino, x, y, collapsedLayersCount);
    }

    public Stage addTetraminoToCells() {
        final char[][] c = Arrays.stream(cells).map(char[]::clone).toArray(char[][]::new); // copy
        for(int y= 0; y < tetramino.shape.length; y++) {
            for(int x = 0; x < tetramino.shape[0].length; x++) {
                final char type = tetramino.shape[y][x];
                if (type != '0') {
                    c[tetraminoY + y][tetraminoX + x]  = tetramino.shape[y][x];
                }
            }
        }
        return new Stage(c, tetramino, tetraminoX, tetraminoY, collapsedLayersCount);
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
        return new Stage(cells, tetramino, tetraminoX, tetraminoY + 1, collapsedLayersCount);
    }

    public Stage moveTetraminoLeft() {
        return new Stage(cells, tetramino, tetraminoX - 1, tetraminoY, collapsedLayersCount);
    }

    public Stage moveTetraminoRight() {
        return new Stage(cells, tetramino, tetraminoX + 1, tetraminoY, collapsedLayersCount);
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
        return new Stage(cells, new Tetromions.Tetromino(rotateMatrix(tetramino.shape)), tetraminoX, tetraminoY, collapsedLayersCount);
    }

    public static Stage create() {
        final char[][] c = new char[HEIGHT][WIDTH];
        for(int y = 0; y < HEIGHT; y++) {
            for(int x = 0; x < WIDTH; x++) {
                c[y][x] = '0';
            }
        }
        return new Stage(c, Tetromions.tetrominoMap.get('0'), 0, 0, 0);
    }

    public Stage collapseFullLayers() {
        final char[][] c = Arrays.stream(cells).map(char[]::clone).toArray(char[][]::new); // copy
        int n = 0;
        for(int y1 = HEIGHT - 1, y2 = HEIGHT - 1; y1 >= 0; y1--) {
            if (!isFull(cells[y1])) {
                System.arraycopy(c,y1, c, y2--, 1);
            } else {
                n++;
            }
        }
        return new Stage(c, tetramino, tetraminoX, tetraminoY, collapsedLayersCount + n);
    }

    private boolean isFull(char[] row) {
        for (char cell:row) {
            if (cell == '0') {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stage stage = (Stage) o;
        return tetraminoX == stage.tetraminoX &&
                tetraminoY == stage.tetraminoY &&
                collapsedLayersCount == stage.collapsedLayersCount &&
                Arrays.equals(cells, stage.cells) &&
                tetramino.equals(stage.tetramino);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(tetramino, tetraminoX, tetraminoY, collapsedLayersCount);
        result = 31 * result + Arrays.hashCode(cells);
        return result;
    }
}
