/**
*  Zigbee Monitor Driver
*  Version: See ChangeLog
*  Download: See importUrl in definition
*  Description: Provides insights into the performance of a Zigbee repeater. Also allows the publishing and merging of Hub based Zigbee information.
*
*  Copyright 2023 Gary J. Milne
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
*  implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
*
*  License:
*  You are free to use this software in an un-modified form. Software cannot be modified or redistributed without prior consent of the author.
*
*  Zigbee Repeater Monitor - CHANGELOG
*  Version 1.0.0 - Initial Release
*  Version 1.0.1 - Changed endpointId from static "0x01" to dynamic using "0x${device.endpointId}"
*  Version 1.0.2 - Updated getHubInfo() to support alternate path "/hub/zigbeeDetails/json" for Zigbee information being introduced in > "2.3.7.1"
*  Version 1.0.3 - Add function compareVersions() to do a precise version comparison between the current firmware version on the box and a reference version, in this case "2.3.7.1".
*  Version 1.0.4 - Updated logic for checking online\offline status so that a device is marked online as soon as any traffic is received.
* 
*  Authors Notes:
*  For more information on the Zigbee Monitor Driver see:
*  Original posting on Hubitat Community forum: https://community.hubitat.com/t/release-zigbee-monitor-driver-like-xray-glasses-for-zigbee-repeaters-and-simple-switches/127676
*  Zigbee Monitor Documentation: N/A
*
*  Gary Milne - January 6th, 2023 @ 10:28 AM
*  Build Version 46
*
**/

import groovy.json.JsonSlurper
import groovy.transform.Field
import java.text.SimpleDateFormat
@Field def ZIGBEE_ERROR_MAP = ["00":"SUCCESS", "80":"INV_REQUESTTYPE", "81":"DEVICE_NOT_FOUND", "82":"INVALID_EP", "83":"NOT_ACTIVE", "84":"NOT_SUPPORTED", "85":"TIMEOUT", "86":"NO_MATCH", "88":"NO_ENTRY", "89":"NO_DESCRIPTOR", "8A":"INSUFFICIENT_SPACE", "8B":"NOT_PERMITTED", "8C":"TABLE_FULL", "8D":"NOT_AUTHORIZED", "8E":"DEVICE_BINDING_TABLE_FULL"]
@Field def dataSeparatorMap = [0:",", 1:";", 2:":", 3:"|"]
@Field static final driverVersion = "<b>Zigbee Monitor Driver v1.0.4 (1/6/24)</b>"
@Field static final driverBuild = 44

metadata {
    definition (name: "Zigbee Monitor Driver", namespace: "garyjmilne", author: "Gary J. Milne", singleThreaded:true, importUrl: "https://raw.githubusercontent.com/GaryMilne/Hubitat-Zigbee/main/Zigbee_Monitor_Driver.groovy",) {
        capability "Actuator"
        capability "Switch"
        capability "HealthCheck"
        capability "SignalStrength"
        
        //Commands
        command "configure", [ [name:"⚙️ Configure: Retrieves basic information from the device and populates the Driver Details Data area. This is optional."]]
        command "getDeviceInfo", [ [name:"🔍 Get Device Neighbors and Routes tables from the device and display information based upon the the preferences section below. Note: Not all device support retrieval of this data. See Help in preferences section."]]
        command "getHubInfo", [ [name:"📜 Get Hub Info: This command initiates two http requests to the hub. 1️⃣ Get JSON data from: /hub2/zigbeeInfo or /hub/zigbeeDetails/json. 2️⃣ Get JSON data from: /hub/zigbee/getChildAndRouteInfoJson. \
                                 Information received is displayed according to the preferences section below."]]
        command "off", [ [name:"🔴 Switch Off: Turns the Zigbee switch OFF -- OR -- Turns OFF the Zigbee device logging. See preferences."]]
        command "on", [ [name:"🟢 Switch On: Turns the Zigbee switch ON -- OR -- Turns ON the Zigbee device logging. See preferences."]]
        command "ping", [ [name:"📡 Ping: Sends a command to the Zigbee device. If a response is received it resets the HealthCheck timeout. See preferences."]]
        command "initialize", [ [name:"❗Initialize:Run this command first. On first run it does the following: 1️⃣Clears all settings and jobs that may exist from the previous driver. 2️⃣Configures the default values for the driver settings. \
                                 3️⃣Creates the default state data structures. 🔄 On subesequent runs it does the following: 🅰Clears any scheduled jobs and rebuilds them based on current settings. 🅱️Initializes any required data structures that may be absent or empty."]]
        command "wipe", [ [name:"🧽 Wipe: This command wipes data from the device driver. 💣No parameter deletes ALL data categories. Optional parameters: 1️⃣Deletes ALL State data. 2️⃣Deletes ALL Current State data. \
                           3️⃣Deletes ALL Preferences\\Settings. 4️⃣Deletes ALL scheduled jobs. 5️⃣Deletes ALL Device Details Data (Configure Data). 6️⃣Deletes ALL State and Current State data. ⚠️Use wipe before switching this device to a different device driver. \
                           If you want to re-use this driver after a wipe you must perform Initialize() again.", type: "NUMBER", description: "The category to delete (1 - 6)."]]                  
        //command "test"
        
        //Attributes
        attribute "Status", "string"
                           
        //Health
        attribute "healthStatus", "enum", ["offline", "online", "unknown"]        
        attribute "checkInterval", "number"
        
        //Device Attributes
        //attribute "diagnostic", "string"
        attribute "deviceChildren", "string"
        attribute "deviceChildCount", "number"
        attribute "deviceDataCollectionMode", "string"
        attribute "deviceLastUpdate", "string"
        attribute "deviceNeighborCount", "number"
        attribute "deviceNeighbors", "string"
        attribute "deviceParent", "string"
        attribute "deviceRoutes", "string"
        attribute "deviceRouteCount", "number"
        attribute "deviceRepeaters", "string"
        attribute "deviceRepeatersCount", "number"
        attribute "switch", "string"
        
        //Hub Attributes
        attribute "hubChildCount", "number"
        attribute "hubChildren", "string"
        attribute "hubDataCollectionMode", "string"
        attribute "hubDeviceCount", "number"
        attribute "hubLowestLqiValue", "number"
        attribute "hubLowestLqiName", "string"
        attribute "hubLastUpdate", "string"
        attribute "hubNeighbors", "string"
        attribute "hubNeighborCount", "number"
        attribute "hubRoutesActive", "string"
        attribute "hubRouteCountActive", "number"
        attribute "hubRouteCountTotal", "number"
        attribute "hubRouteCountUnused", "number"
                
        //Zigbee Attributes
        attribute "zigbeePanID", "number"
        attribute "zigbeeExtPanID", "string"
        attribute "zigbeeNetworkState", "string"
        attribute "zigbeeChannel", "number"
        attribute "zigbeePowerLevel", "number"
        attribute "zigbeeJoinMode", "boolean"
    }
}
    
     section("Configure the Inputs"){
        //Hub
        input name: "hublink", type: "hidden", title: bold("Hub: ") + "<a href='https://community.hubitat.com/t/release-zigbee-monitor-driver-like-xray-glasses-for-zigbee-repeaters-and-simple-switches/127676' target='_blank'>Community Thread Link</a>",
                description: italic("This driver has multiple modes of operation. It can read a variety of Zigbee information from the hub and the device as an aid to monitoring and troubleshooting. The above link will take you to the community thread where you will find documentation and support.")
        input name: "hubDataCollectionMode", type: "enum", title: bold("Hub: ") + dodgerBlue("Data Collection Mode:"), description: italic("This setting determines which types of data will be collected from Hub.)"),
                options: [ [0:"No Data Collection"],[1:"Collect Only Zigbee Address Information (for name resolution)"],[2:"Collect All Zigbee Information"] ], defaultValue: 1, required: true
        input name: "hubPollInterval", type: "enum", title: bold("Hub: ") + dodgerBlue("Data Polling Interval:"), description: italic("The time in minutes between requests sent to the hub for updated Zigbee information.  (Default - 24 hours)."),
                options: [ [0:" Never"],[1:" 1 minute"],[2:" 2 minutes"],[5:" 5 minutes"],[10:"10 minutes"],[15:"15 minutes"],[30:"30 minutes"],[60:" 1 hour"],[120:" 2 hours"],[180:" 3 hours"],[360:" 6 hours"],[720:" 12 hours"],[1440:" 24 hours"] ], defaultValue: 1440, required: true
        
        //Device
        input name: "devicelink", type: "hidden", title: bold("Device: ") + "<a href='https://community.hubitat.com/t/release-zigbee-monitor-driver-like-xray-glasses-for-zigbee-repeaters-and-simple-switches/127676/2' target='_blank'>Device Compatibility Link</a>",
                description: italic("This driver uses 'generic' zigbee calls and will work on any repeater/switch device that has implemented support for Zigbee management requests. The above link will take you to the community thread where any specific device compatibility is discussed.")
        input name: "deviceDataCollectionMode", type: "enum", title: bold("Device: ") + dodgerBlue("Data Collection Mode:"), description: italic("This setting determines which types of data will be collected from the device.)"),
                options: [ [0:"No Data Collection"],[1:"Collect Only Neighbor Data"],[2:"Collect Only Routing Data"],[3:"Collect Neighbor & Routing Data"]], defaultValue: 1, required: true
        input name: "devicePollInterval", type: "enum", title: bold("Device: ") + dodgerBlue("Data Polling Interval."), description: italic("The time between requests sent to the device for updated Neighbor and Routing information. (Default - 60 Minutes)"),
                    options: [ [0:" Never"],[1:" 1 minute"],[2:" 2 minutes"],[5:" 5 minutes"],[10:"10 minutes"],[15:"15 minutes"],[30:"30 minutes"],[60:" 1 hour"],[120:" 2 hours"],[180:" 3 hours"] ], defaultValue: 60, required: true
        
        //Both
        input name: "neighborSortOrder", type: "enum", title: bold("Hub & Device: ") + dodgerBlue("Neighbor Sort Order."), description: italic("The order the device neighbors are displayed in the xxxNeighbors attribute.."), options: [ [0:"Highest LQI - Neighbor"], [1:"Lowest LQI - Neighbor"], [2:"Neighbor(A-Z) - LQI"]], defaultValue: 0, required: true
        input name: "routeSortOrder", type: "enum", title: bold("Hub & Device: ") + dodgerBlue("Route Sort Order."), description: italic("The order the device routes are displayed in the xxxRoutes Attribute."), options: [ [0:"Next Hop ➡ Device"], [1:"Device via Next Hop"] ], defaultValue: 0, required: true
        input name: "bothblank", type: "hidden", title: " ", description: ""
        
        //Misc
        input name: "switchBehaviour", type: "enum", title: bold("Switch: ") + dodgerBlue("Switch Behaviour: "), description: italic("Determine what actions the On\\Off buttons will perform."), options: [ [0:"Normal On\\Off behaviour. Typically used when the Zigbee router is also a switch."],[1:"On\\Off controls will start\\stop the scheduled data collection. Typically used for a dedicated Zigbee router."] ], defaultValue: 0        
        input name: "inactivityLimit", type: "enum", title: bold("Health: ") + dodgerBlue("Inactivity Limit:"), description: italic("The amount of time that must elapse in seconds, with no Zigbee traffic received, in order for the device to be marked offline. When the remaining time drops under 5 minutes a ping command will be issued as a final test."), 
                options: [ [0:" Never"],[15:"15 minutes"],[30:"30 minutes"],[60:" 1 hour"], [120:" 2 hours"], [180:" 3 hours"], [360:" 6 hours"] ], defaultValue: 60, required: true
        input name: "miscblank", type: "hidden", title: " ", description: ""
         
        //Advanced
        input name: "dataSeparator", type: "enum", title: bold("Advanced: ") + dodgerBlue("Record Delimiter:"), description: italic("The character(s) used to separate each record for display and data feed purposes. These separators can be processed by <b>Tile Builder Advanced</b> to provide enhanced formatting. (Default - 0 (Comma,) )"),
                options: [ [0:"Comma ,"],[1:"Semi-Colon ;"],[2:"Colon :"],[3:"Pipe |"]], defaultValue: 0, required: true 
        input name: "deviceExtendedInfo", type: "enum", title: bold("Advanced: ") + dodgerBlue("Include Extended Information:"), description: italic("Normally extended information is turn off to reduce space. Turning it on adds multiple Neighbor and Routing fields to the state display that may be of interest in troubleshooting."), options: [ [0:"False"],[1:"True"] ], defaultValue: 0, required: true
        input name: "deviceShowRoutes", type: "enum", title: bold("Advanced: ") + dodgerBlue("Show Routes:"), description: italic("Choose whether to show only Active Device routes or All routes. Inactive Routes are usually route table entries that are waiting to be reallocated and not of interest."), options: [ [0:"Active Routes Only"],[1:"All Routes, Any State"] ], defaultValue: 0, required: true
        input name: "deviceAppendAddress", type: "enum", title: bold("Advanced: ") + dodgerBlue("Append Network Address."), description: italic("You can choose to append a network address to the device name for troubleshooting purposes."), options: [ [0:"Do not append an address to the device name."],\
                [1:"Append the Hubitat 4 digit DNI."], [2:"Append the last 6 digits of the Zigbee ID as used by the XBEE Network Assistant."] ], defaultValue: 0
        input name: "loglevel", type: "enum", title: bold("Advanced:") + dodgerBlue("Log Level"), description: italic("The log level dictates how much information goes to the Hubitat log. Higher numbers result in more logging. At the default value of Normal, only errors and bulk operations are logged.(Default: 0.)"),
                options: [ [0:" Normal"],[1:" Trace"],[2:" Debug"] ], defaultValue: 0, required: true
    }

