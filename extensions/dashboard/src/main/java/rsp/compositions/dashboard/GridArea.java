package rsp.compositions.dashboard;

public record GridArea(int column, int row, int columnSpan, int rowSpan) {
    public GridArea {
        if (column < 1) {
            throw new IllegalArgumentException("Dashboard grid column must be 1 or greater.");
        }
        if (row < 1) {
            throw new IllegalArgumentException("Dashboard grid row must be 1 or greater.");
        }
        if (columnSpan < 1) {
            throw new IllegalArgumentException("Dashboard grid column span must be 1 or greater.");
        }
        if (rowSpan < 1) {
            throw new IllegalArgumentException("Dashboard grid row span must be 1 or greater.");
        }
    }
}
