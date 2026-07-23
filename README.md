# 읽고 (Ilgo) · Backend

> 어려운 생활 문서를 사진으로 찍으면 AI가 쉬운 한국어 또는 영어로 풀어주는 배리어프리 서비스의 백엔드 API 서버입니다.

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?logo=springsecurity&logoColor=white)
![JPA](https://img.shields.io/badge/Spring_Data_JPA-59666C?logo=hibernate&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-000000?logo=jsonwebtokens&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Swagger](https://img.shields.io/badge/Swagger-85EA2D?logo=swagger&logoColor=black)

## 소개

`읽고`는 고지서, 안내문, 문자처럼 이해하기 어려운 생활 문서를 사진으로 찍으면 AI가 쉽게 풀어서 설명해 주는 서비스입니다. **AI Re-Local 해커톤** 출품작이며(전국 5개 대학 27명 참가, 3등 선도상), 이 저장소는 그중 **백엔드 API 서버**입니다.

## 기술 스택

| 구분 | 사용 기술 |
|------|-----------|
| Language | Java 21 |
| Framework | Spring Boot (Web, Validation) |
| 인증 | Spring Security, JWT (jjwt 0.12.6) |
| 데이터 | Spring Data JPA, PostgreSQL |
| 문서화 | springdoc-openapi (Swagger UI) 2.8.9 |
| 테스트 | JUnit, H2 (인메모리) |

## 주요 기능

- **JWT 인증 / 인가** - 요청별 토큰 검증 필터, Stateless 정책, 경로별 접근 제어
- **문서 분석 API** - AI 게이트웨이와 연동해 문서를 분석하고, 응답을 설명 / 위험도 / 핵심 카드 / 할 일 구조로 파싱
- **업로드 지원** - 이미지(multipart)와 JSON 양쪽 요청 방식 지원
- **커서 기반 페이지네이션** - 분석 목록 조회
- **공통 계층** - 전역 예외 처리, 요청 레이트 리밋
- **API 문서 자동화** - Swagger(OpenAPI)로 프론트엔드 팀과 규격 공유
- **테스트** - 인증, 분석, 문서 생성 테스트로 안정성 검증

## 디렉토리 구조

```
src/main/java/com/ilgo
├─ auth        # 인증 (로그인, JWT 발급)
├─ security    # 시큐리티 설정, 토큰 필터
├─ analysis    # 문서 분석 API, 응답 구조화
├─ ai          # 외부 AI 게이트웨이 연동
├─ user        # 사용자
├─ common      # 공통 (예외 처리, 응답 포맷 등)
└─ config      # 설정
```

## 실행 방법

```bash
# 1. 저장소 클론
git clone https://github.com/Jihwan0210/Ilgo-backend.git

# 2. application.yml(properties)에 PostgreSQL 연결 정보와 AI, JWT 키 설정

# 3. 실행
./gradlew bootRun
```

실행 후 Swagger UI에서 API 명세를 확인할 수 있습니다. (`/swagger-ui/index.html`)

## 담당

팀 프로젝트에서 **백엔드 API 서버 전반**(인증, 문서 분석 연동, 문서화, 테스트)을 담당했습니다.
