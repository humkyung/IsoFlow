// build.gradle.kts — IsoFlow 백엔드 빌드 설정. Spring Boot 3 + Java 21.
// 등각도 엔진(파서·위상·기하·출력)이 이 모듈 안에 함께 들어간다.
plugins {
    java
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "co.atools.isoflow"
version = "0.1.0-SNAPSHOT"

java {
    // Java 21(LTS) 툴체인 사용
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // ── 웹 / 영속 ──
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Flyway — DB 스키마 권위(src/main/resources/db/migration). JPA 는 스키마를 만들지 않는다
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ── 등각도 엔진 ──
    // 위상 해석(연결 그래프) — networkx 대응
    implementation("org.jgrapht:jgrapht-core:1.5.2")
    // 기하 연산(겹침 검출·교차 판정) — shapely 대응
    implementation("org.locationtech.jts:jts-core:1.20.0")
    // PDF 출력. DXF 는 자체 Writer(export/dxf)로 R12 ASCII 를 직접 쓴다
    implementation("org.apache.pdfbox:pdfbox:3.0.3")
    // 심볼 세트(symbols-2d.json / skey-table.json) 로딩 — 엔진이 직접 쓰므로 명시적으로 선언한다
    implementation("com.fasterxml.jackson.core:jackson-databind")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // ── 테스트 ── (starter-test 에 JUnit 5 + AssertJ + Mockito 포함)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // engine 패키지의 Spring 의존 금지를 강제한다
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 프론트 기본 스타일 복제본도 테스트 입력이다 —
    // 선언하지 않으면 이 파일만 바뀌었을 때 gradle 이 테스트를 UP-TO-DATE 로 건너뛴다
    // 골든 갱신 플래그를 테스트 JVM 까지 넘긴다 — gradle 프로세스에만 걸리면 테스트가 못 본다
    systemProperty("golden.update",
            providers.systemProperty("golden.update").getOrElse("false"))

    inputs.file(file("../src/types/isoStyle.ts"))
            .withPropertyName("frontendIsoStyleTs")
            .withPathSensitivity(PathSensitivity.NAME_ONLY)
            .optional(true)
}
