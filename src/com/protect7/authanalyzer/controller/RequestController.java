package com.protect7.authanalyzer.util;

import java.io.PrintWriter;
import java.io.StringReader;
import java.util.*;
import javax.swing.JOptionPane;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.protect7.authanalyzer.entities.Session;
import com.protect7.authanalyzer.entities.Token;
import com.protect7.authanalyzer.entities.TokenLocation;
import com.protect7.authanalyzer.entities.TokenPriority;
import burp.BurpExtender;
import burp.IParameter;
import burp.IRequestInfo;

public class RequestModifHelper {

	
	public static List<String> getModifiedHeaders(List<String> currentHeaders, Session session) {
		List<String> headers = currentHeaders;
		// Check for Parameter Replacement in Path
		replaceParamInPath(headers, session);
		
		if(session.isRemoveHeaders()) {
			String[] headersToRemoveSplit = session.getHeadersToRemove().replace("\r", "").split("\n");
			Iterator<String> iterator = headers.iterator();
			while(iterator.hasNext()) {
				String header = iterator.next();
				for(int i=0; i<headersToRemoveSplit.length; i++) {
					if(header.split(":")[0].equals(headersToRemoveSplit[i].split(":")[0])) {
						iterator.remove();
					}
				}
			}
		}
		for (String headerToReplace : getHeaderToReplaceList(session)) {
			int keyIndex = headerToReplace.indexOf(":");
			if (keyIndex != -1) {
				String headerKey = headerToReplace.substring(0, keyIndex+1);
				boolean headerReplaced = false;
				for (int i = 0; i < headers.size(); i++) {
					if (headers.get(i).startsWith(headerKey)) {
						headers.set(i, headerToReplace);
						headerReplaced = true;
						break;
					}
				}
				// Set new header if it not occurs
				if (!headerReplaced) {
					headers.add(headerToReplace);
				}
			}
		}
		return headers;
	}
	
	private static List<String> replaceParamInPath(List<String> headers, Session session) {
		int paramIndex = headers.get(0).indexOf("?");
		String pathHeader;
		String appendix = "";
		if(paramIndex != -1) {
			pathHeader = headers.get(0).substring(0, paramIndex);
			appendix = headers.get(0).substring(paramIndex);
		}
		else {
			pathHeader = headers.get(0);
		}
		for(Token token : session.getTokens()) {
			if(token.getValue() != null && !token.isRemove() && token.doReplaceAtLocation(TokenLocation.PATH)) {
				String tokenInPathName = "/"+token.getName()+"/";
				int startIndex;
				if(token.isCaseSensitiveTokenName()) {
					startIndex = pathHeader.indexOf(tokenInPathName);
				}
				else {
					startIndex = pathHeader.toLowerCase().indexOf(tokenInPathName.toLowerCase());
				}
				if(startIndex != -1) {
					startIndex = startIndex + tokenInPathName.length();
					int endIndex = pathHeader.indexOf("/", startIndex);
					if(endIndex != -1) {
						pathHeader = pathHeader.substring(0, startIndex) + token.getUrlEncodedValue() + pathHeader.substring(endIndex);
						headers.set(0, pathHeader + appendix);
					}
				}
				// Check for URL path parameters (semicolon syntax)
				String urlPathParameter = ";" + token.getName() + "=";
				int startIndex1 = pathHeader.indexOf(urlPathParameter);
				if(token.isCaseSensitiveTokenName()) {
					startIndex1 = pathHeader.indexOf(urlPathParameter);
				}
				else {
					startIndex1 = pathHeader.toLowerCase().indexOf(urlPathParameter.toLowerCase());
				}
				if(startIndex1 != -1) {
					startIndex1 = startIndex1 + urlPathParameter.length();
					int endIndex1 = pathHeader.indexOf(";", startIndex1);
					if(endIndex1 == -1) {
						// Path Header was divided at '?' therefore endIndex is end of string of path header
						endIndex1 = pathHeader.length();
					}
					if(endIndex1 != -1) {
						pathHeader = pathHeader.substring(0, startIndex1) + token.getUrlEncodedValue() + pathHeader.substring(endIndex1);
						headers.set(0, pathHeader + appendix);
					}
				}
				
			}
		}
		return headers;
	}
	
	private static ArrayList<String> getHeaderToReplaceList(Session session) {
		ArrayList<String> headerToReplaceList = new ArrayList<String>();
		String[] headersToReplace = session.getHeadersToReplace().replace("\r", "").split("\n");
		for (String headerToReplace : headersToReplace) {
			String[] headerKeyValuePair = headerToReplace.split(":");
			if (headerKeyValuePair.length > 1) {
				for (Token token : session.getTokens()) {
					if (headerToReplace.contains(token.getHeaderInsertionPointNameStart())) {
						int startIndex = headerToReplace.indexOf(token.getHeaderInsertionPointNameStart());
						int endIndex = headerToReplace.indexOf("]�", startIndex) + 2;
						if (startIndex != -1 && endIndex != -1) {
							if (token.getValue() != null) {
								headerToReplace = headerToReplace.substring(0, startIndex)
										+ token.getUrlEncodedValue() + headerToReplace.substring(endIndex);
							} else {
								String defaultValue = headerToReplace.substring(
										startIndex + token.getHeaderInsertionPointNameStart().length() + 1,
										endIndex - 2);
								headerToReplace = headerToReplace.substring(0, startIndex) + defaultValue
										+ headerToReplace.substring(endIndex);
							}
						}
					}
				}
				headerToReplaceList.add(headerToReplace);
			}
		}
		return headerToReplaceList;
	}
	
