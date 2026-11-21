package com.voip.voice.asterisk.asteriskserver.controllers;

import com.voip.voice.asterisk.asteriskserver.dtos.AsteriskServerDetails;
import com.voip.voice.asterisk.asteriskserver.dtos.AsteriskServerResponseDetails;
import com.voip.voice.asterisk.asteriskserver.services.AsteriskServerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/server")
public class AsteriskServerController {

    private final AsteriskServerService asteriskServerService;

    public AsteriskServerController(AsteriskServerService asteriskServerService) {
        this.asteriskServerService = asteriskServerService;
    }

    @PostMapping("create")
    public ResponseEntity<AsteriskServerResponseDetails> createServer(@RequestBody AsteriskServerDetails asteriskServerRequestDetails) {
        try {
            String  result = asteriskServerService.createAsteriskServer(asteriskServerRequestDetails);
            if (result.equals("Asterisk server created Successfully")) {
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server details created Successfully"), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new AsteriskServerResponseDetails(result), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("update/{id}")
    public ResponseEntity<AsteriskServerResponseDetails> updateServer(@PathVariable int id, @RequestBody AsteriskServerDetails asteriskServerRequestDetails) {
        try {
            boolean status = asteriskServerService.updateAsteriskServer(id, asteriskServerRequestDetails);
            if (status) {
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server details updated Successfully"), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server details updated Failed"), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping("delete/{id}")
    public ResponseEntity<AsteriskServerResponseDetails> deleteServer(@PathVariable int id) {
        try {
            String status = asteriskServerService.deleteAsteriskServer(id);
            if (status.equals("Asterisk server deleted Successfully")) {
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server details deleted Successfully"), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(new AsteriskServerResponseDetails(status), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("{id}")
    public ResponseEntity<?> getServerById(@PathVariable int id) {
        try {
            AsteriskServerDetails asteriskServerDetails = asteriskServerService.getAsteriskServer(id);
            if (asteriskServerDetails != null) {
                return new ResponseEntity<>(asteriskServerDetails, HttpStatus.OK);
            }else{
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server not found"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return new ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("all")
    public ResponseEntity<?> getAllServers() {
        try{
            List<AsteriskServerDetails> asteriskServersDetails = asteriskServerService.getAllAsteriskServers();
            return new ResponseEntity<>(asteriskServersDetails, HttpStatus.OK);
        } catch (Exception e) {
            return new  ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

    @GetMapping("search")
    public ResponseEntity<?> getAsteriskByIpaddress(@RequestParam String ipAddress){
        try{
            AsteriskServerDetails asteriskServerDetails = asteriskServerService.getAsteriskServerByIPaddress(ipAddress);
            if(asteriskServerDetails != null) {

            return new ResponseEntity<>(asteriskServerDetails, HttpStatus.OK);
            }else{
                return new ResponseEntity<>(new AsteriskServerResponseDetails("Asterisk server not found"), HttpStatus.NOT_FOUND);
            }
        }catch (Exception e){
            return new ResponseEntity<>(new AsteriskServerResponseDetails(e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
