# Nano-Cell SNMP Agent Tutorial

A step-by-step guide to designing a MIB and implementing an SNMPv2c agent in Java using SNMP4J.

---
## How to Navigate This Tutorial

This tutorial is divided into **steps**, each corresponding to a Git branch. As you progress, switching branches reveals the files covered in that step. The README.md remains the same across all branches, containing the complete tutorial content.

**How to follow along:**
1. Use this GitHub page to read the instructions for each step.
2. Replicate the work on your local machine.
3. At the end of each step, click the link to switch to the next branch and **refresh the page (F5)** to reveal the new files.

> **Tip:** While you can copy the file contents directly, take time to understand what each file does.

**Optinal: Local navigation (after cloning the repo):**

```bash
# List all available branches
git branch -vva
# Switch to a specific branch
git switch <branch-name>
```


---

## Table of Contents

- [Step 1: Introduction and MIB Design](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/main/README.md#step-1-introduction-and-mib-design)
- [Step 2: Project Bootstrap and Structure](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step2/README.md#step-2-project-bootstrap-and-structure)
- [Step 3: Code Generation with AgentPro](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step3/README.md#step-3-code-generation-with-agentpro)
- [Step 4: Java Packages and the MIB Helper](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step4/README.md#step-4-java-packages-and-the-mib-helper)
- [Step 5: The Mobility Engine](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step5/README.md#step-5-the-mobility-engine)
- [Step 6: Customizing the Agent](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step6/README.md#step-6-customizing-the-agent)
- [Step 7: Building and Testing](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step6/README.md#step-7-building-and-testing)

---

## Step 1: Introduction and MIB Design

### Goals and Objectives

**Primary Goal:**
- Learn how to design a MIB (Management Information Base) and implement an SNMPv2c agent in Java using the SNMP4J library.

**Secondary Goals:**
- Understand how to use Maven as a build tool for Java projects.
- Learn how to properly structure a Java project with packages and separation of concerns.

### What You Will Build

In this tutorial, you will build a fully functional SNMP agent that simulates a small **radio access network** with multiple nano-cells (simplified base stations). The agent will:

1. Expose network information via SNMP (cells, neighbors, user counts, handover statistics).
2. Simulate user mobility between cells in real-time.
3. Allow network managers to query and monitor the network state using standard SNMP tools.

### The Problem Context

Imagine a simplified mobile network with a small set of **nano-cells**, each acting as a base station. User devices (UEs - User Equipment) are distributed across these cells and periodically move between neighboring cells (this is called a **handover**).

Key characteristics of our simulation:

| Concept | Description |
|---------|-------------|
| **Total UEs** | A fixed number of user devices in the system (never changes) |
| **Cells** | Base stations with names, capacity limits, and dynamic counters |
| **Neighbors** | Directed relations between cells with probability weights |
| **Mobility** | Periodic movement of users based on neighbor weights |

**Important**: The MIB only exposes **aggregated per-cell information** (number of users, handover counters). Individual users are not visible via SNMP; they exist only inside the agent's internal model.

### Requirements

Before starting, make sure you have the following installed:

| Requirement | Version | Purpose |
|-------------|---------|---------|
| **Java JDK** | 17 or higher | Compiling and running the agent |
| **Maven** | 3.6 or higher | Building the project and managing dependencies |
| **AgentPro** | Latest | Generating SNMP4J code from MIB definitions |
| **net-snmp** | Any recent | Testing the agent with `snmpwalk`, `snmpget`, etc. |

### Understanding the MIB Design

The MIB is defined in `mibs/NANOCELL-MIB.mib` using ASN.1 notation. Let's understand its structure:

#### OID Tree Structure

```
enterprises (1.3.6.1.4.1)
    └── 88888 (nanoCellMib)
        ├── 1 (nanoCellMibObjects)
        │   ├── 1 (cellObjects)
        │   │   ├── 1 (totalUEs)           -- Scalar: total users in system
        │   │   ├── 2 (mobilityInterval)   -- Scalar: time between mobility steps
        │   │   └── 3 (cellTable)          -- Table: list of cells
        │   │       └── 1 (cellEntry)
        │   │           ├── 1 (cellId)                -- INDEX
        │   │           ├── 2 (cellName)              -- DisplayString
        │   │           ├── 3 (cellMaxUsers)          -- Unsigned32
        │   │           ├── 4 (cellCurrentUsers)      -- Gauge32
        │   │           ├── 5 (cellHandoversInTotal)  -- Counter32
        │   │           └── 6 (cellHandoversOutTotal) -- Counter32
        │   └── 2 (neighborObjects)
        │       └── 1 (cellNeighborTable)  -- Table: neighbor relations
        │           └── 1 (cellNeighborEntry)
        │               ├── 1 (sourceCellId)    -- INDEX part 1
        │               ├── 2 (neighborCellId)  -- INDEX part 2
        │               └── 3 (neighborWeight)  -- Unsigned32 (read-write)
        └── 2 (nanoCellMibConformance)
            └── ... (compliance and groups)
```

#### Scalars

| Object | Type | Access | Description |
|--------|------|--------|-------------|
| `totalUEs` | Unsigned32 | read-only | Total number of UEs in the system (constant) |
| `mobilityInterval` | TimeTicks | read-write | Time between mobility updates (1/100 seconds) |

The `mobilityInterval` is expressed in **hundredths of a second**. For example, a value of `500` means 5 seconds between mobility steps.

#### Cell Table (`cellTable`)

Each row represents one cell (base station):

| Column | Type | Access | Description |
|--------|------|--------|-------------|
| `cellId` | Unsigned32 | not-accessible | Unique cell identifier (INDEX) |
| `cellName` | DisplayString | read-only | Human-readable cell name |
| `cellMaxUsers` | Unsigned32 | read-only | Maximum capacity |
| `cellCurrentUsers` | Gauge32 | read-only | Current number of users |
| `cellHandoversInTotal` | Counter32 | read-only | Total handovers into this cell |
| `cellHandoversOutTotal` | Counter32 | read-only | Total handovers out of this cell |

**Why Gauge32 vs Counter32?**
- `Gauge32` is used for values that can go up or down (like current users).
- `Counter32` is used for monotonically increasing values (like totals that only grow).

#### Neighbor Table (`cellNeighborTable`)

Each row represents a **directed** neighbor relation:

| Column | Type | Access | Description |
|--------|------|--------|-------------|
| `sourceCellId` | Unsigned32 | not-accessible | Source cell (INDEX part 1) |
| `neighborCellId` | Unsigned32 | not-accessible | Neighbor cell (INDEX part 2) |
| `neighborWeight` | Unsigned32 | read-write | Probability weight for mobility |

**Why directed relations?**
Neighbor relationships are **not symmetric**. Cell A might send users to Cell B with weight 3, but Cell B might send users to Cell A with weight 1. This is why we need two separate entries for bidirectional connectivity.

### Getting Started

Clone this repository and switch to the next step:

```bash
git clone https://github.com/jfpereira-uminho/nanocell-snmp-java.git
cd nanocell-snmp-java
```

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 2: Project Bootstrap and Structure >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step2/README.md#step-2-project-bootstrap-and-structure)**

---
<br><br><br><br><br><br><br><br><br><br>

## Step 2: Project Bootstrap and Structure

In this step, you will generate the Maven project structure using the provided bootstrap script and understand the project layout.

### Running the Bootstrap Script

The `project_bootstart.sh` script generates a complete Maven project skeleton. 

**Run it with your desired Java package name**:

```bash
sh ./project_bootstart.sh pt.uminho.gvr
```

This script performs the following actions:

1. **Creates a Maven project** using the `maven-archetype-quickstart` archetype.
2. **Sets up the directory structure** with separate packages for `agent` and `mobility` code.
3. **Generates `pom.xml`** with SNMP4J dependencies.
4. **Creates the initial configuration file** `nanocell_mib_config.json`.

### Understanding the Project Structure

After running the script, you will have the following structure, **the java files will be added later in this tutorial**:

```
nanocell-snmp4j-agent/
├── pom.xml                          # Maven build configuration
└── src/
    └── main/
        ├── java/
        │   └── pt/uminho/gvr/
        │       ├── agent/           # SNMP agent code (generated + custom)
        │       │   ├── Agent.java
        │       │   ├── Modules.java
        │       │   ├── NanocellMib.java
        │       │   └── NanocellMibHelper.java
        │       └── mobility/        # Mobility simulation logic
        │           └── MobilityEngine.java
        └── resources/
            ├── config/
            │   └── nanocell_mib_config.json    # Initial MIB data
            └── AgentConfig.properties           # SNMP agent configuration
```

### Understanding the `pom.xml`

The `pom.xml` file defines the Maven project configuration. Let's examine its key sections:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" ...>
  <modelVersion>4.0.0</modelVersion>

  <groupId>pt.uminho.gvr</groupId>
  <artifactId>nanocell-snmp4j-agent</artifactId>
  <version>1.0</version>

  <properties>
    <maven.compiler.release>17</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
```

**Properties:**
- `maven.compiler.release`: Specifies Java 17 as the target version.
- `project.build.sourceEncoding`: Uses UTF-8 encoding for source files.

#### Dependencies

```xml
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
```

| Dependency | Purpose |
|------------|---------|
| `snmp4j` | Core SNMP protocol implementation (PDUs, transport, encoding) |
| `snmp4j-agent` | Agent framework for building SNMP agents with managed objects |

#### Adding Jackson for JSON Parsing

The bootstrap script creates a basic `pom.xml`. You need to add the Jackson library to parse the JSON configuration file. Add this dependency inside the `<dependencies>` section:

```xml
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.2</version>
    </dependency>
```

**Why Jackson?**
Jackson is a popular Java library for JSON processing. We use it to:
- Read the initial MIB configuration from `nanocell_mib_config.json`.
- Deserialize JSON into Java objects (records).

#### Build Configuration

```xml
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
                <transformer implementation="...ManifestResourceTransformer">
                  <mainClass>pt.uminho.gvr.agent.Agent</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
```

**Maven Shade Plugin:**
This plugin creates a **fat JAR** (also called uber-JAR) that includes all dependencies. This means:
- You get a single executable JAR file.
- No need to manage classpath or external libraries at runtime.
- The `mainClass` configuration sets the entry point for `java -jar`.

### Understanding the JSON Configuration

The file `src/main/resources/config/nanocell_mib_config.json` contains the initial state of the MIB:

```json
{
  "totalUEs": 100,
  "mobilityIntervalTicks": 200,
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
    { "sourceCellId": 1, "neighborCellId": 2, "neighborWeight": 3 },
    { "sourceCellId": 1, "neighborCellId": 3, "neighborWeight": 1 },
    { "sourceCellId": 2, "neighborCellId": 1, "neighborWeight": 2 },
    { "sourceCellId": 2, "neighborCellId": 3, "neighborWeight": 2 },
    { "sourceCellId": 3, "neighborCellId": 1, "neighborWeight": 1 },
    { "sourceCellId": 3, "neighborCellId": 2, "neighborWeight": 3 }
  ]
}
```

**Configuration Breakdown:**

| Field | Value | Meaning |
|-------|-------|---------|
| `totalUEs` | 100 | Total users distributed across all cells |
| `mobilityIntervalTicks` | 200 | 2 seconds between mobility steps (200 × 10ms) |
| `cells` | Array | Initial cell definitions with current user counts |
| `neighbors` | Array | Neighbor relations with probability weights |


Notice that:
- Cell-A has 30 users (at max capacity).
- Cell-B has 40 users (with room for 10 more).
- Cell-C has 30 users (with room for 10 more).
- Total: 30 + 40 + 30 = 100 users (matches `totalUEs`).

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 3: Code Generation with AgentPro >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step3/README.md#step-3-code-generation-with-agentpro)**

---
<br><br><br><br><br><br><br><br><br><br>

## Step 3: Code Generation with AgentPro

AgentPro is a tool that generates Java code from MIB definitions. It creates the boilerplate code needed for SNMP4J to handle SNMP requests for your custom MIB objects.

### Setting Up AgentPro

#### 1. Set the MIB Repository

First, configure AgentPro to use a MIB repository that contains standard MIB definitions:

1. Go to **File → Set Repository...**
2. Select the repository directory: `mibrepo-2023-07`

This repository contains standard MIBs like SNMPv2-SMI, SNMPv2-TC, etc., which our MIB imports from.

#### 2. Import Your MIB

Import your custom MIB into AgentPro:

1. Go to **File → Import MIB File...**
2. Navigate to and select: `<path_to>/nanocell-snmp-java/mibs/NANOCELL-MIB.mib`

### Creating a New Project

1. Go to **Project → New**
2. Set the **Templates Root Directory** to: `<path_to>/agentPro/templates/snmp4j-agent-v3/`
3. Leave **In-/Output Root Directory** empty

### Configuring Generation Jobs

You need to create four jobs to generate all the necessary files. Click **Add New** for each job:

#### Job 1: MIB Code Generation

This job generates the `NanocellMib.java` file containing the MIB object definitions.

| Setting | Value |
|---------|-------|
| Execution Type | By Selection |
| Generation Template | `java_code.vm` |
| File Name Template | `java_filename.vm` |
| Input Directory | (empty) |
| Output Directory | `<path_to>/nanocell-snmp-java/nanocell-snmp4j-agent/src/main/java/pt/uminho/gvr/agent` |
| Selection Template | `select_1module1file.vm` |

#### Job 2: Modules Generation

This job generates the `Modules.java` file that registers MIB objects with the SNMP4J agent.

| Setting | Value |
|---------|-------|
| Execution Type | By Selection |
| Generation Template | `java_init_code.vm` |
| File Name Template | `java_init_filename.vm` |
| Input Directory | (empty) |
| Output Directory | `<path_to>/nanocell-snmp-java/nanocell-snmp4j-agent/src/main/java/pt/uminho/gvr/agent` |
| Selection Template | `select_1module1file.vm` |

#### Job 3: Agent Main Class Generation

This job generates the `Agent.java` file with the main entry point.

| Setting | Value |
|---------|-------|
| Execution Type | By Selection |
| Generation Template | `java_agent_main.vm` |
| File Name Template | `java_agent_main_filename.vm` |
| Input Directory | (empty) |
| Output Directory | `<path_to>/nanocell-snmp-java/nanocell-snmp4j-agent/src/main/java/pt/uminho/gvr/agent` |
| Selection Template | `select_1module1file.vm` |

#### Job 4: Properties File Generation

This job generates the `AgentConfig.properties` file with SNMP configuration.

| Setting | Value |
|---------|-------|
| Execution Type | By Selection |
| Generation Template | `properties_agentconfig.vm` |
| File Name Template | `properties_agentconfig_filename.vm` |
| Input Directory | (empty) |
| Output Directory | `<path_to>/nanocell-snmp-java/nanocell-snmp4j-agent/src/main/resources` |
| Selection Template | `select_1module1file.vm` |

### Selecting MIBs to Process

1. Click **Next** through the wizard pages
2. When asked about MIBs to process, **do NOT** select "Use all MIB modules available"
3. Select only: **NANOCELL-MIB**
4. Click **Finish**

### Generating the Code

Go to **Project → Generate...** to generate all the files.

### Understanding the Generated Files

AgentPro generates several files:

| File | Purpose |
|------|---------|
| `NanocellMib.java` | Contains OID definitions, table structures, row classes, and validators |
| `Modules.java` | Factory class that creates and registers MIB objects |
| `Agent.java` | Main entry point with SNMP server setup |
| `AgentConfig.properties` | SNMP configuration (VACM, communities, etc.) |

### Fixing the Generated Properties File

The generated `AgentConfig.properties` file contains a lot of configuration for SNMPv3, which we don't need. The repository contains a simplified version optimized for SNMPv2c only.

**Original Generated File** (`AgentConfig.prop.bk`):
- Contains SNMPv3 USM user configurations
- Multiple security groups and views
- Notification targets and filters

**Simplified Version** (`AgentConfig.properties`):

```properties
# Minimal SNMPv2c-only configuration for the nano-cell agent.

snmp4j.agent.cfg.contexts=

# sysObjectID, contact, location, services
snmp4j.agent.cfg.oid.1.3.6.1.2.1.1.2.0={o}1.3.6.1.4.1.88888
snmp4j.agent.cfg.oid.1.3.6.1.2.1.1.4.0={s}System Administrator
snmp4j.agent.cfg.oid.1.3.6.1.2.1.1.6.0={s}<edit location>
snmp4j.agent.cfg.oid.1.3.6.1.2.1.1.7.0={i}72

# VACM: map community "public" to group v1v2cgroup
snmp4j.agent.cfg.oid.1.3.6.1.6.3.16.1.2.1=1:3
snmp4j.agent.cfg.index.1.3.6.1.6.3.16.1.2.1.0={o}2.6.'public'
snmp4j.agent.cfg.value.1.3.6.1.6.3.16.1.2.1.0.0={s}v1v2cgroup
snmp4j.agent.cfg.value.1.3.6.1.6.3.16.1.2.1.0.1={i}4
snmp4j.agent.cfg.value.1.3.6.1.6.3.16.1.2.1.0.2={i}1

# VACM access: v1v2cgroup has unrestricted views
...

# VACM views: allow everything under our enterprise OID
snmp4j.agent.cfg.index.1.3.6.1.6.3.16.1.5.2.1.0={o}20.'unrestrictedReadView'.7.1.3.6.1.4.1.88888
...

# Community MIB: define community "public"
snmp4j.agent.cfg.oid.1.3.6.1.6.3.18.1.1.1=1:7
snmp4j.agent.cfg.index.1.3.6.1.6.3.18.1.1.1.0={o}'public'
...
```

**Key Configuration Sections:**

1. **System Group** (OID 1.3.6.1.2.1.1):
   - `sysObjectID`: Our enterprise OID (1.3.6.1.4.1.88888)
   - `sysContact`, `sysLocation`, `sysServices`: Standard system information

2. **VACM (View-based Access Control Model)**:
   - Maps the community string `public` to a security group
   - Defines read and write views for our MIB subtree

3. **Community MIB**:
   - Defines the `public` community string for SNMPv2c access

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 4: Java Packages and the MIB Helper >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step4/README.md#step-4-java-packages-and-the-mib-helper)**

---

<br><br><br><br><br><br><br><br><br><br>

## Step 4: Java Packages and the MIB Helper

In this step, you will understand the importance of Java packages and learn about the `NanocellMibHelper` class that provides a clean API for MIB access.

### Adding Package Declarations

AgentPro generates files without `package` statements. You must add them manually to each generated file.

Add this line at the top of each Java file in `src/main/java/pt/uminho/gvr/agent/`:

```java
package pt.uminho.gvr.agent;
```

**Why are packages important in Java?**

1. **Namespace Organization**: Packages prevent naming conflicts. Two classes with the same name can coexist in different packages.

2. **Access Control**: Java's access modifiers (`public`, `protected`, `private`, default) work with packages to control visibility.

3. **Logical Grouping**: Related classes are grouped together, making the codebase easier to navigate.

4. **Convention**: The standard naming convention uses reverse domain names (e.g., `pt.uminho.gvr`) to ensure uniqueness.

### Understanding `NanocellMibHelper`

The generated `NanocellMib.java` file is complex and exposes low-level SNMP4J APIs (OIDs, Variables, table models). The `NanocellMibHelper` class provides a **clean abstraction layer** that hides this complexity.

**Benefits of the Helper Pattern:**

| Aspect | Direct MIB Access | With Helper |
|--------|-------------------|-------------|
| **OIDs** | Must know exact OID values | Methods with meaningful names |
| **Types** | Work with SNMP4J Variable types | Work with Java primitives |
| **Thread Safety** | Manual synchronization | Built-in synchronization |
| **Readability** | Complex, OID-heavy code | Clean, self-documenting API |

### `NanocellMibHelper` API

Let's examine the key parts of the helper class:

#### Snapshot Classes

```java
public static final class CellSnapshot {
    public final int cellId;
    public final String cellName;
    public final long cellMaxUsers;
    public final long cellCurrentUsers;
    public final long cellHandoversInTotal;
    public final long cellHandoversOutTotal;
    // ... constructor
}

public static final class NeighSnapshot {
    public final int sourceCellId;
    public final int neighborCellId;
    public final long weight;
    // ... constructor
}
```

These are **immutable snapshot classes** that represent the current state of a cell or neighbor relation. They use primitive Java types instead of SNMP4J types.

**Why snapshots?**
- Thread-safe: Once created, they cannot be modified.
- No coupling: The mobility engine will work with copies, not direct MIB references.
- Clear semantics: Read operations return snapshots, write operations use the `moveUEs` method.

#### Configuration Loading

```java
private record Config(long totalUEs,
                      long mobilityIntervalTicks,
                      List<CellConfig> cells,
                      List<NeighborConfig> neighbors) {}

private void loadInitialConfig() {
    try (InputStream in = NanocellMibHelper.class
            .getResourceAsStream(CONFIG_RESOURCE)) {
        Config config = MAPPER.readValue(in, Config.class);
        seedFromConfig(config);
    } catch (IOException e) {
        throw new IllegalStateException("Failed to load initial config", e);
    }
}
```

The helper uses **Java Records** (introduced in Java 16) to define immutable data classes for JSON deserialization. Jackson automatically maps JSON properties to record components.

#### Read Operations

```java
public synchronized List<CellSnapshot> getCells() {
    List<CellSnapshot> cells = new ArrayList<>();
    MOTableModel<NanocellMib.CellEntryRow> model =
        (MOTableModel<NanocellMib.CellEntryRow>)
            nanocellMib.getCellEntry().getModel();
    Iterator<NanocellMib.CellEntryRow> it = model.iterator();
    while (it.hasNext()) {
        NanocellMib.CellEntryRow row = it.next();
        int cellId = row.getIndex().get(0);
        cells.add(new CellSnapshot(
            cellId,
            row.getCellName().toString(),
            row.getCellMaxUsers().getValue(),
            // ... other fields
        ));
    }
    return cells;
}
```

Notice:
- **`synchronized`**: Ensures thread-safe access to the MIB.
- **Iteration**: Uses the SNMP4J table model iterator.
- **Type conversion**: Converts SNMP4J types to Java primitives.

#### Write Operation

```java
public synchronized void moveUEs(int sourceCellId, int destinationCellId, int count) {
    if (count <= 0 || sourceCellId == destinationCellId) {
        return;
    }

    // Get the source and destination rows
    NanocellMib.CellEntryRow src = model.getRow(new OID(new int[]{sourceCellId}));
    NanocellMib.CellEntryRow dst = model.getRow(new OID(new int[]{destinationCellId}));

    // Validate
    if (src == null || dst == null) {
        LOGGER.warn("Cannot move UEs, missing cells");
        return;
    }

    // Check capacity
    long capacityLeft = Math.max(0, dstCap - dstUsers);
    if (capacityLeft < count) {
        LOGGER.error("Can't move, cell is full");
        return;
    }

    // Update counters atomically
    src.setCellCurrentUsers(new Gauge32(srcUsers - count));
    src.setCellHandoversOutTotal(new Counter32(outTotal + count));
    dst.setCellCurrentUsers(new Gauge32(dstUsers + count));
    dst.setCellHandoversInTotal(new Counter32(inTotal + count));
}
```

This method:
1. Validates inputs (count > 0, different cells).
2. Checks that both cells exist.
3. Verifies the destination has capacity.
4. Updates all four counters atomically (within the synchronized block).

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 5: The Mobility Engine >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step5/README.md#step-5-the-mobility-engine)**

---

<br><br><br><br><br><br><br><br><br><br>

## Step 5: The Mobility Engine

The `MobilityEngine` class implements the simulation logic that periodically moves users between cells.

### Understanding the Algorithm

The mobility algorithm works as follows:

1. **Build Structure**: Create a working copy of all cells and their neighbor relations.
2. **Sort Cells**: Order cells by available capacity (cells with more free space first).
3. **For Each Cell**:
   - Select a random percentage (5-30%) of current users to move.
   - Distribute those users among neighbors based on weights.
   - Respect capacity limits.
4. **Sleep**: Wait for `mobilityInterval` before the next step.

### Code Walkthrough

#### Class Structure

```java
public class MobilityEngine implements Runnable {
    private static final double MOVE_RATIO_MAX = 30.0; // %
    private static final double MOVE_RATIO_MIN = 5.0;  // %

    private final NanocellMibHelper mibHelper;
    private volatile boolean running = true;

    public MobilityEngine(NanocellMibHelper mibHelper) {
        this.mibHelper = mibHelper;
    }

    public void stop() {
        running = false;
    }
```

Key points:
- Implements `Runnable` so it can run in its own thread.
- Uses `volatile` for the `running` flag to ensure thread visibility.
- Takes `NanocellMibHelper` as a dependency (dependency injection).

#### Main Loop

```java
@Override
public void run() {
    LOGGER.debug("Mobility engine started");
    while (running) {
        try {
            List<AuxCell> cellsList = buildStructure();
            if (!cellsList.isEmpty()) {
                // Sort by capacity delta (most free space first)
                cellsList.sort(Comparator
                    .comparingLong(AuxCell::capDelta)
                    .thenComparing((AuxCell c) -> c.cellCurrentUsers,
                                   Comparator.reverseOrder()));
                performMobilityStep(cellsList);
            }
        } catch (Exception e) {
            LOGGER.error("Mobility step failed", e);
        }

        // Sleep for the configured interval
        long sleepMs = Math.max(0L, mibHelper.getMobilityIntervalTicks() * 10L);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
        }
    }
    LOGGER.info("Mobility engine stopped");
}
```

**Why multiply by 10?**
`TimeTicks` are in hundredths of a second (1/100s), but `Thread.sleep()` takes milliseconds. So we multiply by 10 to convert: `200 ticks × 10 = 2000 ms = 2 seconds`.

#### Building the Working Structure

```java
private List<AuxCell> buildStructure() throws Exception {
    List<AuxCell> aux = new ArrayList<>();
    List<NanocellMibHelper.CellSnapshot> cellSnapshots = mibHelper.getCells();
    List<NanocellMibHelper.NeighSnapshot> neighs = mibHelper.getNeighs();

    for (NanocellMibHelper.CellSnapshot cellSnap : cellSnapshots) {
        AuxCell cellAux = new AuxCell(cellSnap);
        cellAux.computeWeights(filterNeighBySrc(cellAux.cellId, neighs));
        aux.add(cellAux);
    }
    return aux;
}
```

This creates `AuxCell` objects that:
- Copy the current state from MIB snapshots.
- Pre-compute normalized neighbor weights.
- Track capacity delta (free space).

#### Performing a Mobility Step

```java
private void performMobilityStep(List<AuxCell> cellsList) {
    for (AuxCell cell : cellsList) {
        // Random percentage between 5% and 30%
        double moveRatio = ThreadLocalRandom.current()
            .nextDouble(MOVE_RATIO_MIN, MOVE_RATIO_MAX) / 100;
        double totalUesToMove = cell.cellCurrentUsers * moveRatio;

        // Distribute among neighbors based on weights
        for (Map.Entry<Integer, Double> entry : cell.computedWeights.entrySet()) {
            long uesToMove = Math.round(totalUesToMove * entry.getValue());
            AuxCell neighbor = getCellById(entry.getKey(), cellsList);

            // Respect capacity limits
            if (uesToMove > neighbor.capacityDelta) {
                uesToMove = neighbor.capacityDelta;
            }

            // Skip if nothing to move or would overdraw
            if (uesToMove <= 0 || cell.cellCurrentUsers - uesToMove < 0) {
                continue;
            }

            // Actually move the users
            this.mibHelper.moveUEs(cell.cellId, neighbor.cellId, (int)uesToMove);
            cell.removeUEs(uesToMove);
            neighbor.addUEs(uesToMove);
        }
    }
}
```

#### The Auxiliary Cell Class

```java
private static final class AuxCell {
    public int cellId;
    public long cellMaxUsers;
    public long cellCurrentUsers;
    public long capacityDelta;
    public Map<Integer, Double> computedWeights;

    AuxCell(NanocellMibHelper.CellSnapshot snapshot) {
        this.cellId = snapshot.cellId;
        this.cellMaxUsers = snapshot.cellMaxUsers;
        this.cellCurrentUsers = snapshot.cellCurrentUsers;
        this.capacityDelta = snapshot.cellMaxUsers - snapshot.cellCurrentUsers;
        this.computedWeights = new HashMap<>();
    }

    public boolean computeWeights(List<NanocellMibHelper.NeighSnapshot> neighList) {
        double total_weight = 0.0;

        // Sum all weights
        for (NanocellMibHelper.NeighSnapshot neigh : neighList) {
            computedWeights.put(neigh.neighborCellId, (double)neigh.weight);
            total_weight += (double)neigh.weight;
        }

        // Normalize to percentages
        for (Map.Entry<Integer, Double> entry : computedWeights.entrySet()) {
            double val = entry.getValue() / total_weight;
            computedWeights.replace(entry.getKey(), val);
        }
        return true;
    }
}
```

**Weight Normalization Example:**

If Cell-A has neighbors:
- Cell-B with weight 3
- Cell-C with weight 1

Total weight = 4

Normalized weights:
- Cell-B: 3/4 = 0.75 (75%)
- Cell-C: 1/4 = 0.25 (25%)

So if 10 users are leaving Cell-A, approximately 7-8 go to Cell-B and 2-3 go to Cell-C.

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 6: Customizing the Agent >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step6/README.md#step-6-customizing-the-agent)**

---

<br><br><br><br><br><br><br><br><br><br>

## Step 6: Customizing the Agent

The AgentPro-generated `Agent.java` needs several modifications to integrate our custom components. Let's understand the differences between the generated version and our customized version.

### Key Modifications

#### 1. SNMPv2c-Only Message Processing

**Original (Agent.java.bk):**
```java
MessageDispatcher messageDispatcher = new MessageDispatcherImpl();
addListenAddresses(messageDispatcher, args.get("address"));
```

**Modified (Agent.java):**
```java
MessageDispatcher messageDispatcher = new MessageDispatcherImpl();
messageDispatcher.addMessageProcessingModel(new MPv2c());
addListenAddresses(messageDispatcher, args.get("address"));
```

By explicitly adding only `MPv2c` (Message Processing Model for SNMPv2c), we ensure the agent:
- Only handles SNMPv2c requests.
- Doesn't waste resources on SNMPv1 or SNMPv3 processing.
- Has a cleaner, more focused implementation.

#### 2. Removing Unused Configuration

**Original:**
```java
private String configFile;
// ...
configFile = (String)args.get("c").get(0);
// ...
new DefaultMOPersistenceProvider(moServers, configFile)
```

**Modified:**
```java
// Removed configFile - we don't persist MIB state
// ...
null  // No persistence provider
```

Since our agent loads configuration from JSON at startup and doesn't need to persist state between sessions, we remove the file-based persistence.

#### 3. Fixing Resource Path

**Original:**
```java
Agent.class.getResourceAsStream("AgentConfig.properties");
```

**Modified:**
```java
Agent.class.getResourceAsStream("/AgentConfig.properties");
```

The leading `/` is important! Without it, Java looks for the resource relative to the class's package. With `/`, it looks from the classpath root.

#### 4. Adding New Fields

```java
private NanocellMibHelper mibHelper;
private MobilityEngine mobilityEngine;
private Thread mobilityThread;

private static final String MIB_CONFIG_RESOURCE = "/config/nanocell_mib_config.json";
```

These fields hold references to our custom components.

#### 5. New Initialization Methods

```java
public void run() {
    agent.initialize();
    registerMIBs();
    agent.setupProxyForwarder();
    agent.run();

    // NEW: Initialize our custom components
    initMibHelper();
    startMobilityEngine();
}

private void initMibHelper() {
    if (mibHelper != null) {
        return;
    }
    mibHelper = new NanocellMibHelper(modules.getNanocellMib());
    logger.info("Loaded initial MIB data from " + MIB_CONFIG_RESOURCE);
}

private void startMobilityEngine() {
    if (mobilityEngine != null) {
        logger.error("Cannot start mobility engine: engine already initialized");
        return;
    }
    if (mibHelper == null) {
        logger.error("Cannot start mobility engine: MIB helper not initialized");
        return;
    }
    mobilityEngine = new MobilityEngine(mibHelper);
    mobilityThread = new Thread(mobilityEngine, "nanocell-mobility-engine");
    mobilityThread.setDaemon(true);
    mobilityThread.start();
    logger.info("Mobility engine thread started");
}
```

**Why daemon thread?**
Setting `setDaemon(true)` means the JVM can exit even if this thread is still running. This prevents the agent from hanging on shutdown.

#### 6. Import Statement

```java
import pt.uminho.gvr.mobility.MobilityEngine;
```

Don't forget to import the MobilityEngine class from its package!

### Initialization Order

The initialization follows a specific order:

```
1. agent.initialize()       → Sets up SNMP4J core components
2. registerMIBs()           → Creates NanocellMib and registers it
3. agent.setupProxyForwarder() → Configures proxy (not used in our case)
4. agent.run()              → Starts listening for SNMP requests
5. initMibHelper()          → Wraps NanocellMib and loads JSON config
6. startMobilityEngine()    → Starts the mobility simulation thread
```

**Why this order?**
- The MIB must be registered before we can wrap it with the helper.
- The helper must load data before the mobility engine starts using it.
- The SNMP listener should be running before we start modifying MIB values.

---
**Next:** Click the link below and **refresh the page (F5)** to see the new files.

**[Continue to Step 7: Building and Testing >>>](https://github.com/jfpereira-uminho/nanocell-snmp-java/blob/step6/README.md#step-7-building-and-testing)**

---

<br><br><br><br><br><br><br><br><br><br>

## Step 7: Building and Testing

Now let's build and test our SNMP agent!

### Building the Project

Navigate to the Maven project directory and build:

```bash
cd nanocell-snmp4j-agent
mvn clean package
```

This will:
1. Compile all Java source files.
2. Package them with dependencies into a shaded JAR.
3. Create `target/nanocell-snmp4j-agent-1.0.jar`.

If the build fails, check:
- All files have the correct `package` statement.
- The Jackson dependency is in `pom.xml`.
- You're using Java 17 or higher: `java -version`

### Running the Agent

Start the agent:

```bash
java -jar target/nanocell-snmp4j-agent-1.0.jar -bc Agent.bc udp:0.0.0.0/4161
```

**Command Line Arguments:**

| Argument | Description |
|----------|-------------|
| `-bc Agent.bc` | Boot counter file (tracks engine restarts) |
| `udp:0.0.0.0/4161` | Listen on UDP port 4161 on all interfaces |

**Note:** Using port 4161 instead of the standard 161 avoids requiring root/administrator privileges.

You should see output like:
```
INFO  - Loaded initial MIB data from /config/nanocell_mib_config.json
INFO  - Mobility engine thread started
```

### Testing with SNMP Tools

Open a new terminal and use net-snmp tools to query the agent.

#### Get Total UEs

```bash
snmpget -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.1.1.0
```

Expected output:
```
SNMPv2-SMI::enterprises.88888.1.1.1.0 = Gauge32: 100
```

#### Get Mobility Interval

```bash
snmpget -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.1.2.0
```

Expected output:
```
SNMPv2-SMI::enterprises.88888.1.1.2.0 = Timeticks: (200) 0:00:02.00
```

#### Walk the Cell Table

```bash
snmpwalk -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.1.3
```

Expected output (values will change due to mobility):
```
SNMPv2-SMI::enterprises.88888.1.1.3.1.2.1 = STRING: "Cell-A"
SNMPv2-SMI::enterprises.88888.1.1.3.1.2.2 = STRING: "Cell-B"
SNMPv2-SMI::enterprises.88888.1.1.3.1.2.3 = STRING: "Cell-C"
SNMPv2-SMI::enterprises.88888.1.1.3.1.3.1 = Gauge32: 30
SNMPv2-SMI::enterprises.88888.1.1.3.1.3.2 = Gauge32: 50
SNMPv2-SMI::enterprises.88888.1.1.3.1.3.3 = Gauge32: 40
SNMPv2-SMI::enterprises.88888.1.1.3.1.4.1 = Gauge32: 28
SNMPv2-SMI::enterprises.88888.1.1.3.1.4.2 = Gauge32: 42
SNMPv2-SMI::enterprises.88888.1.1.3.1.4.3 = Gauge32: 30
...
```

#### Walk the Neighbor Table

```bash
snmpwalk -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.2.1
```

Expected output:
```
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.1.2 = Gauge32: 3
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.1.3 = Gauge32: 1
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.2.1 = Gauge32: 2
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.2.3 = Gauge32: 2
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.3.1 = Gauge32: 1
SNMPv2-SMI::enterprises.88888.1.2.1.1.3.3.2 = Gauge32: 3
```

#### Walk the Entire MIB

```bash
snmpwalk -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888
```

#### Set the Mobility Interval

Change the mobility interval to 5 seconds (500 ticks):

```bash
snmpset -v2c -c public 127.0.0.1:4161 \
    1.3.6.1.4.1.88888.1.1.2.0 t 500
```

Verify:
```bash
snmpget -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.1.2.0
```

#### Set a Neighbor Weight

Change the weight from Cell-A to Cell-B:

```bash
snmpset -v2c -c public 127.0.0.1:4161 \
    1.3.6.1.4.1.88888.1.2.1.1.3.1.2 u 5
```

This increases the probability of users moving from Cell-A to Cell-B.

### Monitoring in Real-Time

Run this in a loop to watch the values change:

```bash
watch -n 2 'snmpwalk -v2c -c public 127.0.0.1:4161 1.3.6.1.4.1.88888.1.1.3.1.4'
```

You'll see the `cellCurrentUsers` values changing every few seconds as the mobility engine moves users around.

### Troubleshooting

| Problem | Solution |
|---------|----------|
| "Connection refused" | Make sure the agent is running |
| "Timeout" | Check the port number matches |
| "No such instance" | OID might be wrong, use snmpwalk first |
| Build fails | Check package statements and imports |
| No mobility happening | Check the log output for errors |

### What You've Learned

Congratulations! You have successfully:

1. **Designed a MIB** using ASN.1 notation with scalars and tables.
2. **Generated Java code** from the MIB using AgentPro.
3. **Structured a Java project** with Maven and proper packages.
4. **Created an abstraction layer** (NanocellMibHelper) over generated code.
5. **Implemented a simulation** (MobilityEngine) that updates MIB values.
6. **Customized the SNMP agent** to integrate all components.
7. **Tested the agent** using standard SNMP tools.

### Next Steps

Ideas for extending this project:

- Add SNMP notifications (traps) when handovers exceed a threshold.
- Implement more sophisticated mobility algorithms.
- Add persistence to save state between agent restarts.
- Create a web dashboard that visualizes the network state.
- Add SNMPv3 support with authentication and encryption.

---

## License

This tutorial is provided for educational purposes by the University of Minho.

## Contact

For questions or feedback, please contact the course instructors.
