package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import com.task09.model.WeatherData;

import java.util.UUID;

@LambdaHandler(lambdaName = "processor",
	roleName = "processor-role",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
		tracingMode = TracingMode.Active
)
@LambdaUrlConfig(
		authType = AuthType.NONE,
		invokeMode = InvokeMode.BUFFERED
)
@EnvironmentVariable(key = "target_table", value = "${target_table}")
public class Processor implements RequestHandler<Void, Void> {

	private final DynamoDB dynamoDB = new DynamoDB(AmazonDynamoDBClientBuilder.defaultClient());
	private final ObjectMapper objectMapper = new ObjectMapper();

	private final OpenMeteoApiService openMeteoApiService = new OpenMeteoApiService();

	public Void handleRequest(Void request, Context context) {
		try {
			WeatherData currentWeather = openMeteoApiService.getCurrentWeather();
			System.out.println("Received: " + currentWeather);
			saveWeather(currentWeather);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	private void saveWeather(WeatherData currentWeather) throws JsonProcessingException {
		Item item = buildItem(currentWeather);
		dynamoDB.getTable(System.getenv("target_table")).putItem(item);
	}

	private Item buildItem(WeatherData weatherData) throws JsonProcessingException {
		return new Item()
				.withPrimaryKey("id", UUID.randomUUID().toString())
				.withJSON("forecast", objectMapper.writeValueAsString(weatherData));
	}
}
