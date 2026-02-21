package rsp.compositions.auth;

import rsp.component.ComponentContext;
import rsp.component.Lookup;
import rsp.compositions.contract.ViewContract;

/**
 * Minimal ViewContract for the login page.
 */
public class LoginContract extends ViewContract {

    public LoginContract(Lookup lookup) {
        super(lookup);
    }

    @Override
    public String title() {
        return "Sign In";
    }

    @Override
    public ComponentContext enrichContext(ComponentContext context) {
        return context;
    }
}
