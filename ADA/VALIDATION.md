# 로컬 변경·검증 결과

완료 시각: 2026-09-10 00:23 KST. 승인 범위는 ADA 소스·DDL·문서·테스트와 로컬 WAR 갱신이다. 실제 DB 접속/변경, 서버 WAR 배포, Worker 영역 변경·자동 실행은 수행하지 않았다.

## 산출물과 보존

- 최종: `D:\eGovFrameDev-4.2.0-64bit\workspace\AonFine\ADA.war` (24,340,457 bytes)
- 동일 빌드 파일: `ADA/target/ADA.war`
- 두 WAR의 SHA-256: `B5191DE94F6388A8A27615BAE5123883C4810385AABD9731C9A54403B4939CFC`
- 교체 직전 백업: `ADA.war.bak.20260910002335821`
- 작업 시작 전 백업: `ADA.war.bak.20260909173359`
- 두 기존 WAR 백업의 SHA-256: `CE37C8C5A8016FAAD1F6BED84254EBC6040A007109726B12BAFE0BEEAAFAB947`
- 기존 소스/SQL/pom/README 스냅샷: `ADA-backup-before-cancel-20260909173359/`
- JNDI datasource 설정은 시작 전 스냅샷과 SHA-256이 동일하다. AonFine의 기존 추적 파일 변경은 없다. ADA는 원래 Git 미추적 상태였으며 임의 커밋/스테이징하지 않았다.

## 통과한 검증

`build-local.ps1`로 JavaScript 검사 → UI 동작 테스트 → Maven clean package → WAR 검사 → 기존 WAR 백업/해시 확인 → 루트 WAR 복사/해시 확인을 수행했다.

| 검증 | 결과 |
|---|---|
| Java JUnit | 43건, 실패 0, 오류 0, 건너뜀 0 |
| Node UI DOM 모의 테스트 | 7건, 모두 통과 |
| Java 8 컴파일 | WAR 내 앱 클래스 42개 모두 major version 52 |
| WAR 구성 | 취소/분석 상태 클래스 및 새 JS 포함. H2, Spring Test, 기존 제외 대상 validation JAR 없음 |
| JSON 직렬화 | 명시적 Jackson 2.18.10 포함, MVC 권한/JSON 응답 테스트 통과 |
| 로컬 WAR | 기존 백업 보존, target과 루트 파일 SHA-256 일치 |

Java 테스트에는 실제 로컬 파일 I/O와 JDBC 상태 전이를 사용한 준비/전송/검증 취소, 차단된 입력의 서버 닫기, 닫기로도 풀리지 않는 입력에서 취소 완료 보류, 저장 직전/직후 취소 경쟁, 중복 본문, 사용자별 동시 준비, 잘린 전송, 잘못된 압축, 부분·최종 파일 정리, 삭제 실패, DB 저장 실패, 커밋 응답 유실, DB 확인 불능 시 원본 보존, 재시작 후 처리자 없는 상태가 포함된다. 압축 내부 읽기와 tar 종료 후 gzip drain에서도 취소를 확인한다.

MVC 테스트는 익명/본인/타인/관리자, POST·CSRF, 잘못된 ID, 직접 요청한 분석 취소·재시도 비활성을 검사한다. 분석 스키마 테스트는 신규 설치, 기존 데이터와 백업 보존, 중복 작업/시도 번호/요청 키, 다른 작업 시도 참조 거절, 오래된 시도/버전의 상태 변경 거절 및 재실행 시 백업 덮어쓰기 방지를 검사한다.

UI 테스트는 팝업 닫기와 취소의 구분, 서버 취소 접수 후 abort, 실제 완료 조회 전 재업로드 차단, 통신/세션 오류, 저장 확정 후 늦은 취소, 오래된 응답 무시, 5GB 미만 경계, 분석 준비 안내/비활성 버튼을 검사한다.

## 실환경에서 남은 확인

- H2에서 CUBRID 타임스탬프 키워드를 치환해 JDBC/스키마 논리를 검증했다. 실제 CUBRID 버전의 DDL·락·트랜잭션·권한은 검증하지 않았다. 적용은 [sql/MIGRATION.md](sql/MIGRATION.md)를 따른다.
- 실제 JBoss/WEB/NFS 조합의 전송 버퍼링·차단된 읽기/닫기·NFS 정체와 권한은 검증하지 않았다. 서버 처리 종료·파일 정리를 확인하지 못하면 취소 요청 중/정리 실패를 유지한다.
- UI는 DOM 모의 테스트이며 실제 브라우저/JSP 렌더링 검증은 수행하지 않았다.
- 사용자별 준비 제한은 단일 WAS2 기준이다. 다중 WAS 확장과 장애 후 미확인 업로드 복구는 별도 운영/설계 항목이다.
- 분석 테이블은 준비만 했고 이번 포털은 생성/조회하지 않는다. Worker 미연동 상태에서 분석·취소·재시도·결과를 생성하지 않는다.

