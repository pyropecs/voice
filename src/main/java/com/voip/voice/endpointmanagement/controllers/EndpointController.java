package com.voip.voice.endpointmanagement.controllers;

import com.voip.voice.endpointmanagement.dtos.EndpointDetails;
import com.voip.voice.endpointmanagement.dtos.EndpointResponseDetails;
import com.voip.voice.endpointmanagement.services.EndpointManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/endpoint")
public class EndpointController {

    public final EndpointManagementService endpointManagementService;
    public EndpointController(EndpointManagementService endpointManagementService) {
        this.endpointManagementService = endpointManagementService;
    }

    @PostMapping("create")
    public ResponseEntity<EndpointResponseDetails> createEndpoint(@RequestBody  EndpointDetails endpointDetails) {
        try{
            return new ResponseEntity<>(new EndpointResponseDetails(endpointManagementService.createEndpoint(endpointDetails)), HttpStatus.OK);
        }catch(Exception e){
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new EndpointResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("{id}")
    public ResponseEntity<?> getEndpoint(@PathVariable Integer id) {
        try{
            return new ResponseEntity<>(endpointManagementService.getEndpointById(id),HttpStatus.OK);
        }catch(Exception e){
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new EndpointResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @DeleteMapping("delete/{id}")
    public ResponseEntity<?> deleteEndpoint(@PathVariable  Integer id) {
        try {
            return new ResponseEntity<>(new  EndpointResponseDetails(endpointManagementService.deleteEndpoint(id)), HttpStatus.OK);
        }catch(Exception e){
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new EndpointResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("update")
    public ResponseEntity<EndpointResponseDetails> updateEndpoint(@RequestBody EndpointDetails endpointDetails) {
        try{
            return new  ResponseEntity<>(new EndpointResponseDetails(endpointManagementService.updateEndpoint(endpointDetails)), HttpStatus.OK);
        }catch(Exception e){
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new EndpointResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("all")
    public ResponseEntity<?> getAllEndpoints() {
        try{
            return new ResponseEntity<>(endpointManagementService.getAllEndpoints(), HttpStatus.OK);
        }catch(Exception e){
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new EndpointResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
