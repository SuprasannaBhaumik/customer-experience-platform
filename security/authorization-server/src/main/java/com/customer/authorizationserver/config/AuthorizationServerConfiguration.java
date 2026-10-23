package com.customer.authorizationserver.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import com.customer.authorizationserver.service.CustomerUserDetails;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration 
@EnableWebSecurity 
public class AuthorizationServerConfiguration {

    /**
     * 
     * Browser requests /oauth2/authorize
        → Chain 1 matches the authorization endpoint
        → Authentication is required
        → Unauthenticated browser is redirected to /login
        → Chain 2 handles /login and authenticates the user
        → Browser resumes the authorization request
        → Chain 1 processes it with the authenticated user
     * 
     */


    //Below filter chain handles all the stuff related to oidc endpoints
    //protects against Invalid clients, invalid OAuth requests, unauthorized token issuance
    @Bean 
    @Order(1)
    SecurityFilterChain oidcSecurityFilterChain(HttpSecurity http) throws Exception {
        
        http.oauth2AuthorizationServer( authorizationserver -> {
            //limits this entire filter chain to the authorization server’s endpoints.
            http.securityMatcher(authorizationserver.getEndpointsMatcher());
            authorizationserver.oidc(Customizer.withDefaults());
        });

        //makes sure all requests within this chain's scope should be authenticated
        http.authorizeHttpRequests( auth -> auth.anyRequest().authenticated());
        
        //exception handling
        http.exceptionHandling( exceptions -> {
            exceptions.defaultAuthenticationEntryPointFor( 
                new LoginUrlAuthenticationEntryPoint("/login"), 
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
            );
        });
        
        return http.build();
    }

    // protects against Users accessing pages/resources without being logged in
    @Bean 
    @Order(2)
    SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
        
        http.authorizeHttpRequests(auth -> 
            auth
                .requestMatchers(
                "/login",
                     "/error",
                    "/.well-known/appspecific/com.chrome.devtools.json"
                )
                .permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        
        return http.build();
    }

    /** This is for customizing the JWT token */
    @Bean 
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            // sub is the customer's unique id (not the username) in both the access and id token,
            // so resource servers can key their data on it and it survives a username change
            if(context.getPrincipal().getPrincipal() instanceof CustomerUserDetails customer) {
                context.getClaims().subject(customer.getCustomerId().toString());
            }

            if(OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication prinicpal = context.getPrincipal();
                List<String> roles = 
                    prinicpal
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(role -> role.startsWith("ROLE_"))
                        .map(role -> role.substring(5))
                        .distinct()
                        .toList();
                
                context.getClaims().claim("roles", roles);

                // every backend api is consumed by the frontend as one api, so access tokens carry a
                // single audience; the authorizer rejects any token whose aud does not contain it.
                // id tokens are not touched here and keep the client id as their audience (OIDC rule)
                context.getClaims().audience(List.of("customer-api"));
            }
        };
    }

    // verifies the BCrypt hashes written by the profile service;
    // users themselves come from CustomUserDetailsService
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /* OIDC Client Registrations */
    @Bean 
    RegisteredClientRepository registeredClientRepository(PasswordEncoder encoder) {
        RegisteredClient client = 
            RegisteredClient
                .withId(UUID.randomUUID().toString())
                .clientId("customer-web")
                .clientSecret(encoder.encode("customer-web-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope("customer.read")
                .scope("customer.write")
                .clientSettings(
                    ClientSettings
                        .builder()
                        .requireAuthorizationConsent(true)
                        .build()
                )
                .build();

        return new InMemoryRegisteredClientRepository(client);
    }


    /**
     * The signing key pair is kept on disk so tokens stay valid across restarts.
     * It is created on first start; in production load it from a keystore or KMS instead.
     */
    @Bean 
    JWKSource<SecurityContext> jwkSource(@Value("${authorization-server.key-dir:./keys}") Path keyDir) throws Exception {
        
        KeyPair keyPair = loadOrCreateRsaKey(keyDir);

        RSAPublicKey publicKey = (RSAPublicKey)keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey)keyPair.getPrivate();

        // the kid is derived from the key itself, so it is the same after every restart
        RSAKey rsaKey = 
                new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyIDFromThumbprint()
                .build();
        
        JWKSet jwkSet = new JWKSet(rsaKey);

        
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair loadOrCreateRsaKey(Path keyDir) throws Exception {
        Path privateKeyFile = keyDir.resolve("signing-key.private");
        Path publicKeyFile = keyDir.resolve("signing-key.public");

        if (Files.exists(privateKeyFile) && Files.exists(publicKeyFile)) {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return new KeyPair(
                keyFactory.generatePublic(new X509EncodedKeySpec(readBase64(publicKeyFile))),
                keyFactory.generatePrivate(new PKCS8EncodedKeySpec(readBase64(privateKeyFile)))
            );
        }

        KeyPair keyPair = generateRsaKey();
        Files.createDirectories(keyDir);
        Files.writeString(publicKeyFile, Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        Files.writeString(privateKeyFile, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        return keyPair;
    }

    private static byte[] readBase64(Path file) throws IOException {
        return Base64.getDecoder().decode(Files.readString(file).trim());
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch(Exception e) {
            throw new IllegalStateException(e);
        }
    }


    @Bean 
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings
            .builder()
            .issuer("http://localhost:9000")
            .build();
    }

    
}
