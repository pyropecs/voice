package com.voip.voice.endpointmanagement.interfaces;

import com.voip.voice.entities.Endpoint;

import java.util.List;

public interface EndpointRetrieveInterface {
    Endpoint getEndpointById(int id) throws Exception;
    List<Endpoint> getAllEndpoints() throws Exception;
}