def test(){
    if (state.data.driverVersion == null)  state.data.driverVersion = driverVersion
    if (state.data.driverBuild == null)  state.data.driverBuild = driverBuild
}   
                           

//*********************************************************************************************************************************************************************
//******
//****** Start of Basic System Functions
//******
//*********************************************************************************************************************************************************************

//Installed gets run when the device driver is selected and saved
def installed(){
	log ("Installed", "Installed with settings: ${settings}", 0)
}

//Updated gets run when the "Save Preferences" button is clicked
def updated(){
	log ("Update", "Updated with settings: ${settings}", 0)
    updateDisplay()
    
    //Recreate the jobs based on the new values.
    unschedule()
	createJobs()
}

//Uninstalled gets run when called from a parent app???
def uninstalled() {
	log ("Uninstall", "Device uninstalled", 0)
}
                                                      
// Configure the driver data area based on information from the device.
def configure() {
    List<String> cmds = []

    // Configure Zigbee reporting in the event it is a switch
    cmds.addAll zigbee.configureReporting(0x0006, 0x0000, 0x10, 300, 600, 0x00) // Report On/Off status every 5 to 10 minutes

    // Query Zigbee attributes of interest
    cmds.addAll zigbee.readAttribute(0x0000, 0x0000)  // ZCLVersion
    cmds.addAll zigbee.readAttribute(0x0000, 0x0001)  // ApplicationVersion
    cmds.addAll zigbee.readAttribute(0x0000, 0x0002)  // Stack Version
    cmds.addAll zigbee.readAttribute(0x0000, 0x0003)  // HWVersion
    cmds.addAll zigbee.readAttribute(0x0000, 0x0004)  // ManufacturerName
    cmds.addAll zigbee.readAttribute(0x0000, 0x0005)  // ModelIdentifier
        
    // Send the commands in batch to the device
    //cmds.add "he raw ${device.deviceNetworkId} 0x0000 0x0000 0x0005 {00 ${zigbee.swapOctets(device.deviceNetworkId)}} {0x0000}"
    hubitat.device.HubMultiAction hubMultiAction = new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE) 
    sendHubCommand(hubMultiAction)
}
                                                      
//Cleans up the attributes used to display the status of data collection. It is called from Updated() and Initialise()                  
def updateDisplay(){
    log ("updateDisplay", "Setting Data Collection Mode attributes.", 0)
    if (hubDataCollectionMode == null ) sendEvent(name: "hubDataCollectionMode", value: red("Refresh & Save Preferences") ) 
    if (hubDataCollectionMode == "0" ) sendEvent(name: "hubDataCollectionMode", value: red("Off") ) 
    if (hubDataCollectionMode == "1" ) sendEvent(name: "hubDataCollectionMode", value: green("Only Addresses") ) 
    if (hubDataCollectionMode == "2" ) sendEvent(name: "hubDataCollectionMode", value: green("All Zigbee Information") ) 
    
    if (deviceDataCollectionMode == null ) sendEvent(name: "deviceDataCollectionMode", value: red("Refresh & Save Preferences") ) 
    if (deviceDataCollectionMode == "0" ) sendEvent(name: "deviceDataCollectionMode", value: red("Not Configured") ) 
    if (deviceDataCollectionMode == "1" ) sendEvent(name: "deviceDataCollectionMode", value: green("Only Neighbor Data") )
    if (deviceDataCollectionMode == "2" ) sendEvent(name: "deviceDataCollectionMode", value: green("Only Routing Data") ) 
    if (deviceDataCollectionMode == "3" ) sendEvent(name: "deviceDataCollectionMode", value: green("All Neighbor & Routing Data") ) 
    //log.info ("A: $deviceDataCollectionMode B: $hubDataCollectionMode")                           
}
                           
//********************************************************************************************************************************************************************
//******
//****** End of System Required functions
//******
//********************************************************************************************************************************************************************

                           
//*********************************************************************************************************************************************************************
//******
//****** Start Interface Commands
//******
//*********************************************************************************************************************************************************************

//Sends out a Zigbee command to the device. We don't care what the response is as long as something comes back to the parse function.
def ping(){
    log("Ping","Issuing command: DNI-${device.deviceNetworkId} ID-{${device.zigbeeId}}",1)
    //We use this version of the command as it works on both XBee and regular devices.
    cmd = "zdo bind ${device.deviceNetworkId} 0x${device.endpointId} 0x01 0x0000 {${device.zigbeeId}} {}"
    return cmd
}
                                                    
//Turns on the Switch or Starts data collection
def on() {
    //For some reason zigbee.on() does not work consistently.
    if (settings.switchBehaviour == "0" ) return "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 1 {}"
    if (settings.switchBehaviour == "1" ) { createJobs(); sendEvent(name: "switch", value: "on"); log("on","Data collection enabled at configured intervals.",0) }
}

