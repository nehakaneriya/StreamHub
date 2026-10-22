package com.neha.VideoStreamApp.exception;

/**
 * OTP / verification email deliver nahi ho paya.
 *
 * Ye exception is liye hai ki mail failure SILENT na ho. Pehle MailService
 * mail bhejne me fail hone par bhi success return karta tha — user ko
 * "OTP has expired" dikhta tha aur usse pata hi nahi chalta tha ki
 * email gaya hi nahi.
 */
public class MailDeliveryException extends RuntimeException {

    public MailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
