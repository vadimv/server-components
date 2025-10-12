package rsp.dom;

import rsp.html.Segment;

public sealed interface Node extends Segment permits Tag, Text {
}
