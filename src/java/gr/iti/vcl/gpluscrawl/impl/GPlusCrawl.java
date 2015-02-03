
package gr.iti.vcl.gpluscrawl.impl;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

/*
 *
 * @author  Samaras Dimitris 
 * June 5th, 2014
 * dimitris.samaras@iti.gr
 * 
 */
public class GPlusCrawl {
  
    private static final String ACTIVITIES_API_SITE = "https://www.googleapis.com/plus/v1/activities";
    private static final String PREFIX_ORDER = "&orderBy=";
    //private static final String PEOPLE = "/people";
    private static final String RESHARERS = "/resharers";
    private static final String PLUSONERS = "/plusoners";
    private static final String PEOPLE_API_SITE = "https://www.googleapis.com/plus/v1/people";
    // DO NOT FORGET THE " ? " AFTER SEARCH!!!
    private static final String API_KEY = "&key=";
    private static final String PREFIX_QUERY = "query=";
    private static final String PREFIX_LIMIT = "&limit=";
    // Results are always too many to get less than max!!!!! (20 for activity search , 100 for people per activiy!)
    private static final String PREFIX_MAX_RESULTS = "&maxResults=";
    private static final String PREFIX_PAGETOKEN = "&pageToken=";
    public static String STREAM_COMMAND_CREATE = "create";
    public static String STREAM_COMMAND_REMOVE = "remove";
    public Connection connection = null;
    public Channel channel = null;

    public GPlusCrawl() {
    }

