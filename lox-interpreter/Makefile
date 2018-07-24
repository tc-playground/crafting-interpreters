GROUP_ID := "com.craftinginterpreters.lox"
ARTIFACT_ID := "lox-interpreter"

.PHONY: build
build:
	mvn package

.PHONY: test
test: build
	mvn test

.PHONY: run
run:
	mvn exec:java -Dexec.mainClass="com.craftinginterpreters.lox.Lox" -Dexec.args="$(ARGS)"

.PHONY: clean
clean:
	mvn clean

