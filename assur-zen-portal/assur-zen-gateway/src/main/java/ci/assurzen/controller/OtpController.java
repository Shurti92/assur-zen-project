package ci.assurzen.controller;

import ci.assurzen.dto.SendOtpRequest;
import ci.assurzen.services.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${env.ws}otp")
@Tag(name = "Otp", description = "Envoi de code de verification par SMS")
@RequiredArgsConstructor
public class OtpController {

    private final OtpService otpService;

    @PostMapping("/send")
    @Operation(operationId = "sendOtp", summary = "Envoyer un code de verification (OTP) a 6 chiffres par SMS")
    @SecurityRequirements
    public ResponseEntity<Void> send(@Valid @RequestBody SendOtpRequest request) {
        otpService.sendOtp(request.phone());
        return ResponseEntity.noContent().build();
    }
}
