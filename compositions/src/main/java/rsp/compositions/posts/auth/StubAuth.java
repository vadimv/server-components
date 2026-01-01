package rsp.compositions.posts.auth;

import java.util.Optional;

public class StubAuth {
    private static final String ADMIN_USER = "admin";
    
    public boolean authenticate(String username, String password) {
        return ADMIN_USER.equals(username) && "admin".equals(password);
    }
    
    public Optional<String> user() {
        // In a real app, this would check the session/context
        return Optional.of(ADMIN_USER); 
    }
}
