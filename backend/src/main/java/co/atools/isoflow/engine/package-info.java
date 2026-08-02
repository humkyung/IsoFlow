/**
 * 등각도 생성 엔진 — PCF/IDF 파싱부터 도면 요소 생성까지의 순수 도메인 로직.
 *
 * <p><b>이 패키지와 하위 패키지는 Spring 에 의존하지 않는다.</b>
 * REST·DB·파일저장은 {@code pipeline} / {@code isometric} / {@code storage} 가 담당하고,
 * 엔진은 값 객체만 주고받는다. 덕분에 CLI·배치·단위테스트에서 엔진만 따로 돌릴 수 있다.
 * 이 규칙은 {@code EngineArchitectureTest} 가 강제한다.
 *
 * <p>구성:
 * <ul>
 *   <li>{@code parser}   — PCF/IDF 파서, 단위 정규화</li>
 *   <li>{@code model}    — 중립 도메인 모델(IR)</li>
 *   <li>{@code topology} — 조인트 병합, 연결 그래프, 진단</li>
 *   <li>{@code geometry} — 축 판정, 등각 투영, skew, 좌표 리베이스</li>
 *   <li>{@code layout}   — 경로 배치, 치수, 주석, 시트 분할, 겹침 회피</li>
 *   <li>{@code symbol}   — SKEY 조회, 2D 심볼 라이브러리</li>
 *   <li>{@code scene}    — Scene3D / Scene2D 빌더</li>
 * </ul>
 */
package co.atools.isoflow.engine;
