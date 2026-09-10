# ADA - DUMP analyze portal

AonFine 저장소 안에 있는 **독립 Maven 모듈**입니다. AonFine의 `pom.xml`/`src`는 전혀 건드리지 않으며,
별도 WAR(`ADA.war`)로 빌드되어 `/ADA` 컨텍스트로 배포됩니다. AonFine의 기존 서비스(맛집/점심투표/공지)는
그대로 보존됩니다.

이 저장소 안의 다른 산출물(`AonFine-2.4.0-oom-test-vm.war`, `aon_oom_tset.war`, `docs/` 등 기존 OOM
테스트 관련 파일)은 ADA와 무관하며 ADA 작업 중 건드리지 않았습니다.

## 기술 스택 (기존 AonFine과 동일)

Java 8, eGovFrame 4.2, Spring MVC 5.3, JSP, Maven WAR, JBoss EAP 7.4, AonFine과 동일한 CUBRID DB(JNDI 재사용).

## 업로드 취소·분석 연동 준비 변경

- 업로드 준비 → 전송 → 검증 → 저장 확정 상태를 관리하고 본인/관리자의 서버 취소 요청을 처리합니다.
- 팝업 닫기는 취소가 아닙니다. 취소 버튼은 서버 요청을 보내며, 처리 종료와 부분파일 정리 확인 후에만 취소 완료로 표시합니다. 네트워크 abort·NFS 정체·DB 응답 유실만으로 완료를 주장하지 않습니다.
- 실패 사유를 목록/팝업에 표시하고, 취소/실패 완료 건은 파일을 재선택하여 새 ID로 재업로드합니다. 정리 실패·결과 미확인 상태는 관리자 확인 전 재업로드를 막습니다.
- **분석 JOB/ATTEMPT 실제 서비스는 이번 변경(2026-09-10)에서 구현·로컬 테스트 완료했습니다** — `AnalysisService`가 JOB→ATTEMPT→DUMP 3-테이블 원자적 트랜잭션(Spring `@Transactional` + `DataSourceTransactionManager`)으로 요청/재시도/취소/claim/heartbeat/보고를 처리하며, `/dump/analyzeRequest.do`·`/dump/analysisStatus.do`·`/dump/analysisCancel.do`·`/dump/analysisRetry.do`가 더 이상 stub이 아닙니다. 내부 Worker API(`/worker/analysis/claim.do`·`heartbeat.do`·`report.do`)는 사용자 세션과 분리된 별도 토큰 인증(`AdaWorkerAuthInterceptor`, `ada.worker.apiToken`/`allowedIps`, 미설정 시 fail-closed)으로 보호됩니다. 자세한 계약은 [API-CONTRACT.md](API-CONTRACT.md) 참고.
- 목록 화면(list.jsp/ada-upload.js)의 Analyze·분석 취소·분석 재시도·분석 결과 버튼도 위 실제 API에 연결했습니다. 선택한 덤프가 STORED·미만료이고 분석 상태가 허용될 때만 각 버튼이 활성화되고, 요청/취소/재시도는 실제로 JOB/ATTEMPT를 등록·전이시키며 완료될 때까지 상태를 폴링해 행의 상태 점(dot)과 버튼 상태를 갱신합니다. **다만 Worker가 아직 없어 분석은 QUEUED에서 더 진행되지 않습니다** — 결과 다운로드 버튼은 SUCCEEDED가 되어도 현재는 501(패키징 미구현) 메시지를 보여줍니다.
- **아직 구현하지 않은 것**: 실제 Worker 프로세스(claim 루프), MAT 실행, 스레드 덤프 파서, 서버 로그 상관관계 추출, Claude Code CLI 비대화형 호출, 분석 결과 ZIP 패키징/다운로드. Worker `/DATA/Work` 및 별도 `adawork` 계정 영역은 변경하지 않았습니다.
- **로컬 코드·SQL·문서·검증·WAR 저장은 2026-09-09 변경에서 완료했습니다. 실제 CUBRID DB에는 2026-09-10에 별도로 반영했습니다** — `TB_ADA_ANALYSIS_JOB`/`TB_ADA_ANALYSIS_ATTEMPT` 생성 및 검증 완료, 백업 테이블 `TB_ADA_DUMP_BAK_20260909_JOBS` 생성, 기존 `TB_ADA_DUMP` 데이터·다른 AonFine 테이블 변경 없음. 자세한 내용은 [sql/MIGRATION.md](sql/MIGRATION.md)의 "실제 적용 결과" 절 참고. **서버 WAR 재배포는 아직 하지 않았습니다.**

