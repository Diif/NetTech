plugins {
    id("java")
    id("application")
    id ("io.freefair.lombok") version "6.5.1"
}

application {
    mainClass.set("ClientMain")
    applicationDefaultJvmArgs = listOf("-Djava.util.logging.config.file=src/main/resources/logging.properties")
}

java{
    toolchain{
        languageVersion.set(JavaLanguageVersion.of(17));
    }
}

version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}