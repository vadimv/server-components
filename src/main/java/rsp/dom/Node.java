package rsp.dom;


public interface Node {

     VirtualDomPath path();

     void appendString(StringBuilder sb);
}
