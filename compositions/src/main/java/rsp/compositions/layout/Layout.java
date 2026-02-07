package rsp.compositions.layout;

import rsp.component.definitions.Component;
import rsp.dsl.Definition;

/**
 * Strategy interface for rendering UI components in a visual layout.
 * <p>
 * Implementations define how primary content, sidebar, and overlay components
 * are visually arranged (e.g., sidebar left of content, overlay as modal).
 *
 * @see DefaultLayout
 */
public interface Layout {
    /**
     * Render components in the layout.
     *
     * @param primary        the main content component (always present)
     * @param sidebar        optional sidebar component (null if none)
     * @param activeOverlay  optional active overlay component (null if none)
     * @param onOverlayClose callback to close the active overlay (null if no overlay)
     * @return the rendered layout definition
     */
    Definition render(Component<?> primary,
                      Component<?> sidebar,
                      Component<?> activeOverlay,
                      Runnable onOverlayClose);
}
