// LabelPlacer.java — 이미 놓인 라벨과 겹치지 않는 자리를 찾는다. 자동 도면 품질의 체감 차이는 대부분 여기서 갈린다
package co.atools.isoflow.engine.layout;

import java.util.ArrayList;
import java.util.List;

public final class LabelPlacer {

    /** 축 정렬 경계 상자 */
    public record Box(double minX, double minY, double maxX, double maxY) {

        public boolean overlaps(Box o) {
            return minX < o.maxX && o.minX < maxX && minY < o.maxY && o.minY < maxY;
        }

        public Box grown(double m) {
            return new Box(minX - m, minY - m, maxX + m, maxY + m);
        }

        /** 중심과 크기로 상자를 만든다 */
        public static Box centred(double cx, double cy, double w, double h) {
            return new Box(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        }
    }

    /**
     * 점유된 선분.
     *
     * <p><b>선분을 상자로 바꿔 등록하면 안 된다.</b> 등각도의 중심선은 대부분 대각선이고,
     * 긴 대각선의 경계 상자는 도면 절반을 덮는다. 그렇게 하면 라벨이 놓일 자리가 남지 않아
     * 전부 강제 배치로 떨어지고, 결국 겹침 회피가 없는 것과 같아진다.
     */
    public record Seg(double x1, double y1, double x2, double y2) {

        /** 이 선분이 상자를 지나가는가 — Liang-Barsky 절단으로 판정한다 */
        public boolean intersects(Box b) {
            double dx = x2 - x1;
            double dy = y2 - y1;

            // 길이 0 인 선분은 점 포함 검사
            if (dx == 0 && dy == 0) {
                return x1 >= b.minX() && x1 <= b.maxX() && y1 >= b.minY() && y1 <= b.maxY();
            }

            double t0 = 0, t1 = 1;
            double[] p = {-dx, dx, -dy, dy};
            double[] q = {x1 - b.minX(), b.maxX() - x1, y1 - b.minY(), b.maxY() - y1};

            for (int i = 0; i < 4; i++) {
                if (p[i] == 0) {
                    // 해당 축과 평행 — 그 축에서 상자 밖이면 만날 일이 없다
                    if (q[i] < 0) return false;
                    continue;
                }
                double r = q[i] / p[i];
                if (p[i] < 0) {
                    if (r > t1) return false;
                    if (r > t0) t0 = r;
                } else {
                    if (r < t0) return false;
                    if (r < t1) t1 = r;
                }
            }
            return t0 <= t1;
        }
    }

    private final List<Box> occupied = new ArrayList<>();
    private final List<Seg> segments = new ArrayList<>();
    /** 라벨 사이에 남길 최소 여백 */
    private final double padding;
    private int placed;
    private int collided;

    public LabelPlacer(double padding) {
        this.padding = padding;
    }

    /** 자리가 비었는지만 본다 — 상태를 바꾸지 않는다 */
    public boolean isFree(Box box) {
        Box padded = box.grown(padding);
        for (Box b : occupied) {
            if (padded.overlaps(b)) return false;
        }
        for (Seg s : segments) {
            if (s.intersects(padded)) return false;
        }
        return true;
    }

    /** 비어 있으면 자리를 차지하고 true. 이미 겹치면 아무것도 하지 않고 false */
    public boolean tryOccupy(Box box) {
        if (!isFree(box)) return false;
        occupied.add(box.grown(padding));
        return true;
    }

    /** 겹치든 말든 자리를 차지한다. 라벨이 아닌 것(심볼 등)을 미리 등록할 때 쓴다 */
    public void forceOccupy(Box box) {
        occupied.add(box.grown(padding));
    }

    /**
     * 라벨을 놓는다.
     *
     * <p><b>{@code gaveUp} 은 호출측이 알려 줘야 한다.</b> 여기서 {@code isFree} 로 되짚으면
     * 러닝 치수의 이웃 칸끼리 붙어 있는 것까지 실패로 세게 된다 —
     * 그건 원래 나란히 놓이는 것이지 회피 실패가 아니다.
     *
     * @param gaveUp 후보를 모두 시도했는데도 빈 자리가 없어 그냥 놓은 경우
     */
    public void placeLabel(Box box, boolean gaveUp) {
        placed++;
        if (gaveUp) collided++;
        occupied.add(box.grown(padding));
    }

    /** 놓인 라벨 수 */
    public int placedCount() {
        return placed;
    }

    /** 자리를 못 찾아 겹친 채로 놓은 라벨 수 */
    public int collidedCount() {
        return collided;
    }

    /** 배치에 실패한 라벨 비율 (0~1). 도면이 얼마나 빽빽한지를 나타낸다 */
    public double collisionRatio() {
        return placed == 0 ? 0 : (double) collided / placed;
    }

    /**
     * 선(중심선·치수선 등)을 점유로 등록한다. 라벨은 이 선을 피해 놓인다.
     * 등록된 선분 자체는 다른 선분을 막지 않는다 — 도면 선끼리는 원래 교차한다.
     */
    public void occupySegment(double x1, double y1, double x2, double y2) {
        segments.add(new Seg(x1, y1, x2, y2));
    }

    /** 문자열이 차지할 대략적인 폭. 정확한 폰트 메트릭 대신 평균 자폭으로 추정한다 */
    public static double estimateTextWidth(String text, double height) {
        return text == null ? 0 : text.length() * height * 0.62;
    }

    public int occupiedCount() {
        return occupied.size();
    }

    public int segmentCount() {
        return segments.size();
    }
}
