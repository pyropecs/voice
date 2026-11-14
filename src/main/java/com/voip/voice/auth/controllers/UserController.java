package com.voip.voice.auth.controllers;

import com.voip.voice.auth.dtos.AuthResponseMessage;
import com.voip.voice.auth.dtos.LoginRequestDetails;
import com.voip.voice.auth.dtos.LoginResponseMessage;
import com.voip.voice.auth.dtos.SignupDetails;
import com.voip.voice.auth.services.AuthService;
import com.voip.voice.entities.User;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;


@RestController()
@RequestMapping("/auth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("signup")
    public ResponseEntity<AuthResponseMessage> registerUser(@RequestBody SignupDetails signupDetails) {

        try {
            User user = authService.register(signupDetails);
            if (user != null) {
                AuthResponseMessage responseMessage = new AuthResponseMessage("User Registered Successfully");
                return new ResponseEntity<>(responseMessage, HttpStatus.OK);
            }else {
                AuthResponseMessage responseMessage = new AuthResponseMessage("User Registration Failed");
                return new ResponseEntity<>(responseMessage,HttpStatus.BAD_REQUEST);
            }
        }catch(IllegalArgumentException e){
            System.err.println("Exception occurred during the signing up the user " + e);
            AuthResponseMessage responseMessage = new AuthResponseMessage(e.getMessage());
            return new ResponseEntity<>(responseMessage,HttpStatus.BAD_REQUEST);
        }catch (Exception e) {
            System.err.println("Exception occurred during the signing up the user " + e);
            AuthResponseMessage responseMessage = new AuthResponseMessage(e.getMessage());
            return new ResponseEntity<>(responseMessage,HttpStatus.INTERNAL_SERVER_ERROR);
        }


    }

    @PostMapping("login")
    public ResponseEntity<LoginResponseMessage> loginUser(@RequestBody LoginRequestDetails loginRequestDetails) {
        try{
          LoginResponseMessage responseMessage = authService.login(loginRequestDetails);
          if (responseMessage.message().equals("Login Successful")) {
              return new ResponseEntity<>(responseMessage, HttpStatus.OK);
          }else{
              return new ResponseEntity<>(responseMessage,HttpStatus.BAD_REQUEST);
          }
        }catch(BadCredentialsException e) {
            LoginResponseMessage responseMessage = new LoginResponseMessage("Invalid username or password","");
            e.printStackTrace();
            return new ResponseEntity<>(responseMessage,HttpStatus.BAD_REQUEST);
        }catch(Exception e){
            LoginResponseMessage responseMessage = new LoginResponseMessage(e.getMessage(),"");
            e.printStackTrace();
            return new ResponseEntity<>(responseMessage,HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
