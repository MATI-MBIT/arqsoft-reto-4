plugins { `java-library` }

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.kafka:spring-kafka")
    api("com.fasterxml.jackson.core:jackson-databind")
    api("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.test { systemProperty("pki.dir", rootProject.file("deploy/pki").absolutePath) }
