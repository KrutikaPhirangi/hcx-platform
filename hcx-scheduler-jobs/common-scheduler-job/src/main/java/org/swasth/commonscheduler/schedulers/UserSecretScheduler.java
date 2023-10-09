package org.swasth.commonscheduler.schedulers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.swasth.common.utils.Constants;
import org.swasth.common.utils.JSONUtils;
import org.swasth.common.utils.NotificationUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
public class UserSecretScheduler extends BaseScheduler{
    @Value("${postgres.table}")
    private String postgresTable;

    @Value("${certificate.expiry-days}")
    private List<Integer> certificateExpiryDaysList;

    @Value("${kafka.topic.notification}")
    private String notifyTopic;

    @Value("${topicCode.secret-expired}")
    private String secretExpired;

    @Value("${topicCode.before-secret-expiry}")
    private String beforeSecretExpiry;

    @Value("${notification.expiry}")
    private int notificationExpiry;

    @Value("${hcx.participantCode}")
    private String hcxParticipantCode;

    @Value("${hcx.privateKey}")
    private String hcxPrivateKey;

    @Scheduled(fixedDelayString = "${fixedDelay.in.milliseconds.retry}")
    public void process() throws Exception {
        String expiryMessage = "";
        String beforeExpiryMessage = "";
        List<String> expiredParticipantCodes = new ArrayList<>();
        List<String> aboutToExpireParticipantCodes = new ArrayList<>();
        try(Connection connection = postgreSQLClient.getConnection();
            Statement createStatement = connection.createStatement()){
            for (int beforeExpiryDay : certificateExpiryDaysList) {
                long expiryTime = System.currentTimeMillis() + (1 + beforeExpiryDay) * 24L * 60 * 60 * 1000;
                long earlierDayTime = expiryTime - (24L * 60 * 60 * 1000);
                String executeQuery = String.format("SELECT * FROM %s  WHERE secret_expiry_date <= %d ;", postgresTable,System.currentTimeMillis());
                String query = String.format("SELECT * FROM %s  WHERE secret_expiry_date > %d AND secret_expiry_date < %d;", postgresTable,System.currentTimeMillis(),earlierDayTime);
                ResultSet result = postgreSQLClient.executeQuery(executeQuery);
                if (result.next()) {
                    long participants = result.getLong("username");
                    expiredParticipantCodes.add(String.valueOf(participants));
                    expiryMessage = getTemplateMessage(String.valueOf(participants));
                }else {
                    ResultSet resultSet = postgreSQLClient.executeQuery(query);
                    if (resultSet.next()){
                        long participants = result.getLong("username");
                        aboutToExpireParticipantCodes.add(String.valueOf(participants));
                        beforeExpiryMessage = getTemplateMessage(beforeSecretExpiry).replace("${days}", String.valueOf(beforeExpiryDay));
                    }
                    generateEvent(aboutToExpireParticipantCodes, beforeExpiryMessage, beforeSecretExpiry);
                    aboutToExpireParticipantCodes.clear();
                }
                generateEvent(expiredParticipantCodes, expiryMessage, secretExpired);
            }
            createStatement.executeBatch();
        }catch (Exception e) {
            System.out.println("Error while processing event: " + e.getMessage());
            throw e;
        }
    }

    private void generateEvent(List<String> participantCodes, String message, String topiCode) throws Exception {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MILLISECOND, notificationExpiry);
        String event = eventGenerator.createNotifyEvent(topiCode, hcxParticipantCode, Constants.PARTICIPANT_CODE, participantCodes, cal.getTime().toInstant().toEpochMilli(), message, hcxPrivateKey);
        kafkaClient.send(notifyTopic, Constants.NOTIFICATION, event);
    }
    private String getTemplateMessage(String topicCode) throws Exception {
        return (String) JSONUtils.deserialize((String) (NotificationUtils.getNotification(topicCode).get(Constants.TEMPLATE)), Map.class).get(Constants.MESSAGE);
    }
}