## 2026-09-10 추가 변경: 분석 JOB/ATTEMPT 실제 서비스 + Worker 내부 API + 목록 화면 연동

완료 시각: 2026-09-10 02:31 KST. 승인 범위는 위와 동일(ADA 소스·SQL·문서·테스트·로컬 WAR 갱신). 실제 DB 접속/서버 배포는 이번에도 하지 않았다.
이 세션은 이 워크스테이션(WEB/WAS2/Worker/DB 원격 서버 SSH 자격증명 없음, publickey 거부 확인됨)에서만 작업했다.

- 최종: `D:\eGovFrameDev-4.2.0-64bit\workspace\AonFine\ADA.war`, SHA-256 `4B94788B3F999EF75AFEF0F0F8D2165E9BA4B17ECD0592F500A1360D51358AF9`
- 이번 세션 시작 직전 백업: `ADA.war.bak.20260910022531193`(Phase 1 백엔드 반영 직전), `ADA.war.bak.20260910023105626`(목록 화면 연동 직전)
- Java JUnit: 69건, 실패 0(기존 43건 + 신규 26건: `AnalysisServiceTest` 16건, `AnalyzeControllerTest` 5건, `AdaWorkerAuthInterceptorTest` 6건, `UploadAccessTest`에서 stub 전용 테스트 1건 제거)
- Node UI DOM 모의 테스트: 11건, 실패 0(기존 7건 + 신규 4건: 분석 버튼 활성 조건, Analyze 요청·폴링, 취소 반영, 결과 다운로드 미구현 메시지)
- `AnalysisServiceTest`는 H2 위에서 Spring `TransactionInterceptor`+`DataSourceTransactionManager`로 실제 `@Transactional` 프록시를 만들어 JOB→ATTEMPT→DUMP 3-테이블 CAS 트랜잭션의 롤백/경쟁 조건(동시 8-스레드 요청에서 ATTEMPT 중복 생성 없음, heartbeat 유실 시 FAILED/CANCELLED 단정 없이 RECOVERY_REQUIRED, 오래된/타 worker 보고 거절, 동일 완료 보고 재전송 멱등)까지 검증한다.
- 새로 구현: `AnalysisService`(JOB/ATTEMPT 요청·재시도·취소·claim·heartbeat·보고·복구 스캔), `AnalysisMapper`/`AnalysisJdbcMapper`(조건부 UPDATE 기반 CAS), `AdaWorkerAuthInterceptor`(`/worker/**` 전용 토큰+IP 허용목록 인증, 미설정 시 fail-closed), `WorkerAnalysisController`(`/worker/analysis/claim.do`·`heartbeat.do`·`report.do`), `AnalysisRecoveryScheduler`. `AnalyzeController`를 stub에서 실제 서비스 호출로 교체하고 `/dump/analyzeRequest.do`를 신설했다.
- `context-datasource.xml`에 `DataSourceTransactionManager` 빈을, `dispatcher-servlet.xml`에 `tx:annotation-driven`과 Worker 전용 인터셉터 매핑을 추가했다. `ada.properties`에 `ada.analysis.heartbeatTimeoutSeconds`, `ada.worker.apiToken`(기본 빈 값, 운영에서 시스템 프로퍼티로 주입), `ada.worker.allowedIps`를 추가했다.
- 목록 화면(list.jsp/ada-upload.js)에서 Analyze/분석 취소/분석 재시도/분석 결과 버튼을 실제 API에 연결했다. Worker가 없어 QUEUED 이후로는 진행되지 않으며, 이는 의도된 현재 상태다(거짓 완료 없음).

**이번에 검증하지 못한 것(다음 단계)**: 실제 CUBRID에 대한 새 트랜잭션 로직 실행(README/MIGRATION의 2026-09-10 DDL 적용은 이전 세션 기록이며 이번 서비스 코드는 그 위에서 동작하도록 작성했을 뿐 이번 세션에서 재검증하지 않았다), 실제 브라우저 렌더링, Worker 프로세스·MAT·스레드덤프 파서·Claude Code CLI 호출·결과 패키징(모두 미구현), 서버 배포.
