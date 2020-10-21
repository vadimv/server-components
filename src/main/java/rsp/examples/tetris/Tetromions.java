package rsp.examples.tetris;

import java.util.*;

public class Tetromions {
    public static final Map<Character, Tetromino> tetrominoMap = create();

    public static Map<Character, Tetromino> create() {
        final Map<Character, Tetromino> m = new HashMap<>();
        m.put('0' , new Tetromino(new char[][] {{'0'}}));
        m.put('I', new Tetromino(new char[][] {{'0', 'I', '0', '0'}, {'0', 'I', '0', '0'}, {'0', 'I', '0', '0'}, {'0', 'I', '0', '0'}}));
        m.put('J', new Tetromino(new char[][] {{'0', 'J', '0'}, {'0', 'J', '0'}, {'J', 'J', '0'}}));
        m.put('L', new Tetromino(new char[][] {{'0', 'L', '0'}, {'0', 'L', '0'}, {'0', 'L', 'L'}}));
        m.put('O', new Tetromino(new char[][] {{'O', 'O'}, {'O', 'O'}}));
        m.put('S', new Tetromino(new char[][] {{'0', 'S', 'S'}, {'S', 'S', '0'}, {'0', '0', '0'}}));
        m.put('T', new Tetromino(new char[][] {{'0', '0', '0'}, {'T', 'T', 'T'}, {'0', 'T', '0'}}));
        m.put('Z', new Tetromino(new char[][] {{'Z', 'Z', '0'}, {'0', 'Z', 'Z'}, {'0', '0', '0'}}));
        return m;
    }

    public static Tetromino randomTetromino() {
        final char[] tetrominos = "IJLOSTZ".toCharArray();
        return tetrominoMap.get(tetrominos[new Random().nextInt(tetrominos.length)]);
    }

    public static class Tetromino {
        public Tetromino(char[][] shape) {
            this.shape = shape;
        }

        public final char[][] shape;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tetromino tetromino = (Tetromino) o;
            return Arrays.equals(shape, tetromino.shape);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(shape);
        }
    }
}
