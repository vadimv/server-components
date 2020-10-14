package rsp.examples.tetris;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Tetromions {
    public final Map<Character, Tetromino> tetrominoMap;

    public Tetromions(Map<Character, Tetromino> tetrominoMap) {
        this.tetrominoMap = tetrominoMap;
    }

    public static Tetromions create() {
        final Map<Character, Tetromino> m = new HashMap<>();
        m.put('0' , new Tetromino("211, 211, 211", new char[][] {{0}}));
        m.put('I', new Tetromino("80, 227, 230", new char[][] {{0, 'I', 0, 0}, {0, 'I', 0, 0}, {0, 'I', 0, 0}, {0, 'I', 0, 0}}));
        m.put('J', new Tetromino("36, 95, 223", new char[][] {{0, 'J', 0}, {0, 'J', 0}, {'J', 'J', 0}}));
        m.put('L', new Tetromino("223, 173, 36", new char[][] {{0, 'L', 0}, {0, 'L', 0}, {0, 'L', 'L'}}));
        m.put('O', new Tetromino("223, 217, 36", new char[][] {{'O', 'O'}, {'O', 'O'}}));
        m.put('S', new Tetromino("48, 211, 56", new char[][] {{0, 'S', 'S'}, {'S', 'S', 0}, {0, 0, 0}}));
        m.put('T', new Tetromino("48, 211, 56", new char[][] {{0, 0, 0}, {'T', 'T', 'T'}, {0, 'T', 0}}));
        m.put('Z', new Tetromino("227, 78, 78", new char[][] {{'Z', 'Z', 0}, {0, 'Z', 'Z'}, {0, 0, 0}}));
        return new Tetromions(m);
    }

    public Tetromino randomTetromino() {
        final char[] tetrominos = "IJLOSTZ".toCharArray();
        return tetrominoMap.get(tetrominos[new Random().nextInt(tetrominos.length)]);
    }

    public static class Tetromino {
        public Tetromino(String color, char[][] shape) {
            this.color = color;
            this.shape = shape;
        }

        public final String color;
        public final char[][] shape;
    }
}
