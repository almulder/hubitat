/**
*  Tuya TS0601 Zigbee Garage Door Opener
*  (This is the one that uses a wired contact and plugs into mains power (No Battery))
*  (https://www.aliexpress.us/item/3256805221929910.html)
*
*  Could not locate a good driver, so needed to make one. 
*
*  Copyright 2024 Albert Mulder
*
*  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License. You may obtain a copy of the License at:
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
*  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*  for the specific language governing permissions an limitations under the License.
*
*  Version: 1.0
*/

import groovy.transform.Field

@Field static final int DEFAULT_DOOR_TIMEOUT = 30

metadata {
    definition(name: "Tuya TS0601 Zigbee Garage Door Opener", namespace: "almulder", author: "Albert MulderyourName") {
        capability "Configuration"
        capability "Refresh"
        capability "ContactSensor"

        attribute "doorStatus", "string"
        attribute "doorContact", "string"

        command "openDoor"
        command "closeDoor"

        fingerprint endpointId: "01", profileId: "0104", deviceId: "0100", inClusters: "0004,0005,EF00,0000", outClusters: "0019,000A", manufacturer: "_TZE204_nklqjk62", model: "TS0601"
    }

    preferences {
        input("errMsg", "hidden", title:"<b>Note:</b> Possible doorStatus:",description:"opened, opening, manually opened <br> closed, closing, manualy closed")           
        input name: "doorTimeout", type: "number", title: "Time needed to Open/Close Door (seconds)", description: "How long does the door take to open or close?", defaultValue: DEFAULT_DOOR_TIMEOUT, required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

def parse(String description) {
    if (logEnable) log.debug "Parsing message: ${description}"

    def event = zigbee.getEvent(description)
    if (event) {
        sendEvent(event)
    } else {
        if (description?.startsWith("catchall:")) {
            def descMap = zigbee.parseDescriptionAsMap(description)
            if (descMap?.clusterInt == 0xEF00) {
                handleCustomCluster(descMap)
            }
        }
    }
}

def handleCustomCluster(descMap) {
    def data = descMap.data
    if (data) {
        if (data[0] == "00" && data[2] == "03" && data[4] == "00" && data[5] == "01") {
            if (data[6] == "01") {
                if (logEnable) log.debug "Door opened"
                sendEvent(name: "doorContact", value: "open")
                if (state.openDoorTriggered != true) {
                    if (logEnable) log.debug "Door manually opened"
                    sendEvent(name: "doorStatus", value: "manually opened")
                }
            } else if (data[6] == "00") {
                if (logEnable) log.debug "Door closed"
                sendEvent(name: "doorContact", value: "closed")
                if (state.closeDoorTriggered != true) {
                    if (logEnable) log.debug "Door manually closed"
                    sendEvent(name: "doorStatus", value: "manually closed")
                }
            }
        }
    }
}

def openDoor() {
    if (txtEnable) log.info "Opening door"
    state.openDoorTriggered = true
    if (device.currentValue("doorContact") == "closed" && (device.currentValue("doorStatus") == "closed" || device.currentValue("doorStatus") == "manually closed")) {
        sendEvent(name: "doorStatus", value: "opening")
        def timeout = (doorTimeout ?: DEFAULT_DOOR_TIMEOUT).toInteger()
        if (logEnable) log.debug "Scheduling finishOpeningDoor to run in ${timeout} seconds"
        runIn(timeout, finishOpeningDoor)
        return [
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0xEF00 0x00 {0001010100010102010001010301000101}", "delay 200",
        ]
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "opening") {
        if (logEnable) log.debug "doorstatus already opening"
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "closing") {
        // Do nothing
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "opened") {
        // Do nothing
    } else if (device.currentValue("doorContact") == "open" &&
               device.currentValue("doorStatus") != "opened" &&
               device.currentValue("doorStatus") != "opening" &&
               device.currentValue("doorStatus") != "closing") {
        if (logEnable) log.debug "doorStatus unknown, setting to open to match contact"
        sendEvent(name: "doorStatus", value: "opened")
    } else if (device.currentValue("doorContact") == "closed" &&
               device.currentValue("doorStatus") != "opened" &&
               device.currentValue("doorStatus") != "opening" &&
               device.currentValue("doorStatus") != "closing") {
        if (logEnable) log.debug "doorStatus unknown, setting to closed to match contact"
        sendEvent(name: "doorStatus", value: "closed")
    }
    state.openDoorTriggered = false
}

def finishOpeningDoor() {
    if (logEnable) log.debug "Executing finishOpeningDoor"
    state.openDoorTriggered = true
    if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "opening") {
        if (logEnable) log.debug "Door contact is open, setting doorStatus to opened"
        sendEvent(name: "doorStatus", value: "opened")
        state.openDoorTriggered = false
    } else if (device.currentValue("doorContact") == "closed" && device.currentValue("doorStatus") == "opening") {
        if (logEnable) log.debug "Door contact is closed, setting doorStatus to opening error"
        sendEvent(name: "doorStatus", value: "opening error")
        state.openDoorTriggered = false
    }
}

def closeDoor() {
    if (txtEnable) log.info "Closing door"
    state.closeDoorTriggered = true
    if (device.currentValue("doorContact") == "open" && (device.currentValue("doorStatus") == "opened" || device.currentValue("doorStatus") == "manually opened")) {
        sendEvent(name: "doorStatus", value: "closing")
        def timeout = (doorTimeout ?: DEFAULT_DOOR_TIMEOUT).toInteger()
        if (logEnable) log.debug "Scheduling finishClosingDoor to run in ${timeout} seconds"
        runIn(timeout, finishClosingDoor)
        return [
            "he cmd 0x${device.deviceNetworkId} 0x${device.endpointId} 0xEF00 0x00 {0001010100010002010001000301000100}", "delay 200",
        ]
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "closing") {
        if (logEnable) log.debug "doorstatus already closing"
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "opening") {
        // Do nothing
    } else if (device.currentValue("doorContact") == "closed" && device.currentValue("doorStatus") == "closed") {
        // Do nothing
    } else if (device.currentValue("doorContact") == "open" &&
               device.currentValue("doorStatus") != "opened" &&
               device.currentValue("doorStatus") != "opening" &&
               device.currentValue("doorStatus") != "closing") {
        if (logEnable) log.debug "doorStatus unknown, setting to open to match contact"
        sendEvent(name: "doorStatus", value: "opened")
    } else if (device.currentValue("doorContact") == "closed" &&
               device.currentValue("doorStatus") != "opened" &&
               device.currentValue("doorStatus") != "opening" &&
               device.currentValue("doorStatus") != "closing") {
        if (logEnable) log.debug "doorStatus unknown, setting to closed to match contact"
        sendEvent(name: "doorStatus", value: "closed")
    }
    state.closeDoorTriggered = false
}

def finishClosingDoor() {
    state.closeDoorTriggered = true
    if (logEnable) log.debug "Executing finishClosingDoor"
    if (device.currentValue("doorContact") == "closed" && device.currentValue("doorStatus") == "closing") {
        if (logEnable) log.debug "Door contact is closed, setting doorStatus to closed"
        sendEvent(name: "doorStatus", value: "closed")
        state.closeDoorTriggered = false
    } else if (device.currentValue("doorContact") == "open" && device.currentValue("doorStatus") == "closing") {
        if (logEnable) log.debug "Door contact is open, setting doorStatus to closing error"
        sendEvent(name: "doorStatus", value: "closing error")
        state.closeDoorTriggered = false
    }
}

def installed() {
    if (logEnable) log.debug "Installed"
    refresh()
}

def updated() {
    if (logEnable) log.debug "Updated"
    refresh()
}

def configure() {
    if (logEnable) log.debug "Configuring"
    refresh()
}

def refresh() {
    if (logEnable) log.debug "Refreshing"
    zigbee.readAttribute(0x0100, 0x0006) + // Assuming 0x0100 is the cluster for door status and 0x0006 is the attribute for the door contact
    zigbee.readAttribute(0x0100, 0x0007) // Assuming 0x0100 is the cluster for door status and 0x0007 is the attribute for the door status
}
