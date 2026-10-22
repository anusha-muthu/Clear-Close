
package com.clearclose.noc.repository;

import com.clearclose.noc.entity.Noc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NocRepository extends JpaRepository<Noc, Long> {

    List<Noc> findByLoanIdOrderByVersionNumberDesc(Long loanId);

    Optional<Noc> findTopByLoanIdOrderByVersionNumberDesc(Long loanId);

    Optional<Noc> findByCertificateNumber(String certificateNumber);
}
