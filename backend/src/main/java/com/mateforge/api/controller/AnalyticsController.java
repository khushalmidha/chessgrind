package com.mateforge.api.controller;

import com.mateforge.api.dto.AnalyticsDtos.FavoriteDto;
import com.mateforge.api.dto.AnalyticsDtos.FavoriteRequest;
import com.mateforge.api.dto.AnalyticsDtos.ProgressSummary;
import com.mateforge.api.security.UserPrincipal;
import com.mateforge.api.service.AnalyticsService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/analytics/progress")
    ProgressSummary progress(@AuthenticationPrincipal UserPrincipal principal) {
        return analytics.progress(principal);
    }

    @GetMapping("/favorites")
    List<FavoriteDto> favorites(@AuthenticationPrincipal UserPrincipal principal) {
        return analytics.favorites(principal);
    }

    @PostMapping("/favorites")
    FavoriteDto addFavorite(@AuthenticationPrincipal UserPrincipal principal, @RequestBody FavoriteRequest request) {
        return analytics.addFavorite(principal, request);
    }

    @GetMapping("/leaderboard")
    Object leaderboard() {
        return analytics.leaderboard();
    }
}
