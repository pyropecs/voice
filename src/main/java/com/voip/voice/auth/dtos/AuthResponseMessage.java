package com.voip.voice.auth.dtos;

public record AuthResponseMessage(String message) {

    public AuthResponseMessage (String message) {
        this.message = message;
    }
}
