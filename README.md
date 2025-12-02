# HH Plus Server

항해플러스 백엔드 서버 프로젝트입니다.

## 📋 프로젝트 개요

이 프로젝트는 Spring Boot 3.4.1 기반의 Java 백엔드 애플리케이션으로, JPA를 활용한 데이터베이스 연동 및 RESTful API 서비스를 제공합니다.

### 주요 기술 스택

- **Java 21** - JVM 언어
- **Spring Boot 3.4.1** - 애플리케이션 프레임워크
- **Spring Data JPA** - ORM 및 데이터 접근 계층
- **MySQL 8.0** - 관계형 데이터베이스
- **Gradle (Kotlin DSL)** - 빌드 도구
- **Docker & Docker Compose** - 컨테이너 기반 인프라
- **Testcontainers** - 통합 테스트 환경

## 🏗️ 프로젝트 구조

```
server-java/
├── src/
│   ├── main/
│   │   ├── java/kr/hhplus/be/server/
│   │   │   ├── ServerApplication.java      # 메인 애플리케이션
│   │   │   └── config/
│   │   │       └── jpa/JpaConfig.java      # JPA 설정
│   │   └── resources/
│   │       └── application.yml             # 애플리케이션 설정
│   └── test/
│       └── java/kr/hhplus/be/server/
│           ├── ServerApplicationTests.java
│           └── TestcontainersConfiguration.java
├── build.gradle.kts                        # Gradle 빌드 스크립트
├── docker-compose.yml                      # Docker 컨테이너 정의
└── README.md
```

## 🚀 시작하기

### 사전 요구사항

다음 소프트웨어가 설치되어 있어야 합니다:

- **Java 21** 이상
- **Docker** 및 **Docker Compose**
- **Gradle** (또는 포함된 Gradle Wrapper 사용)

### 1. 데이터베이스 실행

`local` profile로 실행하기 위해서는 Docker를 통해 MySQL 데이터베이스를 먼저 실행해야 합니다.

```bash
docker-compose up -d
```

MySQL 컨테이너가 다음 설정으로 실행됩니다:
- **포트**: 3306
- **데이터베이스**: hhplus
- **사용자**: application / application
- **루트 비밀번호**: root

### 2. 애플리케이션 빌드

```bash
./gradlew build
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 빌드된 JAR 파일을 직접 실행:

```bash
java -jar build/libs/server-{git-hash}.jar
```

애플리케이션이 정상적으로 실행되면 기본 포트 `8080`에서 서비스가 시작됩니다.

## 🧪 테스트

프로젝트는 Testcontainers를 사용하여 통합 테스트를 수행합니다.

```bash
./gradlew test
```

테스트는 자동으로 Docker 컨테이너를 생성하여 실제 MySQL 환경과 유사한 조건에서 실행됩니다.

## ⚙️ 설정

### Application Profiles

- **local**: 로컬 개발 환경 (기본값)
- **test**: 테스트 환경

### 데이터베이스 설정

`application.yml`에서 다음과 같은 설정을 사용합니다:

- **Connection Pool**: HikariCP (최대 3개 연결)
- **Timezone**: UTC
- **JPA**: `ddl-auto: none` (스키마 자동 생성 비활성화)

## 📦 주요 의존성

- `spring-boot-starter-web` - REST API 개발
- `spring-boot-starter-data-jpa` - JPA 기반 데이터 접근
- `spring-boot-starter-actuator` - 모니터링 및 메트릭
- `mysql-connector-j` - MySQL 드라이버
- `testcontainers` - 통합 테스트 지원

## 🛠️ 개발 가이드

### Git 버전 관리

이 프로젝트는 Git 커밋 해시를 사용하여 버전을 자동으로 관리합니다. 빌드 시 현재 Git 커밋의 짧은 해시가 버전으로 설정됩니다.

### JPA Auditing

JPA Auditing이 활성화되어 있어 엔티티의 생성/수정 시간을 자동으로 추적할 수 있습니다.

## 🐳 Docker 관리

### 컨테이너 시작
```bash
docker-compose up -d
```

### 컨테이너 중지
```bash
docker-compose down
```

### 컨테이너 및 데이터 삭제
```bash
docker-compose down -v
```

### 로그 확인
```bash
docker-compose logs -f mysql
```

## 📝 License

이 프로젝트는 항해플러스의 교육 목적으로 생성되었습니다.

## 👥 Contact

문의사항이 있으시면 이슈를 등록해주세요.