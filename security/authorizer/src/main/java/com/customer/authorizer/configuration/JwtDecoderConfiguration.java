package com.customer.authorizer.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    // this bean replaces Boot's auto-configured decoder, which would otherwise apply this property itself
    @Value("${spring.security.oauth2.resourceserver.jwt.audiences}")
    private String audience;

    @Bean
    public JwtDecoder decoder() {

        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);

        //validates the token was issued by a valid issuer "iss" and exp/nbf
        OAuth2TokenValidator<Jwt> issuerValidator = 
            JwtValidators.createDefaultWithIssuer(issuerUri);

        //will validate the audience
        OAuth2TokenValidator<Jwt> audienceValidator = 
            jwt -> {
                
                if(jwt.getAudience() != null && jwt.getAudience().contains(audience))
                    return OAuth2TokenValidatorResult.success();

                OAuth2Error error = new OAuth2Error("invalid_token", "Token is not intended for " + audience, null);
                return OAuth2TokenValidatorResult.failure(error);
            };
        
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        return decoder;
    }

}
