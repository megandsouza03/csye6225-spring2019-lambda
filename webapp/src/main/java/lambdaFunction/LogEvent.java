package lambdaFunction;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.UUID;

public class LogEvent implements RequestHandler<SNSEvent, Object> {


    static DynamoDB dynamoDb;
    private String tableName = "csye6225";
    private Regions region = Regions.US_EAST_1;
    public String from = "";
    static final String subject = "Password reset link";
    static String htmlBody;
    private static String textBody;
    static String token;
    static String username;

    public Object handleRequest(SNSEvent request, Context context) {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss").format(Calendar.getInstance().getTime());

        String domainName = System.getenv("domainName");
        from = "noreply@test." + domainName;

        //Creating ttl
        context.getLogger().log("Invocation started: " + timeStamp);
        long now = Instant.now().getEpochSecond(); // unix time
        long ttl = 60 * 20; // ttl set to 20 min
        long totalttl = ttl + now;

        //Function Excecution for sending the email
        username = request.getRecords().get(0).getSNS().getMessage();
        context.getLogger().log("Password reset request for username: "+username);
        token = UUID.randomUUID().toString();
        context.getLogger().log("Invocation completed: " + timeStamp);
        try {
            initDynamoDbClient();
            long ttlDbValue = 0;
            Item item = this.dynamoDb.getTable(tableName).getItem("id", username);
            if (item != null) {
                context.getLogger().log("Checking for timestamp");
                ttlDbValue = item.getLong("ttl");
            }

            if (item == null || (ttlDbValue < now && ttlDbValue != 0)) {
                context.getLogger().log("Checking for valid ttl");
                context.getLogger().log("ttl expired, creating new token and sending email");
                this.dynamoDb.getTable(tableName)
                        .putItem(
                                new PutItemSpec().withItem(new Item()
                                        .withString("id", username)
                                        .withString("token", token)
                                        .withLong("ttl", totalttl)));

                textBody = "http://" + domainName + "reset?email=" + username + "&token=" + token;
                context.getLogger().log("Text " + textBody);
                htmlBody = "<h2>Email sent from Amazon SES</h2>"
                        + "<p>Please reset the password using the below link. " +
                        "Link: "+ textBody + "</p>";
                context.getLogger().log("This is HTML body: " + htmlBody);


                //Sending email using Amazon SES client
                AmazonSimpleEmailService clients = AmazonSimpleEmailServiceClientBuilder.standard()
                        .withRegion(region).build();
                SendEmailRequest emailRequest = new SendEmailRequest()
                        .withDestination(
                                new Destination().withToAddresses(username))
                        .withMessage(new Message()
                                .withBody(new Body()
                                        .withHtml(new Content()
                                                .withCharset("UTF-8").withData(htmlBody))
                                        .withText(new Content()
                                                .withCharset("UTF-8").withData(textBody)))
                                .withSubject(new Content()
                                        .withCharset("UTF-8").withData(subject)))
                        .withSource(from);
                clients.sendEmail(emailRequest);
                context.getLogger().log("Email sent successfully to email id: " +username);

            } else {
                context.getLogger().log("ttl is not expired. New request is not processed for the user: " +username);
            }
        } catch (Exception ex) {
            context.getLogger().log("Email was not sent. Error message: " + ex.getMessage());
        }
        return null;
    }

    //creating a DynamoDB Client
    private void initDynamoDbClient() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(region)
                .build();
        dynamoDb = new DynamoDB(client);
    }
}
