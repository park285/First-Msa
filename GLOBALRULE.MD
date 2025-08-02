# Diet Diary MSA 개발 규칙 (v4.0)

이 문서는 `diet-diary-msa` 프로젝트의 아키텍처, 기술 스택, 개발 규칙을 정의합니다. 모든 개발자는 작업 전 반드시 이 문서를 숙지해야 합니다.

## v4.0 주요 변경사항

- **보안 강화**: 모든 Docker 컨테이너가 `root`가 아닌 별도의 `appuser` 권한으로 실행되도록 변경하여 컨테이너 보안을 강화했습니다.
- **인증 로직 개선**: `auth-service`의 관리자 API에서 불필요한 JWT 중복 검증 로직을 제거하고, Spring Security의 인증 컨텍스트를 사용하도록 리팩토링했습니다.
- **로깅 표준화**: 프로젝트 전체의 로깅 시스템을 Spring Boot의 기본 로깅(Logback)으로 표준화하고, `System.out.println` 사용을 제거하여 로그 관리 효율성과 성능을 개선했습니다.

## 1. 기술 스택 (Tech Stack)

- **Backend**: Spring Boot `3.2.12`
- **Language**: Java `17`
- **Frontend**: React
- **Gateway**: Spring Cloud Gateway `2023.0.3`
- **Database**: PostgreSQL `15`
- **In-Memory Store**: Redis `7`
- **Reverse Proxy**: HAProxy `2.8`, Caddy `2.7.6`
- **Container**: Docker, Docker Compose
- **Logging**: SLF4J Facade, Logback Implementation (Spring Boot Default)

## 2. 아키텍처

본 프로젝트는 마이크로서비스 아키텍처(MSA)를 따릅니다.

### 2.1. 요청 흐름 (Request Flow)

1.  **HAProxy**: 최상위 로드 밸런서. 모든 트래픽(80, 443)을 받아 2개의 Caddy 인스턴스로 분산합니다.
2.  **Caddy (x2)**: 리버스 프록시. SSL/TLS 인증서 자동 발급 및 관리, API 요청을 2개의 게이트웨이 인스턴스로 분산합니다.
3.  **API Gateway (x2)**: Spring Cloud Gateway 기반. 모든 API 요청의 단일 진입점 역할을 합니다.
    - **JWT 검증**: `/api/auth/**`의 공개 경로를 제외한 모든 요청의 JWT를 검증합니다.
    - **사용자 정보 전파**: 토큰 검증 후, 사용자 이메일과 ID를 각각 `X-User-Email`, `X-User-Id` 헤더에 담아 내부 서비스로 전달합니다.
4.  **Backend Services**: 각 서비스는 독립적으로 기능을 수행합니다.
    - `auth-service-spring`: 인증/인가, 사용자 관리
    - `diary-service-spring`: 식단 기록 관리
    - `analysis-service-spring`: 식단 분석 및 통계

### 2.2. 서비스 간 통신

- **REST API**: 서비스 간 통신은 REST API를 통해 이루어집니다.
- **OpenFeign**: `analysis-service`는 `diary-service`의 데이터를 조회하기 위해 `spring-cloud-starter-openfeign`을 사용합니다.

## 3. 백엔드 (Backend)

### 3.1. 공통 규칙

- **프레임워크**: 모든 서비스는 **Spring Boot `3.2.12`** 와 **Java `17`** 을 사용합니다.
- **사용자 식별**: API 게이트웨이가 주입해주는 `X-User-Email`, `X-User-Id` 헤더를 통해 사용자를 식별합니다.
- **로깅**: 모든 서비스는 **SLF4J** 인터페이스와 **Logback** 구현체(Spring Boot 기본)를 사용하여 로깅을 표준화합니다.

### 3.2. 인증 서비스 (`auth-service-spring`)

- **인증 방식**: 상태를 저장하지 않는 **Stateless JWT** 인증을 사용합니다.
- **권한 관리**: Spring Security를 통해 API 경로별 접근 제어를 관리합니다. 관리자 API는 `hasAnyRole('ADMIN', 'SUPER_ADMIN')` 규칙으로 보호됩니다.
- **Redis 활용**: JWT 리프레시 토큰 저장, 토큰 블랙리스트 관리, 강제 로그아웃 처리에 사용됩니다.

## 4. 프론트엔드 (Frontend)

- **프레임워크**: **React** (함수형 컴포넌트, Hooks 기반)
- **API 통신**: `src/services/api.js`의 `axios` 인스턴스를 사용하며, 인터셉터를 통해 모든 요청 헤더에 JWT를 자동으로 추가하고 401 오류 시 토큰 갱신을 처리합니다.

## 5. 보안 (Security)

### 5.1. 키 생성 및 관리

- **보안 키 생성**: `JWT_SECRET`, `SERVICE_API_KEY` 등 핵심 보안 키는 **`generate-security-keys.py`** 스크립트를 실행하여 생성합니다.
- **키 저장**: 생성된 키는 `.env.security` 파일에 저장되며, 이 파일을 `.env`로 복사하여 사용해야 합니다.
- **`.gitignore`**: 민감한 정보가 담긴 `.env` 파일은 반드시 `.gitignore`에 추가하여 Git 저장소에 포함되지 않도록 합니다.

### 5.2. 컨테이너 보안

- **Non-Root 사용자**: 모든 서비스의 Docker 이미지는 `appuser`라는 권한 없는 사용자로 실행되어 컨테이너 보안을 강화합니다.

### 5.3. JWT 및 쿠키 보안

- **HttpOnly 쿠키**: JavaScript를 통한 토큰 탈취(XSS)를 방지하기 위해 리프레시 토큰은 HttpOnly 쿠키에 저장됩니다.
- **Secure, SameSite=Strict**: 쿠키는 HTTPS를 통해서만 전송되며, CSRF 공격을 방어합니다.
- **리프레시 토큰 해싱**: 쿠키에 저장되는 리프레시 토큰 값은 SHA-256으로 해시하여 안전하게 보관합니다.

## 6. 개발 환경 실행

### 6.1. 초기 설정

1.  보안 키를 생성합니다.
    `bash
    python3 generate-security-keys.py
    `
2.  생성된 `.env.security` 파일을 `.env` 파일로 복사합니다.
    `bash
    cp .env.security .env
    `

### 6.2. 전체 시스템 실행

- 모든 서비스를 빌드하고 실행합니다.
  `bash
  docker compose up --build -d
  `
- 시스템을 중지하고 컨테이너를 삭제합니다.
  `bash
  docker compose down
  `
- 시스템을 중지하고 데이터 볼륨까지 모두 삭제합니다.
  `bash
  docker compose down -v
  `

## 7. 로깅 및 모니터링

- **로깅**: 모든 서비스의 로그는 Spring Boot의 기본 로깅(Logback)을 통해 표준화된 형식으로 콘솔에 출력됩니다. `docker compose logs -f [service-name]` 명령으로 실시간 로그를 확인할 수 있습니다.
- **헬스체크**: Spring Actuator의 `/actuator/health` 엔드포인트를 통해 각 서비스의 상태를 모니터링할 수 있습니다.
