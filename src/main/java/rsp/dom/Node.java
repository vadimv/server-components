package rsp.dom;


import java.util.List;

public interface Node {
     Path path();
     List<Node> children();
}
