/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.4.2/userguide/building_java_projects.html
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
    id ("org.openjfx.javafxplugin") version "0.0.13"
    id ("io.freefair.lombok") version "6.5.1"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")

    // This dependency is used by the application.
    implementation("com.google.protobuf:protobuf-java:3.21.9")

}

application {
    // Define the main class for the application.
    mainClass.set("com.grishaprimilabu.App")
}

javafx {
    version = "18"
    modules = mutableListOf<String>("javafx.controls")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
