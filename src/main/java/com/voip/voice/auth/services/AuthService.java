package com.voip.voice.auth.services;


import com.voip.voice.auth.dtos.LoginRequestDetails;
import com.voip.voice.auth.dtos.LoginResponseMessage;
import com.voip.voice.auth.dtos.SignupDetails;
import com.voip.voice.auth.repositories.UserRepository;
import com.voip.voice.entities.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,AuthenticationManager authenticationManager,JWTService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public User register(SignupDetails signupDetails) throws IllegalArgumentException {
        if(userRepository.existsByUsername(signupDetails.username())){
            throw new IllegalArgumentException("Username is already exists");
        }
        User user = new User();
        user.setUsername(signupDetails.username());
        user.setPassword(passwordEncoder.encode(signupDetails.password()));
        userRepository.save(user);
        return user;
    }


    public LoginResponseMessage login(LoginRequestDetails loginResponseDetails) throws BadCredentialsException {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginResponseDetails.username(), loginResponseDetails.password()));
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);
        if(authentication.isAuthenticated()){
            String generateToken = jwtService.generateToken(loginResponseDetails.username());
         return new LoginResponseMessage("Login Successful",generateToken);
        }else{
            return new LoginResponseMessage("Incorrect Username or Password","");
        }
    }
}
