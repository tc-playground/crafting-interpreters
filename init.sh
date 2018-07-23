#!/bin/bash


function maven-generate-lox-interpreter() {
    local group_id="com.craftinginterpreters"
    local artifact_id="lox-interpreter"
    local java_version="1.10"

    # Generate project.
	mvn archetype:generate \
	  -DarchetypeArtifactId=maven-archetype-quickstart \
	  -DinteractiveMode=false \
	  -DgroupId=${group_id} \
	  -DartifactId=${artifact_id}
    pushd ${artifact_id}

    # Generate new POM.
    cat > pom.xml <<EOF
<project 
    xmlns="http://maven.apache.org/POM/4.0.0" 
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
    http://maven.apache.org/maven-v4_0_0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${group_id}</groupId>
  <artifactId>${artifact_id}</artifactId>
  <packaging>jar</packaging>
  <version>1.0-SNAPSHOT</version>
  <name>${artifact_id}</name>
  <url>http://maven.apache.org</url>
  <properties>
    <maven.compiler.source>${java_version}</maven.compiler.source>
    <maven.compiler.target>${java_version}</maven.compiler.target>
  </properties>
  <dependencies>
    <dependency>
      <groupId>junit</groupId>
      <artifactId>junit</artifactId>
      <version>3.8.1</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
</project>
EOF

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
fi
