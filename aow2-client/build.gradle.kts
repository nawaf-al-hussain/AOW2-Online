plugins {
    id("java")
    id("application")
    id("jacoco")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

dependencies {
    implementation(project(":aow2-common"))
    implementation(project(":aow2-core"))
    implementation(project(":aow2-modding"))
    implementation("com.github.almasb:fxgl:21")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.3")
    implementation("jakarta.websocket:jakarta.websocket-api:2.1.1")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:2.1.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // OGG/Vorbis playback support for javax.sound.sampled.
    // Required to play the converted OGG SFX files (see AssetTestScene).
    // Without these, AudioSystem.getAudioInputStream() throws
    // UnsupportedAudioFileException on .ogg files.
    // The com.googlecode.soundlibs group hosts the Vorbis SPI and its dependencies.
    implementation("com.googlecode.soundlibs:jorbis:0.0.17.4")
    implementation("com.googlecode.soundlibs:tritonus-share:0.3.7.4")
    implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation(project(":aow2-core")) // for test dependencies on core classes
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.media", "javafx.swing")
}

application {
    mainClass = "com.aow2.client.AOW2App"
}
