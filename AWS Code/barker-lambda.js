/**
 *  Barker - Lambda Code
 *
 *  Version 1.0 - 26 Dec 2016 Copyright © 2016 Jake Tebbett (jebbett)
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
 */
'use strict';
exports.handler = function( event, context ) {
    var https = require( 'https' );
    // Paste app code here between the breaks------------------------------------------------
    var STtoken = '#########ST TOKEN#######';
    var url='#########URL#######' ;
        //---------------------------------------------------------------------------------------
        var cardName ="";
        var stop;
        var areWeDone = false;
        var endSession = true;
        var processedText;
        var process = false;
        if(event.request.intent){
            var intentName = event.request.intent.name;
            var ttstext = event.request.intent.slots.ttstext.value;
        }else{
            intentName = false
        }
        var speechText;
        var outputTxt;
        var pContCmds;
        var cancel;
        var no;
console.log (event.request.type);
if (ttstext=="stop"||ttstext=="nope"||ttstext=="no"||ttstext=="nothing"||ttstext=="no thank you"||ttstext=="no thanks"||ttstext=="cancel"||ttstext=="exit") {
areWeDone=true;
output(" No problem, Goodbye ", context, "Amazon Stop", areWeDone);
}
else if (intentName) {
                url += 't?ttstext=' + ttstext + '&intentName=' + intentName;
                process = true;
}
else if (!intentName){
    process = false;
}
if (!process) {
areWeDone=false;
output("Pardon, could you please repeat?", context, areWeDone); 
}
else {
                    url += '&access_token=' + STtoken;
                    https.get( url, function( response ) {
                    response.on( 'data', function( data ) {
                    var resJSON = JSON.parse(data);
                    var pContCmds = resJSON.pContCmds;
                    var speechText = resJSON.outputTxt;
                    console.log(speechText);
                    if (pContCmds === true) { 
                        areWeDone=false;
                        speechText = speechText + ', anything else?'; 
                    }
                    else {
                        areWeDone=true;
                    }
                    output(speechText, context, cardName, areWeDone);
                } );
            } );
        }
function output( text, context ) {
            var response = {
             outputSpeech: {
             type: "PlainText",
             text: text
                 },
                 card: {
                 type: "Simple",
                 title: "barker",
                 content: text
                    },
                    shouldEndSession: areWeDone
                    };
                    context.succeed( { response: response } );
  }
};