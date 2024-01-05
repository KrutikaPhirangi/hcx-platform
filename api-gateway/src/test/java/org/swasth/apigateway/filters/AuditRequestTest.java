package org.swasth.apigateway.filters;

import okhttp3.mockwebserver.MockResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.swasth.apigateway.BaseSpec;
import org.swasth.common.utils.Constants;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;

public class AuditRequestTest extends BaseSpec {

    @Test
    public void check_audit_request_success_scenario_for_provider() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json"));

        Mockito.when(registryService.getDetails(anyString()))
                .thenReturn(Collections.singletonList(getProviderDetails()));

        client.post().uri(versionPrefix + Constants.AUDIT_SEARCH)
                .header(Constants.AUTHORIZATION, getProviderToken())
                .header("X-jwt-sub", "f7c0e759-bec3-431b-8c4f-6b294d103a74")
                .bodyValue(getAuditRequestBody())
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    assertEquals(result.getStatus(), HttpStatus.ACCEPTED);
                });
    }

    // Testcase for provider specific roles
    @Test
    void check_audit_request_success_scenario_for_hospital_role() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json"));

        Mockito.when(registryService.getDetails(anyString()))
                .thenReturn(Collections.singletonList(getProviderHospitalDetails()));

        client.post().uri(versionPrefix + Constants.AUDIT_SEARCH)
                .header(Constants.AUTHORIZATION, getProviderToken())
                .header("X-jwt-sub", "f7c0e759-bec3-431b-8c4f-6b294d103a74")
                .bodyValue(getAuditRequestBody())
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    assertEquals(HttpStatus.ACCEPTED, result.getStatus());
                });
    }

    @Test
    void check_audit_request_invalid_role_scenario() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json"));

        Mockito.when(registryService.getDetails(anyString()))
                .thenReturn(Collections.singletonList(getInvalidRoleDetails()));

        client.post().uri(versionPrefix + Constants.AUDIT_SEARCH)
                .header(Constants.AUTHORIZATION, getProviderToken())
                .header("X-jwt-sub", "f7c0e759-bec3-431b-8c4f-6b294d103a74")
                .bodyValue(getAuditRequestBody())
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    assertEquals(HttpStatus.ACCEPTED, result.getStatus());
                });
    }

    @Test
    void check_audit_request_success_scenario_for_payor_role() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(202)
                .addHeader("Content-Type", "application/json"));

        Mockito.when(registryService.getDetails(anyString()))
                .thenReturn(Collections.singletonList(getPayorDetails()));

        client.post().uri(versionPrefix + Constants.AUDIT_SEARCH)
                .header(Constants.AUTHORIZATION, getPayorToken())
                .header("X-jwt-sub", "f7b916c4-e148-4cd2-aa66-472e3610d869")
                .bodyValue(getAuditRequestBody())
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    assertEquals(HttpStatus.ACCEPTED, result.getStatus());
                });
    }
    @Test
    public void check_audit_request_invalid_sender_scenario() throws Exception {
        Mockito.when(registryService.getDetails(anyString()))
                .thenReturn(Collections.EMPTY_LIST);

        client.post().uri(versionPrefix + Constants.AUDIT_SEARCH)
                .header(Constants.AUTHORIZATION, getProviderToken())
                .header("X-jwt-sub", "test")
                .bodyValue(getAuditRequestBody())
                .exchange()
                .expectBody(Map.class)
                .consumeWith(result -> {
                    assertEquals(result.getStatus(), HttpStatus.BAD_REQUEST);
                });
    }

}