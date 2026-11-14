package com.voip.voice.endpoint.interfaces;

import com.voip.voice.endpoint.dtos.EndpointDetails;

public interface EndpointCRUDInterface {
    public String createEndpoint(EndpointDetails endpointCreateDetails) throws Exception;
    public String updateEndpoint(EndpointDetails endpoint) throws Exception;
    public String deleteEndpoint(int id)  throws Exception;

}
