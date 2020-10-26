package rsp.examples.components.grid;

import rsp.Component;

import java.util.Arrays;

import static rsp.dsl.Html.*;

public class GridComponent {

    public static final Component<GridModel> subComponent = state ->
            div(
                    table(of(Arrays.stream(state.get().rows).map(row -> tr(
                            of(Arrays.stream(row.fields).map(field -> td(text(field))))
                            )))

                    )

            );
}
