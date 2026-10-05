package com.starstack.repository;

import com.starstack.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    @Query("SELECT o FROM Order o WHERE o.username = :username AND o.status = 'pending'")
    List<Order> findPendingByUsername(@Param("username") String username);

    @Query("SELECT o FROM Order o WHERE o.status = 'pending'")
    List<Order> findAllPending();
}