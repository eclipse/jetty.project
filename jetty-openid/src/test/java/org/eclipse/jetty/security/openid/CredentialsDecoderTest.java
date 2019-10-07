package org.eclipse.jetty.security.openid;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CredentialsDecoderTest
{
    public static Stream<Arguments> paddingExamples()
    {
        return Stream.of(
            Arguments.of("XXXX", "XXXX"),
            Arguments.of("XXX", "XXX="),
            Arguments.of("XX", "XX==")
            );
    }

    public static Stream<Arguments> badPaddingExamples()
    {
        return Stream.of(
            Arguments.of("X"),
            Arguments.of("XXXXX")
        );
    }

    @ParameterizedTest
    @MethodSource("paddingExamples")
    public void testPaddingBase64(String input, String expected)
    {
        byte[] actual = CredentialsDecoder.padJWTSection(input);
        assertThat(actual, is(expected.getBytes()));
    }

    @ParameterizedTest
    @MethodSource("badPaddingExamples")
    public void testPaddingInvalidBase64(String input)
    {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> CredentialsDecoder.padJWTSection(input));

        assertThat(error.getMessage(), is("Not a valid Base64-encoded string"));
    }

    @Test
    public void testEncodeDecode()
    {
        String issuer = "example.com";
        String subject = "1234";
        String clientId = "1234.client.id";
        String name = "Bob";
        long expiry = 123;

        // Create a fake ID Token.
        String claims = JwtEncoder.createIdToken(issuer, clientId, subject, name, expiry);
        String idToken = JwtEncoder.encode(claims);

        // Decode the ID Token and verify the claims are the same.
        Map<String, Object> decodedClaims = CredentialsDecoder.decode(idToken);
        assertThat(decodedClaims.get("iss"), is(issuer));
        assertThat(decodedClaims.get("sub"), is(subject));
        assertThat(decodedClaims.get("aud"), is(clientId));
        assertThat(decodedClaims.get("name"), is(name));
        assertThat(decodedClaims.get("exp"), is(expiry));
    }
}