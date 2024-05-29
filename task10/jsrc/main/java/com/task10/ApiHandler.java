package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserResult;
import com.amazonaws.services.cognitoidp.model.AdminSetUserPasswordRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.AuthenticationResultType;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthResult;
import com.amazonaws.services.cognitoidp.model.ListUserPoolClientsRequest;
import com.amazonaws.services.cognitoidp.model.ListUserPoolClientsResult;
import com.amazonaws.services.cognitoidp.model.ListUserPoolsRequest;
import com.amazonaws.services.cognitoidp.model.ListUserPoolsResult;
import com.amazonaws.services.cognitoidp.model.UserPoolClientDescription;
import com.amazonaws.services.cognitoidp.model.UserPoolDescriptionType;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@LambdaHandler(lambdaName = "api_handler",
        roleName = "api_handler-role",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@EnvironmentVariables({
        @EnvironmentVariable(key = "TABLE_TABLES", value = "${tables_table}"),
        @EnvironmentVariable(key = "TABLE_RESERVATIONS", value = "${reservations_table}"),
        @EnvironmentVariable(key = "USERPOOL", value = "${booking_userpool}"),
})
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String CLIENT_NAME = "booking_app";

    private final AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
    private final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
    private final DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
    ObjectMapper mapper = new ObjectMapper();

    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            System.out.println("Request: " + request);
            String requestPath = request.getResource();
            return handleRequest(request, context, request.getHttpMethod(), requestPath);
        } catch (Exception e) {
            return buildBadRequestResponse(e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context, String method, String requestPath) {
        String body = request.getBody();
        if (method.equals("POST")) {
            if (requestPath.contains("signup")) {
                return signup(body, context);
            } else if (requestPath.contains("signin")) {
                return signin(body, context);
            } else if (requestPath.contains("tables")) {
                return createTable(readJsonBody(body));
            } else if (requestPath.contains("reservations")) {
                return createReservation(readJsonBody(body));
            }
        }
        if (method.equals("GET")) {
            if (requestPath.contains("tables/")) {
                return getTable(Integer.parseInt(request.getPathParameters().get("tableId")));
            } else if (requestPath.contains("tables")) {
                return getTables();
            } else if (requestPath.contains("reservations")) {
                return getAllReservations();
            }
        }

        return buildBadRequestResponse("path not found: " + method + ":" + requestPath);
    }

    private APIGatewayProxyResponseEvent signup(String body, Context context) {
        Map<String, Object> user = readJsonBody(body);
        validateSignup(user);

        PoolIds poolsIds = getUserPoolIdAndClientId(CLIENT_NAME, context);
        System.out.println("pool ids: " + poolsIds);

        AdminCreateUserRequest request = new AdminCreateUserRequest()
                .withUserPoolId(poolsIds.getPoolId())
                .withUsername((String) user.get("email"))
                .withUserAttributes(
                        new AttributeType().withName("email").withValue((String) user.get("email")),
                        new AttributeType().withName("given_name").withValue((String) user.get("firstName")),
                        new AttributeType().withName("family_name").withValue((String) user.get("lastName"))
                )
                .withTemporaryPassword((String) user.get("password"))
                .withMessageAction("SUPPRESS");

        AdminCreateUserResult result = cognitoClient.adminCreateUser(request);
        System.out.println("authresult: " + result);
        AdminSetUserPasswordRequest passwordRequest = new AdminSetUserPasswordRequest()
                .withUserPoolId(poolsIds.getPoolId())
                .withUsername((String) user.get("email"))
                .withPassword((String) user.get("password"))
                .withPermanent(true);
        cognitoClient.adminSetUserPassword(passwordRequest);

        System.out.println("password set");

        return buildOkResponse(Collections.emptyMap());
    }

    private void validateSignup(Map<String, Object> user) {
        if (user.get("email") == null || user.get("password") == null || user.get("firstName") == null || user.get("lastName") == null) {
            throw new IllegalArgumentException("Missing required fields");
        }
        if (!((String) user.get("email")).contains("@") || !((String) user.get("email")).contains(".")) {
            throw new IllegalArgumentException("Invalid email address");
        }
        if (((String) user.get("password")).length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
    }

    private APIGatewayProxyResponseEvent signin(String body, Context context) {
        Map<String, Object> user = readJsonBody(body);

        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", (String) user.get("email"));
        authParams.put("PASSWORD", (String) user.get("password"));

        PoolIds poolsIds = getUserPoolIdAndClientId(CLIENT_NAME, context);
        System.out.println("pool ids: " + poolsIds);
        InitiateAuthRequest authRequest = new InitiateAuthRequest()
                .withClientId(poolsIds.getClientId())
                .withAuthFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .withAuthParameters(authParams);

        InitiateAuthResult authResult = cognitoClient.initiateAuth(authRequest);
        System.out.println("authresult: " + authResult);
        AuthenticationResultType result = authResult.getAuthenticationResult();
        return buildOkResponse(Map.of("accessToken", result.getIdToken()));
    }

    public PoolIds getUserPoolIdAndClientId(String clientName, Context context) {
        try {
            // List all user pools
            ListUserPoolsRequest listUserPoolsRequest = new ListUserPoolsRequest().withMaxResults(60);
            ListUserPoolsResult listUserPoolsResult = cognitoClient.listUserPools(listUserPoolsRequest);
            List<UserPoolDescriptionType> userPools = listUserPoolsResult.getUserPools();

            // Iterate through each user pool to find the client
            for (UserPoolDescriptionType userPool : userPools) {
                String userPoolId = userPool.getId();
                ListUserPoolClientsRequest listUserPoolClientsRequest = new ListUserPoolClientsRequest()
                        .withUserPoolId(userPoolId)
                        .withMaxResults(60);
                ListUserPoolClientsResult listUserPoolClientsResult = cognitoClient.listUserPoolClients(listUserPoolClientsRequest);
                List<UserPoolClientDescription> userPoolClients = listUserPoolClientsResult.getUserPoolClients();

                // Find the client by name
                Optional<UserPoolClientDescription> clientOptional = userPoolClients.stream()
                        .filter(client -> client.getClientName().equals(clientName))
                        .findFirst();

                if (clientOptional.isPresent()) {
                    return new PoolIds(userPoolId, clientOptional.get().getClientId());
                }
            }

            // If no matching client is found
            throw new IllegalAccessException("No user pool found for client: " + clientName);
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private APIGatewayProxyResponseEvent getTables() {
        List<Map<String, Object>> itemsDtos = getAllTables();
        return buildOkResponse(Map.of("tables", itemsDtos));
    }

    private List<Map<String, Object>> getAllTables() {
        Table table = dynamoDB.getTable(getTableTables());
        ScanSpec scanSpec = new ScanSpec();
        List<Item> items = new ArrayList<>();
        IteratorSupport<Item, ScanOutcome> iterator = table.scan(scanSpec).iterator();
        iterator.forEachRemaining(items::add);
        return items.stream()
                .map(ApiHandler::mapTableToMap)
                .collect(Collectors.toList());
    }

    private static Map<String, Object> mapTableToMap(Item item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getNumber("id"));
        map.put("number", item.getNumber("number"));
        map.put("places", item.getNumber("places"));
        map.put("isVip", item.getBoolean("isVip"));
        if (item.getNumber("minOrder") != null) {
            map.put("minOrder", item.getNumber("minOrder"));
        }
        return map;
    }

    private APIGatewayProxyResponseEvent createTable(Map<String, Object> body) {
        int id = (int) body.get("id");
        int number = (int) body.get("number");
        int places = (int) body.get("places");
        boolean isVip = (boolean) body.get("isVip");
        Integer minOrder = body.get("minOrder") != null ? (int) body.get("minOrder") : null;

        Item item = createItem(id, number, places, isVip, minOrder);

        Table table = dynamoDB.getTable(getTableTables());
        table.putItem(item);

        return buildOkResponse(Map.of("id", id));
    }

    private static Item createItem(int id, int number, int places, boolean isVip, Integer minOrder) {
        Item item = new Item()
                .withPrimaryKey("id", id)
                .withNumber("number", number)
                .withNumber("places", places)
                .withBoolean("isVip", isVip);

        if (minOrder != null) {
            item.withNumber("minOrder", minOrder);
        }
        return item;
    }

    private APIGatewayProxyResponseEvent getTable(int tableId) {
        Item item = getTableOrNull(tableId);
        if (item == null) {
            return buildBadRequestResponse("Table not found");
        }
        return buildOkResponse(mapTableToMap(item));
    }

    private Item getTableOrNull(int tableId) {
        Table table = dynamoDB.getTable(getTableTables());
        return table.getItem("id", tableId);
    }

    private APIGatewayProxyResponseEvent createReservation(Map<String, Object> body) {

        int tableNumber = (int) body.get("tableNumber");
        String clientName = (String) body.get("clientName");
        String phoneNumber = (String) body.get("phoneNumber");
        String date = (String) body.get("date");
        String slotTimeStart = (String) body.get("slotTimeStart");
        String slotTimeEnd = (String) body.get("slotTimeEnd");

        validateReservation(tableNumber, date, slotTimeStart, slotTimeEnd);

        String id = UUID.randomUUID().toString();
        Item item = new Item()
                .withPrimaryKey("id", id)
                .withNumber("tableNumber", tableNumber)
                .withString("clientName", clientName)
                .withString("phoneNumber", phoneNumber)
                .withString("date", date)
                .withString("slotTimeStart", slotTimeStart)
                .withString("slotTimeEnd", slotTimeEnd);

        Table table = dynamoDB.getTable(getTableReservations());
        table.putItem(item);
        return buildOkResponse(Map.of("reservationId", id));
    }

    private void validateReservation(int tableNumber, String date, String slotTimeStart, String slotTimeEnd) {
        if (getTableByTableNumberOrNull(tableNumber) == null) {
            throw new IllegalArgumentException("table not found");
        }
        List<Map<String, Object>> otherReservations = getReservationByDate(tableNumber, date);
        for (Map<String, Object> reservation : otherReservations) {
            String existingStart = (String) reservation.get("slotTimeStart");
            String existingEnd = (String) reservation.get("slotTimeEnd");
            if ((slotTimeStart.compareTo(existingEnd) < 0) && (slotTimeEnd.compareTo(existingStart) > 0)) {
                throw new IllegalArgumentException("time slot overlaps with an existing reservation");
            }
        }
    }

    private Map<String, Object> getTableByTableNumberOrNull(int tableNumber) {
        BigDecimal x = new BigDecimal(tableNumber);
        return getAllTables().stream()
                .filter(table -> table.get("number").equals(x))
                    .findFirst().orElse(null);
    }

    private List<Map<String, Object>> getReservationByDate(int tableNumber, String date) {
        BigDecimal x = new BigDecimal(tableNumber);
        return getAllReservationsList().stream().filter(reservation ->
                reservation.get("tableNumber").equals(x) && reservation.get(date).equals(date))
                .collect(Collectors.toList());
    }

    private APIGatewayProxyResponseEvent getAllReservations() {
        return buildOkResponse(Map.of("reservations", getAllReservationsList()));
    }

    private List<Map<String, Object>> getAllReservationsList() {
        Table table = dynamoDB.getTable(getTableReservations());
        ScanSpec scanSpec = new ScanSpec();
        ItemCollection<ScanOutcome> items = table.scan(scanSpec);
        List<Map<String, Object>> reservations = new ArrayList<>();
        for (Item item : items) {
            Map<String, Object> reservation = mapReservationToMap(item);
            reservations.add(reservation);
        }
        return reservations;
    }

    private static Map<String, Object> mapReservationToMap(Item item) {
        Map<String, Object> reservation = new HashMap<>();
        reservation.put("tableNumber", item.getNumber("tableNumber"));
        reservation.put("clientName", item.getString("clientName"));
        reservation.put("phoneNumber", item.getString("phoneNumber"));
        reservation.put("date", item.getString("date"));
        reservation.put("slotTimeStart", item.getString("slotTimeStart"));
        reservation.put("slotTimeEnd", item.getString("slotTimeEnd"));
        return reservation;
    }

    private APIGatewayProxyResponseEvent buildOkResponse() {
        return buildOkResponse(Collections.emptyMap());
    }

    private APIGatewayProxyResponseEvent buildOkResponse(Object response) {
        return buildResponse(200, convertToJson(response));
    }

    private APIGatewayProxyResponseEvent buildBadRequestResponse(String message) {
        return buildBadRequestResponse(Map.of("message", message));
    }

    private APIGatewayProxyResponseEvent buildBadRequestResponse(Map<String, String> response) {
        return buildResponse(400, convertToJson(response));
    }

    private APIGatewayProxyResponseEvent buildResponse(int statusCode, String response) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(response);
    }

    private String convertToJson(Object response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonBody(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private String getTableTables() {
        return System.getenv("TABLE_TABLES");
    }

    private String getTableReservations() {
        return System.getenv("TABLE_RESERVATIONS");
    }

    private String getUserPool() {
        return System.getenv("USERPOOL");
    }

}
