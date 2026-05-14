plugins {
    `java-gradle-plugin`
    `maven-publish`
}

gradlePlugin {
    plugins {
        create("tddGuard") {
            id = "io.github.nizos.tdd-guard-junit5"
            implementationClass = "io.github.nizos.tddguard.junit5.TddGuardPlugin"
        }
    }
}

group = "io.github.nizos"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.junit.platform:junit-platform-launcher:1.11.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "tdd-guard-junit5"
            from(components["java"])

            pom {
                name.set("TDD Guard JUnit5 Reporter")
                description.set("JUnit5 TestExecutionListener that captures test results for TDD Guard validation.")
                url.set("https://github.com/nizos/tdd-guard")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
