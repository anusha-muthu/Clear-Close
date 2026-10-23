
package com.clearclose.noc.repository;

import com.clearclose.noc.entity.NocAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NocAuditRepository extends JpaRepository<NocAudit, Long> {

    List<NocAudit> findByNocIdOrderByActionAtDesc(Long nocId);
}
