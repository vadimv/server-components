package rsp.compositions.block;

/** Identifies whether a schema-driven form creates or edits an entity. */
public enum FormMode {
    CREATE,
    EDIT;

    public boolean isCreate() {
        return this == CREATE;
    }
}
