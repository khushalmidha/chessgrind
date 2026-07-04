package com.mateforge.api.controller;

import com.mateforge.api.dto.AnalyticsDtos.FavoriteDto;
import com.mateforge.api.dto.AnalyticsDtos.FavoriteRequest;
import com.mateforge.api.dto.AnalyticsDtos.ProfileDto;
import com.mateforge.api.dto.AnalyticsDtos.ProgressSummary;
import com.mateforge.api.dto.TrainingDtos.PlayerProfileReportDto;
import com.mateforge.api.security.UserPrincipal;
import com.mateforge.api.service.AnalyticsService;
import com.mateforge.api.service.GeminiReportService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final GeminiReportService reports;

    public AnalyticsController(AnalyticsService analytics, GeminiReportService reports) {
        this.analytics = analytics;
        this.reports = reports;
    }

    @GetMapping("/analytics/progress")
    ProgressSummary progress(@AuthenticationPrincipal UserPrincipal principal) {
        return analytics.progress(principal);
    }

    @GetMapping("/analytics/profile")
    ProfileDto profile(@AuthenticationPrincipal UserPrincipal principal) {
        return analytics.profile(principal);
    }

    @GetMapping("/analytics/profile-report")
    PlayerProfileReportDto profileReport(
        @RequestParam(defaultValue = "false") boolean refresh,
        @AuthenticationPrincipal UserPrincipal principal
    ) {
        return reports.profileReport(principal, refresh);
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
