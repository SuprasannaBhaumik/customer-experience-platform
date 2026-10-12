package com.customer.authorizationserver.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
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
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

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

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails normalUser =
            User.withUsername("suprasanna")
                .password(encoder.encode("bhaumik"))
                .roles("USER")
                .build();

        UserDetails admin = 
            User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("USER", "ADMIN")
                .build();

        return new InMemoryUserDetailsManager(
            normalUser,
            admin
        );
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
                .scope("product.read")
                .scope("product.write")
                .clientSettings(
                    ClientSettings
                        .builder()
                        .requireAuthorizationConsent(true)
                        .build()
                )
                .build();

        return new InMemoryRegisteredClientRepository(client);
    }


    @Bean 
    JWKSource<SecurityContext> jwkSource() {
        
        KeyPair keyPair = generateRsaKey();

        RSAPublicKey publicKey = (RSAPublicKey)keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey)keyPair.getPrivate();

        RSAKey rsaKey = 
                new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        
        JWKSet jwkSet = new JWKSet(rsaKey);

        
        return new ImmutableJWKSet<>(jwkSet);
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

    /** This is for customizing the JWT token */
    @Bean 
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if(OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication prinicpal = context.getPrincipal();
                List<String> roles = 
                    prinicpal
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(role -> role.startsWith("ROLE_"))
                        .map(role -> role.substring(5))
                        .toList();
                
                context.getClaims().claim("roles", roles);
                context.getClaims().audience(List.of("customer-api"));
            }
        };
    }
}
