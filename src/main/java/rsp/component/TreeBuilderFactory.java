package rsp.component;

import rsp.dom.TreePositionPath;

/**
 * Provides an abstraction for creating an instance of ComponentRenderer class.
 */
public interface TreeBuilderFactory {

    TreeBuilder createTreeBuilder(TreePositionPath baseDomPath);

}