	public static byte[] getModifiedRequest(byte[] originalRequest, Session session, TokenPriority tokenPriority) {
		IRequestInfo originalRequestInfo = BurpExtender.callbacks.getHelpers().analyzeRequest(originalRequest);
		byte[] modifiedRequest = originalRequest;
		for (Token token : session.getTokens()) {
			if (token.getValue() != null || token.isRemove() || token.isPromptForInput()) {
				modifiedRequest = getModifiedRequest(modifiedRequest, originalRequestInfo, session, token, tokenPriority);
			}
		}
		return modifiedRequest;
	}

	public static ArrayList<byte[]> getModifiedRequests(byte[] originalRequest, Session session, TokenPriority tokenPriority) {
		IRequestInfo originalRequestInfo = BurpExtender.callbacks.getHelpers().analyzeRequest(originalRequest);
		byte[] request=originalRequest;
		byte[] modifiedRequest = originalRequest;
		ArrayList<byte[]> requests=new ArrayList<>();
		for (Token token : session.getTokens()) {
			//check if current token is in request's parameter
			boolean existed = false;
			for (IParameter parameter : originalRequestInfo.getParameters()) {
				if (parameter.getName().equals(token.getName()) || parameter.getName().equals(token.getUrlEncodedName()) ||
						(!token.isCaseSensitiveTokenName() && parameter.getName().toLowerCase().equals(token.getName().toLowerCase()))) {
					existed = true;
					break;
				}

			}
			if(existed){
				new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("arrive: 174");
				modifiedRequest = getModifiedRequest(modifiedRequest, originalRequestInfo, session, token, tokenPriority);
				requests.add(modifiedRequest);
			}
			else{
				//build request according to the token location config set
				new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("arrive: 180");
                String paramValueTemp="";
				if (token.isPromptForInput()) {
					paramValueTemp = JOptionPane.showInputDialog(session.getStatusPanel(),
							"<html><strong>"+Globals.EXTENSION_NAME+"</strong><br>" + "Enter Parameter Value<br>Session: "
									+ session.getName() + "<br>Parameter Name: " + token.getName() + "<br>"
									+ "Parameter Location: " + "%s" + "<br></html>");
				}

				for (TokenLocation tokenLocation:token.getTokenLocationSet()
					 ) {
					new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("arrive: 191");
					String paramLocation="";
					HashMap<String, Byte> paramlocationtypemap=new HashMap<String, Byte>(){
						{
							put("URL",IParameter.PARAM_URL);
							put("Cookie",IParameter.PARAM_COOKIE);
							put("Body",IParameter.PARAM_BODY);
							put("Json",IParameter.PARAM_JSON);
						}
					};
					switch (tokenLocation){
						case URL:paramLocation="URL";break;
						case COOKIE:paramLocation="Cookie";break;
						case BODY:paramLocation="Body";break;
						case JSON:paramLocation="Json";
					}
					if (token.isPromptForInput()) {
						String paramValue = String.format(paramValueTemp, paramLocation);
						token.setValue(paramValue);
						session.getStatusPanel().updateTokenStatus(token);
					}
					if(paramLocation.equals("Json")){
					    //add new key to json with token
						//TODO
//						modifiedRequest = getModifiedJsonRequestWithAdditionKey(request, originalRequestInfo, token);
					}
					else {
						new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("arrive: 216");
						if (token.getValue() != null) {
							new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("arrive: 218");
							new PrintWriter(BurpExtender.callbacks.getStdout(),true).println("token value:"+token.getValue());
							tokenPriority.setPriority(tokenPriority.getPriority() + 1);
							IParameter newParameter = BurpExtender.callbacks.getHelpers().buildParameter(token.getName(), token.getUrlEncodedValue(),paramlocationtypemap.get(paramLocation));
							modifiedRequest=BurpExtender.callbacks.getHelpers().addParameter(request,newParameter);
							modifiedRequest = BurpExtender.callbacks.getHelpers().updateParameter(modifiedRequest, newParameter);
							requests.add(modifiedRequest);
						}
					}
				}
			}
		}
			return requests;
	}


