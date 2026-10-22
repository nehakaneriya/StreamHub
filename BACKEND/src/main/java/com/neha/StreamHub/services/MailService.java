package com.neha.StreamHub.services;

public interface MailService {

    void sendOtpEmail(String to, String name, String otp);
}