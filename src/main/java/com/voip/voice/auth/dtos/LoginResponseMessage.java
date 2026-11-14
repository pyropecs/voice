package com.voip.voice.auth.dtos;

public record LoginResponseMessage(String message,String token) {

    public  LoginResponseMessage (String message, String token) {
        this.message = message;
        this.token = token;
    }
}
