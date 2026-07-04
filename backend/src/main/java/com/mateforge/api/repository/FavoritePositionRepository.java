package com.mateforge.api.repository;

import com.mateforge.api.model.AppUser;
import com.mateforge.api.model.FavoritePosition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FavoritePositionRepository extends JpaRepository<FavoritePosition, UUID> {
    List<FavoritePosition> findByUserOrderByCreatedAtDesc(AppUser user);

    long countByUser(AppUser user);
}
