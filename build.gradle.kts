plugins {
    id("java")
    id("jacoco")
}

group = "com.aow2"
version = "0.1.0-SNAPSHOT"

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")

    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("--enable-preview", "-Xlint:preview"))
    }

    tasks.withType<Javadoc> {
        options.encoding = "UTF-8"
    }

    tasks.test {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
        finalizedBy(tasks.jacocoTestReport)
    }

    tasks.jacocoTestReport {
        reports {
            xml.required = true
            html.required = true
        }
    }
}
