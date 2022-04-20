package org.swasth.apigateway.helpers;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.swasth.apigateway.exception.ClientException;
import org.swasth.apigateway.exception.ErrorCodes;
import org.swasth.apigateway.exception.JWTVerificationException;
import org.swasth.apigateway.exception.ServerException;
import org.swasth.apigateway.filters.JwtAuthenticationFilter;
import org.swasth.apigateway.models.BaseRequest;
import org.swasth.apigateway.models.Response;
import org.swasth.apigateway.models.ResponseError;
import org.swasth.apigateway.service.AuditService;
import org.swasth.apigateway.utils.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExceptionHandler.class);

    @Autowired
    private AuditService auditService;

    public Mono<Void> errorResponse(Exception e, ServerWebExchange exchange, String correlationId, String apiCallId, BaseRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErrorCodes errorCode = null;
        Exception ex = e;
        if (e instanceof ClientException) {
            status = HttpStatus.BAD_REQUEST;
            errorCode = ((ClientException) e).getErrCode();
        } else if (e instanceof ServerException) {
            errorCode = ((ServerException) e).getErrCode();
        } else if (e instanceof JWTVerificationException) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = ((JWTVerificationException) e).getErrCode();
        } else if (e instanceof TokenExpiredException) {
            status = HttpStatus.UNAUTHORIZED;
            errorCode = ErrorCodes.ERR_ACCESS_DENIED;
        }
        try {
            auditService.createAuditLog(request);
        } catch (Exception exception) {
            ex = new ClientException("Error while creating audit log :: Exception : " + exception.getMessage());
        }
        return this.onError(exchange, status, correlationId, apiCallId, errorCode, ex);
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String correlationId, String apiCallId, ErrorCodes code, Exception e) {
        ServerHttpResponse response = exchange.getResponse();
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        Response resp = new Response(correlationId, apiCallId, new ResponseError(code, e.getMessage(), e.getCause()));
        try {
            byte[] obj = JSONUtils.convertToByte(resp);
            response.setStatusCode(status);
            response.getHeaders().add("Content-Type", "application/json");
            return response.writeWith(Mono.just(obj).map(r -> dataBufferFactory.wrap(r)));
        } catch (JsonProcessingException ex) {
            logger.error(ex.toString());
        }
        return response.setComplete();
    }

}
