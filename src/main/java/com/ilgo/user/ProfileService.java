package com.ilgo.user;

import com.ilgo.user.dto.ProfileResponse;
import com.ilgo.user.dto.ProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(User user) {
        return toResponse(user);
    }

    /** 부분 수정: null 이 아닌 필드만 반영한다(§4 "부분 허용"). */
    @Transactional
    public ProfileResponse update(User user, ProfileUpdateRequest request) {
        User managed = userRepository.findById(user.getId()).orElse(user);
        if (request.outputMode() != null) {
            managed.setDefaultOutputMode(request.outputMode());
        }
        if (StringUtils.hasText(request.lang())) {
            managed.setDefaultLang(request.lang());
        }
        if (request.textScale() != null) {
            managed.setTextScale(request.textScale());
        }
        if (request.deadlineAlarm() != null) {
            managed.setDeadlineAlarm(request.deadlineAlarm());
        }
        return toResponse(userRepository.save(managed));
    }

    private ProfileResponse toResponse(User u) {
        return new ProfileResponse(u.getDefaultOutputMode(), u.getDefaultLang(),
                u.getTextScale(), u.isDeadlineAlarm());
    }
}
