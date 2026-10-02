package com.example.orderbook.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByStatusInOrderByIdAsc(Collection<String> statuses);

    Optional<OrderEntity> findByIdAndUsername(Long id, String username);

    Page<OrderEntity> findByUsername(String username, Pageable pageable);
}