//Turns off the Switch or Stops data collection
def off() {
    //For some reason zigbee.off() does not work consistently.
    if (settings.switchBehaviour == "0" ) return "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0x0006 0 {}"
    if (settings.switchBehaviour == "1" ) { wipe(4); sendEvent(name: "switch", value: "off"); log("off","Data collection disabled if configured.",0) }
}

//Initialize the required data structures.
def initialize(){ 
    log("initialize", "Initializing Device.", 0)
    
    //Configure the default settings
    //We need this because the install routine runs on device installation, but not when a device driver is changed.
    if (hubDataCollectionMode == null) device.updateSetting("hubDataCollectionMode", [value:"1", type:"enum"])
    if (hubPollInterval == null) device.updateSetting("hubPollInterval", [value:"1440", type:"enum"])
    
    if (deviceDataCollectionMode == null) device.updateSetting("deviceDataCollectionMode", [value:"1", type:"enum"])
    if (devicePollInterval == null) device.updateSetting("devicePollInterval", [value:"60", type:"enum"])
    if (neighborSortOrder == null) device.updateSetting("neighborSortOrder", [value:"0", type:"enum"])
    if (routeSortOrder == null) device.updateSetting("routeSortOrder", [value:"0", type:"enum"])
    if (deviceExtendedInfo == null) device.updateSetting("deviceExtendedInfo", [value:"0", type:"enum"])
    if (deviceShowRoutes == null) device.updateSetting("deviceShowRoutes", [value:"0", type:"enum"])
    if (deviceAppendAddress == null) device.updateSetting("deviceAppendAddress", [value:"0", type:"enum"])
    
    if (inactivityLimit == null) device.updateSetting("inactivityLimit", [value:"60", type:"enum"])
    if (dataSeparator == null) device.updateSetting("dataSeparator", [value:"0", type:"enum"])
    if (loglevel == null) device.updateSetting("loglevel", [value:"0", type:"enum"])
    if (switchBehaviour == null) device.updateSetting("switchBehaviour", [value:"0", type:"enum"])
    
    //App data storage
    if (state.data == null) state.clear()
    if (state.data == null) state.data = [:]
    state.Comment = "How to Reset: 1) Wipe, 2) Initialize, 3) Refresh Browser (F5), 4) Modify Preferences and <u>Save</u>, 5) Perform Get Hub Info, 6) Perform Get Device Info. You can repeat this process as many times as you wish."
    if (state.data.isInitialized != true) state.data.isInitialized = true  
    //Initialize driver version information
    if (state.data.driverVersion == null)  state.data.driverVersion = driverVersion
    if (state.data.driverBuild == null)  state.data.driverBuild = driverBuild
    
    //This is a one time flag set the very first time initialize() is run.
    if (state.device == null) state.device = [:]
    
    //This group is for information taken from this device
    if (state.device == null) state.device = [:]
    if (state.device.neighbors == null) state.device.neighbors = [:]
    if (state.device.routes == null) state.device.routes = [:]
    
    //This group is for information taken from this URL:/hub/zigbee/getChildAndRouteInfoJson
    if (state.hubChildandRouteInfo == null) state.hubChildandRouteInfo = [:]
    if (state.hubChildandRouteInfo.routes == null) state.hubChildandRouteInfo.routes = [:]
    if (state.hubChildandRouteInfo.neighbors == null) state.hubChildandRouteInfo.neighbors = [:]
    if (state.hubChildandRouteInfo.children == null ) state.hubChildandRouteInfo.children = [:] 
    if (state.hubChildandRouteInfo.devices == null ) state.hubChildandRouteInfo.devices = [:] 
    
    //This group is for information taken from /hub2/zigbeeInfo or /hub/zigbeeDetails/json
    if (state.hubZigbeeInfo == null) state.hubZigbeeInfo = [:]
    if (state.hubZigbeeInfo.zbDevices == null ) { state.hubZigbeeInfo.zbDevices = [:] }
    if (state.hubZigbeeInfo.zbProperties == null ) { state.hubZigbeeInfo.zbProperties = [:] }
    
     //Schedule data updates and Health Check
    runInMillis(2000, 'createJobs')
    
    //This is the specified attribute for use with the healthCheck attribute. In this release it is not configurable.
    sendEvent(name: "checkInterval", value: 15) 
    
    //Update the Data Collection Attributes with the current settings.
    updateDisplay()
    
    myString = mark("Device Initialized - Refresh Browser!")
    sendEvent(name: "Status", value: myString) 
}

//Remove all State Data, Device Preferences and Scheduled Tasks
def wipe(category){
    
    if (category == null ) log("wipe:", "Performing wipe of all categories.", 0)
    else log("wipe:", "Perform wipe with category $category.", 0)
    
    //Delete All State Data
    if (category == 1 || category == null){
        state.clear()
        log ("Wipe:","State is: $state",0)
    }
    //Delete All Current State data
    if (category == 2 || category == null){
        device.currentStates.each { entry ->        
            name = entry.name
            device.deleteCurrentState("$name")
        }
        log ("Wipe:","Current States are: $device.currentStates", 0)
    }
    // Delete all of the Preferences\Settings.
    if (category == 3 || category == null){
        settings.each { key, value -> device.removeSetting("$key") }
        log ("Wipe:","Preferences\\Settings are: $settings", 0)
    }
    //Delete All scheduled tasks for this device
    if (category == 4 || category == null){
        unschedule("")
        log ("Wipe:","All scheduled jobs have been removed.", 0)
    }
    //Delete the contents of the Driver Data area. Does not affect the endpointId field.
    if (category == 5 || category == null){
        //Make a copy of the data names because we can't iterate the list and modify it at the same time.
        dataCopy = []
        device.data.each { entry -> 
            // Split the string at the '=' sign and extract the name to the left of the '=' sign
            def parts = entry.toString().split('=')
            def name = parts[0]
            dataCopy.add (name)
            }
        //Now go through the list and delete all of the items.
        dataCopy.each { entry -> device.removeDataValue("$entry") }
    }
    
    //Remove all State and Current State but leave settings and scheduled jobs alone.
    if (category == 6 ){
        state.clear()
        device.currentStates.each { entry ->        
            name = entry.name
            device.deleteCurrentState("$name")
        }
        log ("Wipe:","All State and Current State variables have been wiped.", 0)
        //This group is for information taken from this URL:/hub/zigbee/getChildAndRouteInfoJson
        state.hubChildandRouteInfo = [:]; state.hubChildandRouteInfo.routes = [:]; state.hubChildandRouteInfo.neighbors = [:]; state.hubChildandRouteInfo.children = [:] ; state.hubChildandRouteInfo.devices = [:] 
        //This group is for information taken from /hub2/zigbeeInfo or /hub/zigbeeDetails/json
        state.hubZigbeeInfo = [:]; state.hubZigbeeInfo.zbDevices = [:]; state.hubZigbeeInfo.zbProperties = [:]
        //This group is for information taken from the device
        state.device = [:]; state.device.routes = [:]; state.device.neighbors = [:]; state.device.controlN = [:]; state.device.controlR = [:]    
    }
}

                           
//*********************************************************************************************************************************************************************
//******
//****** End Interface Commands
//******
//*********************************************************************************************************************************************************************

                            
//*********************************************************************************************************************************************************************
//******
//****** Start Health  Commands
//******
//*********************************************************************************************************************************************************************

//Performs a periodic check to see if there has been recent activity. When it gets within checkInterval minutes of the inactivityLimit a ping() command is issued to test the operation.
//Note: This command needs a def vs void because the ping() command has a return value and will not work when using the latter.
def healthCheck(){
    int checkIntervalms = device.currentValue('checkInterval') * 60 * 1000
    maxInactivity = inactivityLimit.toInteger() * 60 * 1000
    currentInactivity = now() - state.data.deviceLastZigbeeActivityms
    log("healthCheck","Time since last activity is $currentInactivity ms.", 1)
    
    //If the currentInactivity plus the checkInterval is less than maxInactivity we don't need to do anything except mark it online.
    if ( (currentInactivity + checkIntervalms )  < maxInactivity ) {
        sendEvent(name: "healthStatus", value: "online") 
        log("healthCheck","There is more than $checkIntervalms ms remaining. Do nothing.", 2)
    }
    
    //If the currentInactivity plus the checkInterval is greater than maxInactivity we will initiate a ping test and the parse() command will mark the device online.
    if ( ( currentInactivity + checkIntervalms ) >= maxInactivity ) {
        log("healthCheck","There is less than $checkIntervalms ms remaining. Issuing 'ping' command.", 2)
        ping()
    }
    
    //Test to see if we have exceeded the Inactivity Limit.
    if (currentInactivity >= maxInactivity ) {
        sendEvent(name: "healthStatus", value: "offline") 
        log("healthCheck","This device has had no Zigbee activity for a period that exceeds the inactivity limit of $interval minutes. The Health Status of the device is being marked as offline. It will be changed to online when Zigbee traffic is detected.", 0)
    }
}
                           
