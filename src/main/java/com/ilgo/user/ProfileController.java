package com.ilgo.user;

import com.ilgo.common.CurrentUser;
import com.ilgo.user.dto.ProfileResponse;
import com.ilgo.user.dto.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/profile")
@Tag(name = "Profile", description = "기본 모드/언어 설정")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "프로필 조회")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse get(@CurrentUser User user) {
        return profileService.get(user);
    }

    @Operation(summary = "프로필 저장")
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ProfileResponse update(@Valid @RequestBody ProfileUpdateRequest request, @CurrentUser User user) {
        return profileService.update(user, request);
    }
}