    @SuppressWarnings("empty-statement")
    public JSONObject parseOut(JSONObject jsonObject) throws Exception, IOException {

        // Create the JSONObject to construct the response that will be saved to RabbitMQ
        JSONObject resultObject = new JSONObject();
        //JSONArray for acitivies
        JSONArray activities = new JSONArray();
        DateFormat formatter = null;

        String pageToken = "";
        String publishedAfter = null;
        String publishedBefore = null;
        String id;

        try {

                String apiKey_val = jsonObject.getJSONObject("gplus").getString("apiKey");
                String host = jsonObject.getJSONObject("rabbit").getString("host");
                String qName = jsonObject.getJSONObject("rabbit").getString("queue");

                // check for certain crusial params 
                //keywords
                String topic = jsonObject.getJSONObject("gplus").getString("topic").replaceAll(" ", "+");

                if (topic == null || topic.isEmpty()) {
                    err("No topic given to explore, aborting");
                    resultObject.put("Status", "Error");
                    resultObject.put("Message", "No topic given");
                    //return resultObject;
                } else {
                    resultObject.put("Status", "OK");
                    resultObject.put("Message", "200");
                }

                String order = jsonObject.getJSONObject("gplus").optString("order_by", "best");

                formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                publishedAfter = jsonObject.getJSONObject("gplus").optString("publishedAfter", "1970-01-01T00:00:00.000Z");
                Date conv_publishedAfter = (Date) formatter.parse(publishedAfter);
                //get current date and format to pass to publishedBefore
                Date date_now = new Date();
                String conv_date_now = formatter.format(date_now);
                publishedBefore = jsonObject.getJSONObject("gplus").optString("publishedBefore", conv_date_now);
                Date conv_publishedBefore = (Date) formatter.parse(publishedBefore);

                //LOOP THROUGHT PAGES CAUSES REALLY BIG RESULTS
                do {
                    String rsp = activityCallGET(topic, order, pageToken, apiKey_val);

                    // Create the JSONObject to be parsed
                    JSONObject jobj = new JSONObject(rsp);
                    System.out.println(jobj);
                    pageToken = jobj.optString("nextPageToken", "");

                    JSONArray act_items = jobj.getJSONArray("items");

                    for (int i = 0; i < act_items.length(); i++) {
                        //JSONObject for every activity
                        JSONObject act_jobj = new JSONObject();
                        JSONObject item = new JSONObject(act_items.getString(i));

                        String updated_dt = item.getString("updated");
                        Date conv_updated_dt = (Date) formatter.parse(updated_dt);

                        // should also check in there if item.getString("verb").equals("post") to get nly original posts...
                        if(conv_updated_dt.after(conv_publishedAfter) && conv_updated_dt.before(conv_publishedBefore) && item.getString("verb").equals("post")) {
                            id = item.getString("id");

                            act_jobj.put("id", id);
                            act_jobj.put("title", item.getString("title"));
                            act_jobj.put("actor", item.getJSONObject("actor"));

                            //main body of the activity
                            JSONObject object = item.getJSONObject("object");
                            if (object.getString("objectType").equals("note")) {
                                int replies = object.getJSONObject("replies").getInt("totalItems");
                                if (replies > 0) {

                                    //get comments about activity
                                    //https://www.googleapis.com/plus/v1/activities/z130fnxg5lbkxjgul23xcxiohuaiwlz2s/comments?key={YOUR_API_KEY}
                                    JSONArray comments = new JSONArray();
                                    String com_pageToken = "";
                                    do {

                                        String com = commentsCallGET(id, com_pageToken, apiKey_val);
                                        JSONObject com_jobj = new JSONObject(com);
                                        com_pageToken = com_jobj.optString("nextPageToken", "");
                                        JSONArray com_items = com_jobj.getJSONArray("items");

                                        for (int z = 0; z < com_items.length(); z++) {
                                            JSONObject com_item = new JSONObject(com_items.getString(z));
                                            JSONObject com_result = new JSONObject();;
                                            //get comment id
                                            com_result.put("id", com_item.getString("id"));
                                            //get comment author
                                            com_result.put("actor", com_item.getJSONObject("actor"));
                                            //get comment content
                                            com_result.put("content", com_item.getJSONObject("object").getString("content"));
                                            //get comment plusoners 
                                            com_result.put("plusoners", com_item.getJSONObject("plusoners").getString("totalItems"));
                                            //put object in JSONArray
                                            comments.put(com_result);
                                        }
                                        if (com_items.length() <= 0) {
                                            com_pageToken = "";
                                        }

                                    } while (!com_pageToken.equals(""));
                                    act_jobj.put("replies", comments);
                                } else {
                                    act_jobj.put("replies", "No replies on post");
                                }

                                int plusoners = object.getJSONObject("plusoners").getInt("totalItems");
                                if (plusoners > 0) {
                                    //get plusoners about activity
                                    //https://www.googleapis.com/plus/v1/activities/z12ls1k4clzhdvoyc04cfpexixzkwfxwmn40k/people/plusoners?maxResults=100&key={YOUR_API_KEY}
                                    JSONArray act_plusoners = new JSONArray();
                                    String p1_pageToken = "";
                                    do {
                                        String p1 = peopleCallGET(id, PLUSONERS, p1_pageToken, apiKey_val);
                                        JSONObject p1_jobj = new JSONObject(p1);
                                        p1_pageToken = p1_jobj.optString("nextPageToken", "");
                                        JSONArray p1_items = p1_jobj.getJSONArray("items");

                                        for (int z = 0; z < p1_items.length(); z++) {
                                            JSONObject p1_item = new JSONObject(p1_items.getString(z));
                                            JSONObject p1_result = new JSONObject();;
                                            //get p1 id
                                            p1_result.put("id", p1_item.getString("id"));
                                            //get p1 name
                                            p1_result.put("name", p1_item.getString("displayName"));
                                            //get p1 person url
                                            p1_result.put("page", p1_item.getString("url"));
                                            act_plusoners.put(p1_result);
                                        }
                                        if (p1_items.length() <= 0) {
                                            p1_pageToken = "";
                                        }

                                    } while (!p1_pageToken.equals(""));
                                    act_jobj.put("plusoners", act_plusoners);
                                } else {
                                    act_jobj.put("plusoners", "No plus ones on post");
                                }
                                int resharers = object.getJSONObject("resharers").getInt("totalItems");
                                if (resharers > 0) {
                                    //get resharers about activity
                                    //https://www.googleapis.com/plus/v1/activities/z12ls1k4clzhdvoyc04cfpexixzkwfxwmn40k/people/resharers?maxResults=100&key={YOUR_API_KEY}
                                    JSONArray act_resharers = new JSONArray();
                                    String resha_pageToken = "";
                                    do {
                                        String resha = peopleCallGET(id, RESHARERS, resha_pageToken, apiKey_val);
                                        JSONObject resha_jobj = new JSONObject(resha);
                                        resha_pageToken = resha_jobj.optString("nextPageToken", "");
                                        JSONArray resha_items = resha_jobj.getJSONArray("items");

                                        for (int z = 0; z < resha_items.length(); z++) {
                                            JSONObject resha_item = new JSONObject(resha_items.getString(z));
                                            JSONObject resha_result = new JSONObject();;
                                            //get resharer id
                                            resha_result.put("id", resha_item.getString("id"));
                                            //get resharer name
                                            resha_result.put("name", resha_item.getString("displayName"));
                                            //get resharer person url
                                            resha_result.put("page", resha_item.getString("url"));
                                            act_resharers.put(resha_result);
                                        }
                                        if (resha_items.length() <= 0) {
                                            resha_pageToken = "";
                                        }

                                    } while (!resha_pageToken.equals(""));
                                    act_jobj.put("resharers", act_resharers);
                                } else {
                                    act_jobj.put("resharers", "No resharers on post");
                                }
                                //GET THE IMAGE 
                                try {
                                    //Some activities may not have attachments....
                                    if(object.has("attachments")){
                                    JSONArray attachments = object.getJSONArray("attachments");
                                    JSONArray act_attachments = new JSONArray();
                                    if (attachments.length() > 0) {

                                        for (int y = 0; y < attachments.length(); y++) {
                                            JSONObject att_obj = new JSONObject(attachments.getString(y));
                                            String objType = att_obj.getString("objectType");
                                            if (objType.equals("photo") || objType.equals("article")) {
                                                JSONObject image = new JSONObject();
                                                image.put("type", objType);
                                                String displayName = att_obj.optString("displayName", att_obj.optString("content", "No Name"));
                                                image.put("displayName", displayName);
                                                //If the attachmenent has no "fullImage" skip it...it happens with articles
                                                image.put("source", att_obj.optJSONObject("fullImage"));
                                                act_attachments.put(image);

                                            } else if (objType.equals("video")) {
                                                JSONObject image = new JSONObject();
                                                image.put("type", objType);
                                                String displayName = att_obj.optString("displayName", att_obj.optString("content", "No Name"));
                                                image.put("displayName", displayName);
                                                image.put("source", att_obj.getJSONObject("image"));
                                                act_attachments.put(image);

                                            } else if (objType.equals("album")) {
                                                JSONObject image = new JSONObject();
                                                image.put("type", objType);
                                                String displayName = att_obj.optString("displayName", att_obj.optString("content", "No Name"));
                                                image.put("displayName", displayName);
                                                JSONArray thumbs = att_obj.getJSONArray("thumbnails");
                                                JSONArray act_thumbs = new JSONArray();
                                                for (int x = 0; x < thumbs.length(); x++) {
                                                    JSONObject thumb_obj = new JSONObject(thumbs.getString(x));
                                                    act_thumbs.put(thumb_obj.getJSONObject("image"));
                                                }
                                                image.put("thumbanails", act_thumbs);
                                                act_attachments.put(image);
                                            }
                                        }
                                        act_jobj.put("attachments", act_attachments);

                                    } else {
                                        act_jobj.put("attachments", "No attachments");
                                    }
                                    }
                                } catch (JSONException e) {
                                    err("JSONException getting the attachments: " + e);
                                }
                            }
                        }
                        // If criteria set above not met...
//                    else {
//                        id =item.getString("id");
//                        act_jobj.put("id", id);
//                        act_jobj.put("message", "This is reshared post");
//                        
//                    }

                        //PRINT the result if the act_jobj has been filled with data 
                        if (act_jobj.length()>0){
                            activities.put(act_jobj);
                            System.out.println(act_jobj);
                        }
                    }
                    //gather the results 
                    resultObject.put("activities", activities);

                    if (act_items.length() <= 0) {
                        pageToken = "";
                    }

                } while (!pageToken.equals(""));

                //WRITE act_obj to RABBITMQ : act_jobj
                openRMQ(host, qName);
                System.out.println("To Be Written in RabbitMQ : "+activities.length());
                for (int t = 0; t < activities.length(); t++) {
                    JSONObject out = new JSONObject();
                    JSONObject activity = new JSONObject(activities.getString(t));
                    out.put("about", "GooglePlus");
                    out.put("activity", activity);
                    writeToRMQ(out, qName);
                }
                
                closeRMQ();

        } catch (JSONException e) {
            err("JSONException parsing initial response: " + e);
        }

        return resultObject;

    }