//*********************************************************************************************************************************************************************
//******
//****** End Health  Commands
//******
//*********************************************************************************************************************************************************************


                           
//*********************************************************************************************************************************************************************
//******
//****** Start Scheduling Commands
//******
//*********************************************************************************************************************************************************************
                        
//Creates CRON jobs to perform data collection and Health Checks.
void createJobs(){
    //Schedule data updates
    log("createJobs","Creating scheduled jobs",1)
    unschedule()
    //Data Collection 
    if ( hubDataCollectionMode != null && hubDataCollectionMode != "0" && ( hubPollInterval != null && hubPollInterval.toInteger() > 0) ) scheduleJob(hubPollInterval, "getHubInfo")
    if ( deviceDataCollectionMode != null && deviceDataCollectionMode != "0" && ( devicePollInterval != null && devicePollInterval.toInteger() > 0) ) scheduleJob(devicePollInterval, "getDeviceInfo")
    
    //Health Check
    interval = device.currentValue('checkInterval')
    scheduleJob(interval.toInteger(), healthCheck)                             
}
                           
//Schedule a job for some time in the future //Use https://cronmaker.com for format.
void scheduleJob(interval, command){
    int minutes = interval.toInteger()
    switch(minutes){
        case [0]:
            return
            break
        case [1,2,5,10,15,30]:
            //Example: Every 15 Minute: 0 0/15 * 1/1 * ? *
            cronJob = "0 0/" + minutes.toString() + " * 1/1 * ? *"
            break
        case [60, 120, 180, 360, 720]:
            //Example: Every 2 Hours: 0 0 0/2 1/1 * ? *
            hours = minutes/60
            cronJob = "0 0 0/" + hours.toString() + " 1/1 * ? *"
            break
        case [1440]:
            case 1440:
            //Example: Every Day start at 03:00; 0 0 3 1/1 * ? *
            cronJob = "0 0 3 1/1 * ? *"
            }
    log ("scheduleJob:","Created cronJob ${cronJob} for ${command}", 1)
    schedule(cronJob, command)
}
                           
//*********************************************************************************************************************************************************************
//******
//****** End Scheduling Commands
//******
//*********************************************************************************************************************************************************************

                           
//*********************************************************************************************************************************************************************
//******
//****** Start Hub Related Commands
//******
//*********************************************************************************************************************************************************************

//I am using a synchronous call because we are hitting the hub in two places and I want them to happen sequentially to not overload the hub.
//Also, the call uses the hub loopback address so the network is not involved therefore asynchronous has little advantage.
def getHubInfo() {       
    log ("getHubInfo", "Information request initiated.", 1)
    def myJson = new JsonSlurper()
    
    //Start Get data from /hub2/zigbee or /hub/zigbeeDetails/json and put it in hubData1. This contains the long and short Zigbee ID's along with the device name and is used for name resolution.
    if (hubDataCollectionMode == "1" || hubDataCollectionMode == "2"){
        try{
            def loopback = "http://127.0.0.1:8080"
            def URI = ""
            
            if ( compareVersions(location.hub.firmwareVersionString, "2.3.7.1") >= 0 ) URI = loopback + "/hub/zigbeeDetails/json"
            else URI = loopback + "/hub2/zigbeeInfo"
            log.info ("getHubInfo", "Using URI: $URI",1)

            def requestParams = [ uri:URI, contentType: "application/json" ]
            httpGet(requestParams)
                { response ->   
                    if (response?.status == 200){        //200 is an OK.
                        log ("getHubInfo", "Information received from ${URI}: ${response?.data}", 2)
                        //Now pass this json data to get saved to state.
                        hubInfoZigbeeResponse1(response?.data)
                    }
                    else { log ("getHubInfo", "Error retrieving data from ${URI}: ${response?.status}.", 0) } 
                }    
        }    
        catch(Exception e) { log("getHubInfo","Error $e encountered retrieving data from: ${URI}", 0) }
    }
    //End Get data from /hub2/zigbee or /hub/zigbeeDetails/json
    
    //Start Get data from hub/zigbee/getChildAndRouteInfoJson and put it in hubData2. This contains the Hubs Zigbee Child info and Route table. Only has the short Zigbee id.
    if (hubDataCollectionMode == "2"){
        try {
            def URI = "http://127.0.0.1:8080/hub/zigbee/getChildAndRouteInfoJson"
            def requestParams = [ uri:URI, contentType: "application/json" ]
            httpGet(requestParams) 
                { response ->   
                    if (response?.status == 200){        //200 is an OK.    
                        log ("getHubInfo", "Information received from hub/zigbee/getChildAndRouteInfoJson: ${response?.data}", 2)
                        //Now pass this json data to get saved to state.
                        hubInfoZigbeeResponse2(response?.data)
                    }
                    else { log ("getHubInfo", "Error retrieving data from /hub/zigbee/getChildAndRouteInfoJson: ${response?.status}.", 0) }
               }
        }
       catch(Exception e) { log("getHubInfo","Error $e encountered retrieving data from: http://127.0.0.1:8080/hub/zigbee/getChildAndRouteInfoJson", 0) }
   }
    //End Get data from hub/zigbee/getChildAndRouteInfoJson
    
}
   
//Receives the json data requested from /hub2/zigbeeInfo or /hub/zigbeeDetails/json. This contains the full Zigbee ID's and is used for name resolution.
void hubInfoZigbeeResponse1(myData) {
    tmpDevices = myData.zbDevices
    //Remove some unwanted fields
	tmpDevices.each{ entry ->
		entry.remove('type')
		entry.remove('skipPing')
        if (hubDataCollectionMode == "1" ){ entry.remove('lastMessage'); entry.remove('messageCount') }
	}
	//Now save the shortened list to State.
    if (hubDataCollectionMode == "1" || hubDataCollectionMode == "2" ) state.hubZigbeeInfo.zbDevices = tmpDevices
    if ( hubDataCollectionMode == "2" ) {
        if ( state.hubZigbeeInfo.zbProperties.pan != myData.pan ) state.hubZigbeeInfo.zbProperties.pan = myData.pan
	    if ( state.hubZigbeeInfo.zbProperties.epan != myData.epan ) state.hubZigbeeInfo.zbProperties.epan = myData.epan
	    if ( state.hubZigbeeInfo.zbProperties.networkState != myData.networkState ) state.hubZigbeeInfo.zbProperties.networkState = myData.networkState
	    if ( state.hubZigbeeInfo.zbProperties.channel != myData.channel ) state.hubZigbeeInfo.zbProperties.channel = myData.channel
	    if ( state.hubZigbeeInfo.zbProperties.powerLevel != myData.powerLevel ) state.hubZigbeeInfo.zbProperties.powerLevel = myData.powerLevel
	    if ( state.hubZigbeeInfo.zbProperties.inJoinMode != myData.inJoinMode ) state.hubZigbeeInfo.zbProperties.inJoinMode = myData.inJoinMode
    }
	updateHubAttributes(1)
}
                           
//Receives the json data requested from /hub/zigbee/getChildAndRouteInfoJson
void hubInfoZigbeeResponse2(myData) {
    if (hubDataCollectionMode != "2" ) state.hubChildandRouteInfo = [:]
    if (hubDataCollectionMode == "2" ) state.hubChildandRouteInfo.devices = myData.devices
    if (hubDataCollectionMode == "2" ) state.hubChildandRouteInfo.children = myData.children
    if (hubDataCollectionMode == "2" ) state.hubChildandRouteInfo.neighbors = myData.neighbors
    if (hubDataCollectionMode == "2" ) state.hubChildandRouteInfo.routes = myData.routes
    updateHubAttributes(2)
}


