#!/bin/bash


function maven-generate-lox-interpreter() {
    local group_id="com.craftinginterpreters.lox"
    local artifact_id="lox-interpreter"

    # Generate project.
	mvn archetype:generate \
	  -DarchetypeArtifactId="archetype-quickstart-jdk8" \
    -DarchetypeGroupId="com.github.ngeor" \
	  -DinteractiveMode=false \
	  -DgroupId=${group_id} \
	  -DartifactId=${artifact_id}
    pushd ${artifact_id}

    # Generate Makefile
    local tab=$(echo -e [\\t])
    cat > Makefile <<EOF
GROUP_ID := "${group_id}"
ARTIFACT_ID := "${artifact_id}"

.PHONY: build
build:
	mvn package

.PHONY: test
test: build
	mvn test

.PHONY: run
run:
	mvn exec:java -Dexec.mainClass="${group_id}".App

.PHONY: clean
clean:
	mvn clean

EOF

    # Reset directory.
    make test
    popd
}

# Main ************************************************************************
#

# If provided, execute the specified function.
if [ ! -z "$1" ]; then
  $1
else
  maven-generate-lox-interpreter
fi
