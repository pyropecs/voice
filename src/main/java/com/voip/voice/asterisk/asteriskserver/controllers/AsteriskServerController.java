package com.voip.voice.asterisk.asteriskserver.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/server")
public class AsteriskServerController {


    @GetMapping("create")
    public ResponseEntity<String> createServer(){

    }

}
