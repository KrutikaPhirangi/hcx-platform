package org.swasth.hcx.service;

import kong.unirest.HttpResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.swasth.auditindexer.function.AuditIndexer;
import org.swasth.common.dto.RegistryResponse;
import org.swasth.common.dto.ResponseError;
import org.swasth.common.dto.Token;
import org.swasth.common.dto.User;
import org.swasth.common.exception.ClientException;
import org.swasth.common.exception.ErrorCodes;
import org.swasth.common.utils.JSONUtils;
import java.util.*;
import static org.swasth.common.response.ResponseMessage.INVALID_USER_DETAILS;
import static org.swasth.common.response.ResponseMessage.INVALID_USER_ID;
import static org.swasth.common.utils.Constants.*;

@Service
public class UserService extends BaseRegistryService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    @Value("${registry.user-api-path}")
    private String registryUserPath;
    @Autowired
    private ParticipantService participantService;

    @Autowired
    protected AuditIndexer auditIndexer;

    public RegistryResponse create(Map<String, Object> requestBody, String code) throws Exception {
        HttpResponse<String> response = invite(requestBody, registryUserPath);
        if (response.getStatus() == 200) {
            generateUserAudit(code, USER_CREATE, requestBody, (String) requestBody.get(CREATED_BY));
            logger.debug("Created user :: user id: {}", code);
        }
        return responseHandler(response, code, USER);
    }

    public RegistryResponse search(Map<String, Object> requestBody) throws Exception {
        return search(requestBody, registryUserPath, USER);
    }

    public RegistryResponse update(Map<String, Object> requestBody, Map<String, Object> registryDetails, HttpHeaders headers, String code) throws Exception {
        HttpResponse<String> response = update(requestBody, registryDetails, registryUserPath);
        if (response.getStatus() == 200) {
            generateUserAudit(code, USER_UPDATE, requestBody, getUserFromToken(headers));
            logger.debug("Updated user :: user id: {}", code);
        }
        return responseHandler(response, code, USER);
    }

    public RegistryResponse delete(Map<String, Object> registryDetails, HttpHeaders headers, String code) throws Exception {
        HttpResponse<String> response = delete(registryDetails, registryUserPath);
        if (response.getStatus() == 200) {
            generateUserAudit(code, USER_DELETE, registryDetails, getUserFromToken(headers));
            logger.debug("User deleted :: userId: {}", code);
            RegistryResponse registryResponse = new RegistryResponse(code, USER);
            registryResponse.setStatus(INACTIVE);
            return registryResponse;
        }
        return responseHandler(response, code, USER);
    }

    public Map<String, Object> addUser(Map<String, Object> userBody, HttpHeaders headers) throws Exception {
        Map<String, Object> registryDetails = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        try {
            HttpResponse<String> response;
            registryDetails = getUser((String) userBody.get(USER_ID));
            ArrayList<Map<String, Object>> tenantRolesList = new ArrayList<>();
            Map<String, Object> requestBody = new HashMap<>();
            if (registryDetails.containsKey(TENANT_ROLES)) {
                tenantRolesList = JSONUtils.convert(registryDetails.getOrDefault(TENANT_ROLES, new ArrayList<>()), ArrayList.class);
                for (Map<String, Object> userExist : tenantRolesList) {
                    if (userExist.get(ROLE).equals(userBody.get(ROLE)) && userExist.get(PARTICIPANT_CODE).equals(userBody.get(PARTICIPANT_CODE))) {
                        throw new ClientException("User with the role : " + userBody.get(ROLE) + " and participant code : " + userBody.get(PARTICIPANT_CODE) + " is already exist for the user_id : " + userBody.get(USER_ID));
                    }
                }
            }
            requestBody.put(TENANT_ROLES, tenantRolesList);
            userBody.remove(USER_ID);
            tenantRolesList.add(userBody);
            response = update(requestBody, registryDetails, registryUserPath);
            generateAddRemoveUserAudit((String) registryDetails.get(USER_ID), PARTICIPANT_USER_ADD, userBody, getUserFromToken(headers));
            logger.debug("added role for the userId: {}", registryDetails.get(USER_ID));
            if (response.getStatus() == 200) {
                responseMap = getResponse((String) registryDetails.get(USER_ID), SUCCESSFUL);
            }
            return responseMap;
        } catch (Exception e) {
            responseMap = getResponse((String) registryDetails.get(USER_ID), FAILED);
            responseMap.put(ERROR, new ResponseError(null, e.getMessage(), e.getCause()));
            return responseMap;
        }
    }

    public Map<String, Object> removeUser(Map<String, Object> requestBody, HttpHeaders headers) throws Exception {
        Map<String, Object> registryDetails = new HashMap<>();
        Map<String, Object> responseMap = new HashMap<>();
        try {
            HttpResponse<String> response;
            String userId = (String) requestBody.get(USER_ID);
            registryDetails = getUser(userId);
            ArrayList<Map<String, Object>> filteredTenantRoles = new ArrayList<>();
            if (registryDetails.containsKey(TENANT_ROLES)) {
                ArrayList<Map<String, Object>> tenantRolesList = JSONUtils.convert(registryDetails.get(TENANT_ROLES), ArrayList.class);
                if (tenantRolesList.isEmpty()) {
                    throw new ClientException("User does not have any role to remove");
                }
                for (Map<String, Object> tenantRole : tenantRolesList) {
                    String role = (String) tenantRole.get(ROLE);
                    String participantCode = (String) tenantRole.get(PARTICIPANT_CODE);
                    if (!requestBody.get(ROLE).equals(role) && participantCode.equals(requestBody.get(PARTICIPANT_CODE))) {
                        filteredTenantRoles.add(tenantRole);
                    }
                }
            }
            Map<String, Object> request = new HashMap<>();
            request.put(TENANT_ROLES, filteredTenantRoles);
            response = update(request, registryDetails, registryUserPath);
            generateAddRemoveUserAudit(userId, PARTICIPANT_USER_REMOVE, requestBody, getUserFromToken(headers));
            logger.debug("removed role for the :: user_id: {}", registryDetails.get(USER_ID));
            if (response != null && response.getStatus() == 200) {
                responseMap = getResponse((String) registryDetails.get(USER_ID), SUCCESSFUL);
            }
            return responseMap;
        } catch (Exception e) {
            responseMap = getResponse((String) registryDetails.get(USER_ID), FAILED);
            responseMap.put(ERROR, new ResponseError(null, e.getMessage(), e.fillInStackTrace()));
            return responseMap;
        }
    }

    private Map<String, Object> getResponse(String userId, String status) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put(USER_ID, userId);
        responseMap.put("status", status);
        return responseMap;
    }

    private Map<String, Object> getErrorMessage(Exception e) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("code", "");
        errorMap.put(MESSAGE, e.getMessage());
        errorMap.put("trace", e.getStackTrace());
        return errorMap;
    }

    public Map<String, Object> getUser(String userId) throws Exception {
        logger.debug("searching for :: user id : {}", userId);
        ResponseEntity<Object> searchResponse = getSuccessResponse(search(JSONUtils.deserialize(getUserRequest(userId), Map.class)));
        RegistryResponse registryResponse = (RegistryResponse) Objects.requireNonNull(searchResponse.getBody());
        if (registryResponse.getUsers().isEmpty())
            throw new ClientException(ErrorCodes.ERR_INVALID_USER_ID, INVALID_USER_ID);
        return (Map<String, Object>) registryResponse.getUsers().get(0);
    }

    private String getUserRequest(String userId) {
        return "{ \"filters\": { \"user_id\": { \"eq\": \" " + userId + "\" } } }";
    }

    private void generateUserAudit(String userId, String action, Map<String, Object> requestBody, String updatedBy) throws Exception {
        Map<String, Object> edata = new HashMap<>();
        if (StringUtils.equals(action, USER_CREATE)) {
            edata.put("createdBy", updatedBy);
        } else {
            edata.put(UPDATED_BY, updatedBy);
        }
        Map<String, Object> event = eventGenerator.createAuditLog(userId, USER, getCData(action, requestBody), edata);
        eventHandler.createUserAudit(event);
    }

    public void generateAddRemoveUserAudit(String userId, String action, Map<String, Object> requestBody, String updatedBy) throws Exception {
        Map<String, Object> edata = new HashMap<>();
        edata.put(UPDATED_BY, updatedBy);
        Map<String, Object> event = eventGenerator.createAuditLog(userId, USER, getCData(action, requestBody), edata);
        eventHandler.createUserAudit(event);
    }

    public String createUserId(Map<String, Object> requestBody) throws ClientException {
        if (requestBody.containsKey(EMAIL) || requestBody.containsKey(MOBILE)) {
            if (requestBody.containsKey(EMAIL) && EmailValidator.getInstance().isValid((String) requestBody.get(EMAIL))) {
                return ((String) requestBody.get(EMAIL));
            } else if (requestBody.containsKey(MOBILE)) {
                return (String) requestBody.get(MOBILE);
            }
        }
        throw new ClientException(ErrorCodes.ERR_INVALID_USER_DETAILS, INVALID_USER_DETAILS);
    }

    public void updateAllowedFields(Map<String, Object> requestBody) throws ClientException {
        List<String> requestFields = new ArrayList<>(requestBody.keySet());
        for (String fields : requestFields) {
            if (NOT_ALLOWED_FIELDS_FOR_UPDATE.contains(fields)) {
                requestFields.remove(USER_ID);
                throw new ClientException(ErrorCodes.ERR_INVALID_USER_DETAILS, "Fields not allowed for update: " + requestFields);
            }
        }
    }

    public Map<String, Object> constructRequestBody(Map<String, Object> requestBody, Map<String, Object> user) {
        Map<String, Object> userRequest = new HashMap<>();
        userRequest.put(PARTICIPANT_CODE, requestBody.get(PARTICIPANT_CODE));
        userRequest.put(ROLE, user.get(ROLE));
        userRequest.put(USER_ID, user.get(USER_ID));
        return userRequest;
    }

    public void authorizeToken(HttpHeaders headers, String participantCode) throws Exception {
        Token token = new Token(Objects.requireNonNull(headers.get(AUTHORIZATION)).get(0));
        boolean result = false;
        if (token.getRoles().contains(ADMIN_ROLE)) {
            result = true;
        } else if (StringUtils.equals(token.getEntityType(), USER)) {
            for (Map<String, String> roleMap : token.getTenantRoles()) {
                if (StringUtils.equals(roleMap.get(PARTICIPANT_CODE), participantCode) && StringUtils.equals(roleMap.get(ROLE), ADMIN)) {
                    result = true;
                }
            }
        } else if (StringUtils.equals(token.getEntityType(), ORGANISATION)) {
            Map<String, Object> details = participantService.getParticipant(participantCode);
            if (StringUtils.equals(token.getSubject(), ((List<String>) details.get(OS_OWNER)).get(0))) {
                result = true;
            }
        }
        if (!result) {
            throw new ClientException(ErrorCodes.ERR_ACCESS_DENIED, "Participant/User does not have permissions to perform this operation");
        }
    }

    public String overallStatus(List<Map<String, Object>> responses) {
        boolean successful = false;
        boolean failed = false;
        for (Map<String, Object> response : responses) {
            String status = (String) response.get("status");
            if (status.equals(SUCCESSFUL)) {
                successful = true;
            } else if (status.equals(FAILED)) {
                failed = true;
            }
        }
        String overallStatus = FAILED;
        if (failed && successful) {
            overallStatus = "partial";
        } else if (successful) {
            overallStatus = SUCCESSFUL;
        } else if (failed) {
            overallStatus = FAILED;
        }

        return overallStatus;
    }
}