//Updates the Device Attributes for Hub info
void updateHubAttributes(int screen){
    def myText = ""
    def myMap
    def date = new Date()
    def formattedDate = date.format("E @ HH:mm:ss")
    def separator = dataSeparatorMap[settings.dataSeparator.toInteger()]
    sendEvent(name: "hubLastUpdate", value:formattedDate)
    
    //Process info supplied by hubInfoZigbeeResponse1
    if (screen == 2){
        //************** Start of Hub Neighbors **********************
        if (neighborSortOrder == "0"){ myMap = state.hubChildandRouteInfo.neighbors.sort { a, b -> b.lqi <=> a.lqi } }
        if (neighborSortOrder == "1"){ myMap = state.hubChildandRouteInfo.neighbors.sort { a, b -> a.lqi <=> b.lqi } }
        if (neighborSortOrder == "2"){ myMap = state.hubChildandRouteInfo.neighbors.sort { a, b -> a.id <=> b.id } }
        
        processedNeighbors = [:] // Initialize an empty map to track processed neighbors
        // Loop through the myMap which has been sorted and generate text output for the attribute.
        log("updateHubAttributes:neighbors", "myMap is: $myMap", 2)

        myMap.each { map ->
            // Construct a unique key for each neighbor based on the sorting criteria
            def neighborKey
            if (neighborSortOrder != "2") { neighborKey = "${map.lqi}-${map.id}" }
            if (neighborSortOrder == "2") { neighborKey = "${map.id}-${map.lqi}" }

            // Check if the neighbor with the same key has already been processed
            if (!processedNeighbors.containsKey(neighborKey)) {
                // Add the neighbor to the processedNeighbors map to mark it as processed
                processedNeighbors[neighborKey] = true

                // Show LQI first and then device name
                if (neighborSortOrder != "2") { myText = myText + "${map.lqi}" + " - " + getDeviceName("${map.id}") + separator }
                // Show device name first and then LQI
                if (neighborSortOrder == "2") { myText = myText + getDeviceName("${map.id}") + " - " + "${map.lqi}" + separator }
            }
        }
        
        // Remove the trailing separator
        if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
        log("updateHubAttributes:neighbors", "deviceNeighbors is: $myText", 1)
        
        if (myText != "") sendEvent(name: "hubNeighbors", value:myText)
        else sendEvent(name: "hubNeighbors", value:"None")
        sendEvent(name: "hubNeighborCount", value:processedNeighbors.size())
        //************** End of Hub Neighbors **********************

        //This is the only attribute that gets update when we are only in address collection mode.
        if (state.hubChildandRouteInfo.devices != null && state.hubChildandRouteInfo.devices.size() != null && ( hubDataCollectionMode == "1" || hubDataCollectionMode == "2" ) ) sendEvent(name: "hubDeviceCount", value:state.hubChildandRouteInfo.devices.size() )
        
		//Update the Current State. We only do this if the Data Collection Mode is All.
        if ( hubDataCollectionMode == "2") {
            if (state.hubChildandRouteInfo.children.size() != null ) sendEvent(name: "hubChildCount", value:state.hubChildandRouteInfo.children.size())
		    if (state.hubChildandRouteInfo.neighbors.size() != null ) sendEvent(name: "hubNeighborCount", value:state.hubChildandRouteInfo.neighbors.size())
		    if (state.hubChildandRouteInfo.devices.size() != null ) sendEvent(name: "hubDeviceCount", value:state.hubChildandRouteInfo.devices.size())
		    if (state.hubChildandRouteInfo.routes.size() != null ) sendEvent(name: "hubRouteCountTotal", value:state.hubChildandRouteInfo.routes.size())
    
            //************** Start of Hub Children **********************
            myText = ""
            state.hubChildandRouteInfo.children.each{ child ->
                netAddr = getDeviceName(child.id)
                lqi = child.lqi ?: "?"
                //Show LQI if known and the device name
                myText = myText + "(${lqi})" + " - " + "${netAddr}" + separator
            }
            
            if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
            log("updateHubAttributes:children", "hubChildren is: $myText", 1)
            if (myText != "") sendEvent(name: "hubChildren", value:myText)
            else sendEvent(name: "hubChildren", value:"None")
            //************** End of Hub Children **********************
            
            //************** Start of Hub Routes **********************
            //Get the number of Active Routes
		    def activeRouteCount = 0
		    def unusedRouteCount = 0

		    // Iterate through the Route map and count Active entries
            myText = ""
		    state.hubChildandRouteInfo.routes.each { entry ->
                if (entry.status == 'Unused') { unusedRouteCount++ }
		  	    if (entry.status == 'Active') { activeRouteCount++ ; myText += "[" + getDeviceName (entry.id)  + ", " + entry.id + "] via [" + getDeviceName(entry.nextHopId) + ", " + entry.nextHopId + "]" + separator }
		    }
            // Remove the trailing separator
            if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
            log("updateHubAttributes:Routes", "hubRoutesActive is: $myText", 1)
            if (myText != "") sendEvent(name: "hubRoutesActive", value:myText)
		    sendEvent(name: "hubRouteCountActive", value:activeRouteCount)
		    sendEvent(name: "hubRouteCountUnused", value:unusedRouteCount)
            //************** Start of Hub Routes **********************
		
            //************** Start of Hub Lowest LQI **********************
		    // Iterate through the Neighbor map and get lowest LQI
		    def lowestLqiValue = 256
		    def lowestLqiName = ""
		    // Iterate through the map and count Active entries and get lowest LQI
		    state.hubChildandRouteInfo.neighbors.each { entry ->
			    def lqi = entry.lqi as int 
			    if (lqi < lowestLqiValue) {
				    lowestLqiValue = lqi
                    log("updateHubAttributes", "entry.id is: $entry.id", 2)
				    lowestLqiName = getDeviceName(entry.id)
				    }
		    }
            sendEvent(name: "hubLowestLqiValue", value:lowestLqiValue)
		    sendEvent(name: "hubLowestLqiName", value:lowestLqiName)
            //************** End of Hub Lowest LQI **********************
        }
    }
    
    //Process info supplied by hubInfoZigbeeResponse2
    if (screen == 2 && hubDataCollectionMode == "2"){
	    sendEvent(name: "zigbeePanID", value:state.hubZigbeeInfo.zbProperties.pan)
		sendEvent(name: "zigbeeExtPanID", value:state.hubZigbeeInfo.zbProperties.epan)
		sendEvent(name: "zigbeeNetworkState", value:state.hubZigbeeInfo.zbProperties.networkState)
		sendEvent(name: "zigbeeChannel", value:state.hubZigbeeInfo.zbProperties.channel)
		sendEvent(name: "zigbeePowerLevel", value:state.hubZigbeeInfo.zbProperties.powerLevel)
		sendEvent(name: "zigbeeJoinMode", value:state.hubZigbeeInfo.zbProperties.inJoinMode)
    }    
    myString = mark("Hub Info Updated - Refresh Browser!")
    sendEvent(name: "Status", value: myString) 
}

//Extract messageCount, lastMessage\ElapsedTime from state.hubZigbeeInfo.zbDevices.
def hubExtractData(){   
    // Initialize an empty list to store the extracted data
    def extractedData = []
    // Create a SimpleDateFormat object and set the input pattern
    def inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")
    
    // Iterate through the zbDevices list and extract the desired fields
    state.hubZigbeeInfo.zbDevices.each { device ->
        Date date = inputFormat.parse(device.lastMessage)
        // Convert the Date to a long timestamp in milliseconds and calculate how long ago it was.
        long timestamp = date.time
        elapsedTime = now() - timestamp
    
        def extractedDeviceInfo = [
            'name': device.name,
            'lastMessage': device.lastMessage,
            'messageCount': device.messageCount,
            'elapsedTime': elapsedTime
        ]
        extractedData.add(extractedDeviceInfo)
    }
    //DO NOT DELETE THESE LINES - THEY WILL BE USED LATER FOR DIRECT APP QUERIES//
    log("hubExtractData", "Extracted data is: $extractedData", 2)
    //def sortedData = extractedData.sort { a, b -> b.messageCount <=> a.messageCount } //Sorted by messageCount High to low.
    //def sortedData = extractedData.sort { a, b -> a.messageCount <=> b.messageCount } //Sorted by messageCount low to high.
    //def sortedData = extractedData.sort { a, b -> a.name <=> b.name } //Sorted by name.
    //def sortedData = extractedData.sort { a, b -> a.lastMessage <=> b.lastMessage } //Sorted by lastMessage, oldest first.
    //def sortedData = extractedData.sort { a, b -> b.lastMessage <=> a.lastMessage } //Sorted by lastMessage, newest first.
    //def sortedData = extractedData.sort { a, b -> b.elapsedTime <=> a.elapsedTime } //Sorted by elaspsedTime since lastMessage, longest first.
    def sortedData = extractedData.sort { a, b -> a.elapsedTime <=> b.elapsedTime } //Sorted by elaspsedTime since lastMessage, longest first.
    log("hubExtractData", "sortedData is: $sortedData", 2)
}
                           
//*********************************************************************************************************************************************************************
//******
//****** End Hub Related Commands
//******
//*********************************************************************************************************************************************************************


//*********************************************************************************************************************************************************************
//******
//****** Start Data Lookup Related Commands
//******
//*********************************************************************************************************************************************************************

//Top level command used to call the Neighbors and Routing commands.
void getDeviceInfo(){    
    log("getDeviceInfo", "Starting refresh of device neighbors and routes.", 1)
    if (deviceDataCollectionMode == "1" || deviceDataCollectionMode == "3") getNeighbors()
    if (deviceDataCollectionMode == "2" || deviceDataCollectionMode == "3") getRoutes()
}
                           
//Retrieve the Device Name if found.                         
def getDeviceName(String address){
    log("getDeviceName", "Received Address: $address", 2)
    
    //Set the default return name as the provided address which will be used if we don't find a match
    String myDeviceName = address
    String myAddr6 = ""
    
    //Test to see if it is the Coordinator Address (Hub)
    if (address == "0000") return "HUB"
    
    //Iterate the list of Zigbee devices and try to find a match
    state.hubZigbeeInfo.zbDevices.each { device ->
        if (device.shortZigbeeId == address) {
            myAddr6 = device.zigbeeId.substring(device.zigbeeId.length() - 6)
            myDeviceName = device.name
            }
    }
    if (deviceAppendAddress == "0") return myDeviceName        
    if (deviceAppendAddress == "1") return myDeviceName + " ❨" + address + "❩"
    if (deviceAppendAddress == "2") return myDeviceName + " ❨" + myAddr6 + "❩"  
}

//*********************************************************************************************************************************************************************
//******
//****** Start Device Related Commands - Neighbors
//******
//*********************************************************************************************************************************************************************
//Initiates a refresh of the Zigbee neighbor LQI data.
def getNeighbors(){
    log("getNeighbors", "Refreshing Device Neighbors", 1)
    //Clear old Neighbor Data
    state.device.neighbors = [:]
    state.device.controlN = [:]
    state.device.controlN.startIndex = 0
    getNeighborsData("00")
}

