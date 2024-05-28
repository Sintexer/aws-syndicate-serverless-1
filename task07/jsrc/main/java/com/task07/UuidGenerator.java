package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.events.RuleEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@LambdaHandler(lambdaName = "uuid_generator",
        roleName = "uuid_generator-role",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@RuleEventSource(targetRule = "uuid_trigger")
@EnvironmentVariable(key = "BUCKET_NAME", value = "${target_bucket}")
public class UuidGenerator implements RequestHandler<Object, Void> {

    private final AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

    public Void handleRequest(Object request, Context context) {
        // Generate 10 random UUIDs
        String uuids = "{'ids': [";
        uuids += generateUUIDs(10);
        uuids += "]}";
        System.out.println("Generated UUIDs: " + uuids);


        // Create the file name
        String fileName = getFileName();
        System.out.println("Generated file name: " + fileName);

        // Upload the file to S3
        s3Client.putObject(new PutObjectRequest(getBucketName(), fileName,
                new ByteArrayInputStream(uuids.getBytes(StandardCharsets.UTF_8)), null));

        System.out.println("Uploaded file to S3");
        return null;
    }

    private static String getFileName() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static String generateUUIDs(int number) {
        return IntStream.range(0, Math.max(1, number))
                .mapToObj(i -> "'" + UUID.randomUUID() + "'")
                .collect(Collectors.joining(",        \n"));
    }

    private String getBucketName() {
        return System.getenv("BUCKET_NAME");
    }
}
