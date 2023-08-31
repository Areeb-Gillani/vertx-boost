plugins {
    id("java")
    id("maven-publish")
}

group = "com.areebgillani"
version = "0.0.1"
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.areebgillani"
            artifactId = rootProject.name
            version = "0.0.1"
            from(components["java"])
        }
    }
}
tasks.publishToMavenLocal{
    dependsOn (tasks.build)
    onlyIf{
        true
    }
}
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("org.reflections:reflections:0.10.2")
    compileOnly("io.vertx:vertx-web:4.4.4")
}
