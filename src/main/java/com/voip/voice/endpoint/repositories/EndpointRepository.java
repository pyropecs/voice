package com.voip.voice.endpoint.repositories;

import com.voip.voice.entities.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EndpointRepository extends JpaRepository<Endpoint,Integer> {
    public Endpoint findById(int id);

    boolean existsByEndpoint(String endpoint);

    Endpoint findByEndpoint(String endpoint);
}
