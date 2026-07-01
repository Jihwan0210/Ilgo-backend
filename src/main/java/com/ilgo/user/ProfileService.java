package com.ilgo.user;

import com.ilgo.user.dto.ProfileResponse;
import com.ilgo.user.dto.ProfileUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(User user) {
        return new ProfileResponse(user.getDefaultOutputMode(), user.getDefaultLang());
    }

    @Transactional
    public ProfileResponse update(User user, ProfileUpdateRequest request) {
        User managed = userRepository.findById(user.getId()).orElse(user);
        managed.setDefaultOutputMode(request.outputMode());
        managed.setDefaultLang(request.lang());
        User saved = userRepository.save(managed);
        return new ProfileResponse(saved.getDefaultOutputMode(), saved.getDefaultLang());
    }
}
