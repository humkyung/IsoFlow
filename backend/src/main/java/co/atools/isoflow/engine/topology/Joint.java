// Joint.java — 좌표 허용오차로 병합된 접합점. 여기 모인 포트들이 서로 연결된 것으로 본다
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Joint {

    private final String key;
    private final Vec3 position;
    private final List<PortRef> ports = new ArrayList<>();
    /** END-CONNECTION-PIPELINE / END-POSITION-OPEN 이 이 위치를 종단으로 표시했는지 */
    private boolean terminator;
    /** 모재 배관 중간에 올라탄 분기점인지 (올렛 등) */
    private boolean attachedToHost;

    Joint(String key, Vec3 position) {
        this.key = key;
        this.position = position;
    }

    void add(PortRef ref) {
        ports.add(ref);
    }

    void markTerminator() {
        this.terminator = true;
    }

    public String key() {
        return key;
    }

    public Vec3 position() {
        return position;
    }

    public List<PortRef> ports() {
        return Collections.unmodifiableList(ports);
    }

    public int degree() {
        return ports.size();
    }

    void markAttachedToHost() {
        this.attachedToHost = true;
    }

    /** 라인의 끝(다른 라인/장비로 이어지거나 열린 끝)으로 선언된 지점인지 */
    public boolean isTerminator() {
        return terminator;
    }

    /** 모재 배관 선분 중간에 붙은 분기점인지 — 차수 1이어도 미연결이 아니다 */
    public boolean isAttachedToHost() {
        return attachedToHost;
    }

    @Override
    public String toString() {
        return "Joint[%s] deg=%d%s".formatted(key, degree(), terminator ? " terminator" : "");
    }
}
