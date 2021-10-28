package org.swasth.hcx.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.swasth.hcx.Helpers.KafkaEventGenerator;
import org.swasth.hcx.middleware.KafkaClient;
import org.swasth.hcx.pojos.Response;
import org.swasth.hcx.pojos.ResponseParams;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class BaseController {

    @Value("${kafka.denormaliser.topic}")
    private String topic;

    @Value("${kafka.denormaliser.topic.key}")
    private String key;

    @Autowired
    protected KafkaEventGenerator kafkaEventGenerator;

    @Autowired
    protected KafkaClient kafkaClient;

    @Autowired
    protected Environment env;

    protected String getUUID() {
        UUID uid = UUID.randomUUID();
        return uid.toString();
    }

    public Map<String, Object> getAndValidateBody(Map<String, Object> requestBody) {
        Map<String, Object> body = (Map<String, Object>) requestBody.get("request");
        if(body.isEmpty())
            throw new RestClientException("Request Body cannot be Empty.");
        else
            return body;
    }

    public void validateHeaders(HttpHeaders header) {
        List<String> headerKeys = header.keySet().stream().collect(Collectors.toList());
        List<String> mandatoryProtocalHeaders = (List<String>) env.getProperty("protocal.mandatory.headers", List.class);
        List<String> mandatoryJoseHeaders = (List<String>) env.getProperty("jose.headers", List.class);
        List<String> missingJoseHeaders = mandatoryJoseHeaders.stream().filter(key -> !headerKeys.contains(key)).collect(Collectors.toList());
        if(!missingJoseHeaders.isEmpty()) {
            throw new RestClientException("Jose mandatory headers are missing: " + missingJoseHeaders);
        }
        List<String> missingProtocalHeaders = mandatoryProtocalHeaders.stream().filter(key -> !headerKeys.contains(key)).collect(Collectors.toList());
        if(!missingProtocalHeaders.isEmpty()) {
            throw new RestClientException("Protocal mandatory headers are missing: " + missingProtocalHeaders);
        }
    }

    public Response getResponse(String apiId){
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(apiId, "ACCEPTED", responseParams);
        return response;
    }

    public Response getErrorResponse(Response response, Exception e){
        ResponseParams responseParams = new ResponseParams();
        responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        responseParams.setErrmsg(e.getMessage());
        response.setResponseCode(HttpStatus.BAD_REQUEST.toString());
        return response;
    }

    public void processAndSendEvent(String apiId, HttpHeaders header, Map<String, Object> requestBody) throws JsonProcessingException {
        validateHeaders(header);
        Map<String, Object> body = getAndValidateBody(requestBody);
        String mid = getUUID();
        String payloadEvent = kafkaEventGenerator.generatePayloadEvent(mid, body);
        String metadataEvent = kafkaEventGenerator.generateMetadataEvent(mid, apiId, header, body);
        kafkaClient.send(topic, key, payloadEvent);
        kafkaClient.send(topic, key, metadataEvent);
    }
}