상태·엔드포인트·향후 계약은 [API-CONTRACT.md](API-CONTRACT.md), 신규 설치/이행/복구는 [sql/MIGRATION.md](sql/MIGRATION.md)를 참고하세요.

## 빌드

```powershell
cd D:\eGovFrameDev-4.2.0-64bit\workspace\AonFine\ADA
.\build-local.ps1
```

`build-local.ps1`은 Node UI 테스트와 Maven `clean package`를 수행하고 WAR 구성을 확인합니다. 모두 통과하면 기존 루트 WAR를 타임스탬프 백업하고 `target/ADA.war`를 `D:\eGovFrameDev-4.2.0-64bit\workspace\AonFine\ADA.war`로 복사해 SHA-256을 대조합니다. 서버 전송·배포·DB 접속은 하지 않습니다. Maven 위치가 다르면 `-MavenCommand`로 지정합니다.

Maven만 직접 실행하면 산출물은 `target/ADA.war`이며 루트 WAR는 자동 갱신되지 않습니다. JSON 응답은 명시적으로 포함한 Jackson 2.18.10으로 직렬화합니다. 테스트용 H2/Spring Test는 WAR에 포함하지 않습니다.

JBoss `spring-modules-validation-0.9.jar` 배제 이력(AonFine 인수인계 문서 8.2절)을 반영해
`maven-war-plugin`의 `packagingExcludes`로 자동 제외되도록 pom.xml에 구성했습니다(수동 후처리 불필요).

## 배포 전 서버 준비 사항 (이번 작업 범위 밖 - 운영 담당자 확인 필요)

1. **DB**: 현재 설치 상태에 맞게 [sql/MIGRATION.md](sql/MIGRATION.md)의 순서를 따릅니다. 이미 덤프 테이블이 있는 DB에 신규 설치 DDL을 재실행하지 않습니다.
   이번 변경에서 실제 DB에는 접속/적용하지 않았습니다. `TB_USER`, `TB_RESTAURANT`, `TB_BOARD`,
   `TB_LUNCH_VOTE` 등 기존 테이블은 전혀 변경하지 않았습니다.
2. **JNDI**: `java:jboss/datasources/AonFineDS`를 그대로 재사용합니다. 별도 DataSource 설정이 필요 없습니다.
3. **NFS 스토리지**: WAS2(`192.168.2.14`)의 `/DATA`(Worker `192.168.2.17`의 NFS 마운트) 아래
   `ada.properties`의 `ada.storage.root=/DATA/ada` 경로에 `incoming/`, `store/` 디렉터리를
   `jboss` 계정(uid/gid 1001, WAS2에서 확인된 값) 권한으로 미리 만들어야 합니다.
   ```bash
   mkdir -p /DATA/ada/incoming /DATA/ada/store
   chown -R 1001:1001 /DATA/ada
   chmod -R 750 /DATA/ada
   ```
4. **NFS 마운트 감지용 마커 파일**: Worker(`192.168.2.17`)의 `/DATA` export 안에 마커 파일을 만들어야
   애플리케이션이 "NFS 미마운트 → 로컬 저장" 상황을 즉시 감지해 거절할 수 있습니다.
   ```bash
   # Worker(.17)에서 실행
   touch /DATA/.ada_nfs_marker
   ```
   (WAS2가 NFS로 마운트되어 있으면 `/DATA/.ada_nfs_marker`가 WAS2에서도 그대로 보입니다.)
5. **컨텍스트**: `/ADA/dump/list.do`가 로그인 후 기본 화면입니다.

## 실제로 검증되지 않은 것 (한계 - 명확히 구분)

- **실제 NFS+DB 통합 검증은 이번 작업에서 수행하지 않았습니다.** 위 3~4번 서버 준비가 끝난 뒤,
  실제 JBoss EAP 7.4에 배포하여 업로드 → 저장 → 목록 조회 → 다운로드 전 과정을 재검증해야 합니다.
