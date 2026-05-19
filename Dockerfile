# Step 1: Use the official Maven image with Java 25
FROM maven:3.9-eclipse-temurin-25

# Step 2: Set the working directory inside the container
WORKDIR /app

# Step 3: Copy the entire project (all modules) into the container
COPY . .

# Step 4: Build the project and install all modules locally inside the container
# We skip tests to make the deployment process significantly faster
RUN mvn clean install -DskipTests

# Step 5: Expose port 8085 (the default port for the CrudApp demo)
EXPOSE 8085

# Step 6: Run the Admin Panel demo exactly as specified in the docs
CMD ["mvn", "exec:java", "-pl", "examples", "-Dexec.mainClass=rsp.app.posts.CrudApp"]