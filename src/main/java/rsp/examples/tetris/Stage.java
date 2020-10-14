package rsp.examples.tetris;

public class Stage {
    public static final int WIDTH = 12;
    public static final int HEIGHT = 20;

    public Cell[][] cells;

    public Stage(Cell[][] cells) {
        this.cells = cells;
    }

    public static Stage create() {
        final Cell[][] cells = new Cell[HEIGHT][WIDTH];
        for(int i = 0; i < HEIGHT; i++) {
            for(int j = 0; j < WIDTH; j++) {
                cells[i][j] = new Cell('0', "blue");
            }
        }
        return new Stage(cells);
    }

    public static class Cell {
        public final int type;
        public final String color;

        public Cell(int type, String color) {
            this.type = type;
            this.color = color;
        }

        public String toString() {
            return "*";
        }
    }
}
