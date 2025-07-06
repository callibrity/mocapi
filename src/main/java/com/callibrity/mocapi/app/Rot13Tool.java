package com.callibrity.mocapi.app;


import com.callibrity.mocapi.mcp.method.Tool;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Component;

@Component
public class Rot13Tool {

    @Tool(description = "A ROT-13 encryption/decryption utility.")
    public Rot13Response rot13(@Schema(description = "The plain text or cipher text to be rotated.") String plainText) {
        StringBuilder result = new StringBuilder();

        for (char c : plainText.toCharArray()) {
            if ('a' <= c && c <= 'z') {
                result.append((char) ((c - 'a' + 13) % 26 + 'a'));
            } else if ('A' <= c && c <= 'Z') {
                result.append((char) ((c - 'A' + 13) % 26 + 'A'));
            } else {
                result.append(c); // leave non-alphabetic characters unchanged
            }
        }

        return new Rot13Response(result.toString());
    }

    public record Rot13Response(String cipherText) {
    }
}
