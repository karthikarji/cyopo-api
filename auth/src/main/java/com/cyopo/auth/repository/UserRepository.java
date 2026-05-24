package com.cyopo.auth.repository;

import com.cyopo.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = """
    SELECT * FROM auth.users
    WHERE (notification_preferences->>'weeklyDigest')::boolean = true
    AND status = 'ACTIVE'
    """, nativeQuery = true)
    List<User> findUsersWithWeeklyDigestEnabled();

    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name,
            String email,
            Pageable pageable);

    List<User> findTop5ByEmailContainingIgnoreCase(String email);
}