    public String activityCallGET(String topic, String order, String pageToken, String apiKey_val) {
        String output;
        int code = 0;
        String msg = null;

        try {
            URL url = new URL(ACTIVITIES_API_SITE + "?" + PREFIX_QUERY + topic + PREFIX_MAX_RESULTS + 20 + PREFIX_ORDER + order + PREFIX_PAGETOKEN + pageToken + API_KEY + apiKey_val);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            // you need the following if you pass server credentials
            // httpCon.setRequestProperty("Authorization", "Basic " + new BASE64Encoder().encode(servercredentials.getBytes()));
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");
            output = convertStreamToString(httpCon.getInputStream());
            code = httpCon.getResponseCode();
            msg = httpCon.getResponseMessage();
            //output = "" + httpCon.getResponseCode() + "\n" + httpCon.getResponseMessage() + "\n" + output;

        } catch (IOException e) {
            output = "IOException during GET activityCallGET: " + e;
            err(output);
        }
        // Check for Response 
        if ((code != 200 || code != 201) && !("OK".equals(msg))) {
            //output = "NOT OK RESPONSE";
            err("Failed activityCallGET: HTTP error code : " + code);
        }
        return output;

    }

    private String commentsCallGET(String id, String pageToken, String apiKey_val) {
        String output;
        int code = 0;
        String msg = null;

        try {
            URL url = new URL(ACTIVITIES_API_SITE + "/" + id + "/comments" + "?" + PREFIX_MAX_RESULTS + 500 + PREFIX_PAGETOKEN + pageToken + API_KEY + apiKey_val);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            // you need the following if you pass server credentials
            // httpCon.setRequestProperty("Authorization", "Basic " + new BASE64Encoder().encode(servercredentials.getBytes()));
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");
            output = convertStreamToString(httpCon.getInputStream());
            code = httpCon.getResponseCode();
            msg = httpCon.getResponseMessage();
            //output = "" + httpCon.getResponseCode() + "\n" + httpCon.getResponseMessage() + "\n" + output;

        } catch (IOException e) {
            output = "IOException during GET SearchListCallGET: " + e;
            err(output);
        }
        // Check for Response 
        if ((code != 200 || code != 201) && !("OK".equals(msg))) {
            //output = "NOT OK RESPONSE";
            err("Failed SearchListCallGET: HTTP error code : " + code);

        }

        return output;
    }

