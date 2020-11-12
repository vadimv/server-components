package rsp.examples.crud.entities;

public class Name {
    public final String firstName;
    public final String secondName;

    public Name(String firstName, String secondName) {
        this.firstName = firstName;
        this.secondName = secondName;
    }

    public static Name of(String str) {
        final String[] tokens = str.split(" ");
        return new Name(tokens[0], tokens.length > 1 ? tokens[1] : "");
    }

    @Override
    public String toString() {
        return firstName + " " + secondName;
    }
}
