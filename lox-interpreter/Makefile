GROUP_ID := "com.craftinginterpreters.lox"
ARTIFACT_ID := "lox-interpreter"

AST_ARGS := "src/main/java/com/craftinginterpreters/lox"

.PHONY: generate
generate:
	mvn exec:java -Dexec.mainClass="com.craftinginterpreters.tool.GenerateAst" -Dexec.args="$(AST_ARGS)"

.PHONY: build
build: generate
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

