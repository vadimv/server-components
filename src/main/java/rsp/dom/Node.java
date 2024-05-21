package rsp.dom;


public sealed interface Node permits Tag, Text {

     void appendString(StringBuilder sb);
}
