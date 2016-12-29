/**
 *  Barker - Your Personal Butler To 'Go Fetch' Actions From Other Apps
 *
 *  Copyright 2016 Jake Tebbett (jebbett)
 *
 *  Special thanks to Keith DeLong @N8XD (For publishing the original base code)
 *  And to Jason Headley @Bamarayne and Bobby Dobrescu @SBDOBRESCU for letting me use their modified version of code.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 * VERSION CONTROL
 *
 *	V0.1	2016-12-29	Initial Beta
 *
 *	
 */
definition(
    name: "Barker (Personal Butler)",
    namespace: "jebbett",
    author: "Jake Tebbett",
    description: "Your Personal Butler To Go Fetch Actions From Other Apps",
    category: "My Apps",
	iconUrl: "https://raw.githubusercontent.com/jebbett/Barker/master/icons/Barker.png",
    iconX2Url: "https://raw.githubusercontent.com/jebbett/Barker/master/icons/Barker.png",
    iconX3Url: "https://raw.githubusercontent.com/jebbett/Barker/master/icons/Barker.png"
)

preferences {
	page name: "mainPage"
    page name: "listPage"
    page name: "logPage"
    
}

private mainPage() {
	dynamicPage(name: "mainPage", title: "", install: true, uninstall: true) {
		section ("What's My Name Sir?") { 
            input "butlerName", "text", title: "Your Butlers Name:", defaultValue: "Barker", required: false, image: "https://raw.githubusercontent.com/jebbett/Barker/master/icons/Barker.png"
        }
        section ("The Commands I Can Run For You Sir") {
	        href(name: "listPage", title:"Active Commands", description: "", page: "listPage")
        }
        section(title: "Alexa Settings") {
			input "echoRespond", "bool", title: "Should Alexa Ask If There Is Anything Else?", defaultValue: false, required: false
        }
        section ("Facebook Messenger Settings") { 
            input "fbVerifyToken", "password", title: "Verify Token", description:"", required: false
            input "fbAccessToken", "password", title: "FB Page Access Token", description:"", required: false
            input "fbAdminUser", "text", title: "Admin ID (Must Be A Single ID)", description:"", required: false
            paragraph "Verify Token: Used to setup link to FB bot (You make up this value)\nAccess Token: To allow ST to send messages via FB Messenger (From developers.facebook.com)\nAllowed Users: These are the Facebook user IDs, seperated by commas (You can get this in the debug logging)"
        }
        section(title: "Debugging") {
        	href name: "logPage", title:"Last 20 Commands", description: "", page: "logPage"
			input "debugLogging", "bool", title: "Enable Debug Logging", defaultValue: false, submitOnChange: true, required: false
        }
	}
}

private listPage() {
	dynamicPage(name: "listPage", title: "", install: false, uninstall: false) {
		section ("Commands I Can Run For You Sir") {
			input "modType", "enum", title: "Available appLink Commands", required: false, submitOnChange: true, options: appLinkHandler(value: "list")
        }
	}
}

private logPage() {
	dynamicPage(name: "logPage", title: "", install: false, uninstall: false) {
		section ("Last 20 Commands") {
			paragraph(updateLog("get", "messageLog", 20, null))
        }
	}
}

mappings {
      path("/t") { action: [GET: "echoHandler"] }
      path("/m") { action: [GET: "messengerGetHandler", POST: "messengerPostHandler"] }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    initialize()
}

def initialize() {
    // Subscribe To New Activities To Trigger and get updated list
    subscribe(location, "appLink", appLinkHandler)
    sendLocationEvent(name: "appLink", value: "refresh" , isStateChange: true, descriptionText: "appLink refresh")
	// Create Access Token
	if (!state.accessToken) {
    	try {
		createAccessToken()
			logWriter("Created Access Token")
		} catch (e) {
			logWriter("Please enable OAuth in the IDE")
		}
    }
    // publish app ID and token to debug logging
    logWriter("ECHO LAMBDA INFO:\nvar STtoken = '${state.accessToken}';\nvar url = '${getApiServerUrl()}/api/smartapps/installations/${app.id}/';")
    logWriter("URL FOR USE AT DEVELOPERS.FACEBOOK.COM:\n${getApiServerUrl()}/api/smartapps/installations/${app.id}/m?access_token=${state.accessToken}")
}

// Debug Logging
def logWriter(value) {
	if(debugLogging) {log.debug "${app.label} >> ${value}"}
}

/*****************************************************************/
/** Check and Action command if available
/*****************************************************************/

def actionCommand(command){
	def textToProcess = command.toLowerCase()
    def returnMsg = "Sorry $butlerName could not carry out $textToProcess"
	// update historic log    
    updateLog("set", "messageLog", 20, "${textToProcess}")
    // remove please for polite people
    textToProcess = textToProcess.minus(" please")
    
    // Barker responses
    def barkerList = [
        "help":"I can't help you at the moment",
        "about ${butlerName.toLowerCase()}":"${butlerName} is your butler, he can help you trigger various activities"
	]
    
    barkerList.each { key, value->
        if( textToProcess.endsWith(key) ){ returnMsg = "$value" }
    }
    
    // run appLink command if available
    state.appLink.each { key, value ->
    	value.each { skey, svalue ->
        	// convert to lowercase and check if value is in the list
            if( textToProcess.endsWith( "${svalue}".toLowerCase() ) ){
            	logWriter("Found Match For: ${svalue}")
               	appLinkHandler(value: "run", data: "${key}:${skey}")
                returnMsg = "OK, $svalue"
            }
        }
    }
    return returnMsg
}

