package potaymaster.aws.lambda.jasperreports;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;

public class LambdaFunctionHandler implements RequestStreamHandler
{
	LambdaLogger logger;
	String template;
	String ext;
	JSONObject postBody;

	public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
		this.logger = context.getLogger();
		JSONObject responseJson = new JSONObject();
		try {
			processInputStream(inputStream);
			
			AmazonS3Consumer s3Consumer = new AmazonS3Consumer(this.logger);
			s3Consumer.retrieveTemplateFromS3(this.template);

			ReportGenerator reportGenerator = new ReportGenerator(this.logger, this.ext);
			String encodedReport = reportGenerator.generateBase64EncodedReport(this.postBody);
			
			buildSuccessfulResponse(encodedReport, responseJson);
		}
		catch (ParseException e) {
			e.printStackTrace();
			logger.log("Parse error when handling request." + e.getMessage());
			this.buildErrorResponse(e.getMessage(), 400, responseJson);
		}
		catch (Exception e) {
			e.printStackTrace();
			logger.log("Error when handling request." + e.getMessage());
			this.buildErrorResponse(e.getMessage(), 500, responseJson);
		}
		OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
		writer.write(responseJson.toString());
		writer.close();
	}

	private void processInputStream(InputStream inputStream) throws ParseException, IOException {
		JSONParser parser = new JSONParser();
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
		JSONObject queryParameters = null;
		this.template = "template";
		this.ext = "pdf";
		this.postBody = null;
		try {
			JSONObject event = (JSONObject) parser.parse((Reader) reader);

			if (event.get("queryStringParameters") != null) {
				queryParameters = (JSONObject)event.get("queryStringParameters");
				if (queryParameters.get((Object)"template") != null) {
					this.template = (String)queryParameters.get((Object)"template");
				}
				if (queryParameters.get((Object)"export") != null) {
					String ext = (String)queryParameters.get((Object)"export");
					this.ext = this.getExt(ext);
				}
			}

			if (event.get("body") != null) {
				this.postBody = (JSONObject)parser.parse((String)event.get("body"));
				if (this.postBody.get("template") != null) {
					this.template = (String)this.postBody.get("template");
				}
				if (this.postBody.get("export") != null) {
					String ext = (String)this.postBody.get("export");
					this.ext = this.getExt(ext);
				}
			}
			
			if (!this.template.contains(".jrxml")){
				this.template = this.template + ".jrxml";
			}
		}
		catch (ParseException e) {
			logger.log("Error when parsing inputstream.");
			throw e;
		} catch (IOException e) {
			logger.log("Error extracting inputstream.");
			throw e;
		}
	}

	private String getExt(String ext){
		return ext.equals("excel") || ext.equals("xls") || ext.equals("xlsx") ? "xls" : "pdf";
	}

	@SuppressWarnings("unchecked")
	public void buildSuccessfulResponse(String encodedReport, JSONObject responseJson) {
		JSONObject headerJson = new JSONObject();
		this.fillResponseHeaders(headerJson);
		responseJson.put("body", encodedReport);
		responseJson.put("statusCode", 200);
		responseJson.put("isBase64Encoded", true);
		responseJson.put("headers", headerJson);
	}

	@SuppressWarnings("unchecked")
	public void buildErrorResponse(String body, int statusCode, JSONObject responseJson) {
		responseJson.put("body", body);
		responseJson.put("statusCode", statusCode);
	}

	private void fillResponseHeaders(JSONObject headerJson){
		if(this.ext.equals("pdf")){
			headerJson.put("Content-Type", "application/pdf");
			headerJson.put("Accept", "application/pdf");
			headerJson.put("Content-disposition", "attachment; filename=file.pdf");
		}else{
			headerJson.put("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			headerJson.put("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			headerJson.put("Content-disposition", "attachment; filename=file.xls");
		}
	}
}