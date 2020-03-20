import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.2.5.RELEASE"
	id("io.spring.dependency-management") version "1.0.9.RELEASE"
	kotlin("jvm") version "1.3.61"
	kotlin("plugin.spring") version "1.3.61"
}

group = "uk.gov.dwp.dataworks"

repositories {
	mavenCentral()
}

dependencies {
	// AWS
	implementation(platform("software.amazon.awssdk:bom:2.10.89"))
	implementation("software.amazon.awssdk:regions")

	implementation ("com.auth0:java-jwt:3.10.0")
	implementation ("com.auth0:jwks-rsa:0.11.0")
	implementation ("com.fasterxml.jackson.core:jackson-annotations:2.10.2")
	implementation ("com.fasterxml.jackson.core:jackson-core:2.10.2")
	implementation ("com.fasterxml.jackson.core:jackson-databind:2.10.2")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "1.8"
	}

}

