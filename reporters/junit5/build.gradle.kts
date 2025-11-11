plugins {
    `java-library`
    `maven-publish`
}

group = "com.lightspeed.tddguard"
version = project.findProperty("version") as String? ?: "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    // Build with Java 21 for optimizations
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // JUnit Platform API for TestExecutionListener
    compileOnly("org.junit.platform:junit-platform-launcher:1.10.1")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.1")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-engine:1.10.1")
}

tasks.test {
    useJUnitPlatform {
        // Exclude test fixtures that are run programmatically by integration tests
        excludeEngines("junit-vintage") // Exclude to avoid running JUnit4VintageFixture directly
    }

    // Exclude fixture classes from test execution (they're run programmatically)
    exclude("**/SampleTests.class")
    exclude("**/JUnit4VintageFixture.class")
    exclude("**/NestedTestExample.class")
    exclude("**/AdvancedTestTypes.class")
    exclude("**/TestDescriptorStub.class")

    // Configure test execution
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }

    // Configure system properties for TDD Guard
    systemProperty("tddguard.testSourceDirs", System.getProperty("tddguard.testSourceDirs", "src/test/java"))
    systemProperty("tddguard.mainSourceDirs", System.getProperty("tddguard.mainSourceDirs", "src/main/java"))
    systemProperty("tddguard.outputPath", System.getProperty("tddguard.outputPath", "${project.buildDir}/tdd-guard-results.json"))

    // Enable TDD Guard JUnit5 reporter
    systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:unchecked")
    options.compilerArgs.add("-Xlint:deprecation")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "junit5"

            pom {
                name.set("TDD Guard JUnit5 Reporter")
                description.set("JUnit 5 reporter for TDD Guard with educational feedback")
                url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY") ?: "lightspeed/tdd-guard"}")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/${System.getenv("GITHUB_REPOSITORY") ?: "lightspeed/tdd-guard"}.git")
                    url.set("https://github.com/${System.getenv("GITHUB_REPOSITORY") ?: "lightspeed/tdd-guard"}")
                }
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/${System.getenv("GITHUB_REPOSITORY") ?: "lightspeed/tdd-guard"}")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = project.findProperty("githubToken") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
