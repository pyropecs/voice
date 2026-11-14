package com.voip.voice.endpoint.interfaces;

import com.voip.voice.entities.Endpoint;

import java.util.List;

public interface EndpointRetrieveInterface {
    Endpoint getEndpointById(int id) throws Exception;
    List<Endpoint> getAllEndpoints() throws Exception;
}
