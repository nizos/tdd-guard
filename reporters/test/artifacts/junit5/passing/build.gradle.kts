buildscript {
    val reporterJar: String by project
    dependencies { classpath(files(reporterJar)) }
}

plugins { java }

apply(plugin = "io.github.nizos.tdd-guard-junit5")

val reporterJar: String by project

repositories { mavenCentral() }

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(files(reporterJar))
}

tasks.test {
    useJUnitPlatform()
}
