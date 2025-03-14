plugins {
	java
	id("org.springframework.boot") version "3.3.0"
	id("io.spring.dependency-management") version "1.1.5"
}
val testcontainersVersion = "1.20.6"

dependencies {
//	implementation("io.github.lucidity-labs:maestro-core:0.1.4")
	implementation(project(":maestro-core"))
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.github.kagkarlsson:db-scheduler:14.0.1")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    // https://mvnrepository.com/artifact/org.testcontainers/postgresql
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    // https://mvnrepository.com/artifact/org.awaitility/awaitility
    testImplementation("org.awaitility:awaitility:4.3.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
