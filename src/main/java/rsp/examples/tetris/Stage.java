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
                c[tetraminoY + y][tetraminoX + x]  = tetramino.shape[y][x];
            }
        }
        return c;
    }

    public Stage addTetramino(Tetromions.Tetromino tetramino, int x, int y) {
        return new Stage(cells, tetramino, x, y);
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
