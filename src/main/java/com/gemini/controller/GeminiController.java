package com.gemini.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/ocr")
public class GeminiController {

//	private static final String OPENAI_API_KEY = "AIzaSyDZ9swokAW-8SvTEtQRwAZkfMQdDtsSg7c";
	private static final String OPENAI_API_KEY ="AIzaSyBha0qrtgD-D3RJKYGySBmiu7M6Hh17XKY";
	
	@PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<String> extractFromImage(@RequestParam("file") MultipartFile imageFile) throws Exception {

	    // 1. Get the bytes
	    byte[] imageBytes = imageFile.getBytes();
	    
	    // 2. Encode to Base64 and ensure no line breaks (\n or \r)
	    String base64Image = Base64.getEncoder().encodeToString(imageBytes).replaceAll("\\s", "");

	    // 3. Get the correct MIME type (e.g., image/png, image/jpeg)
	    String mimeType = imageFile.getContentType();

	    String prompt = """
	            You are a professional document classifier and data extraction expert. 
			Analyze the provided multi-page document and return the following details in a structured format:
			
			1. Identify the 'Total Pages' in the document.
			2. For EACH page, identify the 'Document Type' (e.g., Bank Statement, Invoice, ID, etc.) 
			   and provide a brief summary of what data is visible on that specific page.
			3. Determine the 'Start Page' and 'End Page' for each specific document type found 
			   within the file.
			
			MANDATORY OUTPUT RULES:
			- If a document spans multiple pages, clearly state the range (e.g., Bank Statement: Page 1 to Page 5).
			- Report if any pages contain duplicate data or repeating headers.
			- Return the response clearly and concisely.
	            """;

	    // Pass mimeType to your helper method
	    System.err.println("calling openAi () ++++");
	    String response = callOpenAIVision(prompt, base64Image, mimeType);
	    System.out.println("got the response"+response);
//	    return ResponseEntity.ok(response);
	 // 3. Parse the specific JSON field from the Gemini response
	    System.out.println("cleaning json---------------");
	    String cleanJson = parseGeminiResponse(response);
	    System.err.println("json Cleaned ");
	    return ResponseEntity.ok()
	            .header("Content-Type", "application/json")
	            .body(cleanJson);
	    
	}
	
	private String callOpenAIVision(String prompt, String base64Image, String mimeType) throws Exception {
	    // 1. Updated to a stable version (Gemini 2.5 is very new/preview; 2.0 is stable)
	    String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + OPENAI_API_KEY;

	    // 2. IMPORTANT: Escape the prompt to make it JSON-safe
	    // Without this, the newlines in your prompt will break the JSON structure
	    ObjectMapper mapper = new ObjectMapper();
	    String jsonSafePrompt = mapper.writeValueAsString(prompt); 
	    // This adds surrounding quotes, so we strip them for the text block below
	    jsonSafePrompt = jsonSafePrompt.substring(1, jsonSafePrompt.length() - 1);

	    String requestBody = """
	    {
	      "contents": [
	        {
	          "parts": [
	            { "text": "%s" },
	            {
	              "inline_data": {
	                "mime_type": "%s",
	                "data": "%s"
	              }
	            }
	          ]
	        }
	      ]
	    }
	    """.formatted(jsonSafePrompt, mimeType, base64Image);

	    HttpRequest request = HttpRequest.newBuilder()
	            .uri(URI.create(url))
	            .header("Content-Type", "application/json")
	            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
	            .build();

	    HttpClient client = HttpClient.newHttpClient();
	    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
	    
	    return response.body();
	}
	private String parseGeminiResponse(String rawResponse) throws Exception {
	    ObjectMapper mapper = new ObjectMapper();
	    JsonNode root = mapper.readTree(rawResponse);
	    
	    // Check if the API returned an error object instead of candidates
	    if (root.has("error")) {
	        throw new RuntimeException("Gemini API Error: " + root.path("error").path("message").asText());
	    }

	    JsonNode candidates = root.path("candidates");
	    if (candidates.isMissingNode() || candidates.isEmpty()) {
	        System.err.println("Full Response: " + rawResponse);
	        throw new RuntimeException("AI returned no results. Check safety filters or image quality.");
	    }

	    String contentText = candidates.get(0)
	                             .path("content")
	                             .path("parts").get(0)
	                             .path("text").asText();

	    // Remove potential Markdown formatting
	    return contentText.replaceAll("(?s)^```(?:json)?\\n|\\n```$", "").trim();
	}
}
