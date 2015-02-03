# GooglePlus_Crawler
NetBeans project of Java web crawler for the ‘Google+’ social network API v.1


##About this project 

Project name: GooglePlusCrawler
Architecture: Restfull application
Programming language: java 
Structuring and output format: json
Application server: Apache Tomcat
Messaging system: RabbitMQ, based on the AMQP standard

A java wrapper for the ‘Google+’ social network API v.1, a platform that allows users to interact and communicate with each other.
With the GooglePlusCrawler we try to gather images and surrounding information.
Every image and its relevant metadata form a separate message that is delivered to the RabbitMQ.
The process is initiated by posting (POST request) our request to the Tomcat using a rest client (i.e. Advanced Rest Client for Google Chrome browser) followed by the .json file containing the request payload. The result of the request is written to the RabbitMQ and we get a server response about the operation status.


#Users--- REST calls 

The user in order to search the Google+ social network for uploaded photos over a specific topic has to post a request with a specific payload to indicate the search parameters. 

i.e.
POST http://localhost:8084/Google+Crawler/resources/crawl
Content-Type: "application/json"

Payload
{
"gplus": {
		"apiKey": "yourApiKey",
		"topic":"fashion",
		"order_by":"best/recent",
		"publishedBefore":"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
		"publishedAfter":"2014-04-01T00:00:00.000Z"

 },
"rabbit": {
		"host": "localhost",
		"queue": "GOOGLEPLUS_CRAWLER_IN_QUEUE"
}
}

•	The url defines where the service runs
•	The content-type defines what type is the request payload we are about to send to the application server
•	gplus object:
o	API key primitive ‘apiKey’ is the consumer key provided to us when we register a new application when a new application exploiting the Google+ v1 API is registered to the site.
o	Topic primitive ‘topic’ is the parameter we want to search for.
o	Order primitive ‘order_by’ (optional) specifies how the results will be ordered, default is best.
o	Published before primitive ‘publishedBefore’ (optional)  defines the date before which the video should be posted
o	Published after primitive ‘publishedAfter’ (optional)  defines the date after which the video should be posted
•	rabbit object:
o	Host primitive ‘host’ defines where the RabbitMQ server is hosted 
o	Queue primitive ‘queue’ defined how the queue that will hold the messages should be named.


Since the server returns a 200, OK message the json objects that have been created can be accessed through the RabbitMQ server platform (localhost:15672…guest,guest)
	 

#Developers classes description

Package: gr.iti.vcl.gpluscrawl.impl

GPlusCrawl.java methods documentation

The output of this class is zero or more messages written to a specific RabbitMQ queue containing information about images posted on Google+ as Google+ activities and a json object over the operation status.

parseOut

Responsible method calling the request and parsing the responses from the GET requests to the GOOGLE+ API. Opens and closes connection, creates queues to the RabbitMQ service and writes multiple objects to the queues. Returns a json object that notifies the user over the operation status. The operation time depends on the amount of the resulting topics and videos per topic of the responses that the calls to APIs return.

@param jsonObject 	The paylod of the initial POST request that the user provides and. defines the parameters to form the GET request to the GOOGLE+ API. 
@return 		The json object containing information about process status.
@throws IOException 	If an input or output exception occurred.
@throws Exception 		If an input or output exception occurred.

activityCallGET 

Responsible for passing the user defined parameters to the GET request to the GOOGLE+ API to retrieve activities relevant to the topic and passing the response back so that processing is initiated.

@param topic 		The topic parameter of the request.
@param order		Order results.
@param pageToken		Variable to iterate to results next pages.
@param apikey_val		The Google Developer API key.
@return 		The response of the GET request as String. 

commentsCallGET 

Responsible for passing the activity id parameter to the GET request to the GOOGLE+ API to retrieve comments per activity id.

@param id 		The activity id parameter of the request.
@param apikey_val		The Google Developer API key.
@return 		The string containing the GET response. 

peopleCallGET

Responsible for passing the activity id parameter to the GET request to the GOOGLE+ API to retrieve people of certain category per activity id.

@param id 		The activity id parameter of the request.
@param apikey_val		The Google Developer API key.
@return 		The string containing the GET response. 

convertStreamToString

Responsible for parsing the input stream created by the GET request to a String 

@param is 		The inputStream.
@return 		The String. 
@throws IOException 	If an input or output exception occurred.

writeToRMQ

Responsible for writing messages to the queue.

@param json 		The json object that will be stored to the messages queue (bytes).
@param qName		The qName that the message will be stored to.
@throws IOException 	If an input or output exception occurred.

openRMQ

Responsible for creating a connection with the RabbitMQ server and creating a queue 

@param host		The host of the RabitMQ.
@param qName		The qName that the message will be stored to.
@throws IOException 	If an input or output exception occurred.

closeRMQ

Responsible for closing the connection and channels to the RabbitMQ queue

log & err

Logging and error messaging methods

Package: gr.iti.vcl.gpluscrawl.rest

GPlusCrawl_Rest.java methods documentation

@POST
@Consumes("application/json")
@Produces("application/json")

postJson

The rest implementation for the Google+ crawler.
@param json 	The json object containing the payload for the Post request provided by the user.
@return json	The json object containing the operation status.
@throws Exception	if json object not provided to method 


#Problems met

There is a bug concerning next page Tokens’ primitive value when searching for activities. NextPageToken has always a value whether there is one or not, so a check to the response whether is empty or not is required.

With topic search the results’ number returned is really big because with that request every metadata field gets searched for match. Restriction using request parameters is essential.

Activities shared are treated as separate activities but because this results to multiple records with the same image only originals are taken into consideration.

Because of the sequential and multiple get requests as resulting videos increase the process slows down.

#Future work

Add encoding to messages posted  to RabbitMQ (UTF-8)

Multitheading? Change set for activities to HashMap for parallel processing? 

Display all the messages created as a single object as a server log message. 
 
Try Catch statements surrounding all object parsing methods to prevent user from malformed and erroneous input…. (Maybe! restrict it from the UI).

ETags  primitive stored in the messages while seeming irrelevant  can be of use for search results Optimization and Quota usage limitation because  ETags will change whenever the underlying data changes.

Search shared activities for additional +1 and comments.

Output only the get operation status instead of the entire result.

Plus.people.search for extensive information about people?
