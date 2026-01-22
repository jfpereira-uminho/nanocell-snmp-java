#!/usr/bin/env bash

# It enables Bash “fail fast” mode: exit on errors, on unset variables, and on failures inside pipelines.
set -euo pipefail

# Bash parameter expansion that both reads the first argument and enforces it exists.
GROUP_ID="${1:?Usage: $0 <groupId>  e.g. pt.uminho.gvr}"
ARTIFACT_ID="nanocell-snmp4j-agent"
VERSION="1.0"

# Convert groupId -> path (pt.uminho.gvr -> pt/uminho/gvr)
PKG_PATH="${GROUP_ID//./\/}"

# 1) Generate Maven skeleton
mvn -B archetype:generate \
  -DarchetypeGroupId=org.apache.maven.archetypes \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DarchetypeVersion=1.4 \
  -DgroupId="${GROUP_ID}" \
  -DartifactId="${ARTIFACT_ID}" \
  -Dversion="${VERSION}" \
  -Dpackage="${GROUP_ID}"

cd "${ARTIFACT_ID}"

# 2) Set project structure
rm -rf src/test
rm -f "src/main/java/${PKG_PATH}/App.java"
mkdir -p "src/main/java/${PKG_PATH}/agent"
mkdir -p "src/main/java/${PKG_PATH}/mobility"
mkdir -p src/main/resources/config

# 3) Write pom.xml (NOTE: no quotes on XML => variables expand)
cat > pom.xml <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>${GROUP_ID}</groupId>
  <artifactId>${ARTIFACT_ID}</artifactId>
  <version>${VERSION}</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.snmp4j</groupId>
      <artifactId>snmp4j</artifactId>
      <version>3.9.6</version>
    </dependency>

    <dependency>
      <groupId>org.snmp4j</groupId>
      <artifactId>snmp4j-agent</artifactId>
      <version>3.8.3</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.6.0</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>${GROUP_ID}.Agent</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
XML

# 4) generate the initial configs
cd src/main/resources/config
cat > nanocell_mib_config.json <<JSON
{
  "totalUEs": 100,
  "mobilityIntervalTicks": 900,
  "cells": [
    {
      "cellId": 1,
      "cellName": "Cell-A",
      "cellMaxUsers": 30,
      "cellCurrentUsers": 30
    },
    {
      "cellId": 2,
      "cellName": "Cell-B",
      "cellMaxUsers": 50,
      "cellCurrentUsers": 40
    },
    {
      "cellId": 3,
      "cellName": "Cell-C",
      "cellMaxUsers": 40,
      "cellCurrentUsers": 30
    }
  ],
  "neighbors": [
    {
      "sourceCellId": 1,
      "neighborCellId": 2,
      "neighborWeight": 3
    },
    {
      "sourceCellId": 1,
      "neighborCellId": 3,
      "neighborWeight": 1
    },
    {
      "sourceCellId": 2,
      "neighborCellId": 1,
      "neighborWeight": 2
    },
    {
      "sourceCellId": 2,
      "neighborCellId": 3,
      "neighborWeight": 2
    },
    {
      "sourceCellId": 3,
      "neighborCellId": 1,
      "neighborWeight": 1
    },
    {
      "sourceCellId": 3,
      "neighborCellId": 2,
      "neighborWeight": 3
    }
  ]
}
JSON

# 5) instructions
echo "Now run AgentPro to generate Agent.java, NanocellMib.java, Modules.java into src/main/java/${PKG_PATH}/agent (package ${GROUP_ID})"
echo "AgentConfig.properties should go to src/main/resources/"
echo "You should code the NanocellMibAdapter.java into src/main/java/${PKG_PATH}/agent"
echo "And the MobilityEngine.java src/main/java/${PKG_PATH}/mobility"