- 이번 분석 테이블 DDL은 SQL 준비 및 H2 보존/제약 테스트까지 수행하며 실제 CUBRID 적용/조회 검증은 하지 않습니다. 이전 유형/호스트 이행 기록은 기존 SQL 주석에 보존되어 있습니다.
- CUBRID의 `LIMIT ?, ?` 페이징 문법과 `TO_CHAR`/`CASE WHEN REG_DT < ?` 비교 구문은 기존 AonFine 코드
  (`LunchVoteJdbcMapper` 등)에서 쓰이는 것과 같은 계열의 문법을 따랐으나, 이 모듈에서 실제 CUBRID에
  대해 실행 검증하지는 못했습니다.
- 목록 화면의 Analyze/분석 취소/분석 재시도/분석 결과 버튼은 실제 API에 연결되어 있지만, Worker가 없어 QUEUED 이후로
  진행되지 않습니다. 브라우저 실제 렌더링/클릭 검증은 아직 하지 않았습니다(Node DOM 모의 테스트만 수행).
- 실제 Worker 프로세스, MAT 실행, 스레드 덤프 파서, Claude Code CLI 호출, 결과 패키징/다운로드는 구현되지 않았습니다.

## 자동화 테스트

`src/test/java`에 다음 JUnit 테스트를 포함합니다:
- 경로 위험성 검사(`PathSafetyUtilTest`) - `../`, 절대경로 거절
- tar.gz 손상/구조 검증(`TarGzStreamValidatorTest`) - 정상 tar.gz(hprof 포함), 손상된 gzip, hprof 누락,
  위험 경로 포함, 허용되지 않은 확장자 케이스
- 업로드 준비·서버 스트림 중단·검증 취소·취소/저장 경쟁·중복 본문·부분/최종 파일 정리·DB 커밋 응답 유실·재시작 후 미확인 상태
- MVC의 본인/관리자/타인 권한, POST/CSRF, 세션 만료, 직접 호출한 분석 준비 엔드포인트
- H2에서 실행한 신규 설치/보존형 이행, 참조/유일성 제약, 현재 시도 ID/버전 조건부 갱신. CUBRID 검증과는 별개입니다.
- `AnalysisServiceTest`(H2 + 실제 Spring `@Transactional` 프록시): 최초 요청/중복 요청 거절/claim/heartbeat 소유자 확인/취소(QUEUED 즉시 취소·RUNNING은 확인 대기)/오래된 보고 거절/동일 완료 재전송 멱등/재시도 시 새 ATTEMPT 번호/heartbeat 유실 시 RECOVERY_REQUIRED 전환(FAILED·CANCELLED로 단정하지 않음)/동시 요청 경쟁에서 ATTEMPT 중복 생성 없음(스레드 8개 동시 요청)까지 검증합니다.
- `AnalyzeControllerTest`, `AdaWorkerAuthInterceptorTest`: 사용자 세션 인증과 Worker 토큰 인증이 서로 분리되어 있는지, 토큰 미설정 시 fail-closed인지 검증합니다.
- `node --test src/test/js/ada-upload.test.cjs`: 닫기/취소 구분, abort 후 서버 확인, 오래된 응답 무시, 세션 만료, 5GB 경계값에 더해 분석 버튼 활성/비활성 조건, Analyze/취소/재시도 요청과 상태 폴링, 결과 다운로드 미구현 메시지 표시에 대한 DOM 모의 회귀 테스트(총 11건)

Java 테스트는 `mvn test`로 실행됩니다(빌드 시 자동 실행). UI 테스트는 위 Node 명령 또는 빌드 스크립트로 실행합니다. 실제 브라우저 렌더링 및 서버 통합(NFS/JBoss/CUBRID) 테스트는 포함되어 있지
않습니다 - 위 "한계" 절 참고.

## 보안/운영 메모

- DB 비밀번호 등 민감정보는 소스/설정 어디에도 없습니다 (JNDI만 사용).
- 회원가입 기능 없음. 계정 생성/수정 기능 없음(TB_USER는 SELECT만).
- ADA 로그인 세션은 AonFine 로그인 세션과 별개의 세션 키(`adaLoginUser`)를 사용합니다.
- 대용량 업로드는 JVM 힙에 전체를 올리지 않고 스트리밍으로 디스크에 직접 저장합니다.
- 압축 파일은 디스크에 실제로 풀지 않고 순차 스트림 검증만 수행합니다.
- 현재 사용자별 업로드 준비 동시 제한은 단일 WAS2 인스턴스 기준입니다. 다중 WAS 확장 및 재시작 후 미확인 업로드 복구 규칙은 [sql/MIGRATION.md](sql/MIGRATION.md)를 확인하세요.
