package rsp.examples.components.entities;

public class Name {
    public final String firstName;
    public final String secondName;

    public Name(String firstName, String secondName) {
        this.firstName = firstName;
        this.secondName = secondName;
    }

    @Override
    public String toString() {
        return firstName + " " + secondName;
    }
}