//This function places calls to the Zigbee network at a given startIndex.
def getNeighborsData(startIndex){   
    cmd = "he raw $device.deviceNetworkId 0x${device.endpointId} 0x00 0x0031 { 01 $startIndex$device.deviceNetworkId } { 0000 }"
    hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
    sendHubCommand(hubAction)
}

//This function parses the response to the Device Neighbors Query and also includes LQI information.
def parseDeviceNeighborsQuery(hexString){
    log("parseDeviceNeighborsQuery", "Response received", 1)
    def parsed = [:]
    int i = 0
    int offset = 10
        
    myString = mark("Device Neighbors Updated - Refresh Browser!")
    sendEvent(name: "Status", value: myString) 
    
    //Check the hexStatus and get result from table.
    hexStatus = hexString.substring(2, 4)
    def errorText = ZIGBEE_ERROR_MAP[hexStatus]
    if ( "0x${hexStatus}" == "0x84" ) {
        sendEvent(name: "deviceNeighbors", value: markred("Error: $errorText") )
        log ("parseDeviceRoutingQuery","This device does not support Neighbor requests: $errorText", 1)
        return
    }
    //If we get anything other than 00 it is an error.
    if ( "0x${hexStatus}" != "0x00" ) {
        log("parseDeviceRoutingQuery","An error was encountered retrieving the Neighbor table. $errorText", 0)
        return
    }
  
    parsed.status = Integer.parseInt(hexString.substring(2, 4), 16)
    parsed.totalEntries = Integer.parseInt(hexString.substring(4, 6), 16)
    parsed.startIndex = Integer.parseInt(hexString.substring(6, 8), 16)
    parsed.listCount = Integer.parseInt(hexString.substring(8, 10), 16)
    state.device.controlN.totalEntries = parsed.totalEntries
    
    for (i = 0; i < parsed.listCount; i++) {
        def entry = [:]
        myPanId = reverseByteOrder(hexString.substring(offset, offset+16)) ;offset += 16
        myExtAddr= reverseByteOrder(hexString.substring(offset, offset+16)) ;offset += 16
        entry.netAddr = reverseByteOrder(hexString.substring(offset, offset+4)) ;offset += 4
        //myInfo is an Octet with multiple bit fields
        myInfo = hexString.substring(offset, offset+2) ; offset += 2    
        myResult = decodeNeighborInfo( myInfo )
        entry.relationship = myResult.relationship
        
        //Only include this extended information if explicity selected
        if (deviceExtendedInfo == "1"){
            entry.extPanId = myPanId
            entry.extAddr = myExtAddr
            entry.deviceType = myResult.deviceType
            entry.rxOnWhenIdle = myResult.rxOnWhenIdle
        }
        
        //We ignore 'Permit Joining and Reserved' and they are not useful.
        offset += 2
        //We ignore tree depth as it has no meaning in  a mesh.
        offset += 2
        
        entry.lqi = Integer.parseInt(hexString.substring(offset, offset+2),16) ; offset += 2
        
        //If Device Names are configured and enabled then we will use those.
        deviceName = getDeviceName(entry.netAddr)
        entry.netAddr = deviceName
        log("parseDeviceNeighborsQuery", "entry is: $entry", 2)
        state.device.neighbors."${state.device.controlN.startIndex}" = entry
        state.device.controlN.startIndex = state.device.controlN.startIndex + 1
    }
    log("parseDeviceNeighborsQuery", "startIndex: $state.device.controlN.startIndex - Entries: $parsed.totalEntries - ListCount: $parsed.listCount", 2)
    //If the ListCount is greater than 0 then there is more information to get.
    if (parsed.listCount.toInteger() != 0 ){
        //def hex_string = Integer.parseInt(state.device.controlN.startIndex, 16).padLeft(2, '0')
        def hex_Index = String.format("%02X", state.device.controlN.startIndex)
        log("parseDeviceNeighborsQuery","Requesting data at startIndex $hex_Index",1)
        getNeighborsData(hex_Index)
    }
    else {
        //Now refresh the attributes
        log("parseDeviceNeighborsQuery","Completed receiving data.",1)
        updateDeviceAttributes("neighbors")
    }
}

//Decodes the interesting bit fields for a Neighbor request
def decodeNeighborInfo(hexValue) {
    log("decodeNeighborInfo", "Decoding Neighbor Extended Info: $hexValue", 2)
    def data = Integer.parseInt(hexValue, 16)
    
    //Pad the binaryValue to make sure it has at least eight places in its string form.
    def binaryValue = "00000000" + Integer.toBinaryString(Integer.parseInt(hexValue, 16)) 
    def result = [:]
    deviceType = binaryValue[-2..-1]
    
    // Decode Device Type (bits 1 & 2)
    switch (deviceType) {
        case "00": result.deviceType = "Coordinator"; result.deviceTypeIcon = "🕸️"; break
        case "01": result.deviceType = "Router"; result.deviceTypeIcon = "🔀"; break
        case "10": result.deviceType = "End Device"; result.deviceTypeIcon = "📍"; break
        case "11": result.deviceType = "Unknown"; result.deviceTypeIcon = "❓"; break
    }
    // Decode RxOnWhenIdle (bits 3 and 4)
    def rxOnWhenIdle = binaryValue[-4..-3]
    switch (rxOnWhenIdle) {
        case "00": result.rxOnWhenIdle = "Off"; result.rxOnWhenIdleIcon = "🎧"; break
        case "01": result.rxOnWhenIdle = "On"; result.rxOnWhenIdleIcon = "👂🏼"; break
        case "10": result.rxOnWhenIdle = "Unknown"; result.rxOnWhenIdleIcon = "❓"; break
    }
    // Decode Relationship (bits 5 - 7)
    def relationship = binaryValue[-7..-5]
    switch (relationship) {
        case "000": result.relationship = "Parent"; result.relationshipIcon = "🚹"; break
        case "001": result.relationship = "Child"; result.relationshipIcon = "🚼"; break
        case "010": result.relationship = "Sibling"; result.relationshipIcon = "👯"; break
        case "011": result.relationship = "None of the above"; result.relationshipIcon = "❓"; break
        case "100": result.relationship = "Previous child"; result.relationshipIcon = "❓"; break
    }
    if (result.deviceType == "End Device" && result.rxOnWhenIdle == "Off" ) { result.deviceType = "Sleepy End Device" ; result.deviceTypeIcon = "💤" }
    return result
}

//*********************************************************************************************************************************************************************
//******
//****** End Device Related Commands - Neighbors
//******
//*********************************************************************************************************************************************************************


//*********************************************************************************************************************************************************************
//******
//****** Start Device Related Commands - Routing
//******
//*********************************************************************************************************************************************************************
//Initiates a refresh of the Zigbee Routing data.
def getRoutes(){
    log("getRoutes", "Refreshing Device Routes", 1)
    //Clear the old routing info
    state.device.routes = [:]
    state.device.controlR = [:]
    state.device.controlR.startIndex = 0
    getDeviceRoutingData("00")
}

//This function places calls to the device for routing information at a given startIndex.
def getDeviceRoutingData(startIndex){
    cmd = "he raw $device.deviceNetworkId 0x${device.endpointId} 0x00 0x0032 { 01 $startIndex$device.deviceNetworkId } { 0000 }"
    hubitat.device.HubAction hubAction = new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZIGBEE) 
    sendHubCommand(hubAction)
}

