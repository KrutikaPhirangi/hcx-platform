package org.swasth.hcx.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.swasth.common.dto.Response;
import org.swasth.common.dto.ResponseParams;
import org.swasth.common.exception.ClientException;
import org.swasth.common.exception.ResponseCode;
import org.swasth.hcx.helpers.KafkaEventGenerator;
import org.swasth.kafka.client.KafkaClient;
import java.util.ArrayList;
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
    KafkaEventGenerator kafkaEventGenerator;

    @Autowired
    Environment env;

    KafkaClient kafkaClient = new KafkaClient();

    private String getUUID() {
        UUID uid = UUID.randomUUID();
        return uid.toString();
    }

    public void validatePayload(Map<String, Object> requestBody) throws ClientException {
        if(requestBody.isEmpty()) {
            throw new ClientException("Request Body cannot be Empty.");
        } else {
            List<String> mandatoryPayloadProps = (List<String>) env.getProperty("payload.mandatory.properties", List.class, new ArrayList<String>());
            List<String> missingPayloadProps = mandatoryPayloadProps.stream().filter(key -> !requestBody.keySet().contains(key)).collect(Collectors.toList());
           if(!missingPayloadProps.isEmpty()) {
               throw new ClientException("Payload mandatory properties are missing: " + missingPayloadProps);
           }
        }
    }

    public void validateHeaders(HttpHeaders header) throws ClientException {
        List<String> mandatoryProtocolHeaders = (List<String>) env.getProperty("protocol.mandatory.headers", List.class, new ArrayList<String>());
        List<String> mandatoryJoseHeaders = (List<String>) env.getProperty("jose.headers", List.class, new ArrayList<String>());
        List<String> missingJoseHeaders = mandatoryJoseHeaders.stream().filter(key -> !header.containsKey(key)).collect(Collectors.toList());
        if(!missingJoseHeaders.isEmpty()) {
            throw new ClientException("Jose mandatory headers are missing: " + missingJoseHeaders);
        }
        List<String> missingProtocolHeaders = mandatoryProtocolHeaders.stream().filter(key -> !header.containsKey(key)).collect(Collectors.toList());
        if(!missingProtocolHeaders.isEmpty()) {
            throw new ClientException("Protocol mandatory headers are missing: " + missingProtocolHeaders);
        }
    }

    public Response getResponse(String apiId){
        ResponseParams responseParams = new ResponseParams();
        Response response = new Response(apiId, ResponseCode.ACCEPTED, responseParams);
        return response;
    }

    public Response badRequestResponse(Response response, Exception e){
        ResponseParams responseParams = new ResponseParams();
        responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        responseParams.setErrmsg(e.getMessage());
        response.setParams(responseParams);
        response.setResponseCode(ResponseCode.CLIENT_ERROR);
        return response;
    }

    public Response serverErrorResponse(Response response, Exception e){
        ResponseParams responseParams = new ResponseParams();
        responseParams.setStatus(Response.Status.UNSUCCESSFUL);
        responseParams.setErrmsg(e.getMessage());
        response.setParams(responseParams);
        response.setResponseCode(ResponseCode.SERVER_ERROR);
        return response;
    }

    public void processAndSendEvent(String apiId, HttpHeaders header, Map<String, Object> requestBody) throws JsonProcessingException, ClientException {
        String mid = getUUID();
        String payloadEvent = kafkaEventGenerator.generatePayloadEvent(mid, requestBody);
        String metadataEvent = kafkaEventGenerator.generateMetadataEvent(mid, apiId, header);
        kafkaClient.send(topic, key, payloadEvent);
        kafkaClient.send(topic, key, metadataEvent);
    }
}
