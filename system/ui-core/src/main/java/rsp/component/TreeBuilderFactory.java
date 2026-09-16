package rsp.component;

import rsp.dom.TreePositionPath;

/**
 * Provides an abstraction for creating an instance of the TreeBuilder class.
 */
public interface TreeBuilderFactory {

    /**
     * Creates a new instance of a TreeBuilder class.
     * @param baseDomPath a start position in the DOM tree, must not be null
     * @return a new TreeBuilder
     */
    TreeBuilder createTreeBuilder(TreePositionPath baseDomPath);

}