	private static byte[] getModifiedRequest(byte[] request, IRequestInfo originalRequestInfo, Session session, Token token, TokenPriority tokenPriority) {
		byte[] modifiedRequest = request;
		boolean existed=false;
		for (IParameter parameter : originalRequestInfo.getParameters()) {

			if (parameter.getName().equals(token.getName()) || parameter.getName().equals(token.getUrlEncodedName()) ||
					(!token.isCaseSensitiveTokenName() && parameter.getName().toLowerCase().equals(token.getName().toLowerCase()))) {
			    existed=true;
				String paramLocation = null;
				// Helper can only handle URL, COOKIE and BODY Parameters
				if (parameter.getType() == IParameter.PARAM_URL) {
					if(token.doReplaceAtLocation(TokenLocation.URL)) {
						paramLocation = "URL";
					}
				}
				if (parameter.getType() == IParameter.PARAM_COOKIE) {
					if(token.doReplaceAtLocation(TokenLocation.COOKIE)) {
						paramLocation = "Cookie";
					}
				}
				if (parameter.getType() == IParameter.PARAM_BODY) {
					if(token.doReplaceAtLocation(TokenLocation.BODY)) {
						paramLocation = "Body";
					}
				}
				// Handle JSON as well (self implemented --> Burp API update parameter does not work for JSON)
				if (parameter.getType() == IParameter.PARAM_JSON) {
					if(token.doReplaceAtLocation(TokenLocation.JSON)) {
						paramLocation = "Json";
					}
				}
				if (paramLocation != null) {
					if (token.isPromptForInput()) {
						String paramValue = JOptionPane.showInputDialog(session.getStatusPanel(),
								"<html><strong>"+Globals.EXTENSION_NAME+"</strong><br>" + "Enter Parameter Value<br>Session: "
										+ session.getName() + "<br>Parameter Name: " + token.getName() + "<br>"
										+ "Parameter Location: " + paramLocation + "<br></html>");
						token.setValue(paramValue);
						session.getStatusPanel().updateTokenStatus(token);
					}
					if (token.isRemove()) {
						if (parameter.getType() == IParameter.PARAM_JSON) {
							modifiedRequest = getModifiedJsonRequest(request, originalRequestInfo, token);
						} else {
							modifiedRequest = BurpExtender.callbacks.getHelpers().removeParameter(modifiedRequest, parameter);
						}
					} else if (token.getValue() != null) {
						tokenPriority.setPriority(tokenPriority.getPriority() + 1);
						if (parameter.getType() == IParameter.PARAM_JSON) {
							modifiedRequest = getModifiedJsonRequest(request, originalRequestInfo, token);
						} else {
							IParameter modifiedParameter = BurpExtender.callbacks.getHelpers().buildParameter(parameter.getName(),
									token.getUrlEncodedValue(), parameter.getType());
							modifiedRequest = BurpExtender.callbacks.getHelpers().updateParameter(modifiedRequest,
									modifiedParameter);
						}
					}
				}
			}
		}
		return modifiedRequest;
	}
	
	private static byte[] getModifiedJsonRequest(byte[] request, IRequestInfo originalRequestInfo, Token token) {
		if (!token.isRemove() && token.getValue() == null) {
			return request;
		}
		JsonElement jsonElement = null;
		try {
			String bodyAsString = new String(
					Arrays.copyOfRange(request, originalRequestInfo.getBodyOffset(), request.length));
			JsonReader reader = new JsonReader(new StringReader(bodyAsString));
			reader.setLenient(true);
			jsonElement = JsonParser.parseReader(reader);
		} catch (Exception e) {
			BurpExtender.callbacks.printError("Can not parse JSON Request Body. Error Message: " + e.getMessage());
			return request;
		}
		modifyJsonTokenValue(jsonElement, token);
		String jsonBody = jsonElement.toString();
		List<String> headers = originalRequestInfo.getHeaders();
		for (int i = 0; i < headers.size(); i++) {
			if (headers.get(i).startsWith("Content-Length:")) {
				headers.set(i, "Content-Length: " + jsonBody.length());
			}
		}
		byte[] modifiedRequest = BurpExtender.callbacks.getHelpers().buildHttpMessage(headers, jsonBody.getBytes());
		return modifiedRequest;
	}
	
	private static void modifyJsonTokenValue(JsonElement jsonElement, Token token) {
		if (jsonElement.isJsonObject()) {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			Iterator<Map.Entry<String, JsonElement>> it = jsonObject.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<String, JsonElement> entry = it.next();
				if (entry.getValue().isJsonArray() || entry.getValue().isJsonObject()) {
					modifyJsonTokenValue(entry.getValue(), token);
				}
				if (entry.getValue().isJsonPrimitive()) {
					if (entry.getKey().equals(token.getName()) || 
							(!token.isCaseSensitiveTokenName() && entry.getKey().toLowerCase().equals(token.getName().toLowerCase()))) {
						if (token.isRemove()) {
							jsonObject.remove(entry.getKey());
						} else {
							jsonObject.addProperty(entry.getKey(), token.getUrlEncodedValue());
						}
					}
				}
			}
		}
		if (jsonElement.isJsonArray()) {
			for (JsonElement arrayJsonEl : jsonElement.getAsJsonArray()) {
				if (arrayJsonEl.isJsonObject()) {
					modifyJsonTokenValue(arrayJsonEl.getAsJsonObject(), token);
				}
			}
		}
	}
}
