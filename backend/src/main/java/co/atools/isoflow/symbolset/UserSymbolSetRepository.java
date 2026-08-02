// UserSymbolSetRepository.java — 사용자 심볼 세트 저장소
package co.atools.isoflow.symbolset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSymbolSetRepository extends JpaRepository<UserSymbolSet, UUID> {

    List<UserSymbolSet> findAllByOrderByNameAsc();

    Optional<UserSymbolSet> findByName(String name);
}
