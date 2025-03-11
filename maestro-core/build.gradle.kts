plugins {
    id("java-library")
    id("com.vanniktech.maven.publish") version "0.31.0"
}

java {
    withSourcesJar()
}

val jacksonVersion = "2.17.2"
val flywayVersion = "11.3.4"
val testcontainersVersion = "1.20.6"

dependencies {
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("io.github.resilience4j:resilience4j-retry:2.2.0")
    implementation("com.github.kagkarlsson:db-scheduler:14.0.1")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("net.bytebuddy:byte-buddy:1.17.2")

    // https://mvnrepository.com/artifact/org.flywaydb/flyway-core
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    // https://mvnrepository.com/artifact/org.flywaydb/flyway-database-postgresql
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")



    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    // https://mvnrepository.com/artifact/org.testcontainers/postgresql
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    // https://mvnrepository.com/artifact/org.awaitility/awaitility
    testImplementation("org.awaitility:awaitility:4.3.0")


}

tasks.withType<Test> {
    useJUnitPlatform()
}
