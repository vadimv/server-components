## The goal
A working prototype of a CRUD web admin-like UI prototype for Posts and one "admin" user.

## Steps

### Create a service for Posts e.g. something like
- List<Post> findAll(int page)
- int create(Post post)
- boolean update(int id, Post post)

The server should be a self-containing Java class with hardcoded test Posts with titles and content e.g. Lorem ipsum etc

### Implement a stub auth layer similar to the PCKE prototype without external dependencies allowing test the login of an "admin" user

### Implement a working web SPA app where
    - an "admin" user can sign-n
    - a user can sign-out
    - when the user is logged-in shouing a text "hello admin"

### Implement core classes/interface in the rsp.compositions package (there are some classes files already added)

### Create concrete implementation of PostContract and the Module

### Implement very simple Layout and a basic css 

### Try to assemble a working prototype application