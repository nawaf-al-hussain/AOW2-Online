plugins {
    id("java")
    id("jacoco")
}

dependencies {
    implementation(project(":aow2-common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("org.slf4j:slf4j-api:2.0.12")

    // NOTE: aow2-modding is NOT a compile dependency here to avoid circular build.
    // MissionScriptEngine is loaded via reflection in CampaignManager.createWithLuaEngine().
    // The runtime classpath must include aow2-modding for the factory method to work.

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.3")
}
