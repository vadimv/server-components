package rsp.examples.crud.services;

import rsp.examples.crud.entities.Principal;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Auth {
    public CompletableFuture<Optional<Principal>> authenticate(String login, String password) {
        return "admin".equals(login) && "admin".equals(password) ? principal(login, password) : CompletableFuture.completedFuture(Optional.empty());
    }

    private CompletableFuture<Optional<Principal>> principal(String login, String password) {
        return CompletableFuture.completedFuture(Optional.of(new Principal(login)));
    }
}
