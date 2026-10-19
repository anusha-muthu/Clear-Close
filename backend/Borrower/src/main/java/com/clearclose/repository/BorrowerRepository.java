package com.clearclose.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.clearclose.entity.Borrowers;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrowers,Long>{
	Optional<Borrowers> findByUsername(String username);
}
