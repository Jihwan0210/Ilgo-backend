package com.ilgo.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * device_id 로 사용자를 조회하고 없으면 생성(upsert).
     * 동시 최초 요청으로 unique 충돌이 나면 재조회한다.
     */
    @Transactional
    public User getOrCreate(String deviceId) {
        return userRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    try {
                        return userRepository.save(new User(deviceId));
                    } catch (DataIntegrityViolationException race) {
                        return userRepository.findByDeviceId(deviceId)
                                .orElseThrow(() -> race);
                    }
                });
    }
}