    private String peopleCallGET(String id, String type, String pageToken, String apiKey_val) {
        String output;
        int code = 0;
        String msg = null;

        try {
            URL url = new URL(ACTIVITIES_API_SITE + "/" + id + "/people" + type + "?" + PREFIX_MAX_RESULTS + 100 + PREFIX_PAGETOKEN + pageToken + API_KEY + apiKey_val);
            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            // you need the following if you pass server credentials
            // httpCon.setRequestProperty("Authorization", "Basic " + new BASE64Encoder().encode(servercredentials.getBytes()));
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("GET");
            output = convertStreamToString(httpCon.getInputStream());
            code = httpCon.getResponseCode();
            msg = httpCon.getResponseMessage();
            //output = "" + httpCon.getResponseCode() + "\n" + httpCon.getResponseMessage() + "\n" + output;

        } catch (IOException e) {
            output = "IOException during GET SearchListCallGET: " + e;
            err(output);
        }
        // Check for Response 
        if ((code != 200 || code != 201) && !("OK".equals(msg))) {
            //output = "NOT OK RESPONSE";
            err("Failed SearchListCallGET: HTTP error code : " + code);

        }

        return output;
    }

    private static String convertStreamToString(InputStream is) throws IOException {
        //
        // To convert the InputStream to String we use the
        // Reader.read(char[] buffer) method. We iterate until the
        // Reader return -1 which means there's no more data to
        // read. We use the StringWriter class to produce the string.
        //
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }

            return writer.toString();
        } else {
            return "";
        }
    }

    public void writeToRMQ(JSONObject json, String qName) throws IOException {

        channel.basicPublish("", qName,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                json.toString().getBytes("UTF-8"));
        log(" [x] Sent to queue '" + json + "'");
    }

    public void openRMQ(String host, String qName) throws IOException {
        //Pass the queue name here from the RESQUEST JSON

        //Create queue, connect and write to rabbitmq
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        log("connected to rabbitMQ on localhost ...");

        try {
            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.queueDeclare(qName, true, false, false, null);
        } catch (IOException ex) {
            err("IOException during queue creation: " + ex);
        }
    }

    public void closeRMQ() throws IOException {

        if (connection != null) {
            log("Closing rabbitmq connection and channels");
            try {
                connection.close();
                connection = null;
            } catch (IOException ex) {
                err("IOException during closing rabbitmq connection and channels: " + ex);
            }
        } else {
            log("Closed OK");
        }
    }

    private void log(String message) {
        System.out.println("Google+Crawler:INFO: " + message);
    }

    private void err(String message) {
        System.err.println("Google+Crawler:ERROR:" + message);
    }
}