//This function processes the Zigbee response and parses out the data.
def parseDeviceRoutingQuery(hexString){ 
    log("parseDeviceRoutingQuery", "Response received", 1)
    def parsed = [:]
    def routingTable = []
    int i = 0
    int offset = 10
    
    myString = mark("Device Routing Updated - Refresh Browser!")
    sendEvent(name: "Status", value: myString) 
    
    //Check the hexStatus and get result from table.
    hexStatus = hexString.substring(2, 4)
    def errorText = ZIGBEE_ERROR_MAP[hexStatus]
    if ( "0x${hexStatus}" == "0x84" ) {
        sendEvent(name: "deviceRoutes", value: markred("Error: $errorText") )
        log ("parseDeviceRoutingQuery","This device does not support Routing requests: $errorText", 1)
        return
    }
    //If we get anything other than 00 it is an error.
    if ( "0x${hexStatus}" != "0x00" ) {
        log("parseDeviceRoutingQuery","An error was encountered retrieving the routing table. $errorText", 0)
        return
    }
    //If we get this far we should have properly formed data.    
    parsed.status = Integer.parseInt(hexString.substring(2, 4), 16)
    parsed.startIndex = Integer.parseInt(hexString.substring(4, 6), 16)
    parsed.totalEntries = Integer.parseInt(hexString.substring(6, 8), 16)
    state.device.controlR.totalEntries = parsed.totalEntries
    parsed.listCount = Integer.parseInt(hexString.substring(8, 10), 16)
    //parsed.unknown = Integer.parseInt(hexString.substring(8, 10), 16)
    log("parseDeviceRoutingQuery", "startIndex: $parsed.startIndex - Status: $parsed.status - Entries: $parsed.totalEntries - Index: $parsed.startIndex - ListCount: $parsed.listCount", 2)
        
    for (i = 0; i < parsed.listCount; i++) {
        def entry = [:]
        entry.destAddr = reverseByteOrder(hexString.substring(offset, offset+4)) ; offset += 4
        myExtendedInfo = hexString.substring(offset, offset+2) ; offset += 2    
        result = decodeDeviceRoutingInfo( myExtendedInfo )
        entry.routeStatus = result.routeStatus
        
        //Only include this extended information if explicity selected
        if (deviceExtendedInfo == "1"){
            entry.memoryConstrained = result.memoryConstrained
            entry.manyToOne = result.manyToOne
            entry.routeRecordRequired = result.routeRecordRequired
        }
        
        entry.nextHop = reverseByteOrder(hexString.substring(offset, offset+4)) ; offset += 4
        //If Device Names are configured and enabled then we will use those.
        entry.destAddr = getDeviceName(entry.destAddr)
        entry.nextHop = getDeviceName(entry.nextHop)
        log("parseDeviceRoutingQuery", "entry is: $entry  with startIndex: $state.device.controlR.startIndex", 2)
        state.device.routes."${state.device.controlR.startIndex}" = entry
        state.device.controlR.startIndex = state.device.controlR.startIndex + 1
    }
    log("parseDeviceRoutingQuery", "startIndex: $state.device.controlR.startIndex - Entries: $parsed.totalEntries - ListCount: $parsed.listCount", 2)
    //If the ListCount is greater than 0 then there is more information to get.
    if (parsed.listCount.toInteger() != 0 ){
        def hex_Index = String.format("%02X", state.device.controlR.startIndex)
        log("parseDeviceRoutingQuery","Requesting data at startIndex $hex_Index",1)
        getDeviceRoutingData(hex_Index)
    }
    else {
        //Now refresh the attributes
        log("parseDeviceRoutingQuery","Completed receiving data.",1)
        updateDeviceAttributes("routing")
    }
}

//Decodes the interesting bit fields for a Neighbor request
def decodeDeviceRoutingInfo(hexValue) {
    def binaryValue = Integer.toBinaryString(Integer.parseInt(hexValue, 16)) 
    //Pad the binaryValue to make sure it has at least eight places in its string form.
    if (binaryValue.length() < 8) { binaryValue = '0' * (8 - binaryValue.length()) + binaryValue }
    
    def result = [:]
    routeStatus = binaryValue[-3..-1]
    
    // Route Status (bits 1 - 3)
    switch (routeStatus) {
        case "000":result.routeStatus = "Active"; break //⚡    
        case "001": result.routeStatus = "Discovery Underway"; break //🔍
        case "010": result.routeStatus = "Discovery Failed"; break //🚫
        case "011": result.routeStatus = "Inactive"; break //💤
        case "100": result.routeStatus = "Validation Underway"; break  //📝
        case "101" | "110" | "111": result.routeStatus = "Reserved"; break  //❓
    }
    // Decode memoryConstrained (bit 4)
    def memoryConstrained = binaryValue[-4]
    switch (memoryConstrained) {
        case "0": result.memoryConstrained = "False"; break
        case "1": result.memoryConstrained = "True"; break
    } 
    // Decode manyToOne (bit 5)
    def manyToOne = binaryValue[-5]
    switch (manyToOne) {
        case "0": result.manyToOne = "False"; break
        case "1": result.manyToOne = "True"; break
    }
    // Decode Route Record Required (bit 6)
    def routeRecordRequired = binaryValue[-6]
    switch (routeRecordRequired) {
        case "0": result.routeRecordRequired = "False"; break
        case "1": result.routeRecordRequired = "True"; break
    }
    return result
}

//*********************************************************************************************************************************************************************
//******
//****** End Device Related Commands - Routing
//******
//*********************************************************************************************************************************************************************

                           
//*********************************************************************************************************************************************************************
//******
//****** Start Parse
//******
//*********************************************************************************************************************************************************************
// Parsing incoming messages
def parse(String description) {
    log("parse", "Message received.", 1)
    
    //Update state to indicate the last time a Zigbee message was received. This is used in conjunction with checking online status.
    def date = new Date()
    state.data = [deviceLastZigbeeActivityms: now(), deviceLastZigbeeActivity: date.format("E @ HH:mm:ss")]
    //If the Repeater was marked as offline then change it to online.
    if ( device.currentValue('healthStatus') == "offline" ) sendEvent(name: "healthStatus", value: "online") 

    //Get the basic components of the Zigbee data.
    def map = zigbee.parseDescriptionAsMap(description)
	log("parse", "Zigbee Data is: ${map}", 2)
    def cluster = map.cluster
	def hexValue = map.value
	def attrId = map.attrId
    
    if (map?.clusterInt == 0x0000) {  // Basic Cluster Response
        switch (map.attrInt) {
                case 0x0000: 
                    updateDataValue ("ZCLVersion", map?.value)
                    break
                case 0x0001: 
                    updateDataValue ("application", map?.value)
                    break
                case 0x0002: 
                    updateDataValue ("stackVersion", map?.value)
                    break
                case 0x0003: 
                    updateDataValue ("hardwareVersion", map?.value)
                    break
                case 0x0004: 
                    updateDataValue ("manufacturer", map?.value)
                    break
                case 0x0005:
                    updateDataValue ("model", map?.value)
                    break
            }
        log("parse", "Received Response to Basic query", 1)
	}
        
    if (map?.clusterInt == 0x8031) {  // ZDO Management LQI Response
        def hexString = map.data.join("")
        log("parse", "Received Response to Neighbor query", 1)
        log("parse", "Map Data is: ${map.data}", 2)
        parseDeviceNeighborsQuery(hexString)
    }  
    
    if (map?.clusterInt == 0x8032) {  // ZDO Management Routing Response
        def hexString = map.data.join("")
        log("parse", "Received Response to Routing query", 1)
        log("parse", "Map Data is: ${map.data}", 2)
        parseDeviceRoutingQuery(hexString)
    }  
    
    if (map?.clusterInt == 0x0006) {  // Switch Response
         if (map.attrInt == 0) {        //0 Indicates the status as normal
            log("parse", "Received Response to Switch Action", 1)
            if (map.data != null) log("parse", "Map Data is: ${map.data}", 2)
            rawValue = Integer.parseInt(map.value, 16)
            String switchValue
            switchValue = (rawValue == 0) ? "off" : "on"
            sendEvent(name: "switch", value: switchValue, descriptionText: "${device.displayName} switch is ${switchValue}")
         }
    }    
}

//*********************************************************************************************************************************************************************
//******
//****** End Parse
//******
//*********************************************************************************************************************************************************************


