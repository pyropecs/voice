package com.voip.voice.endpointmanagement.services;

import com.voip.voice.auth.repositories.UserRepository;
import com.voip.voice.endpointmanagement.dtos.EndpointDetails;
import com.voip.voice.endpointmanagement.interfaces.EndpointCRUDInterface;
import com.voip.voice.endpointmanagement.interfaces.EndpointRetrieveInterface;
import com.voip.voice.endpointmanagement.repositories.EndpointRepository;
import com.voip.voice.entities.Endpoint;
import com.voip.voice.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
@Service
public class EndpointManagementService implements EndpointCRUDInterface, EndpointRetrieveInterface {
    private final EndpointRepository endpointRepository;
    private final UserRepository userRepository;
    public EndpointManagementService(EndpointRepository endpointRepository,UserRepository userRepository) {
        this.endpointRepository = endpointRepository;
        this.userRepository = userRepository;
    }



    @Override
    @Transactional
    public String createEndpoint(EndpointDetails endpointCreateDetails) throws Exception {
        if(endpointCreateDetails.phoneNumber().isEmpty() || endpointCreateDetails.password().isEmpty()) {
           return "Please Enter the Phone Number and Password";
        }
        if(endpointCreateDetails.sipServer().isEmpty()) {
            return "Please Enter the Sip Server Address";
        }
        Endpoint endpoint = new Endpoint();
        endpoint.setEndpoint(endpointCreateDetails.phoneNumber());
        endpoint.setPassword(endpointCreateDetails.password());
        endpoint.setSipServer(endpointCreateDetails.sipServer());
        User user = userRepository.getUserByUsername(endpointCreateDetails.username());
        endpoint.setUser(user);
        endpointRepository.save(endpoint);
        return "Endpoint created successfully";
    }



    @Override
    @Transactional
    public String updateEndpoint(EndpointDetails endpointCreateDetails) throws Exception {

        if(endpointCreateDetails.phoneNumber().isEmpty() || endpointCreateDetails.password().isEmpty()) {
            return "Please Enter the Phone Number and Password";
        }
        if(endpointCreateDetails.sipServer().isEmpty()) {
            return "Please Enter the Sip Server Address";
        }
        Endpoint endpoint = endpointRepository.findByEndpoint(endpointCreateDetails.phoneNumber());
        if(endpoint == null) {
            return "Endpoint " + endpointCreateDetails.phoneNumber() + " not found";
        }
        User user = userRepository.getUserByUsername(endpointCreateDetails.username());
        endpoint.setPassword(endpointCreateDetails.password());
        endpoint.setSipServer(endpointCreateDetails.sipServer());
        endpoint.setUser(user);
        endpointRepository.save(endpoint);

        return "Endpoint updated successfully";

    }

    @Override
    @Transactional
    public String deleteEndpoint(int id) throws Exception {
        if(endpointRepository.existsById(id)) {
            endpointRepository.deleteById(id);
            return "Endpoint deleted Sucessfully";
        }else{
            return "Endpoint not found";
        }
    }

    @Override
    public Endpoint getEndpointById(int id) throws Exception {
        if(endpointRepository.existsById(id)) {
            return endpointRepository.findById(id);
        }else{
            return null;
        }
    }

    @Override
    public List<Endpoint> getAllEndpoints() throws Exception{
        return endpointRepository.findAll();
    }
}
