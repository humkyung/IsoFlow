// PortRef.java — 포트와 그 소유 컴포넌트를 함께 가리키는 참조. Port 자체는 소유자를 모른다
package co.atools.isoflow.engine.topology;

import co.atools.isoflow.engine.model.PipingComponent;
import co.atools.isoflow.engine.model.Port;

public record PortRef(PipingComponent component, Port port) {
}
