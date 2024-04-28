package com.task02;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.lambda.url.AuthType;
import com.syndicate.deployment.model.lambda.url.InvokeMode;
import edu.emory.mathcs.backport.java.util.Collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@LambdaHandler(lambdaName = "hello_world",
		roleName = "hello_world-role",
		isPublishVersion = true,
		logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@LambdaUrlConfig(authType = AuthType.NONE, invokeMode = InvokeMode.BUFFERED)
public class HelloWorld implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final String PATH_KEY = "rawPath";

	private static final String HELLO_MAPPING = "/hello";

	public Map<String, Object> handleRequest(Map<String, Object> request, Context context) {
		System.out.println("request: " + request);
		Object path = request.get(PATH_KEY);
		System.out.println("path="+path);
		if (path != null) {
			System.out.println("Path is not null");
			String pathString = (String) path;
			System.out.println("pathString="+pathString);
			if (HELLO_MAPPING.equals(pathString)) {
				System.out.println("Building hello response as a map");
				Map<String, Object> response = buildHelloResponse();
				System.out.println("response="+response);
				return response;
			}
			System.out.println("For some reason /hello didn't match. returning empty map");
		}
		return new HashMap<>();
	}

	private Map<String, Object> buildHelloResponse() {
		Map<String, Object> response = new HashMap<>();
		response.put("statusCode", 200);
		response.put("message", "Hello from Lambda");
		return response;
	}
}