//Takes the information saved to state and makes it available in Attributes according to the rules.
void updateDeviceAttributes(section){
    def myText = ""
    def myMap
    def separator = dataSeparatorMap[settings.dataSeparator.toInteger()]
    def date = new Date()
    def formattedDate = date.format("E @ HH:mm:ss")
    //Update the attribute to reflect a successful update.
    sendEvent(name: "deviceLastUpdate", value:formattedDate)
    
    if (section == "neighbors"){   
        
        //************** Start of Neighbors **********************
        if (neighborSortOrder == "0"){ myMap = state.device.neighbors.entrySet().sort { a, b -> b.value.lqi <=> a.value.lqi } }
        if (neighborSortOrder == "1"){ myMap = state.device.neighbors.entrySet().sort { a, b -> a.value.lqi <=> b.value.lqi } }
        if (neighborSortOrder == "2"){ myMap = state.device.neighbors.entrySet().sort { a, b -> a.value.netAddr <=> b.value.netAddr } }
        
        processedNeighbors = [:] // Initialize an empty map to track processed neighbors
        // Loop through the myMap which has been sorted and generate text output for the attribute.
        log("updateDeviceAttributes:neighbors", "myMap is: $myMap", 2)

        myMap.each { map ->
            // Construct a unique key for each neighbor based on the sorting criteria
            def neighborKey
            if (neighborSortOrder != "2") { neighborKey = "${map.value.lqi}-${map.value.netAddr}" }
            if (neighborSortOrder == "2") { neighborKey = "${map.value.netAddr}-${map.value.lqi}" }

            // Check if the neighbor with the same key has already been processed
            if (!processedNeighbors.containsKey(neighborKey)) {
                // Add the neighbor to the processedNeighbors map to mark it as processed
                processedNeighbors[neighborKey] = true

                // Show LQI first and then device name
                if (neighborSortOrder != "2") { myText = myText + "${map.value.lqi}" + " - " + "${map.value.netAddr}" + separator }
                // Show device name first and then LQI
                if (neighborSortOrder == "2") { myText = myText + "${map.value.netAddr}" + " - " + "${map.value.lqi}" + separator }
            }
        }
        
        // Remove the trailing separator
        if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
        log("updateDeviceAttributes:neighbors", "deviceNeighbors is: $myText", 1)
        
        if (myText != "") sendEvent(name: "deviceNeighbors", value:myText)
        else sendEvent(name: "deviceNeighbors", value:"None")
        sendEvent(name: "deviceNeighborCount", value:processedNeighbors.size())
        //************** End of Neighbors **********************
    
        
        //************** Start of Children **********************
        // Filter the neighbors based on the 'relationship' being 'Child'
        //Loop through the myMap which has been sorted and generate text output for the attribute.
        def children = state.device.neighbors.findAll { entry -> 
            entry.value instanceof Map && entry.value.relationship == 'Child'
        }
        
        //Sort the results.
        if (neighborSortOrder == "0"){ myMap = children.entrySet().sort { a, b -> b.value.lqi <=> a.value.lqi } }
        if (neighborSortOrder == "1"){ myMap = children.entrySet().sort { a, b -> a.value.lqi <=> b.value.lqi } }
        if (neighborSortOrder == "2"){ myMap = children.entrySet().sort { a, b -> a.value.netAddr <=> b.value.netAddr } }
        
        myText = ""
        processedChildren = [] // Initialize an empty list to track processed children
        //Eliminate any duplicates from the list
        myMap.each { child ->
            if (!processedChildren.contains(child.value.netAddr)) {
                // Show LQI first and then device name
                if (neighborSortOrder != "2") { myText = myText + "${child.value.lqi}" + " - " + "${child.value.netAddr}" + separator }
                // Show device name first and then LQI
                if (neighborSortOrder == "2") { myText = myText + "${child.value.netAddr}" + " - " + "${child.value.lqi}" + separator }
                // Add the processed child to the list
                processedChildren.add(child.value.netAddr)
            }
        }
        
        if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
        log("updateDeviceAttributes:neighbors", "deviceChildren is: $myText", 1)
        if (myText != "") sendEvent(name: "deviceChildren", value:myText)
        else sendEvent(name: "deviceChildren", value:"None") 
        sendEvent(name: "deviceChildCount", value:children.size())
        //************** End of Children ********************** 
    
        //************** Start of Repeaters (Siblings & Parent) **********************
        // Filter the neighbors based on the 'relationship' NOT being 'Child'. This captures both Siblings and Parent.
        //Loop through the myMap which has been sorted and generate text output for the attribute.
        def repeaters = state.device.neighbors.findAll { entry -> 
            entry.value instanceof Map && entry.value.relationship != 'Child'
        }
        
        //Sort the results.
        if (neighborSortOrder == "0"){ myMap = repeaters.entrySet().sort { a, b -> b.value.lqi <=> a.value.lqi } }
        if (neighborSortOrder == "1"){ myMap = repeaters.entrySet().sort { a, b -> a.value.lqi <=> b.value.lqi } }
        if (neighborSortOrder == "2"){ myMap = repeaters.entrySet().sort { a, b -> a.value.netAddr <=> b.value.netAddr } }
        
        myText = ""
        processedRepeaters = [] // Initialize an empty list to track processed repeaters
        //Eliminate any duplicates from the list
        myMap.each { repeater ->
            isParent = false
            if (!processedRepeaters.contains(repeater.value.netAddr)) {
                //Identify if it is the Parent in which case we will append an *.
                if (repeater.value.relationship == "Parent") isParent = true
                // Show LQI first and then device name
                if (neighborSortOrder != "2") {
                    if (isParent == true ) myText = myText + "${repeater.value.lqi}" + " - " + "${repeater.value.netAddr}*" + separator
                    else myText = myText + "${repeater.value.lqi}" + " - " + "${repeater.value.netAddr}" + separator
                }
                // Show device name first and then LQI
                if (neighborSortOrder == "2") {
                    if (isParent == true ) myText = myText + "${repeater.value.netAddr}*" + " - " + "${repeater.value.lqi}" + separator
                    else myText = myText + "${repeater.value.netAddr}" + " - " + "${repeater.value.lqi}" + separator
                }
                // Add the processed repeaters to the list
                processedRepeaters.add(repeater.value.netAddr)
            }
        }
        
        if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
        log("updateDeviceAttributes:neighbors", "deviceRepeaters is: $myText", 1)
        if (myText != "") sendEvent(name: "deviceRepeaters", value:myText)
        else sendEvent(name: "deviceRepeaters", value:"None") 
        sendEvent(name: "deviceRepeatersCount", value:processedRepeaters.size())
		//************** End of Repeaters (Siblings & Parent) **********************
        
        
        
        //************** Start of Parent **********************
        // Initialize variables to store the Parent lqi and netAddr
        def parentLqi = null
        def parentNetAddr = null
        myText = "None"    
        // Iterate through the 'neighbors' map to find the entry with 'relationship' = 'Parent'
        state.device.neighbors.each { index, entry ->
            if (entry.relationship == 'Parent') { parentLqi = entry.lqi; parentNetAddr = entry.netAddr }
        }
        // Show LQI first and then device name
        if (neighborSortOrder != "2" && parentNetAddr != null) { myText = "${parentLqi}" + " - " + "${parentNetAddr}" }
        // Show device name first and then LQI
        if (neighborSortOrder == "2" && parentNetAddr != null) { myText = "${parentNetAddr}" + " - " + "${parentLqi}" }
        
        log("updateDeviceAttributes:neighbors", "Parent is: $myText", 1)
        if (myText != "") sendEvent(name: "deviceParent", value:myText)
        else sendEvent(name: "deviceParent", value:"None") 
        //************** End of Parent **********************
    
    } //End of Neighbors
    
    
    //************** Start of Routing **********************
    if (section == "routing"){
        myText = ""
        routeCount = 0
        if (routeSortOrder == "0") { myMap = state.device.routes.entrySet().sort { a, b -> a.value.destAddr <=> b.value.destAddr } }
        if (routeSortOrder == "1") { myMap = state.device.routes.entrySet().sort { a, b -> b.value.nextHop <=> a.value.nextHop } }

        // Loop through the myMap which has been sorted and generate text output for the attribute.
        log("updateDeviceAttributes:routing", "myMap is: $myMap", 2)

        myMap.each { map ->
            // Show only active routes
            if ((deviceShowRoutes == "0") && (map.value.routeStatus == "Active")) {
                routeCount = routeCount + 1
                if (routeSortOrder == "0") myText = myText + "${map.value.nextHop}" + " ➡ " + "${map.value.destAddr}" + separator
                if (routeSortOrder == "1") myText = myText + "${map.value.destAddr}" + " via " + "${map.value.nextHop}" + separator
              }
          // Show All Routes
            if (deviceShowRoutes == "1") {
                routeCount = routeCount + 1
                if (routeSortOrder == "0") myText = myText + "${map.value.nextHop}" + " ➡ " + "${map.value.destAddr}(${map.value.routeStatus})" + separator
                if (routeSortOrder == "1") myText = myText + "${map.value.destAddr}" + " via " + "${map.value.nextHop} (${map.value.routeStatus})" + separator
                
            }
        }

        // Remove the trailing separator
        if (myText.endsWith(separator)) { myText = myText[0..-(separator.length() + 1)] }
        log("updateDeviceAttributes:routing", "deviceRoutes is: $myText", 1)
        
        if (myText != "") sendEvent(name: "deviceRoutes", value:myText)
        else sendEvent(name: "deviceRoutes", value:"None")
        sendEvent(name: "deviceRouteCount", value:routeCount)
    }
    //************** End of Routing **********************

}

//*********************************************************************************************************************************************************************
//******
//****** Start of Utility Functions
//******
//*********************************************************************************************************************************************************************
//Reverses the Byte order of a string. Zigbee.swapOctets() only handles 2 octets
String reverseByteOrder(String input) {
    if (input.length() % 2 != 0) {
        throw new IllegalArgumentException("Input string should have an even number of characters.")
    }
    def output = []
    for (int i = input.length() - 2; i >= 0; i -= 2) {
        output << input[i..i+1]
    }   
    return output.join('')
}
                           
//Compare two firmware versions by comparing each part of the version number                        
def compareVersions(version1, version2) {
    def v1 = version1.tokenize('.').collect { it as Integer }
    def v2 = version2.tokenize('.').collect { it as Integer }

    // Compare each level of the version numbers
    for (int i = 0; i < Math.min(v1.size(), v2.size()); i++) {
        def compareResult = v1[i] <=> v2[i]
        if (compareResult != 0) {
            return compareResult
        }
    }
    // If all levels are equal, compare the length of version numbers
    return v1.size() <=> v2.size()
}
                           
//Log status messages
private log(name, message, int loglevel){
    int threshold = 0
    if ( settings.loglevel == "1" ) threshold = 1
    if ( settings.loglevel == "2" ) threshold = 2
    
    //This is a quick way to filter out messages based on loglevel
	if ( loglevel > threshold) {return}
    if ( loglevel == 0 ) { log.info ( "${name}(): " + message )  }
    if ( loglevel == 1 ) { log.trace ( "${name}(): " + message ) }
    if ( loglevel == 2) { log.debug ( "${name}(): " + message ) }
}

//Functions to enhance text appearance
String bold(s) { return "<b>$s</b>" }
String italic(s) { return "<i>$s</i>" }
String underline(s) { return "<u>$s</u>" }
String dodgerBlue(s) { return '<font color = "DodgerBlue">' + s + '</font>'}
String red(s) { return '<font color = "red">' + s + '</font>'}
String green(s) { return '<font color = "green">' + s + '</font>'}
String mark(s) { return '<mark>' + s + '</mark>'}
String markred(s) { return '<mark>' + red(s) + "</mark>" } 