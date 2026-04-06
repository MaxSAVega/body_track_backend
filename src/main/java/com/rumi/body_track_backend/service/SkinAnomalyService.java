package com.rumi.body_track_backend.service;

import com.rumi.body_track_backend.model.SkinAnomaly;
import com.rumi.body_track_backend.model.User;
import com.rumi.body_track_backend.repository.SkinAnomalyRepository;
import com.rumi.body_track_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SkinAnomalyService {

    private final SkinAnomalyRepository skinAnomalyRepository;
    private final UserRepository userRepository;

    public SkinAnomaly create(SkinAnomaly anomaly, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        anomaly.setUser(user);
        return skinAnomalyRepository.save(anomaly);
    }

    public List<SkinAnomaly> getByUser(String email) {
        return skinAnomalyRepository.findByUserEmail(email);
    }

    public void delete(Long id) {
        skinAnomalyRepository.deleteById(id);
    }
}