/*****************************************************************/
/** Logging
/*****************************************************************/
def updateLog(command, name, length, event){
    def logName = "log$name" as String
    if(length == null || length == 0){state.remove(logName); return "State Cleared"}
    if(!state."$logName"){state."$logName" = []}
    def tempList = state."$logName"

    switch(command) {    
        case "set":
            if(!length || tempList.size() < length){length = tempList.size()+1}
            tempList.add(0,"${new Date(now()).format("dd MMM HH:mm", location.timeZone)} - ${event}")
            state."$logName" = tempList.subList(0, length)
        break;

        case "get":
            if(!length || tempList.size() < length){length = tempList.size()}
            def formattedList = ""
            tempList = tempList.subList(0, length)
            tempList.each { item ->
                if(formattedList == ""){formattedList = item} else {formattedList = formattedList + "\n" + item}
            }
            return formattedList
        break;
    }
}

/*****************************************************************/
/** appLink
/*****************************************************************/

// appLink core code: This should be included in every appLink compatible app, and not customised.
def appLinkHandler(evt){
    if(!state.appLink) state.appLink = [:]
    switch(evt.value) { //[appLink V0.0.2 2016-12-08]
   		case "add":	state.appLink << evt.jsonData;	break;
        case "del":	state.appLink.remove(evt.jsonData.app);	break;             
        case "list":	def list = [:];	state.appLink.each {key, value -> value.each{skey, svalue -> list << ["${key}:${skey}" : "[${key}] ${svalue}"]}};
        	return list.sort { a, b -> a.value.toLowerCase() <=> b.value.toLowerCase() };	break;
        case "run":	sendLocationEvent(name: "${evt.data.split(":")[0]}", value: evt.data.split(":")[1] , isStateChange: true, descriptionText: "appLink run"); break;
    }
    state.appLink.remove("$app.name")
}

/*****************************************************************/
/** Amazon Echo
/*****************************************************************/
def echoHandler(){
	//vars
    def echoRawTxt = params.ttstext
    def echoIntent = params.intentName
    logWriter("Recived ${echoRawTxt} @ ${echoIntent}")
	def responseTxt = actionCommand(echoRawTxt)
	// Respond back to Echo
   	logWriter("Response Back To Echo: ${responseTxt}")
   	return ["outputTxt":responseTxt, "pContCmds":echoRespond]
}

/*****************************************************************/
/** Facebook Messenger
/*****************************************************************/

def messengerGetHandler(){
    if (params.hub.mode == 'subscribe' && params.hub.verify_token == settings.fbVerifyToken) {
        log.debug "Validating webhook"
        render contentType: "text/html", data: params.hub.challenge, status: 200
    } else {
        log.debug "Failed validation. Make sure the Verify Tokens match."
        render contentType: "text/html", data: params.hub.challenge, status: 403        
    }
}

def messengerPostHandler(){
    if(!state.allowedUsers) state.allowedUsers = []
    //state.allowedUsers = []
    def fbJSON = request.JSON
    def responseTxt = ""
    def senderID = fbJSON.entry.messaging.sender.id[0][0]
    def fbMessage = fbJSON.entry.messaging.message.text[0][0]
    
	if(state.allowedUsers.contains(senderID)|| settings?.fbAdminUser == senderID){
    	responseTxt = actionCommand(fbJSON.entry.messaging.message.text[0][0]) as String
        if(settings?.fbAdminUser == senderID && fbMessage.startsWith("Add ")){
			fbMessage = fbMessage.substring(4)
            def addUserName = fbGetUser(fbMessage)
            if(addUserName != "Unknown" && !state.allowedUsers.contains(fbMessage)){
        		responseTxt = "Added $addUserName to be able to send messages to Barker, make sure they are added as a test user otherwise Barker won't be able to talk back" as String
                state.allowedUsers.add(fbMessage)
                fbSendMessage(fbMessage, "You can now send messages to ${butlerName}", null)
            }
        }
    }else{
    	responseTxt = "Sorry I don't know you."
        //send message to admin
        def intMsg = "${fbGetUser(senderID)} has sent a message, if you want to allow access then reply - Add ${senderID}" as String
        fbSendMessage(fbAdminUser, intMsg, "Add ${senderID}")
    }
    fbSendMessage(senderID, responseTxt, null)
}

def fbSendMessage(userid, String message, String buttontxt){
	def params = []
    if(buttontxt == null){
        params = [
            uri: "https://graph.facebook.com/v2.6/me/messages?access_token=$fbAccessToken",
            body: [recipient: [id: userid], message: [text: message]]
        ]
    }else{
        params = [
            uri: "https://graph.facebook.com/v2.6/me/messages?access_token=$fbAccessToken",
            body: [recipient: [id: userid], "message": [text: message, quick_replies:[
                [content_type:"text", title: buttontxt, payload:"none", image_url:""]
            ]]]
        ]
    }
    try { httpPostJson(params) } catch (e) { log.error "something went wrong: $e" }
}

def fbGetUser(userID){
	def params = [uri: "https://graph.facebook.com/v2.6/${userID}?access_token=${fbAccessToken}&fields=first_name,last_name"]
    try { httpGet(params) { resp -> return "${resp.data.first_name} ${resp.data.last_name}" }
    } catch (e) { log.error "something went wrong: $e"; return "Unknown" }
}