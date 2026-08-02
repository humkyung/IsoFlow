// Rebaser.java — 절대 플랜트 좌표를 로컬 좌표로 옮긴다. three.js 의 float32 정밀도 문제를 막는 필수 단계
package co.atools.isoflow.engine.geometry;

import co.atools.isoflow.engine.model.Pipeline;
import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;
import co.atools.isoflow.engine.model.Vec3;

/**
 * PCF 좌표는 {@code 5650130.600} 같은 절대 플랜트 좌표다.
 * three.js 는 float32(유효자리 ~7)라 이 값을 그대로 넣으면 mm 단위 정밀도가 남지 않아
 * 떨림·z-fighting 이 발생한다.
 *
 * <p>파이프라인 bounding box 의 중심을 {@code origin} 으로 빼고 로컬 좌표만 내보낸다.
 * {@code 로컬좌표 + origin = 원본 좌표} 이므로 되돌릴 수 있다.
 */
public final class Rebaser {

    private Rebaser() {
    }

    /** 파이프라인의 모든 포트를 bbox 중심 기준 로컬 좌표로 옮기고 origin 을 기록한다 */
    public static void rebase(Pipeline pipeline) {
        Vec3 origin = boundingBoxCentre(pipeline);
        if (origin == null) return;

        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) {
                p.setPosition(p.position().minus(origin));
            }
        }
        pipeline.setOrigin(origin);
    }

    /** 모든 포트를 감싸는 bounding box 의 중심. 포트가 하나도 없으면 null */
    public static Vec3 boundingBoxCentre(Pipeline pipeline) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;

        for (PipingComponent c : pipeline.components()) {
            for (Port p : c.ports()) {
                Vec3 v = p.position();
                minX = Math.min(minX, v.x());
                minY = Math.min(minY, v.y());
                minZ = Math.min(minZ, v.z());
                maxX = Math.max(maxX, v.x());
                maxY = Math.max(maxY, v.y());
                maxZ = Math.max(maxZ, v.z());
                any = true;
            }
        }
        if (!any) return null;
        // 정수 mm 로 반올림해 origin 자체가 지저분한 소수가 되지 않게 한다
        return new Vec3(
                Math.rint((minX + maxX) / 2),
                Math.rint((minY + maxY) / 2),
                Math.rint((minZ + maxZ) / 2));
    }
}
