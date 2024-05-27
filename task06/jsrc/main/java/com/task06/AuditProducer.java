package com.task06;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.events.DynamoDbTriggerEventSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@LambdaHandler(lambdaName = "audit_producer",
	roleName = "audit_producer-role",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DynamoDbTriggerEventSource(targetTable = "Configuration", batchSize = 1)
@EnvironmentVariable(key = "AUDIT_TABLE", value = "${target_table}")
public class AuditProducer implements RequestHandler<DynamodbEvent, Void> {

	DynamoDbClient client = DynamoDbClient.builder().build();

	public Void handleRequest(DynamodbEvent request, Context context) {
		for (DynamodbEvent.DynamodbStreamRecord record : request.getRecords()) {
			processRecord(record);
		}
		return null;
	}

	private void processRecord(DynamodbEvent.DynamodbStreamRecord record) {
		if (record.getDynamodb().getOldImage() == null) {
			processNewRecord(record);
		} else {
			processRecordValueUpdated(record);
		}
	}

	private void processNewRecord(DynamodbEvent.DynamodbStreamRecord record) {
		String itemKey = record.getDynamodb().getKeys().get("key").getS();

		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage = record.getDynamodb().getNewImage();
		Map<String, AttributeValue> newValue = Map.of(
				"key", AttributeValue.builder().s(itemKey).build(),
							"value", AttributeValue.builder().n(newImage.get("value").getN()).build());

		Map<String, AttributeValue> item = buildAuditItem(itemKey);
		item.put("newValue", AttributeValue.builder().m(newValue).build());

		PutItemRequest request = PutItemRequest.builder()
				.tableName(getAuditTableName())
				.item(item)
				.build();

		client.putItem(request);

	}

	private void processRecordValueUpdated(DynamodbEvent.DynamodbStreamRecord record) {
		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage = record.getDynamodb().getNewImage();
		Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> oldImage = record.getDynamodb().getOldImage();
		String itemKey = record.getDynamodb().getKeys().get("key").getS();
		Map<String, AttributeValue> auditItem = buildAuditItem(itemKey);
		auditItem.put("updatedAttribute", AttributeValue.builder().s("value").build());
		auditItem.put("oldValue", AttributeValue.builder().n(oldImage.get("value").getN()).build());
		auditItem.put("newValue", AttributeValue.builder().n(newImage.get("value").getN()).build());

		PutItemRequest request = PutItemRequest.builder()
				.tableName(getAuditTableName())
				.item(auditItem)
				.build();

		client.putItem(request);
	}

	private String getAuditTableName() {
		return System.getenv("AUDIT_TABLE");
	}

	private static Map<String, AttributeValue> buildAuditItem(String itemKey) {
		Map<String, AttributeValue> item = new HashMap<>();
		item.put("id", AttributeValue.builder().s(UUID.randomUUID().toString()).build());
		item.put("itemKey", AttributeValue.builder().s(itemKey).build());
		String currentTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
		item.put("modificationTime", AttributeValue.builder().s(currentTime).build());
		return item;
	}

}
