
/**
*  Xfinity MCT-350 Zigbee Contact Sensor
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

metadata {
    definition (name: "Xfinity MCT-350 Zigbee Contact Sensor", namespace: "almulder", author: "Albert Mulder") {
        capability "Battery"
        capability "Contact Sensor"
        capability "Temperature Measurement"
        capability "Tamper Alert"
        capability "Configuration"
        capability "Refresh"
        capability "Health Check"

        fingerprint profileId: "0104", deviceId: "0107", inClusters: "0000,0001,0003,0020,0402,0500,0B05", outClusters: "0019", model: "URC4460BC0-X-R", manufacturer: "Universal Electronics Inc"
    }
}

preferences {
    input name: "debugLogging", type: "bool", title: "Enable Debug Logging", defaultValue: false
}

def parse(String description) {
    if (settings.debugLogging) {
        log.debug "Parsing '${description}'"
    }

    if (description?.startsWith('zone status')) {
        def status = description.split(" ")[2]
        handleZoneStatus(status)
    } else {
        def event = zigbee.getEvent(description)
        if (event) {
            if (settings.debugLogging) {
                log.debug "Parsed event: ${event}"
            }
            sendEvent(event)
        } else {
            if (settings.debugLogging) {
                log.debug "Parsing description as map"
            }
            def descMap = zigbee.parseDescriptionAsMap(description)
            if (settings.debugLogging) {
                log.debug "Parsed description map: ${descMap}"
            }
            if (descMap.clusterInt == zigbee.TEMPERATURE_MEASUREMENT_CLUSTER && descMap.attrInt == 0x0000) {
                def tempValue = Integer.parseInt(descMap.value, 16) / 100.0
                log.info "Temperature reported: ${tempValue}°C"
                sendEvent(name: "temperature", value: tempValue, unit: "C")
            } else if (descMap.clusterInt == zigbee.POWER_CONFIGURATION_CLUSTER && descMap.attrInt == 0x0020) {
                def batteryVoltage = Integer.parseInt(descMap.value, 16) / 10.0
                def minVolts = 2.1
                def maxVolts = 3.0
                def batteryPct = ((batteryVoltage - minVolts) / (maxVolts - minVolts)) * 100
                log.info "Battery voltage: ${batteryVoltage} V, Battery percentage: ${batteryPct.round(1)}%"
                sendEvent(name: "battery", value: batteryPct.round(1), unit: "%", isStateChange: true)
            } else {
                if (settings.debugLogging) {
                    log.debug "Unhandled cluster: ${descMap.clusterInt}, attribute: ${descMap.attrInt}"
                }
            }
        }
    }
}

def handleZoneStatus(String status) {
    if (settings.debugLogging) {
        log.debug "Handling zone status: ${status}"
    }
    def zoneStatus = Integer.parseInt(status.replace("0x", ""), 16)
    if (settings.debugLogging) {
        log.debug "Zone Status: ${zoneStatus}"
    }

    def contactState = ""
    def tamperState = ""

    if (zoneStatus == 0x0021 || zoneStatus == 0x0025) {
        contactState = "open"
    } else if (zoneStatus == 0x0020 || zoneStatus == 0x0024) {
        contactState = "closed"
    }

    if (zoneStatus == 0x0024 || zoneStatus == 0x0025) {
        tamperState = "detected"
    } else if (zoneStatus == 0x0020 || zoneStatus == 0x0021) {
        tamperState = "clear"
    }

    def currentContactState = device.currentValue("contact")
    def currentTamperState = device.currentValue("tamper")

    if (contactState && currentContactState != contactState) {
        log.info "Contact is ${contactState}"
        sendEvent(name: "contact", value: contactState, isStateChange: true, descriptionText: "Contact is ${contactState}")
    }

    if (tamperState && currentTamperState != tamperState) {
        log.info "Tamper ${tamperState}"
        sendEvent(name: "tamper", value: tamperState, isStateChange: true, descriptionText: "Tamper ${tamperState}")
    }
}

def refresh() {
    if (settings.debugLogging) {
        log.debug "Refreshing Values"
    }
    return zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, 0x0020) +
    zigbee.readAttribute(zigbee.IAS_ZONE_CLUSTER, 0x0002) +
    zigbee.readAttribute(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0x0000)
}

def configure() {
    if (settings.debugLogging) {
        log.debug "Configuring Reporting"
    }
 if (debugLogging) log.debug "Configuring"
    def configCmds = []
    if (device.hasCapability("Battery")) {
        configCmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 30, 21600, 0x01)
        configCmds += zigbee.readAttribute(0x0001, 0x0020)
    }
    if (device.hasCapability("Tamper Alert")) {
        configCmds += zigbee.configureReporting(0x0500, 0x0002, 0x18, 10, 600, null)
        configCmds += zigbee.readAttribute(0x0500, 0x0002)
    }
    if (device.hasCapability("Temperature Measurement")) {
        configCmds += zigbee.configureReporting(0x0402, 0x0000, 0x29, 30, 3600, 0x10)
        configCmds += zigbee.readAttribute(0x0402, 0x0000)
    }
    configCmds += zigbee.enrollResponse()
    return configCmds
	refresh()
}

def installed() {
    configure()
    if (settings.debugLogging) {
        log.debug "Installed"
    }
    sendEvent(name: "contact", value: "open", displayed: false)
    sendEvent(name: "tamper", value: "detected", displayed: false)
    sendEvent(name: "battery", value: 100, unit: "%", displayed: false)
    sendEvent(name: "temperature", value: 25.0, unit: "C", displayed: false)
    configure()
}

def updated() {
    configure()
    if (settings.debugLogging) {
        log.debug "Updated"
    }
    sendEvent(name: "contact", value: device.currentValue("contact") ?: "open", displayed: false)
    sendEvent(name: "tamper", value: device.currentValue("tamper") ?: "detected", displayed: false)
    sendEvent(name: "battery", value: device.currentValue("battery") ?: 100, unit: "%", displayed: false)
    sendEvent(name: "temperature", value: device.currentValue("temperature") ?: 25.0, unit: "C", displayed: false)
    configure()
}

def ping() {
    if (settings.debugLogging) {
        log.debug "Ping"
    }
    refresh()
}
